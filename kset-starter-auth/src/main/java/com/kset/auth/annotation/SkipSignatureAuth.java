package com.kset.auth.annotation;

import com.kset.auth.core.AuthSchemes;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@SkipAuthScheme(AuthSchemes.SIGNATURE)
public @interface SkipSignatureAuth {
}
