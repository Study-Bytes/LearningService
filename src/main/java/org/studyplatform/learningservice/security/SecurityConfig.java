package org.studyplatform.learningservice.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/health/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/learn/my-courses").authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/v1/learn/submissions/*").authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/v1/learn/courses/*/items/*/submissions").authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/v1/learn/courses/*/items/*").authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/v1/learn/courses/*").authenticated()
                        .requestMatchers(HttpMethod.POST, "/api/v1/learn/courses/*/items/*/run").authenticated()
                        .requestMatchers(HttpMethod.POST, "/api/v1/learn/courses/*/items/*/submit").authenticated()
                        .requestMatchers(HttpMethod.POST, "/api/v1/learn/courses/*/enroll").authenticated()
                        .requestMatchers(HttpMethod.POST, "/api/v1/learning/tasks/*/submissions").authenticated()
                        .anyRequest().permitAll()
                )
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(Customizer.withDefaults()));

        return http.build();
    }
}
