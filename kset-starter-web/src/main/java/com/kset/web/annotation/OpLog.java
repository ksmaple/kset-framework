package com.kset.web.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 操作日志注解
 *
 * <p>标记在 Controller 方法上，由 starter-web 的 AOP 切面自动记录操作日志。
 *
 * <p>示例：
 * <pre>{@code
 * @OpLog(type = "CREATE", target = "document")
 * public ApiResponse<IdResponse> create(@RequestBody CreateDocumentCommand command) { ... }
 * }</pre>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface OpLog {

    /** 操作类型，如 CREATE / UPDATE / DELETE / LOGIN / LOGOUT / EXPORT / IMPORT */
    String type();

    /** 目标对象类型，如 document / user / role / permission */
    String target() default "";

    /** 目标对象ID SpEL 表达式，如 "#command.id" 或 "#request.id" */
    String targetId() default "";

    /** 旧目标名称表达式；为避免记录个人信息，日志不再输出该值 */
    @Deprecated
    String targetName() default "";

    /** 是否在 DEBUG 中记录与 HTTP 请求日志相同的脱敏参数 */
    boolean recordParams() default false;

    /** 是否在 DEBUG 中记录响应类型；不记录完整响应 */
    boolean recordResult() default false;
}
