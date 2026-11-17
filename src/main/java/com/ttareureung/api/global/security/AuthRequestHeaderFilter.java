package com.ttareureung.api.global.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpMethod;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerExceptionResolver;

import java.io.IOException;

public class AuthRequestHeaderFilter extends OncePerRequestFilter {

    public static final String HEADER_NAME = "X-Requested-With";
    public static final String HEADER_VALUE = "XMLHttpRequest";
    private final RequestMatcher cookieAuthPost = new OrRequestMatcher(
            PathPatternRequestMatcher.withDefaults().matcher(HttpMethod.POST, "/api/v1/auth/refresh"),
            PathPatternRequestMatcher.withDefaults().matcher(HttpMethod.POST, "/api/v1/auth/logout"));
    private final HandlerExceptionResolver exceptionResolver;

    public AuthRequestHeaderFilter(HandlerExceptionResolver exceptionResolver) {
        this.exceptionResolver = exceptionResolver;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        if (cookieAuthPost.matches(request) && !HEADER_VALUE.equals(request.getHeader(HEADER_NAME))) {
            exceptionResolver.resolveException(request, response, null,
                    new AccessDeniedException("인증 요청 필수 헤더 누락"));
            return;
        }
        filterChain.doFilter(request, response);
    }
}
