package com.kset.auth.web;

import com.kset.auth.config.KsetAuthProperties;
import com.kset.auth.core.AuthRequest;
import com.kset.auth.core.AuthResult;
import com.kset.auth.core.AuthRuleResolver;
import com.kset.auth.core.LoginAuthService;
import com.kset.common.auth.LoginContext;
import com.kset.common.auth.LoginContextSnapshot;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;

public class LoginAuthFilter extends OncePerRequestFilter {

    private final KsetAuthProperties properties;
    private final LoginAuthService authService;
    private final ServletAuthFailureHandler failureHandler;

    public LoginAuthFilter(KsetAuthProperties properties,
                           LoginAuthService authService,
                           ServletAuthFailureHandler failureHandler) {
        this.properties = properties;
        this.authService = authService;
        this.failureHandler = failureHandler;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !properties.getWeb().isEnabled();
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        if (LoginContext.currentUser().isPresent()) {
            filterChain.doFilter(request, response);
            return;
        }
        LoginContextSnapshot previous = LoginContext.capture();
        try {
            AuthResult result = authService.authenticate(new AuthRequest(
                    request.getRequestURI(),
                    request::getHeader,
                    AuthRuleResolver.SOURCE_WEB,
                    request.getMethod(),
                    queryParams(request)));
            if (result.isPermitAll()) {
                filterChain.doFilter(request, response);
                return;
            }
            if (!result.isAuthenticated() || result.getUser().isEmpty()) {
                failureHandler.handle(request, response, result.getCode(), result.getMessage());
                return;
            }
            LoginContext.bind(result.getUser().get());
            filterChain.doFilter(request, response);
        } finally {
            LoginContext.restore(previous);
        }
    }

    private static Map<String, String> queryParams(HttpServletRequest request) {
        return request.getParameterMap().entrySet().stream()
                .filter(entry -> entry.getValue() != null && entry.getValue().length > 0)
                .collect(Collectors.toMap(Map.Entry::getKey,
                        entry -> Arrays.stream(entry.getValue())
                                .filter(value -> value != null && !value.isBlank())
                                .findFirst()
                                .orElse(""),
                        (left, right) -> left));
    }
}
