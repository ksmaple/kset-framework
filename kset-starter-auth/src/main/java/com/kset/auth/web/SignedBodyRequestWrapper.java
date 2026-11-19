package com.kset.auth.web;

import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class SignedBodyRequestWrapper extends HttpServletRequestWrapper {

    private final byte[] body;
    private final Map<String, String[]> formParameters;

    SignedBodyRequestWrapper(HttpServletRequest request, int maxBytes) throws IOException {
        super(request);
        InputStream input = request.getInputStream();
        body = input.readNBytes(maxBytes);
        if (body.length == maxBytes && input.read() != -1) {
            throw new BodyTooLargeException();
        }
        formParameters = isFormRequest(request) ? parseForm(request, body) : null;
    }

    byte[] body() {
        return body;
    }

    Map<String, String> queryParameters() {
        Charset charset = getCharacterEncoding() != null
                ? Charset.forName(getCharacterEncoding()) : StandardCharsets.UTF_8;
        Map<String, List<String>> values = new LinkedHashMap<>();
        addFields(values, getQueryString(), charset);
        Map<String, String> result = new LinkedHashMap<>();
        values.forEach((key, items) -> {
            if (items.size() != 1) {
                throw new IllegalArgumentException("duplicate signed query parameter: " + key);
            }
            result.put(key, items.get(0));
        });
        return result;
    }

    @Override
    public Map<String, String[]> getParameterMap() {
        return formParameters != null ? formParameters : super.getParameterMap();
    }

    @Override
    public String getParameter(String name) {
        String[] values = getParameterValues(name);
        return values != null && values.length > 0 ? values[0] : null;
    }

    @Override
    public String[] getParameterValues(String name) {
        return formParameters != null ? formParameters.get(name) : super.getParameterValues(name);
    }

    @Override
    public Enumeration<String> getParameterNames() {
        return formParameters != null ? Collections.enumeration(formParameters.keySet())
                : super.getParameterNames();
    }

    @Override
    public int getContentLength() {
        return body.length;
    }

    @Override
    public long getContentLengthLong() {
        return body.length;
    }

    @Override
    public ServletInputStream getInputStream() {
        ByteArrayInputStream input = new ByteArrayInputStream(body);
        return new ServletInputStream() {
            @Override
            public int read() {
                return input.read();
            }

            @Override
            public boolean isFinished() {
                return input.available() == 0;
            }

            @Override
            public boolean isReady() {
                return true;
            }

            @Override
            public void setReadListener(ReadListener listener) {
                try {
                    if (!isFinished()) {
                        listener.onDataAvailable();
                    }
                    listener.onAllDataRead();
                } catch (IOException error) {
                    listener.onError(error);
                }
            }
        };
    }

    @Override
    public BufferedReader getReader() {
        String encoding = getCharacterEncoding();
        Charset charset = encoding != null ? Charset.forName(encoding) : StandardCharsets.UTF_8;
        return new BufferedReader(new InputStreamReader(getInputStream(), charset));
    }

    static final class BodyTooLargeException extends IOException {
    }

    private static boolean isFormRequest(HttpServletRequest request) {
        String contentType = request.getContentType();
        return contentType != null && contentType.toLowerCase(java.util.Locale.ROOT)
                .startsWith("application/x-www-form-urlencoded");
    }

    private static Map<String, String[]> parseForm(HttpServletRequest request, byte[] body) {
        Charset charset = request.getCharacterEncoding() != null
                ? Charset.forName(request.getCharacterEncoding()) : StandardCharsets.UTF_8;
        Map<String, List<String>> values = new LinkedHashMap<>();
        addFields(values, request.getQueryString(), charset);
        addFields(values, new String(body, charset), charset);
        Map<String, String[]> result = new LinkedHashMap<>();
        values.forEach((key, items) -> result.put(key, items.toArray(String[]::new)));
        return Collections.unmodifiableMap(result);
    }

    private static void addFields(Map<String, List<String>> values, String encoded, Charset charset) {
        if (encoded == null || encoded.isEmpty()) {
            return;
        }
        for (String field : encoded.split("&", -1)) {
            if (field.isEmpty()) {
                continue;
            }
            int separator = field.indexOf('=');
            String name = separator >= 0 ? field.substring(0, separator) : field;
            String value = separator >= 0 ? field.substring(separator + 1) : "";
            values.computeIfAbsent(URLDecoder.decode(name, charset), ignored -> new ArrayList<>())
                    .add(URLDecoder.decode(value, charset));
        }
    }
}
