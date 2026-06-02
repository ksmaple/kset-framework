package com.kset.auth.core;

import com.kset.auth.spi.LoginUserHeaderCodec;

import java.util.Map;

public final class AuthRequest {

    private final String path;
    private final LoginUserHeaderCodec.HeaderReader headerReader;
    private final String source;
    private final String method;
    private final Map<String, String> params;

    public AuthRequest(String path, LoginUserHeaderCodec.HeaderReader headerReader, String source) {
        this(path, headerReader, source, null, Map.of());
    }

    public AuthRequest(String path,
                       LoginUserHeaderCodec.HeaderReader headerReader,
                       String source,
                       String method,
                       Map<String, String> params) {
        this.path = path;
        this.headerReader = headerReader;
        this.source = source;
        this.method = method;
        this.params = params != null ? Map.copyOf(params) : Map.of();
    }

    public String getPath() {
        return path;
    }

    public LoginUserHeaderCodec.HeaderReader getHeaderReader() {
        return headerReader;
    }

    public String getSource() {
        return source;
    }

    public String getMethod() {
        return method;
    }

    public Map<String, String> getParams() {
        return params;
    }

    public String param(String name) {
        return name != null ? params.get(name) : null;
    }

    public String header(String name) {
        return headerReader != null ? headerReader.get(name) : null;
    }
}
