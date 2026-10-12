package com.visupicture.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 接口限流注解
 *
 * <p>基于 Redis 固定窗口计数实现。限流维度由 {@link #dimension()} 决定，
 * 键为 {@code visupicture:rate:{dimension}:{方法签名}:{标识}}。</p>
 *
 * <p>⚠️ 与 {@code @AuthCheck} 一样，本注解依赖 AOP，仅对<b>外部调用</b>生效；
 * 同类内部方法自调用不会触发（Spring AOP 代理机制所限）。</p>
 *
 * <p>⚠️ 标识取自客户端 IP / 用户 id。IP 可被伪造头影响（见 {@code IpUtils}），
 * 因此本注解用于<b>抗滥用</b>（防刷评论、防刷上传），不承担强安全边界职责。</p>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RateLimit {

    /**
     * 窗口内允许的最大请求数（作用于主维度：USER / IP）
     */
    int count() default 10;

    /**
     * 窗口大小（秒）
     */
    int period() default 60;

    /**
     * {@link Dimension#IP_AND_USER} 下 IP 维度的独立阈值。
     *
     * <p><b>为什么 IP 阈值要与用户阈值分开？</b>
     * 同一出口 IP 后面可能有大量正常用户（校园网、公司 NAT、运营商 CGNAT）。
     * 若两个维度用同一个很小的阈值，那么「每人每分钟 5 条评论」会退化成
     * 「这个 IP 后面所有人加起来每分钟 5 条」—— 一个活跃用户就能把同网段
     * 其他人全部误伤，而这与防刷的初衷无关。</p>
     *
     * <p>因此 IP 维度只承担「防止批量注册小号绕过用户级限制」的兜底职责，
     * 阈值显著放宽；精细控制交给用户维度。取值 {@code <= 0} 时默认取
     * {@code count * 6}。</p>
     */
    int ipCount() default 0;

    /**
     * 限流维度
     */
    Dimension dimension() default Dimension.IP;

    /**
     * 限流提示信息
     */
    String message() default "操作过于频繁，请稍后再试";

    /**
     * 触发限流后是否仍然放行（仅记日志，用于灰度观察；正式拦截时用 false）
     */
    boolean failOpen() default false;

    /**
     * 限流维度
     */
    enum Dimension {
        /**
         * 按客户端 IP 限流（未登录接口也能用）
         */
        IP,
        /**
         * 按登录用户 id 限流（更准确，但要求已登录）
         */
        USER,
        /**
         * IP + 用户双重限流（两者任一超限即拒绝）
         */
        IP_AND_USER
    }
}