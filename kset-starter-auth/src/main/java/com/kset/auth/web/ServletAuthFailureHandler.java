package com.kset.auth.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

public interface ServletAuthFailureHandler {

    void handle(HttpServletRequest request, HttpServletResponse response, int code, String message);
}
