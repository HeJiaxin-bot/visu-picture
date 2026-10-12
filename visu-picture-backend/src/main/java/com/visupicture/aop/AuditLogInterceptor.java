package com.visupicture.aop;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.visupicture.annotation.AuditLog;
import com.visupicture.model.entity.User;
import com.visupicture.service.AuditLogService;
import com.visupicture.service.UserService;
import com.visupicture.utils.IpUtils;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 操作审计切面
 *
 * <p>{@code @Around} 记录业务方法的执行结果与耗时，业务抛异常时也会留痕
 * （{@code success=0} + {@code errorMessage}），然后原样抛出，不改变原有异常语义。</p>
 *
 * <p>所有落库动作委托给 {@link AuditLogService#recordQuietly}，
 * 该方法内部吞掉全部异常，确保审计故障不会放大成业务故障。</p>
 */
@Slf4j
@Aspect
@Component
public class AuditLogInterceptor {

    /**
     * detail 字段长度上限（与 audit_log.detail varchar(1024) 对齐，留出余量）
     */
    private static final int DETAIL_MAX = 1000;

    /**
     * userAgent 字段长度上限
     */
    private static final int UA_MAX = 500;

    @Resource
    private AuditLogService auditLogService;

    @Resource
    private UserService userService;

    @Around("@annotation(auditLog)")
    public Object doInterceptor(ProceedingJoinPoint joinPoint, AuditLog auditLog) throws Throwable {
        long start = System.currentTimeMillis();
        Object result = null;
        Throwable error = null;
        try {
            result = joinPoint.proceed();
            return result;
        } catch (Throwable t) {
            error = t;
            throw t;
        } finally {
            try {
                saveAuditLog(joinPoint, auditLog, error, System.currentTimeMillis() - start);
            } catch (Exception e) {
                // 双重保险：即使组装日志本身出错，也不能影响业务返回
                log.error("记录操作审计日志失败, module={}, action={}", auditLog.module(), auditLog.action(), e);
            }
        }
    }

    private void saveAuditLog(ProceedingJoinPoint joinPoint, AuditLog auditLog,
                              Throwable error, long costMs) {
        HttpServletRequest request = currentRequest();
        Long userId = null;
        String userAccount = null;
        if (request != null) {
            try {
                User loginUser = userService.getLoginUser(request);
                if (loginUser != null) {
                    userId = loginUser.getId();
                    userAccount = loginUser.getUserAccount();
                }
            } catch (Exception ignored) {
                // 未登录 / 会话失效：审计仍要记录，只是操作人为空
            }
        }

        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        Object[] args = joinPoint.getArgs();

        auditLogService.recordQuietly(
                userId,
                userAccount,
                auditLog.module(),
                auditLog.action(),
                StrUtil.blankToDefault(auditLog.targetType(), null),
                resolveTargetId(auditLog, method, args),
                buildDetail(auditLog, method, args, error),
                request == null ? null : IpUtils.getClientIp(request),
                request == null ? null : StrUtil.maxLength(request.getHeader("User-Agent"), UA_MAX),
                error == null,
                error == null ? null : StrUtil.maxLength(error.getMessage(), 500),
                costMs
        );
    }

    /**
     * 解析操作目标 id：
     * 1. 指定了 targetIdParam 则优先取该参数（支持实体对象的 getId / getPictureId 等）；
     * 2. 否则按常见命名在参数里找。
     */
    private Long resolveTargetId(AuditLog auditLog, Method method, Object[] args) {
        if (args == null || args.length == 0) {
            return null;
        }
        String[] names = parameterNames(method);
        if (StrUtil.isNotBlank(auditLog.targetIdParam())) {
            for (int i = 0; i < names.length; i++) {
                if (auditLog.targetIdParam().equals(names[i])) {
                    return extractId(args[i]);
                }
            }
        }
        // 兜底：按常见字段名依次尝试
        String[] candidates = {"id", "pictureId", "spaceId", "userId", "notificationId", "commentId"};
        for (String candidate : candidates) {
            for (int i = 0; i < names.length; i++) {
                if (candidate.equals(names[i])) {
                    Long id = extractId(args[i]);
                    if (id != null) {
                        return id;
                    }
                }
            }
        }
        // 最后从实体参数的 getter 里找
        for (Object arg : args) {
            Long id = extractIdFromBean(arg);
            if (id != null) {
                return id;
            }
        }
        return null;
    }

    /**
     * 从参数值取 id：直接是数字则返回；是实体/DTO 则调 getId 及常见 id getter
     */
    private Long extractId(Object arg) {
        if (arg == null) {
            return null;
        }
        if (arg instanceof Number) {
            return ((Number) arg).longValue();
        }
        return extractIdFromBean(arg);
    }

    private Long extractIdFromBean(Object bean) {
        if (bean == null || bean instanceof Number || bean instanceof CharSequence) {
            return null;
        }
        String[] getters = {"getId", "getPictureId", "getSpaceId", "getUserId"};
        for (String getter : getters) {
            try {
                Method m = bean.getClass().getMethod(getter);
                Object value = m.invoke(bean);
                if (value instanceof Number) {
                    long v = ((Number) value).longValue();
                    if (v > 0) {
                        return v;
                    }
                }
            } catch (Exception ignored) {
                // 没有该 getter，继续尝试下一个
            }
        }
        return null;
    }

    /**
     * 构造 detail：描述 + 方法名 + 关键参数（截断到字段上限）
     */
    private String buildDetail(AuditLog auditLog, Method method, Object[] args, Throwable error) {
        Map<String, Object> detail = new LinkedHashMap<>();
        if (StrUtil.isNotBlank(auditLog.description())) {
            detail.put("desc", auditLog.description());
        }
        detail.put("method", method.getDeclaringClass().getSimpleName() + "." + method.getName());
        Map<String, Object> params = new LinkedHashMap<>();
        String[] names = parameterNames(method);
        for (int i = 0; i < names.length && i < args.length; i++) {
            Object value = args[i];
            // 请求对象与文件流不记录
            if (value instanceof HttpServletRequest || value instanceof org.springframework.web.multipart.MultipartFile) {
                continue;
            }
            params.put(names[i], value);
        }
        if (!params.isEmpty()) {
            detail.put("params", params);
        }
        String json;
        try {
            json = JSONUtil.toJsonStr(detail);
        } catch (Exception e) {
            json = String.valueOf(detail);
        }
        return StrUtil.maxLength(json, DETAIL_MAX);
    }

    /**
     * 取方法参数名。若编译时未保留参数名（{@code -parameters} 未开启）则返回 arg0、arg1…
     */
    private String[] parameterNames(Method method) {
        try {
            org.springframework.core.DefaultParameterNameDiscoverer discoverer =
                    new org.springframework.core.DefaultParameterNameDiscoverer();
            String[] names = discoverer.getParameterNames(method);
            if (names != null && names.length > 0) {
                return names;
            }
        } catch (Exception ignored) {
            // 落到下面的兜底
        }
        int count = method.getParameterCount();
        String[] fallback = new String[count];
        for (int i = 0; i < count; i++) {
            fallback[i] = "arg" + i;
        }
        return fallback;
    }

    private HttpServletRequest currentRequest() {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        if (attributes instanceof ServletRequestAttributes) {
            return ((ServletRequestAttributes) attributes).getRequest();
        }
        return null;
    }
}