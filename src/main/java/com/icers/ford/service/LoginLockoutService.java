package com.icers.ford.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Deque;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;

/**
 * Bloqueio temporário de login por CONTA (email tentado) — não por IP.
 * <p>
 * Bloquear por IP criaria um risco de negação de serviço coletiva: uma
 * rede compartilhada (escritório, wifi público) inteira ficaria travada
 * por causa de uma única pessoa errando a senha repetidamente. Bloquear
 * pela conta afeta só quem está de fato tentando acessar aquele email.
 * <p>
 * Estado em memória, mesmo padrão dos buckets do Bucket4j usados no
 * rate limiting — não sobrevive a um restart da aplicação, o que é
 * aceitável dado que a janela de bloqueio é de segundos, não dias.
 */
@Slf4j
@Service
public class LoginLockoutService {

    private static final int LIMITE_FALHAS = 5;
    private static final Duration JANELA_FALHAS = Duration.ofMinutes(10);
    private static final Duration DURACAO_BLOQUEIO = Duration.ofSeconds(30);

    private final Map<String, Deque<Instant>> falhasRecentes = new ConcurrentHashMap<>();
    private final Map<String, Instant> bloqueios = new ConcurrentHashMap<>();

    /**
     * Registra uma tentativa de login falha para o email. Se isso fizer
     * a conta ultrapassar o limite dentro da janela, ativa o bloqueio.
     */
    public void registrarFalha(String email) {
        Instant agora = Instant.now();

        Deque<Instant> falhas = falhasRecentes.computeIfAbsent(
                email, k -> new ConcurrentLinkedDeque<>()
        );
        falhas.addLast(agora);

        Instant limiteJanela = agora.minus(JANELA_FALHAS);
        while (!falhas.isEmpty() && falhas.peekFirst().isBefore(limiteJanela)) {
            falhas.pollFirst();
        }

        if (falhas.size() >= LIMITE_FALHAS) {
            Instant bloqueadoAte = agora.plus(DURACAO_BLOQUEIO);
            bloqueios.put(email, bloqueadoAte);
            log.warn("Conta temporariamente bloqueada — email: {} | até: {}",
                    mascararEmail(email), bloqueadoAte);
        }
    }

    /**
     * Login bem-sucedido limpa qualquer histórico de falha da conta —
     * comportamento padrão de bloqueio temporário.
     */
    public void registrarSucesso(String email) {
        falhasRecentes.remove(email);
        bloqueios.remove(email);
    }

    /**
     * Retorna quantos segundos faltam de bloqueio para este email, ou
     * vazio se a conta não está bloqueada (nunca esteve, ou o bloqueio
     * já expirou).
     */
    public Optional<Long> segundosRestantesDeBloqueio(String email) {
        Instant bloqueadoAte = bloqueios.get(email);
        if (bloqueadoAte == null) {
            return Optional.empty();
        }

        Instant agora = Instant.now();
        if (!agora.isBefore(bloqueadoAte)) {
            bloqueios.remove(email);
            return Optional.empty();
        }

        long segundos = Duration.between(agora, bloqueadoAte).toSeconds() + 1;
        return Optional.of(segundos);
    }

    private String mascararEmail(String email) {
        int arroba = email.indexOf('@');
        return arroba > 0 ? "***" + email.substring(arroba) : "***";
    }
}