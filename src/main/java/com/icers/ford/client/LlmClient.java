package com.icers.ford.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.icers.ford.dto.response.CampoSpec;
import com.icers.ford.exception.LlmUnavailableException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class LlmClient {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final String apiKey;
    private final String apiUrl;
    private final String model;

    public LlmClient(
            RestTemplate restTemplate,
            ObjectMapper objectMapper,
            @Value("${llm.api.key}") String apiKey,
            @Value("${llm.api.url}") String apiUrl,
            @Value("${llm.api.model}") String model
    ) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
        this.apiKey = apiKey;
        this.apiUrl = apiUrl;
        this.model = model;
    }

    /**
     * Consulta o Gemini para extrair especificações técnicas de um veículo.
     * URL final: {apiUrl}/{model}:generateContent?key={apiKey}
     */
    public List<CampoSpec> consultarEspecificacoes(String marca, String modelo,
                                                   String versao,
                                                   List<String> atributos) {
        String prompt = construirPrompt(marca, modelo, versao, atributos);
        String url = apiUrl + "/" + model + ":generateContent?key=" + apiKey;

        log.info("Consultando Gemini para: {} {} {}", marca, modelo, versao);

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            LlmRequest request = LlmRequest.of(prompt);
            HttpEntity<LlmRequest> entity = new HttpEntity<>(request, headers);

            ResponseEntity<LlmResponse> response = restTemplate.exchange(
                    url,
                    HttpMethod.POST,
                    entity,
                    LlmResponse.class
            );

            if (response.getBody() == null) {
                throw new LlmUnavailableException("Resposta vazia do Gemini");
            }

            String textoResposta = response.getBody().extractText();
            if (textoResposta == null || textoResposta.isBlank()) {
                throw new LlmUnavailableException(
                        "Conteúdo vazio na resposta do Gemini"
                );
            }

            if (response.getBody().usageMetadata() != null) {
                log.debug("Tokens utilizados — total: {}",
                        response.getBody().usageMetadata().totalTokenCount());
            }

            return parsearResposta(textoResposta, atributos);

        } catch (HttpClientErrorException e) {
            // 429 = rate limit atingido — gratuito esgotado por hoje
            if (e.getStatusCode().value() == 429) {
                log.warn("Rate limit do Gemini atingido");
                throw new LlmUnavailableException(
                        "Limite de consultas atingido. Tente novamente mais tarde.", e
                );
            }
            log.error("Erro HTTP ao consultar Gemini: {} — {}",
                    e.getStatusCode(), e.getMessage());
            throw new LlmUnavailableException(
                    "Erro ao consultar serviço externo", e
            );
        } catch (ResourceAccessException e) {
            log.error("Timeout ao consultar Gemini: {}", e.getMessage());
            throw new LlmUnavailableException(
                    "Timeout ao consultar serviço externo", e
            );
        } catch (LlmUnavailableException e) {
            throw e;
        } catch (Exception e) {
            log.error("Erro inesperado ao consultar Gemini: {}", e.getMessage());
            throw new LlmUnavailableException(
                    "Erro ao processar resposta do serviço externo", e
            );
        }
    }

    // CONSTRUÇÃO DO PROMPT

    /**
     * Constrói o prompt de forma defensiva contra prompt injection.
     * Os inputs do usuário são inseridos entre delimitadores XML
     * para que o modelo não os interprete como instruções.
     */
    private String construirPrompt(String marca, String modelo,
                                   String versao, List<String> atributos) {
        String listaAtributos = String.join(", ", atributos);
        String dataHoje = LocalDate.now().toString();

        return """
                Você é um especialista em especificações técnicas automotivas.
                
                Sua tarefa é retornar as especificações técnicas do veículo abaixo \
                em formato JSON estruturado.
                
                <veiculo>
                Marca: %s
                Modelo: %s
                Versão: %s
                </veiculo>
                
                <atributos_solicitados>
                %s
                </atributos_solicitados>
                
                REGRAS OBRIGATÓRIAS:
                1. Retorne APENAS o JSON, sem texto antes ou depois
                2. Todos os atributos solicitados devem aparecer no JSON, \
                mesmo os não encontrados
                3. Para atributos não encontrados use: \
                "valor": null, "confianca": "NAO_ENCONTRADO", "fonte": null
                4. Níveis de confiança válidos: ALTA, MEDIA, INFERIDA, NAO_ENCONTRADO
                5. Use "fonte" para indicar a URL ou fonte onde encontrou o dado
                6. Use "verificado_em" com a data de hoje: %s
                7. Não invente dados — se não tiver certeza use confianca: INFERIDA
                
                FORMATO JSON OBRIGATÓRIO:
                {
                  "campos": [
                    {
                      "campo": "nome_do_atributo",
                      "valor": "valor encontrado ou null",
                      "confianca": "ALTA|MEDIA|INFERIDA|NAO_ENCONTRADO",
                      "fonte": "url ou nome da fonte ou null",
                      "verificado_em": "%s"
                    }
                  ],
                  "confidence_geral": "ALTA|MEDIA|PARCIAL|BAIXA"
                }
                """.formatted(marca, modelo, versao,
                listaAtributos, dataHoje, dataHoje);
    }

    // PARSE DA RESPOSTA

    /**
     * Parseia o JSON retornado pelo Gemini para lista de CampoSpec.
     * Se o JSON vier malformado/incompleto (ex: resposta cortada por
     * instabilidade do Gemini), lança LlmUnavailableException — o
     * chamador NUNCA deve tratar isso como "consulta bem-sucedida,
     * mas sem dados". Antes, esse catch devolvia tudo como
     * NAO_ENCONTRADO silenciosamente, o que fazia o SpecService achar
     * que era uma resposta válida e SALVAR essa ficha vazia no banco
     * — poluindo o cache pra sempre com um erro pontual e temporário.
     */
    @SuppressWarnings("unchecked")
    private List<CampoSpec> parsearResposta(String textoJson,
                                            List<String> atributosEsperados) {
        try {
            // Remove possível markdown code block se o modelo ignorar a instrução
            String jsonLimpo = limparJson(textoJson);
            Map<String, Object> resposta =
                    objectMapper.readValue(jsonLimpo, Map.class);

            List<Map<String, Object>> camposJson =
                    (List<Map<String, Object>>) resposta.get("campos");

            if (camposJson == null || camposJson.isEmpty()) {
                log.warn("Gemini retornou JSON sem campos");
                return atributosEsperados.stream()
                        .map(CampoSpec::naoEncontrado)
                        .toList();
            }

            List<CampoSpec> campos = new ArrayList<>();

            for (Map<String, Object> campoMap : camposJson) {
                String campo = (String) campoMap.get("campo");
                String valor = (String) campoMap.get("valor");
                String confianca = (String) campoMap.get("confianca");
                String fonte = (String) campoMap.get("fonte");
                String verificadoEm = (String) campoMap.get("verificado_em");

                if (campo == null) continue;

                if (valor == null || "NAO_ENCONTRADO".equals(confianca)) {
                    campos.add(CampoSpec.naoEncontrado(campo));
                } else {
                    campos.add(CampoSpec.encontrado(
                            campo, valor,
                            confianca != null ? confianca : "MEDIA",
                            fonte,
                            verificadoEm
                    ));
                }
            }

            // Garante todos os atributos solicitados no response
            for (String atributo : atributosEsperados) {
                boolean presente = campos.stream()
                        .anyMatch(c -> atributo.equalsIgnoreCase(c.campo()));
                if (!presente) {
                    log.warn("Gemini omitiu '{}' — NAO_ENCONTRADO", atributo);
                    campos.add(CampoSpec.naoEncontrado(atributo));
                }
            }

            return campos;

        } catch (LlmUnavailableException e) {
            throw e;
        } catch (Exception e) {
            log.error("Falha ao parsear resposta do Gemini (JSON malformado/incompleto): {}",
                    e.getMessage());
            throw new LlmUnavailableException(
                    "Resposta do serviço externo veio malformada", e
            );
        }
    }

    /**
     * Remove markdown code blocks que o modelo pode inserir
     * mesmo sendo instruído a não fazê-lo.
     * ex: ```json { ... } ``` → { ... }
     */
    private String limparJson(String texto) {
        if (texto == null) return "{}";
        return texto
                .replaceAll("```json\\s*", "")
                .replaceAll("```\\s*", "")
                .trim();
    }
}