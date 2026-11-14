package com.example.generalmcp.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.regex.Pattern;

/**
 * Rejects requests coming from web pages of other origins (DNS rebinding protection).
 * Requests without an Origin header (CLI MCP clients, curl) are allowed; the server listens on 127.0.0.1 only anyway.
 */
@Component
public class LocalOriginFilter extends OncePerRequestFilter {

    private static final Pattern LOCAL_ORIGIN = Pattern.compile("^https?://(localhost|127\\.0\\.0\\.1|\\[::1])(:\\d+)?$");

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String origin = request.getHeader(HttpHeaders.ORIGIN);
        if (origin != null && !LOCAL_ORIGIN.matcher(origin).matches()) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN, "Origin not allowed");
            return;
        }
        chain.doFilter(request, response);
    }
}
