package com.icers.ford.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.icers.ford.client.LlmClient;
import com.icers.ford.dto.request.SpecQueryRequest;
import com.icers.ford.dto.response.*;
import com.icers.ford.exception.FichaNaoEncontradaException;
import com.icers.ford.model.*;
import com.icers.ford.model.enums.ConfidenceLevel;
import com.icers.ford.repository.FichaTecnicaRepository;
import com.icers.ford.repository.HistoricoConsultaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class SpecService {

    private final FichaTecnicaRepository fichaTecnicaRepository;
    private final HistoricoConsultaRepository historicoConsultaRepository;
    private final LlmClient llmClient;
    private final ObjectMapper objectMapper;

    // QUERY — verifica cache, chama LLM se necessário

    /**
     * Consulta especificações de um veículo.
     * Fluxo: verifica cache → se hit retorna do banco →
     *        se miss chama LLM → salva no banco → retorna.
     */
    @Transactional
    public SpecResponse query(SpecQueryRequest request, Usuario usuario) {
        long inicio = System.currentTimeMillis();

        String marca = request.marca().trim();
        String modelo = request.modelo().trim();
        String versao = request.versao().trim();
        List<String> atributos = request.atributos();

        // Verifica cache no banco
        Optional<FichaTecnica> fichaExistente = fichaTecnicaRepository
                .findFirstByMarcaIgnoreCaseAndModeloIgnoreCaseAndVersaoIgnoreCase(
                        marca, modelo, versao
                );

        if (fichaExistente.isPresent()) {
            log.info("Cache hit — {} {} {}", marca, modelo, versao);

            FichaTecnica ficha = fichaExistente.get();
            List<CampoSpec> campos = desserializarCampos(ficha.getCamposJson());

            // Filtra apenas os atributos solicitados
            List<CampoSpec> camposFiltrados = filtrarAtributos(campos, atributos);

            registrarHistorico(usuario, marca, modelo, versao,
                    atributos, true,
                    System.currentTimeMillis() - inicio);

            return SpecResponse.fromCache(
                    marca, modelo, versao,
                    camposFiltrados,
                    ficha.getConfidenceGeral().name(),
                    ficha.getVerificadoEm()
            );
        }

        // Cache miss — chama o LLM
        log.info("Cache miss — consultando LLM para {} {} {}",
                marca, modelo, versao);

        List<CampoSpec> campos = llmClient.consultarEspecificacoes(
                marca, modelo, versao, atributos
        );

        // Calcula confidence geral baseado nos campos retornados
        String confidenceGeral = calcularConfidenceGeral(campos);

        // Salva no banco para consultas futuras
        salvarFicha(marca, modelo, versao, campos,
                confidenceGeral, usuario);

        registrarHistorico(usuario, marca, modelo, versao,
                atributos, false,
                System.currentTimeMillis() - inicio);

        return SpecResponse.fromLlm(
                marca, modelo, versao, campos, confidenceGeral
        );
    }

    // FIND BY VEICULO — consulta direta ao banco

    /**
     * Busca ficha técnica armazenada sem chamar o LLM.
     * Lança FichaNaoEncontradaException se não existir — vira 404.
     */
    @Transactional(readOnly = true)
    public SpecResponse findByVeiculo(String marca, String modelo,
                                      String versao) {
        FichaTecnica ficha = fichaTecnicaRepository
                .findFirstByMarcaIgnoreCaseAndModeloIgnoreCaseAndVersaoIgnoreCase(
                        marca, modelo, versao
                )
                .orElseThrow(() ->
                        new FichaNaoEncontradaException(marca, modelo, versao)
                );

        List<CampoSpec> campos = desserializarCampos(ficha.getCamposJson());

        return SpecResponse.fromCache(
                ficha.getMarca(),
                ficha.getModelo(),
                ficha.getVersao(),
                campos,
                ficha.getConfidenceGeral().name(),
                ficha.getVerificadoEm()
        );
    }

    // COMPARE — comparativo entre dois veículos

    /**
     * Compara dois veículos do banco campo a campo.
     * Para atributos numéricos, indica qual veículo tem valor superior.
     * Ambos os veículos devem existir no banco.
     */
    @Transactional(readOnly = true)
    public Map<String, Object> compare(String v1Marca, String v1Modelo,
                                       String v1Versao, String v2Marca,
                                       String v2Modelo, String v2Versao,
                                       List<String> atributos) {

        SpecResponse ficha1 = findByVeiculo(v1Marca, v1Modelo, v1Versao);
        SpecResponse ficha2 = findByVeiculo(v2Marca, v2Modelo, v2Versao);

        List<CampoSpec> campos1 = filtrarAtributos(ficha1.campos(), atributos);
        List<CampoSpec> campos2 = filtrarAtributos(ficha2.campos(), atributos);

        // Monta comparativo campo a campo
        List<Map<String, Object>> comparativo = atributos.stream()
                .map(atributo -> {
                    CampoSpec campo1 = buscarCampo(campos1, atributo);
                    CampoSpec campo2 = buscarCampo(campos2, atributo);
                    String vencedor = determinarVencedor(atributo,
                            campo1, campo2,
                            v1Modelo, v2Modelo);

                    return Map.<String, Object>of(
                            "atributo", atributo,
                            "veiculo1", campo1 != null ? campo1 : CampoSpec.naoEncontrado(atributo),
                            "veiculo2", campo2 != null ? campo2 : CampoSpec.naoEncontrado(atributo),
                            "vencedor", vencedor
                    );
                })
                .toList();

        return Map.of(
                "veiculo1", Map.of("marca", v1Marca, "modelo", v1Modelo, "versao", v1Versao),
                "veiculo2", Map.of("marca", v2Marca, "modelo", v2Modelo, "versao", v2Versao),
                "comparativo", comparativo
        );
    }

    // HISTORY — lista fichas com filtros

    @Transactional(readOnly = true)
    public List<SpecResponse> findHistory(String marca, String modelo) {
        return fichaTecnicaRepository
                .findWithFilters(marca, modelo)
                .stream()
                .map(ficha -> SpecResponse.fromCache(
                        ficha.getMarca(),
                        ficha.getModelo(),
                        ficha.getVersao(),
                        desserializarCampos(ficha.getCamposJson()),
                        ficha.getConfidenceGeral().name(),
                        ficha.getVerificadoEm()
                ))
                .toList();
    }

    // MÉTODOS PRIVADOS

    private void salvarFicha(String marca, String modelo, String versao,
                             List<CampoSpec> campos, String confidenceGeral,
                             Usuario usuario) {
        try {
            String camposJson = objectMapper.writeValueAsString(campos);

            FichaTecnica ficha = FichaTecnica.builder()
                    .marca(marca)
                    .modelo(modelo)
                    .versao(versao)
                    .camposJson(camposJson)
                    .confidenceGeral(
                            ConfidenceLevel.valueOf(confidenceGeral)
                    )
                    .criadoPor(usuario)
                    .build();

            fichaTecnicaRepository.save(ficha);
            log.info("Ficha salva no banco — {} {} {}", marca, modelo, versao);

        } catch (Exception e) {
            log.error("Falha ao salvar ficha no banco: {}", e.getMessage());
        }
    }

    private void registrarHistorico(Usuario usuario, String marca,
                                    String modelo, String versao,
                                    List<String> atributos,
                                    boolean cacheHit, long tempoMs) {
        try {
            String atributosJson = objectMapper.writeValueAsString(atributos);

            HistoricoConsulta historico = HistoricoConsulta.builder()
                    .usuario(usuario)
                    .marca(marca)
                    .modelo(modelo)
                    .versao(versao)
                    .atributosSolicitados(atributosJson)
                    .tempoRespostaMs(tempoMs)
                    .build();

            historico.setCacheHit(cacheHit);
            historicoConsultaRepository.save(historico);

        } catch (Exception e) {
            log.error("Falha ao registrar histórico: {}", e.getMessage());
        }
    }

    private List<CampoSpec> desserializarCampos(String camposJson) {
        try {
            return objectMapper.readValue(
                    camposJson,
                    new TypeReference<List<CampoSpec>>() {}
            );
        } catch (Exception e) {
            log.error("Falha ao desserializar campos: {}", e.getMessage());
            return List.of();
        }
    }

    private List<CampoSpec> filtrarAtributos(List<CampoSpec> todos,
                                             List<String> atributos) {
        if (atributos == null || atributos.isEmpty()) return todos;
        return todos.stream()
                .filter(c -> atributos.stream()
                        .anyMatch(a -> a.equalsIgnoreCase(c.campo())))
                .toList();
    }

    private CampoSpec buscarCampo(List<CampoSpec> campos, String atributo) {
        return campos.stream()
                .filter(c -> atributo.equalsIgnoreCase(c.campo()))
                .findFirst()
                .orElse(null);
    }

    /**
     * Calcula o nível de confiança geral da ficha.
     * ALTA: todos os campos com confiança ALTA
     * MEDIA: maioria dos campos encontrados
     * PARCIAL: menos da metade encontrada
     * BAIXA: maioria não encontrada
     */
    private String calcularConfidenceGeral(List<CampoSpec> campos) {
        if (campos.isEmpty()) return "BAIXA";

        long encontrados = campos.stream()
                .filter(c -> !"NAO_ENCONTRADO".equals(c.confianca()))
                .count();

        long alta = campos.stream()
                .filter(c -> "ALTA".equals(c.confianca()))
                .count();

        double taxaEncontrados = (double) encontrados / campos.size();
        double taxaAlta = (double) alta / campos.size();

        if (taxaAlta >= 0.8) return "ALTA";
        if (taxaEncontrados >= 0.7) return "MEDIA";
        if (taxaEncontrados >= 0.4) return "PARCIAL";
        return "BAIXA";
    }

    /**
     * Determina o vencedor de um atributo entre dois veículos.
     * Para valores numéricos, compara os números.
     * Para valores não numéricos, retorna "EMPATE".
     */
    private String determinarVencedor(String atributo,
                                      CampoSpec campo1, CampoSpec campo2,
                                      String nomeVeiculo1, String nomeVeiculo2) {
        if (campo1 == null || campo1.valor() == null
                || campo2 == null || campo2.valor() == null) {
            return "INDISPONIVEL";
        }

        try {
            // Extrai o primeiro número encontrado no valor
            double num1 = extrairNumero(campo1.valor());
            double num2 = extrairNumero(campo2.valor());

            if (num1 > num2) return nomeVeiculo1;
            if (num2 > num1) return nomeVeiculo2;
            return "EMPATE";

        } catch (Exception e) {
            // Valor não numérico — não dá para comparar
            return "EMPATE";
        }
    }

    private double extrairNumero(String valor) {
        // Remove tudo que não for dígito, ponto ou vírgula
        String numerico = valor.replaceAll("[^0-9.,]", "")
                .replace(",", ".");
        if (numerico.isEmpty()) throw new NumberFormatException();
        return Double.parseDouble(numerico);
    }
}