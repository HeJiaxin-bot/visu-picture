package com.visupicture.aop;

import cn.hutool.core.util.StrUtil;
import com.visupicture.annotation.RateLimit;
import com.visupicture.exception.BusinessException;
import com.visupicture.exception.ErrorCode;
import com.visupicture.model.entity.User;
import com.visupicture.service.UserService;
import com.visupicture.utils.IpUtils;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.concurrent.TimeUnit;

/**
 * 接口限流切面（Redis 固定窗口计数）
 *
 * <p>实现要点：</p>
 * <ul>
 *   <li>用 {@code INCR} 做原子自增，返回 1 时顺手设置过期时间 —— 这样计数与过期
 *       只在窗口第一个请求上发生一次，不存在「先读后写」的竞态。</li>
 *   <li>{@code EXPIRE} 失败不影响计数（最坏是键永不过期，下次仍会被 INCR 命中）；
 *       但为避免极端情况下键泄漏，兜底再补一次过期设置。</li>
 *   <li>Redis 不可用时按 {@link RateLimit#failOpen()} 处理：默认拒绝（安全优先），
 *       配置 failOpen=true 则放行并记日志。</li>
 * </ul>
 */
@Slf4j
@Aspect
@Component
public class RateLimitInterceptor {

    private static final String KEY_PREFIX = "visupicture:rate:";

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private UserService userService;

    @Around("@annotation(rateLimit)")
    public Object doInterceptor(ProceedingJoinPoint joinPoint, RateLimit rateLimit) throws Throwable {
        HttpServletRequest request = currentRequest();
        // 方法签名作为键的一部分：不同接口各自独立计数
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        String methodKey = signature.getDeclaringType().getSimpleName() + "." + signature.getName();

        String ip = request == null ? "unknown" : IpUtils.getClientIp(request);
        Long userId = tryGetUserId(request);

        boolean blocked = false;
        boolean redisDown = false;
        try {
            switch (rateLimit.dimension()) {
                case IP:
                    blocked = overLimit(methodKey + ":ip:" + ip, rateLimit.count(), rateLimit.period());
                    break;
                case USER:
                    // 未登录时退化为按 IP 限流，避免 USER 维度对匿名请求完全失效
                    blocked = userId != null
                            ? overLimit(methodKey + ":user:" + userId, rateLimit.count(), rateLimit.period())
                            : overLimit(methodKey + ":ip:" + ip, rateLimit.count(), rateLimit.period());
                    break;
                case IP_AND_USER:
                default:
                    // IP 维度用放宽后的阈值：同一出口 IP 背后可能有大量正常用户
                    // （校园网 / 公司 NAT / 运营商 CGNAT），小阈值会误伤无关用户。
                    // 它只负责兜底防"批量小号绕过用户级限制"，精细控制交给用户维度。
                    int ipLimit = rateLimit.ipCount() > 0 ? rateLimit.ipCount() : rateLimit.count() * 6;
                    blocked = overLimit(methodKey + ":ip:" + ip, ipLimit, rateLimit.period());
                    if (!blocked && userId != null) {
                        blocked = overLimit(methodKey + ":user:" + userId, rateLimit.count(), rateLimit.period());
                    }
                    break;
            }
        } catch (Exception e) {
            // Redis 异常：不能因为限流组件故障就阻断业务，按 failOpen 语义处理
            redisDown = true;
            log.error("限流检查失败（Redis 不可用？），method={}, dimension={}",
                    methodKey, rateLimit.dimension(), e);
        }

        if (redisDown) {
            if (rateLimit.failOpen()) {
                return joinPoint.proceed();
            }
            // 默认安全策略：限流组件不可用则拒绝，避免防护形同虚设
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "服务繁忙，请稍后再试");
        }
        if (blocked) {
            log.warn("触发限流，method={}, ip={}, userId={}, limit={}/{}s",
                    methodKey, ip, userId, rateLimit.count(), rateLimit.period());
            throw new BusinessException(ErrorCode.TOO_MANY_REQUEST, rateLimit.message());
        }
        return joinPoint.proceed();
    }

    /**
     * 固定窗口计数：INCR 后判断是否超限
     *
     * @param limit 该维度的阈值
     * @return true 表示超限
     */
    private boolean overLimit(String keySuffix, int limit, int period) {
        String key = KEY_PREFIX + keySuffix;
        Long current = stringRedisTemplate.opsForValue().increment(key);
        if (current == null) {
            return false;
        }
        if (current == 1L) {
            // 窗口内第一个请求，设定过期时间，窗口结束自动清理
            stringRedisTemplate.expire(key, period, TimeUnit.SECONDS);
        } else {
            // 兜底：若因异常导致键没有过期时间，补设一次，防止键无限堆积
            Long ttl = stringRedisTemplate.getExpire(key, TimeUnit.SECONDS);
            if (ttl == null || ttl < 0) {
                stringRedisTemplate.expire(key, period, TimeUnit.SECONDS);
            }
        }
        return current > limit;
    }

    /**
     * 尽力获取当前登录用户 id，未登录返回 null（不抛异常，便于匿名接口使用限流）
     */
    private Long tryGetUserId(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        try {
            User loginUser = userService.getLoginUser(request);
            return loginUser == null ? null : loginUser.getId();
        } catch (Exception e) {
            return null;
        }
    }

    private HttpServletRequest currentRequest() {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        if (attributes instanceof ServletRequestAttributes) {
            return ((ServletRequestAttributes) attributes).getRequest();
        }
        return null;
    }

    /**
     * 拼接限流键的可读描述（仅用于日志排查）
     */
    static String describe(String methodKey, String ip, Long userId) {
        return StrUtil.format("{} ip={} userId={}", methodKey, ip, userId);
    }
}