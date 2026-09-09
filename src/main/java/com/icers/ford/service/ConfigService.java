package com.icers.ford.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.icers.ford.dto.response.ConfigResponse;
import com.icers.ford.model.Config;
import com.icers.ford.model.Usuario;
import com.icers.ford.repository.ConfigRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ConfigService {

    private final ConfigRepository configRepository;
    private final ObjectMapper objectMapper;

    /**
     * Fallback usado apenas se a tabela sr_config estiver vazia por
     * algum motivo (não deveria acontecer — V6 já popula a linha
     * inicial). É o mesmo array que existia hardcoded no ChatService
     * antes desta mudança.
     */
    private static final List<String> FALLBACK_PADRAO = List.of(
            "motor", "potencia", "torque", "transmissao",
            "tracao", "preco", "consumo", "dimensoes"
    );

    /**
     * Usado pelo ChatService — retorna só a lista, sem metadado.
     */
    @Transactional(readOnly = true)
    public List<String> getAtributosPadrao() {
        return configRepository.findFirstByOrderByIdAsc()
                .map(this::parseAtributos)
                .orElse(FALLBACK_PADRAO);
    }

    /**
     * Usado pelo endpoint GET/PUT de config — retorna com metadado
     * (data da última atualização).
     */
    @Transactional(readOnly = true)
    public ConfigResponse getConfig() {
        Config config = configRepository.findFirstByOrderByIdAsc()
                .orElseThrow(() -> new IllegalStateException(
                        "Configuração do sistema não inicializada — verifique a migration V6"
                ));
        return ConfigResponse.of(parseAtributos(config), config.getAtualizadoEm());
    }

    /**
     * Atualiza a lista de atributos padrão. Só ADMIN pode chamar isso
     * (garantido pelo @PreAuthorize no controller, não aqui).
     */
    @Transactional
    public ConfigResponse atualizarAtributosPadrao(List<String> novosAtributos, Usuario admin) {
        Config config = configRepository.findFirstByOrderByIdAsc()
                .orElseGet(Config::new);

        try {
            LocalDateTime agora = LocalDateTime.now();

            String json = objectMapper.writeValueAsString(novosAtributos);
            config.setAtributosPadraoJson(json);
            config.setAtualizadoPor(admin);
            // Setado explicitamente aqui — @PreUpdate só roda quando o
            // Hibernate executa o UPDATE de fato (geralmente no flush,
            // no fim da transação), não no momento do save() em si. Sem
            // isso, o valor devolvido na resposta fica uma leitura
            // atrasado em relação ao que efetivamente foi persistido.
            config.setAtualizadoEm(agora);

            configRepository.save(config);

            log.info("[AUDITORIA] Configuração atualizada — atributos_padrao: {} | por: {}",
                    novosAtributos, admin.getEmail());

            return ConfigResponse.of(novosAtributos, agora);

        } catch (Exception e) {
            log.error("Falha ao atualizar configuração: {}", e.getMessage());
            throw new IllegalStateException("Falha ao salvar a configuração", e);
        }
    }

    private List<String> parseAtributos(Config config) {
        try {
            return objectMapper.readValue(
                    config.getAtributosPadraoJson(),
                    objectMapper.getTypeFactory()
                            .constructCollectionType(List.class, String.class)
            );
        } catch (Exception e) {
            log.error("Falha ao parsear atributos_padrao salvos, usando fallback: {}",
                    e.getMessage());
            return FALLBACK_PADRAO;
        }
    }
}