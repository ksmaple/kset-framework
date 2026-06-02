package com.kset.auth.core;

import com.kset.auth.spi.LoginUserHeaderCodec;

public final class AuthRequest {

    private final String path;
    private final LoginUserHeaderCodec.HeaderReader headerReader;
    private final String source;

    public AuthRequest(String path, LoginUserHeaderCodec.HeaderReader headerReader, String source) {
        this.path = path;
        this.headerReader = headerReader;
        this.source = source;
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

    public String header(String name) {
        return headerReader != null ? headerReader.get(name) : null;
    }
}
