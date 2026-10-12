package com.visupicture.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 操作审计注解
 *
 * <p>标注在需要留痕的写操作接口上，由 {@code AuditLogInterceptor} 记录
 * 操作人、模块、动作、目标、IP、耗时与成败。</p>
 *
 * <p>设计原则：<b>审计失败绝不能影响业务</b>。切面在业务方法执行完毕后
 * 异步/静默落库，任何异常只记日志。</p>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface AuditLog {

    /**
     * 业务模块：picture / user / space / notification / audit
     */
    String module();

    /**
     * 操作类型：delete / update / review / create / grant ...
     */
    String action();

    /**
     * 目标类型（可选）：picture / space / user ...
     */
    String targetType() default "";

    /**
     * 目标 id 的取法：从方法参数中按名字取值。
     * <ul>
     *   <li>填了 {@code targetIdParam}：从对应参数（或其 {@code getId()} 方法）取值。</li>
     *   <li>留空：尝试从任意参数中取 {@code id} / {@code pictureId} / {@code spaceId} 字段。</li>
     * </ul>
     */
    String targetIdParam() default "";

    /**
     * 操作描述（可选，会写入 detail）
     */
    String description() default "";
}