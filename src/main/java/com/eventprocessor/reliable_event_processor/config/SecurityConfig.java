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
    private final ObjectProvider<com.eventprocessor.reliable_event_processor.security.TenantContextFilter> tenantFilterProvider;

    public SecurityConfig(ObjectProvider<JwtAndApiKeyAuthenticationFilter> authFilterProvider,
                          ObjectProvider<RestAuthenticationEntryPoint> authenticationEntryPointProvider,
                          ObjectProvider<RestAccessDeniedHandler> accessDeniedHandlerProvider,
                          ObjectProvider<com.eventprocessor.reliable_event_processor.security.TenantContextFilter> tenantFilterProvider) {
        this.authFilterProvider = authFilterProvider;
        this.authenticationEntryPointProvider = authenticationEntryPointProvider;
        this.accessDeniedHandlerProvider = accessDeniedHandlerProvider;
        this.tenantFilterProvider = tenantFilterProvider;
    }

    @Bean
    @ConditionalOnBean({JwtTokenProvider.class, AdminSecurityProperties.class})
    public JwtAndApiKeyAuthenticationFilter jwtAndApiKeyAuthenticationFilter(
            JwtTokenProvider jwtTokenProvider,
            AdminSecurityProperties adminSecurityProperties,
            ObjectProvider<com.eventprocessor.reliable_event_processor.service.ApiKeyService> apiKeyServiceProvider,
            com.fasterxml.jackson.databind.ObjectMapper objectMapper) {
        return new JwtAndApiKeyAuthenticationFilter(jwtTokenProvider, adminSecurityProperties, apiKeyServiceProvider, objectMapper);
    }

    @Bean
    public com.eventprocessor.reliable_event_processor.security.TenantContextFilter tenantContextFilter(
            ObjectProvider<com.eventprocessor.reliable_event_processor.service.TenantService> tenantServiceProvider,
            ObjectProvider<com.eventprocessor.reliable_event_processor.repository.UserRepository> userRepositoryProvider,
            com.fasterxml.jackson.databind.ObjectMapper objectMapper) {
        return new com.eventprocessor.reliable_event_processor.security.TenantContextFilter(
                tenantServiceProvider.getIfAvailable(),
                userRepositoryProvider.getIfAvailable(),
                objectMapper
        );
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
                .requestMatchers(HttpMethod.POST, "/api/v1/businesses/register").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/v1/events").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/v1/operations").permitAll()

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
                .requestMatchers(HttpMethod.POST, "/api/v1/operations/cleanup").hasRole("ADMIN")
                .requestMatchers(HttpMethod.POST, "/api/v1/operations/reconciliations/**").hasAnyRole("ADMIN", "OPERATOR")
                .requestMatchers("/api/v1/operations/**").hasAnyRole("ADMIN", "OPERATOR", "VIEWER")

                // API Key management: VIEWER can read, ADMIN and OPERATOR can create/rotate/revoke
                .requestMatchers(HttpMethod.GET, "/api/v1/api-keys", "/api/v1/api-keys/**").hasAnyRole("ADMIN", "OPERATOR", "VIEWER")
                .requestMatchers(HttpMethod.POST, "/api/v1/api-keys", "/api/v1/api-keys/*/rotate").hasAnyRole("ADMIN", "OPERATOR")
                .requestMatchers(HttpMethod.DELETE, "/api/v1/api-keys/**").hasAnyRole("ADMIN", "OPERATOR")

                // Integration management: VIEWER can read, ADMIN and OPERATOR can create/update/delete/test
                .requestMatchers(HttpMethod.GET, "/api/v1/integrations", "/api/v1/integrations/**").hasAnyRole("ADMIN", "OPERATOR", "VIEWER")
                .requestMatchers("/api/v1/integrations/**").hasAnyRole("ADMIN", "OPERATOR")

                // Webhook management: VIEWER can read, ADMIN and OPERATOR can create/delete/rotate
                .requestMatchers(HttpMethod.GET, "/api/v1/webhooks", "/api/v1/webhooks/**").hasAnyRole("ADMIN", "OPERATOR", "VIEWER")
                .requestMatchers("/api/v1/webhooks/**").hasAnyRole("ADMIN", "OPERATOR")

                // Deny by default for any unrecognized protected routes
                .anyRequest().denyAll()
        );

        JwtAndApiKeyAuthenticationFilter authFilter = authFilterProvider.getIfAvailable();
        if (authFilter != null) {
            http.addFilterBefore(authFilter, UsernamePasswordAuthenticationFilter.class);
        }

        com.eventprocessor.reliable_event_processor.security.TenantContextFilter tenantFilter =
                tenantFilterProvider.getIfAvailable();
        if (tenantFilter != null) {
            if (authFilter != null) {
                http.addFilterAfter(tenantFilter, JwtAndApiKeyAuthenticationFilter.class);
            } else {
                http.addFilterBefore(tenantFilter, UsernamePasswordAuthenticationFilter.class);
            }
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
        configuration.setAllowedHeaders(List.of("Authorization", "Content-Type", "X-Admin-API-Key", "X-API-Key", "X-Tenant-ID", "Accept"));
        configuration.setExposedHeaders(List.of("Authorization"));
        configuration.setAllowCredentials(false);
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
