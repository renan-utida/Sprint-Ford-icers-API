package com.icers.ford.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingResponseWrapper;

import java.io.IOException;
import java.util.Set;

@Slf4j
public class RequestLoggingFilter extends OncePerRequestFilter {

    /**
     * Headers que NUNCA devem aparecer nos logs.
     * Authorization contém o JWT — nunca loga.
     */
    private static final Set<String> HEADERS_SENSIVEIS = Set.of(
            "authorization",
            "cookie",
            "x-api-key"
    );

    /**
     * Endpoints excluídos do log de request
     * (muito verbosos e sem valor de auditoria)
     */
    private static final Set<String> ENDPOINTS_EXCLUIDOS = Set.of(
            "/swagger-ui",
            "/v3/api-docs",
            "/error",
            "/favicon.ico"
    );

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain chain
    ) throws ServletException, IOException {

        String uri = request.getRequestURI();

        // Não loga endpoints excluídos
        if (isEndpointExcluido(uri)) {
            chain.doFilter(request, response);
            return;
        }

        long inicio = System.currentTimeMillis();

        // Wrap do response para capturar o status depois do processamento
        ContentCachingResponseWrapper responseWrapper =
                new ContentCachingResponseWrapper(response);

        try {
            chain.doFilter(request, responseWrapper);
        } finally {
            long tempoMs = System.currentTimeMillis() - inicio;
            int status = responseWrapper.getStatus();

            // Log estruturado — sem dados sensíveis
            log.info("REQUEST | método={} | endpoint={} | status={} | tempoMs={} | ip={}",
                    request.getMethod(),
                    uri,
                    status,
                    tempoMs,
                    extrairIp(request)
            );

            // WARN para respostas de erro do cliente (4xx) exceto 401/403
            // que já são logados pelo Spring Security
            if (status >= 400 && status < 500
                    && status != 401 && status != 403) {
                log.warn("ERRO_CLIENTE | método={} | endpoint={} | status={} | ip={}",
                        request.getMethod(), uri, status, extrairIp(request));
            }

            // ERROR para falhas do servidor (5xx)
            if (status >= 500) {
                log.error("ERRO_SERVIDOR | método={} | endpoint={} | status={} | ip={}",
                        request.getMethod(), uri, status, extrairIp(request));
            }

            // Copia o response de volta para o stream original
            responseWrapper.copyBodyToResponse();
        }
    }

    private boolean isEndpointExcluido(String uri) {
        return ENDPOINTS_EXCLUIDOS.stream()
                .anyMatch(uri::startsWith);
    }

    private String extrairIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}