package com.visupicture.manager.auth;

import com.visupicture.constant.UserConstant;
import com.visupicture.model.entity.User;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

/**
 * Sa-Token 空间会话自愈拦截器
 *
 * <p><b>要解决的问题</b>：登录时系统写入了两套彼此独立的登录态 ——</p>
 * <ul>
 *   <li><b>HttpSession</b>（Spring Session，Cookie 名 {@code SESSION}）：
 *       页面判断「是否已登录」、以及 {@code getLoginUser()} 的依据，是真正的登录态来源；</li>
 *   <li><b>Sa-Token</b>（Cookie 名 {@code satoken}）：
 *       仅用于 {@code @SaSpaceCheckPermission} 的空间权限校验。</li>
 * </ul>
 *
 * <p>两者生命周期互不感知，一旦 Sa-Token 侧失效而 HttpSession 仍有效，就会出现
 * 「页面显示已登录，但在空间里点创建 / AI 配文时被踢回登录页」——
 * 因为上传接口的空间校验抛 {@code NotLoginException}（40100），
 * 前端响应拦截器收到 40100 即跳转登录页。</p>
 *
 * <p>Sa-Token 侧失效的现实诱因很多，且都不是 HttpSession 的问题：
 * Cookie 被浏览器清理、换用 127.0.0.1 与 localhost 导致域名不匹配、
 * {@code satoken} Cookie 因 {@code Secure} 属性在非 HTTPS 下被丢弃、Redis 抖动等。</p>
 *
 * <p><b>自愈做法</b>：请求进入时若「HttpSession 有登录态」且「Sa-Token 无登录态」，
 * 就按 HttpSession 里的用户补建 Sa-Token 会话（顺带回写 {@code satoken} Cookie）。
 * 由于 Sa-Token 的 {@code space} 账号体系本就是登录时从同一个 userId 派生出来的，
 * 这里只是把派生结果重建，不引入任何新的权限，
 * 因此不存在越权风险（HttpSession 仍是唯一的登录态事实来源）。</p>
 *
 * <p>执行顺序必须在 Sa-Token 拦截器之前（见 {@link annotation.SaTokenConfigure} 的 order(-1)），
 * 否则注解鉴权会先抛异常，补建就来不及了。</p>
 */
@Slf4j
@Component
public class SpaceSessionSyncInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        try {
            // 1. Sa-Token 侧已登录：无需处理
            if (StpKit.SPACE.getLoginIdDefaultNull() != null) {
                return true;
            }
            // 2. 取 HttpSession（不新建）：没有会话说明确实未登录
            HttpSession session = request.getSession(false);
            if (session == null) {
                return true;
            }
            Object loginState = session.getAttribute(UserConstant.USER_LOGIN_STATE);
            if (!(loginState instanceof User)) {
                return true;
            }
            User user = (User) loginState;
            if (user.getId() == null) {
                return true;
            }
            // 3. HttpSession 有效但 Sa-Token 缺失：按同一 userId 补建
            StpKit.SPACE.login(user.getId());
            StpKit.SPACE.getSession().set(UserConstant.USER_LOGIN_STATE, user);
            log.info("检测到 Sa-Token 空间会话缺失，已依据 HttpSession 自动补建, userId={}", user.getId());
        } catch (Exception e) {
            // 旁路修复：补建失败不应阻断请求，让其按原有流程走（该报未登录就报未登录）
            log.warn("Sa-Token 空间会话自愈失败", e);
        }
        return true;
    }
}
