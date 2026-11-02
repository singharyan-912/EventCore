package com.eventprocessor.reliable_event_processor.config;

import com.eventprocessor.reliable_event_processor.security.JwtAndApiKeyAuthenticationFilter;
import com.eventprocessor.reliable_event_processor.security.JwtTokenProvider;
import com.eventprocessor.reliable_event_processor.security.RestAccessDeniedHandler;
import com.eventprocessor.reliable_event_processor.security.RestAuthenticationEntryPoint;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private final ObjectProvider<JwtAndApiKeyAuthenticationFilter> authFilterProvider;
    private final ObjectProvider<RestAuthenticationEntryPoint> authenticationEntryPointProvider;
    private final ObjectProvider<RestAccessDeniedHandler> accessDeniedHandlerProvider;

    public SecurityConfig(ObjectProvider<JwtAndApiKeyAuthenticationFilter> authFilterProvider,
                          ObjectProvider<RestAuthenticationEntryPoint> authenticationEntryPointProvider,
                          ObjectProvider<RestAccessDeniedHandler> accessDeniedHandlerProvider) {
        this.authFilterProvider = authFilterProvider;
        this.authenticationEntryPointProvider = authenticationEntryPointProvider;
        this.accessDeniedHandlerProvider = accessDeniedHandlerProvider;
    }

    @Bean
    @ConditionalOnBean({JwtTokenProvider.class, AdminSecurityProperties.class})
    public JwtAndApiKeyAuthenticationFilter jwtAndApiKeyAuthenticationFilter(
            JwtTokenProvider jwtTokenProvider,
            AdminSecurityProperties adminSecurityProperties) {
        return new JwtAndApiKeyAuthenticationFilter(jwtTokenProvider, adminSecurityProperties);
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                // Stateless REST API with JWT Bearer tokens does not use cookies/session CSRF
                .csrf(AbstractHttpConfigurer::disable)
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .headers(headers -> headers
                        .frameOptions(frame -> frame.deny())
                        .contentTypeOptions(contentType -> {})
                );

        RestAuthenticationEntryPoint authenticationEntryPoint = authenticationEntryPointProvider.getIfAvailable();
        RestAccessDeniedHandler accessDeniedHandler = accessDeniedHandlerProvider.getIfAvailable();

        http.exceptionHandling(exceptions -> {
            if (authenticationEntryPoint != null) {
                exceptions.authenticationEntryPoint(authenticationEntryPoint);
            }
            if (accessDeniedHandler != null) {
                exceptions.accessDeniedHandler(accessDeniedHandler);
            }
        });

        http.authorizeHttpRequests(auth -> auth
                // Public endpoints
                .requestMatchers(HttpMethod.GET, "/api/v1/health").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/v1/auth/login").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/v1/events").permitAll()

                // Actuator observability endpoints
                .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
                .requestMatchers("/actuator/info").permitAll()
                .requestMatchers("/actuator/metrics", "/actuator/metrics/**").permitAll()

                // User profile and session management
                .requestMatchers("/api/v1/auth/me", "/api/v1/auth/logout").authenticated()

                // Administrator only endpoints
                .requestMatchers("/api/v1/admin/users/**").hasRole("ADMIN")
                .requestMatchers("/api/v1/operations/audit-logs/**").hasRole("ADMIN")

                // Dead-letter management: ADMIN and OPERATOR
                .requestMatchers(HttpMethod.POST, "/api/v1/admin/dead-letters/*/reprocess").hasAnyRole("ADMIN", "OPERATOR")
                .requestMatchers("/api/v1/admin/dead-letters/**").hasAnyRole("ADMIN", "OPERATOR")

                // Operations & metrics inspection: ADMIN, OPERATOR, and VIEWER
                .requestMatchers("/api/v1/operations/**").hasAnyRole("ADMIN", "OPERATOR", "VIEWER")

                // Deny by default for any unrecognized protected routes
                .anyRequest().denyAll()
        );

        JwtAndApiKeyAuthenticationFilter authFilter = authFilterProvider.getIfAvailable();
        if (authFilter != null) {
            http.addFilterBefore(authFilter, UsernamePasswordAuthenticationFilter.class);
        }

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration authenticationConfiguration) throws Exception {
        return authenticationConfiguration.getAuthenticationManager();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(List.of("http://localhost:3000", "http://localhost:8080"));
        configuration.setAllowedMethods(List.of("GET", "POST", "PATCH", "PUT", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("Authorization", "Content-Type", "X-Admin-API-Key", "Accept"));
        configuration.setExposedHeaders(List.of("Authorization"));
        configuration.setAllowCredentials(false);
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
