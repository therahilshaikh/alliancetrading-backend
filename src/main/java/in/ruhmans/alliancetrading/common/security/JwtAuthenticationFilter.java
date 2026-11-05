package in.ruhmans.alliancetrading.common.security;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

import in.ruhmans.alliancetrading.auth.service.JwtService;
import in.ruhmans.alliancetrading.auth.service.SessionService;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final SessionService sessionService;

    public JwtAuthenticationFilter(JwtService jwtService, SessionService sessionService) {
        this.jwtService = jwtService;
        this.sessionService = sessionService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {

        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) {
            chain.doFilter(request, response);
            return;
        }

        String token = header.substring(7);
        JwtService.TokenClaims claims = jwtService.parse(token);
        if (claims == null) {
            chain.doFilter(request, response);
            return;
        }

        // Validate session is still active
        if (claims.sessionId() == null || !sessionService.isSessionActive(claims.sessionId())) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/problem+json");
            response.getWriter().write(
                    "{\"type\":\"about:blank\",\"title\":\"SESSION REPLACED\","
                    + "\"status\":401,\"detail\":\"Session has been revoked\","
                    + "\"code\":\"SESSION_REPLACED\"}");
            return;
        }

        AppPrincipal principal = new AppPrincipal(
                claims.subjectId(), claims.sessionId(), claims.role(), claims.type());

        List<SimpleGrantedAuthority> authorities = resolveAuthorities(claims.role(), claims.type());
        var auth = new UsernamePasswordAuthenticationToken(principal, null, authorities);
        SecurityContextHolder.getContext().setAuthentication(auth);

        chain.doFilter(request, response);
    }

    private List<SimpleGrantedAuthority> resolveAuthorities(String role, String type) {
        return switch (type) {
            case "USER" -> List.of(new SimpleGrantedAuthority("ROLE_USER"));
            case "ADMIN" -> switch (role) {
                case "SUPER_ADMIN" -> List.of(
                        new SimpleGrantedAuthority("ROLE_ADMIN"),
                        new SimpleGrantedAuthority("ROLE_SUPER_ADMIN"));
                default -> List.of(new SimpleGrantedAuthority("ROLE_ADMIN"));
            };
            default -> List.of();
        };
    }
}
