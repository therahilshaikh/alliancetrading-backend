package in.ruhmans.alliancetrading.common.security;

import java.net.URI;

import com.fasterxml.jackson.databind.ObjectMapper;

import in.ruhmans.alliancetrading.common.config.SecurityProperties;
import in.ruhmans.alliancetrading.common.config.StorageProperties;

import jakarta.servlet.http.HttpServletResponse;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@EnableConfigurationProperties({SecurityProperties.class, StorageProperties.class})
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtFilter;
    private final ObjectMapper objectMapper;

    public SecurityConfig(JwtAuthenticationFilter jwtFilter, ObjectMapper objectMapper) {
        this.jwtFilter = jwtFilter;
        this.objectMapper = objectMapper;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .cors(cors -> {})
            .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                // CORS preflight carries no credentials
                .requestMatchers(HttpMethod.OPTIONS, "/api/**").permitAll()
                // Public endpoints
                .requestMatchers("/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**", "/actuator/health", "/error").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/v1/app/version").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/v1/auth/otp/request").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/v1/auth/otp/verify").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/v1/auth/refresh").permitAll()
                // Admin auth (must come before /api/v1/admin/**)
                .requestMatchers(HttpMethod.POST, "/api/v1/admin/auth/login").permitAll()
                // Logout (any authenticated user or admin)
                .requestMatchers(HttpMethod.POST, "/api/v1/auth/logout").authenticated()
                // Admin endpoints
                .requestMatchers("/api/v1/admin/**").hasAnyRole("ADMIN", "SUPER_ADMIN")
                // User endpoints
                .requestMatchers("/api/v1/**").hasRole("USER")
                .anyRequest().denyAll()
            )
            .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class)
            .exceptionHandling(ex -> ex
                .authenticationEntryPoint((request, response, authException) -> {
                    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                    response.setContentType("application/problem+json");
                    ProblemDetail pd = ProblemDetail.forStatusAndDetail(
                            HttpStatus.UNAUTHORIZED, "Authentication required");
                    pd.setProperty("code", "UNAUTHENTICATED");
                    pd.setType(URI.create("about:blank"));
                    response.getWriter().write(objectMapper.writeValueAsString(pd));
                })
                .accessDeniedHandler((request, response, accessDeniedException) -> {
                    response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                    response.setContentType("application/problem+json");
                    ProblemDetail pd = ProblemDetail.forStatusAndDetail(
                            HttpStatus.FORBIDDEN, "Access denied");
                    pd.setProperty("code", "FORBIDDEN");
                    pd.setType(URI.create("about:blank"));
                    response.getWriter().write(objectMapper.writeValueAsString(pd));
                })
            );
        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }
}
