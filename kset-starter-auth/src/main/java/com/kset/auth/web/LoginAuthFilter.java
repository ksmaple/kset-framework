package com.kset.auth.web;

import com.kset.auth.config.KsetAuthProperties;
import com.kset.auth.core.AuthAnnotationSupport;
import com.kset.auth.core.AuthRequest;
import com.kset.auth.core.AuthResult;
import com.kset.auth.core.AuthRuleMatch;
import com.kset.auth.core.AuthRuleResolver;
import com.kset.auth.core.LoginAuthService;
import com.kset.common.auth.LoginContext;
import com.kset.common.auth.LoginContextSnapshot;
import com.kset.common.logging.LogMaskingUtil;
import com.kset.common.monitor.Monitor;
import com.kset.common.trace.TraceHeaders;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerExecutionChain;
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.web.util.pattern.PathPattern;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
public class LoginAuthFilter extends OncePerRequestFilter {

    private final KsetAuthProperties properties;
    private final LoginAuthService authService;
    private final ServletAuthFailureHandler failureHandler;
    private final List<HandlerMapping> handlerMappings;

    public LoginAuthFilter(KsetAuthProperties properties,
                           LoginAuthService authService,
                           ServletAuthFailureHandler failureHandler) {
        this(properties, authService, failureHandler, List.of());
    }

    public LoginAuthFilter(KsetAuthProperties properties,
                           LoginAuthService authService,
                           ServletAuthFailureHandler failureHandler,
                           List<HandlerMapping> handlerMappings) {
        this.properties = properties;
        this.authService = authService;
        this.failureHandler = failureHandler;
        this.handlerMappings = handlerMappings != null ? List.copyOf(handlerMappings) : List.of();
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
        HandlerMethod handlerMethod = resolveHandlerMethod(request);
        if (shouldSkipAuth(handlerMethod)) {
            filterChain.doFilter(request, response);
            return;
        }
        AuthRequest authRequest = new AuthRequest(
                request.getRequestURI(),
                request::getHeader,
                AuthRuleResolver.SOURCE_WEB,
                request.getMethod(),
                queryParams(request));
        AuthRuleMatch match = authService.resolve(authRequest);
        if (shouldSkipAuthScheme(handlerMethod, match)) {
            filterChain.doFilter(request, response);
            return;
        }
        LoginContextSnapshot previous = LoginContext.capture();
        try {
            AuthResult result = authService.authenticate(authRequest, match);
            if (result.isPermitAll()) {
                filterChain.doFilter(request, response);
                return;
            }
            if (!result.isAuthenticated() || result.getUser().isEmpty()) {
                try {
                    log.warn("auth result=REJECTED source=web method={} path={} rule={} subjectType={} businessCode={} traceId={}",
                            request.getMethod(), route(request), match != null ? match.getName() : null,
                            match != null ? match.getSubject() : null, result.getCode(), Monitor.currentTraceId().orElse(null));
                } catch (RuntimeException ignored) {
                    // Authentication response must not depend on logging.
                }
                failureHandler.handle(request, response, result.getCode(), result.getMessage());
                return;
            }
            var user = result.getUser().get();
            LoginContext.bind(user);
            request.setAttribute(TraceHeaders.LOGIN_USER_ID_ATTRIBUTE, user.getUserId());
            request.setAttribute(TraceHeaders.LOGIN_SUBJECT_TYPE_ATTRIBUTE, user.getSubjectType());
            filterChain.doFilter(request, response);
        } finally {
            LoginContext.restore(previous);
        }
    }

    private String route(HttpServletRequest request) {
        Object pattern = request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
        if (pattern instanceof String value && !value.isBlank()) {
            return value;
        }
        if (pattern instanceof PathPattern value) {
            return value.getPatternString();
        }
        return LogMaskingUtil.maskFieldValue("path", request.getRequestURI());
    }

    private HandlerMethod resolveHandlerMethod(HttpServletRequest request) throws ServletException {
        for (HandlerMapping handlerMapping : handlerMappings) {
            HandlerExecutionChain chain;
            try {
                chain = handlerMapping.getHandler(request);
            } catch (Exception e) {
                throw new ServletException("Failed to resolve auth handler", e);
            }
            if (chain == null || !(chain.getHandler() instanceof HandlerMethod handlerMethod)) {
                continue;
            }
            return handlerMethod;
        }
        return null;
    }

    private boolean shouldSkipAuth(HandlerMethod handlerMethod) {
        return handlerMethod != null
                && AuthAnnotationSupport.shouldSkipAuth(handlerMethod.getMethod(), handlerMethod.getBeanType());
    }

    private boolean shouldSkipAuthScheme(HandlerMethod handlerMethod, AuthRuleMatch match) {
        return handlerMethod != null
                && match != null
                && AuthAnnotationSupport.shouldSkipScheme(
                handlerMethod.getMethod(), handlerMethod.getBeanType(), match.getScheme());
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
