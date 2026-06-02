package com.kset.auth.core;

public final class AuthRuleMatch {

    private final String name;
    private final String subject;
    private final String scheme;
    private final String tokenHeader;
    private final String trustedHeaderName;

    public AuthRuleMatch(String name, String subject, String scheme, String tokenHeader, String trustedHeaderName) {
        this.name = name;
        this.subject = normalize(subject);
        this.scheme = normalize(scheme);
        this.tokenHeader = tokenHeader;
        this.trustedHeaderName = trustedHeaderName;
    }

    public String getName() {
        return name;
    }

    public String getSubject() {
        return subject;
    }

    public String getScheme() {
        return scheme;
    }

    public String getTokenHeader() {
        return tokenHeader;
    }

    public String getTrustedHeaderName() {
        return trustedHeaderName;
    }

    private static String normalize(String value) {
        return value != null && !value.isBlank() ? value.trim().toLowerCase() : value;
    }
}
