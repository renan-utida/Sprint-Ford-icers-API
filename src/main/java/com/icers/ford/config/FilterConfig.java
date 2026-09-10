package com.icers.ford.config;

import com.icers.ford.util.IpResolver;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

@Configuration
public class FilterConfig {

    @Value("${ratelimit.ip.requests-per-minute:60}")
    private int requestsPerMinute;

    private final IpResolver ipResolver;

    public FilterConfig(IpResolver ipResolver) {
        this.ipResolver = ipResolver;
    }

    /**
     * Registra o RateLimitFilter com prioridade alta —
     * executa antes do JwtAuthFilter e do Spring Security.
     * Isso garante que o limite por IP é aplicado mesmo
     * em requisições não autenticadas (ex: brute force no /login).
     */
    @Bean
    public FilterRegistrationBean<RateLimitFilter> rateLimitFilter() {
        FilterRegistrationBean<RateLimitFilter> registration =
                new FilterRegistrationBean<>(new RateLimitFilter(requestsPerMinute, ipResolver));
        registration.addUrlPatterns("/api/*");
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return registration;
    }

    /**
     * Registra o RequestLoggingFilter após o RateLimitFilter —
     * loga metodo, endpoint, status e tempo de cada requisição.
     * Nunca loga headers sensíveis (Authorization, Cookie).
     */
    @Bean
    public FilterRegistrationBean<RequestLoggingFilter> requestLoggingFilter() {
        FilterRegistrationBean<RequestLoggingFilter> registration =
                new FilterRegistrationBean<>(new RequestLoggingFilter());
        registration.addUrlPatterns("/api/*");
        registration.setOrder(2);
        return registration;
    }
}