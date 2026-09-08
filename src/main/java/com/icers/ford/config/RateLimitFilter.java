package com.icers.ford.config;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
public class RateLimitFilter extends OncePerRequestFilter {

    // Cache de buckets por IP — um bucket por endereço IP
    private final ConcurrentHashMap<String, Bucket> bucketsPorIp =
            new ConcurrentHashMap<>();

    private final int requestsPerMinute;

    public RateLimitFilter(int requestsPerMinute) {
        this.requestsPerMinute = requestsPerMinute;
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain chain
    ) throws ServletException, IOException {

        // Aplica rate limiting apenas em endpoints da API
        // Swagger e health check ficam livres
        String uri = request.getRequestURI();
        if (isEndpointExcluido(uri)) {
            chain.doFilter(request, response);
            return;
        }

        String ip = extrairIp(request);
        Bucket bucket = bucketsPorIp.computeIfAbsent(ip, this::criarBucket);

        ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);

        if (probe.isConsumed()) {
            // Adiciona header informativo com requisições restantes
            response.addHeader("X-RateLimit-Remaining",
                    String.valueOf(probe.getRemainingTokens()));
            chain.doFilter(request, response);
        } else {
            // Calcula segundos para o bucket recarregar — arredonda pra
            // cima (ceiling division), nunca mostra "0 segundos" quando
            // ainda falta uma fração de segundo de espera real.
            long nanosParaEsperar = probe.getNanosToWaitForRefill();
            long retryAfterSeconds =
                    (nanosParaEsperar + 999_999_999L) / 1_000_000_000L;

            log.warn("Rate limit excedido por IP: {} | endpoint: {} | retry-after: {}s",
                    ip, uri, retryAfterSeconds);

            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setContentType("application/json;charset=UTF-8");
            response.addHeader("Retry-After",
                    String.valueOf(retryAfterSeconds));
            response.addHeader("X-RateLimit-Remaining", "0");

            response.getWriter().write(
                    "{" +
                            "\"codigo_erro\":\"RATE_LIMIT_EXCEEDED\"," +
                            "\"mensagem\":\"Limite de requisições excedido. " +
                            "Aguarde " + retryAfterSeconds + " segundos.\"," +
                            "\"endpoint\":\"" + uri + "\"" +
                            "}"
            );
        }
    }

    /**
     * Cria um novo bucket para o IP com a capacidade configurada.
     * Capacidade: requestsPerMinute tokens.
     * Recarga: requestsPerMinute tokens por minuto (1 token por segundo aprox).
     */
    private Bucket criarBucket(String ip) {
        Bandwidth limite = Bandwidth.builder()
                .capacity(requestsPerMinute)
                .refillGreedy(requestsPerMinute, Duration.ofMinutes(1))
                .build();

        return Bucket.builder()
                .addLimit(limite)
                .build();
    }

    private boolean isEndpointExcluido(String uri) {
        return uri.startsWith("/swagger-ui") ||
                uri.startsWith("/v3/api-docs") ||
                uri.startsWith("/error") ||
                uri.equals("/");
    }

    private String extrairIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}