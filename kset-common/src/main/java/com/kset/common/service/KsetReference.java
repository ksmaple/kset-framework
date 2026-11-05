package com.kset.common.service;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** 注入本地 Spring 服务；宿主机提供远端适配器时可引用远端服务。 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface KsetReference {

    String group() default "";

    String version() default "";
}
