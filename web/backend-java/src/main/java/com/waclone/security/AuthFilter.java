package com.waclone.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Optional;

@Component
public class AuthFilter extends OncePerRequestFilter {

    public static final String USER_ATTR = "username";

    private final JwtService jwt;

    public AuthFilter(JwtService jwt) {
        this.jwt = jwt;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {
        Optional<String> sub = jwt.verify(bearer(req));
        if (sub.isEmpty() && "GET".equalsIgnoreCase(req.getMethod())) {
            sub = jwt.verify(req.getParameter("token"));
        }
        sub.ifPresent(u -> req.setAttribute(USER_ATTR, u));

        if (isProtected(req) && req.getAttribute(USER_ATTR) == null) {
            res.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            res.setContentType("application/json");
            res.getWriter().write("{\"error\":\"unauthorized\"}");
            return;
        }
        chain.doFilter(req, res);
    }

    private static String bearer(HttpServletRequest req) {
        String header = req.getHeader("Authorization");
        return (header != null && header.startsWith("Bearer ")) ? header.substring(7).trim() : null;
    }

    private boolean isProtected(HttpServletRequest req) {
        if ("OPTIONS".equalsIgnoreCase(req.getMethod())) return false;
        String path = req.getRequestURI();
        return path.startsWith("/api/")
                && !path.startsWith("/api/auth/")

                && !path.startsWith("/api/internal/");
    }
}
