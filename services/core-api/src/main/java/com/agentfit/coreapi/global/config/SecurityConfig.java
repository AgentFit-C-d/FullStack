package com.agentfit.coreapi.global.config;

import com.agentfit.coreapi.domain.auth.service.GitHubOAuth2UserService;

import java.io.IOException;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.session.jdbc.config.annotation.web.http.EnableJdbcHttpSession;
import org.springframework.session.web.http.CookieSerializer;
import org.springframework.session.web.http.DefaultCookieSerializer;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Configuration
@EnableWebSecurity
@EnableJdbcHttpSession(maxInactiveIntervalInSeconds = 7 * 24 * 60 * 60)
public class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http,
            GitHubOAuth2UserService githubOAuth2UserService) throws Exception {
        CookieCsrfTokenRepository csrfRepository = CookieCsrfTokenRepository.withHttpOnlyFalse();
        csrfRepository.setCookieName("XSRF-TOKEN");
        csrfRepository.setCookiePath("/");
        csrfRepository.setHeaderName("X-XSRF-TOKEN");

        CsrfTokenRequestAttributeHandler csrfHandler = new CsrfTokenRequestAttributeHandler();
        http
            .csrf(csrf -> csrf
                .csrfTokenRepository(csrfRepository)
                .csrfTokenRequestHandler(csrfHandler))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/health", "/api/oauth2/authorization/**", "/api/login/oauth2/code/**",
                    "/api/auth/csrf", "/api/auth/get-session").permitAll()
                .requestMatchers("/api/auth/logout").authenticated()
                .anyRequest().authenticated())
            .exceptionHandling(exceptions -> exceptions
                .authenticationEntryPoint(new JsonAuthenticationEntryPoint())
                .accessDeniedHandler((request, response, denied) -> writeError(response, 403, "CSRF_INVALID")))
            .oauth2Login(oauth -> oauth
                .authorizationEndpoint(endpoint -> endpoint.baseUri("/api/oauth2/authorization"))
                .redirectionEndpoint(endpoint -> endpoint.baseUri("/api/login/oauth2/code/*"))
                .userInfoEndpoint(endpoint -> endpoint.userService(githubOAuth2UserService))
                .successHandler(redirectHandler("/?login=success"))
                .failureHandler((request, response, exception) -> response.sendRedirect("/?login=failed")))
            .logout(logout -> logout
                .logoutUrl("/api/auth/logout")
                .invalidateHttpSession(true)
                .clearAuthentication(true)
                .deleteCookies("AGENTFIT_SESSION")
                .logoutSuccessHandler((request, response, authentication) -> response.setStatus(204)));

        return http.build();
    }

    @Bean
    CookieSerializer sessionCookieSerializer(
            @Value("${agentfit.auth.cookie-secure:true}") boolean secureCookie) {
        DefaultCookieSerializer serializer = new DefaultCookieSerializer();
        serializer.setCookieName("AGENTFIT_SESSION");
        serializer.setCookiePath("/api");
        serializer.setUseHttpOnlyCookie(true);
        serializer.setUseSecureCookie(secureCookie);
        serializer.setSameSite("Lax");
        serializer.setCookieMaxAge(7 * 24 * 60 * 60);
        return serializer;
    }

    private AuthenticationSuccessHandler redirectHandler(String location) {
        return (request, response, authentication) -> response.sendRedirect(location);
    }

    private static void writeError(HttpServletResponse response, int status, String code) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json");
        response.getWriter().write("{\"code\":\"" + code + "\"}");
    }

    private static final class JsonAuthenticationEntryPoint implements AuthenticationEntryPoint {
        @Override
        public void commence(HttpServletRequest request, HttpServletResponse response,
                AuthenticationException authException) throws IOException {
            writeError(response, 401, "UNAUTHENTICATED");
        }
    }
}



