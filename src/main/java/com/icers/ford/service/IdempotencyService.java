package com.icers.ford.service;

import com.icers.ford.dto.response.SpecResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Guarda a resposta de requisições já processadas, identificadas por
 * uma Idempotency-Key enviada pelo cliente (header opcional) — se a
 * MESMA requisição for reenviada com a mesma chave (ex: o cliente teve
 * timeout de rede e não sabe se a primeira tentativa foi processada),
 * devolve a resposta já calculada, sem reprocessar nada — sem nova
 * chamada ao Gemini, sem nova gravação no histórico.
 * <p>
 * Diferente do índice único em sr_fichas_tecnicas (V7): aquele protege
 * contra dado duplicado para o MESMO VEÍCULO, não importa quem
 * perguntou. Isto protege contra a MESMA REQUISIÇÃO (identificada pela
 * chave que o próprio cliente gerou) sendo executada mais de uma vez.
 * <p>
 * Estado em memória, com expiração — não sobrevive a um restart da
 * aplicação. Aceitável pro propósito: proteger contra retry de rede de
 * curto prazo, não ser um registro permanente (isso já existe, é o
 * histórico de consultas em si).
 */
@Slf4j
@Service
public class IdempotencyService {

    private static final long EXPIRACAO_SEGUNDOS = 24 * 60 * 60; // 24h

    private record Entrada(SpecResponse resposta, Instant expiraEm) {}

    private final Map<String, Entrada> respostasSalvas = new ConcurrentHashMap<>();

    /**
     * Se a chave já foi vista antes (e ainda não expirou), devolve a
     * resposta salva daquela vez. Caso contrário, vazio — a requisição
     * deve seguir o fluxo normal.
     */
    public Optional<SpecResponse> buscar(String chave) {
        if (chave == null || chave.isBlank()) {
            return Optional.empty();
        }

        limparExpirados();

        Entrada entrada = respostasSalvas.get(chave);
        if (entrada == null) {
            return Optional.empty();
        }

        log.info("Idempotency-Key reconhecida — devolvendo resposta já processada, sem reprocessar: {}",
                chave);
        return Optional.of(entrada.resposta());
    }

    /**
     * Registra o resultado de uma requisição sob essa chave, para que
     * uma repetição exata (mesma chave) não seja reprocessada.
     */
    public void salvar(String chave, SpecResponse resposta) {
        if (chave == null || chave.isBlank()) {
            return;
        }
        respostasSalvas.put(
                chave,
                new Entrada(resposta, Instant.now().plusSeconds(EXPIRACAO_SEGUNDOS))
        );
    }

    private void limparExpirados() {
        Instant agora = Instant.now();
        respostasSalvas.entrySet().removeIf(e -> e.getValue().expiraEm().isBefore(agora));
    }
}