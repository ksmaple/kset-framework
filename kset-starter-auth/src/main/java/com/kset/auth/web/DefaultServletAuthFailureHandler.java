package com.kset.auth.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

public class DefaultServletAuthFailureHandler implements ServletAuthFailureHandler {

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response, int code, String message) {
        response.setStatus(HttpStatus.OK.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        String body = "{\"code\":" + code + ",\"message\":\"" + escape(message) + "\",\"data\":null}";
        try {
            response.getWriter().write(body);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to write auth failure response", e);
        }
    }

    private static String escape(String value) {
        return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
