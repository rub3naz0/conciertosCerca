package com.rubenazo.buscaConciertos.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Locale;
import java.util.Set;

/**
 * Stateless CSRF defense for HTTP Basic auth. Browsers replay cached Basic
 * credentials on cross-site requests and SameSite does not apply to them, so
 * a mutating request must prove it is not a CORS "simple request": either it
 * carries a custom header or a JSON content type — both force a cross-origin
 * preflight, which fails because no CORS mappings are configured.
 */
@Component
public class CsrfHardeningFilter extends OncePerRequestFilter {

    static final String REQUIRED_HEADER = "X-Requested-With";

    private static final Set<String> SAFE_METHODS = Set.of("GET", "HEAD", "OPTIONS", "TRACE");

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (SAFE_METHODS.contains(request.getMethod()) || isNonSimpleRequest(request)) {
            chain.doFilter(request, response);
            return;
        }
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType("application/json");
        response.getWriter().write(
            "{\"error\":\"Mutating requests require the " + REQUIRED_HEADER
                + " header or a JSON content type\",\"status\":403}");
    }

    private static boolean isNonSimpleRequest(HttpServletRequest request) {
        if (request.getHeader(REQUIRED_HEADER) != null) {
            return true;
        }
        String contentType = request.getContentType();
        return contentType != null && contentType.toLowerCase(Locale.ROOT).startsWith("application/json");
    }
}
