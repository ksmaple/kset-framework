package com.kset.auth.core;

import com.kset.auth.config.KsetAuthProperties;
import com.kset.common.auth.AuthHeaders;
import org.springframework.util.AntPathMatcher;

public class AuthRuleResolver {

    public static final String SOURCE_WEB = "web";
    public static final String SOURCE_GATEWAY = "gateway";

    private final KsetAuthProperties properties;
    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    public AuthRuleResolver(KsetAuthProperties properties) {
        this.properties = properties;
    }

    public AuthRuleMatch resolve(AuthRequest request) {
        KsetAuthProperties.AuthRule rule = findRule(request.getPath());
        if (rule != null) {
            return buildMatch(rule, request);
        }
        String subject = defaultSubject(request);
        if (isPublicPath(request)) {
            return new AuthRuleMatch("public", subject, AuthSchemes.NONE,
                    defaultTokenHeader(request), AuthHeaders.loginContextHeader(subject));
        }
        return new AuthRuleMatch("default", subject, defaultScheme(request),
                defaultTokenHeader(request), AuthHeaders.loginContextHeader(subject));
    }

    private KsetAuthProperties.AuthRule findRule(String path) {
        return properties.getRules().stream()
                .filter(rule -> rule.getPaths().stream().anyMatch(pattern -> pathMatcher.match(pattern, path)))
                .findFirst()
                .orElse(null);
    }

    private AuthRuleMatch buildMatch(KsetAuthProperties.AuthRule rule, AuthRequest request) {
        String subject = hasText(rule.getSubject()) ? rule.getSubject() : defaultSubject();
        String scheme = hasText(rule.getScheme()) ? rule.getScheme() : defaultScheme(request);
        String tokenHeader = hasText(rule.getTokenHeader()) ? rule.getTokenHeader() : defaultTokenHeader(request);
        String trustedHeader = hasText(rule.getTrustedHeaderName())
                ? rule.getTrustedHeaderName()
                : AuthHeaders.loginContextHeader(subject);
        return new AuthRuleMatch(rule.getName(), subject, scheme, tokenHeader, trustedHeader);
    }

    private boolean isPublicPath(AuthRequest request) {
        if (SOURCE_GATEWAY.equals(request.getSource())) {
            return properties.getGateway().getPublicPaths().stream()
                    .anyMatch(pattern -> pathMatcher.match(pattern, request.getPath()));
        }
        return properties.getWeb().getPublicPaths().stream()
                .anyMatch(pattern -> pathMatcher.match(pattern, request.getPath()));
    }

    private String defaultSubject() {
        return hasText(properties.getDefaultSubject()) ? properties.getDefaultSubject() : "app";
    }

    private String defaultSubject(AuthRequest request) {
        String override = request.header(properties.getSubjectHeader());
        return hasText(override) ? override : defaultSubject();
    }

    private String defaultScheme(AuthRequest request) {
        if (SOURCE_WEB.equals(request.getSource())
                && properties.getWeb().getMode() == KsetAuthProperties.Mode.TRUSTED_HEADER
                && AuthSchemes.SESSION.equalsIgnoreCase(properties.getDefaultScheme())) {
            return AuthSchemes.TRUSTED_HEADER;
        }
        return hasText(properties.getDefaultScheme()) ? properties.getDefaultScheme() : AuthSchemes.SESSION;
    }

    private String defaultTokenHeader(AuthRequest request) {
        if (SOURCE_WEB.equals(request.getSource())
                && !AuthHeaders.SESSION_TOKEN.equals(properties.getWeb().getTokenHeader())) {
            return properties.getWeb().getTokenHeader();
        }
        if (SOURCE_GATEWAY.equals(request.getSource())
                && !AuthHeaders.SESSION_TOKEN.equals(properties.getGateway().getTokenHeader())) {
            return properties.getGateway().getTokenHeader();
        }
        return hasText(properties.getDefaultTokenHeader()) ? properties.getDefaultTokenHeader() : AuthHeaders.SESSION_TOKEN;
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
