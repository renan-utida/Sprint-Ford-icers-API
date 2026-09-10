package com.icers.ford.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.icers.ford.client.LlmClient;
import com.icers.ford.dto.request.SpecQueryRequest;
import com.icers.ford.dto.response.CampoSpec;
import com.icers.ford.dto.response.SpecResponse;
import com.icers.ford.exception.FichaNaoEncontradaException;
import com.icers.ford.exception.RateLimitExceededException;
import com.icers.ford.model.FichaTecnica;
import com.icers.ford.model.HistoricoConsulta;
import com.icers.ford.model.Usuario;
import com.icers.ford.model.enums.ConfidenceLevel;
import com.icers.ford.repository.FichaTecnicaRepository;
import com.icers.ford.repository.HistoricoConsultaRepository;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
public class SpecService {

    private final FichaTecnicaRepository fichaTecnicaRepository;
    private final HistoricoConsultaRepository historicoRepository;
    private final LlmClient llmClient;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;
    private final int requestsPerMinute;

    // Cache de buckets por usuário — um bucket por user_id
    private final ConcurrentHashMap<Long, Bucket> bucketsPorUsuario =
            new ConcurrentHashMap<>();

    public SpecService(
            FichaTecnicaRepository fichaTecnicaRepository,
            HistoricoConsultaRepository historicoRepository,
            LlmClient llmClient,
            AuditService auditService,
            ObjectMapper objectMapper,
            @Value("${ratelimit.user.requests-per-minute:60}") int requestsPerMinute
    ) {
        this.fichaTecnicaRepository = fichaTecnicaRepository;
        this.historicoRepository = historicoRepository;
        this.llmClient = llmClient;
        this.auditService = auditService;
        this.objectMapper = objectMapper;
        this.requestsPerMinute = requestsPerMinute;
    }

    // QUERY — verifica cache, chama LLM se necessário

    /**
     * Consulta especificações de um veículo.
     * Fluxo: rate limit → cache → LLM (se miss) → salva → retorna.
     */
    @Transactional
    public SpecResponse query(SpecQueryRequest request,
                              Usuario usuario, String ip) {
        long inicio = System.currentTimeMillis();

        String marca = request.marca().trim();
        String modelo = request.modelo().trim();
        String versao = request.versao().trim();
        List<String> atributos = sanitizarAtributos(request.atributos());

        log.info("Query — usuário: {} | veículo: {} {} {}",
                usuario.getEmail(), marca, modelo, versao);

        // Rate limiting por usuário — antes de qualquer operação
        verificarRateLimitUsuario(usuario.getId(), ip);

        // Verifica cache no banco
        Optional<FichaTecnica> cache = fichaTecnicaRepository
                .findFirstByMarcaIgnoreCaseAndModeloIgnoreCaseAndVersaoIgnoreCase(
                        marca, modelo, versao
                );

        boolean cacheHit = cache.isPresent();
        SpecResponse response;

        if (cacheHit) {
            log.info("Cache hit — {} {} {}", marca, modelo, versao);
            FichaTecnica ficha = cache.get();
            List<CampoSpec> campos = parsearCamposJson(
                    ficha.getCamposJson(), atributos
            );
            response = SpecResponse.fromCache(
                    ficha.getMarca(), ficha.getModelo(), ficha.getVersao(),
                    campos,
                    ficha.getConfidenceGeral().name(),
                    ficha.getVerificadoEm()
            );
        } else {
            // Cache miss — chama o LLM
            log.info("Cache miss — chamando LLM para {} {} {}",
                    marca, modelo, versao);

            List<CampoSpec> campos = llmClient.consultarEspecificacoes(
                    marca, modelo, versao, atributos
            );

            String confidenceGeral = calcularConfidenceGeral(campos);

            try {
                salvarFicha(marca, modelo, versao, campos, confidenceGeral, usuario);
                response = SpecResponse.fromLlm(
                        marca, modelo, versao, campos, confidenceGeral
                );
            } catch (DataIntegrityViolationException e) {
                // Corrida de concorrência: outra requisição para o MESMO
                // veículo terminou de salvar entre o nosso check de cache
                // (linha acima) e este insert — o índice único
                // uk_sr_ficha_veiculo_ci (V8) rejeitou nosso insert.
                // Em vez de propagar um 500 pro cliente, devolvemos a
                // ficha que a "vencedora" da corrida já salvou — o
                // resultado funcional é idêntico (specs do mesmo
                // veículo), só sem duplicar linha nem mascarar o
                // desperdício de uma segunda chamada ao LLM.
                log.warn("Corrida de concorrência detectada ao salvar {} {} {} — " +
                                "devolvendo ficha já salva por requisição concorrente",
                        marca, modelo, versao);

                FichaTecnica jaSalva = fichaTecnicaRepository
                        .findFirstByMarcaIgnoreCaseAndModeloIgnoreCaseAndVersaoIgnoreCase(
                                marca, modelo, versao
                        )
                        .orElseThrow(() -> e);

                cacheHit = true;
                List<CampoSpec> camposExistentes = parsearCamposJson(
                        jaSalva.getCamposJson(), atributos
                );
                response = SpecResponse.fromCache(
                        jaSalva.getMarca(), jaSalva.getModelo(), jaSalva.getVersao(),
                        camposExistentes,
                        jaSalva.getConfidenceGeral().name(),
                        jaSalva.getVerificadoEm()
                );
            }
        }

        long tempoMs = System.currentTimeMillis() - inicio;
        registrarHistorico(usuario, marca, modelo, versao,
                atributos, cacheHit, tempoMs);

        auditService.logConsulta(
                usuario.getId(), "/api/v1/specs/query", ip,
                marca, modelo, versao, cacheHit, 200
        );

        return response;
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

        return toSpecResponse(ficha);
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

        List<String> atributosComparar =
                (atributos != null && !atributos.isEmpty())
                        ? atributos
                        : ficha1.campos().stream()
                        .map(CampoSpec::campo).toList();

        List<Map<String, Object>> comparativo = new ArrayList<>();

        for (String atributo : atributosComparar) {
            CampoSpec campo1 = buscarCampo(ficha1.campos(), atributo);
            CampoSpec campo2 = buscarCampo(ficha2.campos(), atributo);

            String vencedor = determinarVencedor(
                    campo1, campo2, v1Modelo, v2Modelo
            );

            comparativo.add(Map.of(
                    "atributo", atributo,
                    "veiculo1", campo1 != null
                            ? campo1 : CampoSpec.naoEncontrado(atributo),
                    "veiculo2", campo2 != null
                            ? campo2 : CampoSpec.naoEncontrado(atributo),
                    "vencedor", vencedor
            ));
        }

        return Map.of(
                "veiculo1", Map.of(
                        "marca", v1Marca, "modelo", v1Modelo, "versao", v1Versao
                ),
                "veiculo2", Map.of(
                        "marca", v2Marca, "modelo", v2Modelo, "versao", v2Versao
                ),
                "comparativo", comparativo
        );
    }

    // HISTORY — lista fichas com filtros

    @Transactional(readOnly = true)
    public List<SpecResponse> listarHistorico(String marca, String modelo) {
        return fichaTecnicaRepository.findWithFilters(marca, modelo)
                .stream()
                .map(this::toSpecResponse)
                .toList();
    }

    // DELETE — remove uma ficha técnica (ação administrativa)

    @Transactional
    public void deletarFicha(Long id) {
        if (!fichaTecnicaRepository.existsById(id)) {
            throw new FichaNaoEncontradaException(id);
        }
        fichaTecnicaRepository.deleteById(id);
        log.info("Ficha técnica deletada — id: {}", id);
    }

    // RATE LIMITING POR USUÁRIO

    /**
     * Verifica rate limit por usuário antes de chamar o LLM.
     * Cada usuário tem seu próprio bucket de 60 req/min.
     * Lança RateLimitExceededException se o limite for excedido.
     */
    private void verificarRateLimitUsuario(Long usuarioId, String ip) {
        Bucket bucket = bucketsPorUsuario.computeIfAbsent(usuarioId, id -> {
            Bandwidth limite = Bandwidth.builder()
                    .capacity(requestsPerMinute)
                    .refillGreedy(requestsPerMinute, Duration.ofMinutes(1))
                    .build();
            return Bucket.builder().addLimit(limite).build();
        });

        ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);

        if (!probe.isConsumed()) {
            long retryAfter =
                    (probe.getNanosToWaitForRefill() + 999_999_999L) / 1_000_000_000L;

            auditService.logRateLimitExceeded(
                    usuarioId, "/api/v1/specs/query", ip
            );

            throw new RateLimitExceededException(retryAfter);
        }
    }

    // MÉTODOS PRIVADOS

    /**
     * Converte a entidade FichaTecnica para o DTO público SpecResponse.
     * Nunca deixa a entidade JPA (nem o relacionamento LAZY criadoPor →
     * Usuario, que carrega o hash da senha) sair do service.
     */
    private SpecResponse toSpecResponse(FichaTecnica ficha) {
        List<CampoSpec> campos = parsearCamposJson(
                ficha.getCamposJson(), List.of()
        );
        return SpecResponse.fromCache(
                ficha.getMarca(), ficha.getModelo(), ficha.getVersao(),
                campos,
                ficha.getConfidenceGeral().name(),
                ficha.getVerificadoEm()
        );
    }

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
                    .confidenceGeral(ConfidenceLevel.valueOf(confidenceGeral))
                    .criadoPor(usuario)
                    .build();
            fichaTecnicaRepository.save(ficha);
            log.info("Ficha salva no banco — {} {} {}",
                    marca, modelo, versao);
        } catch (JsonProcessingException e) {
            log.error("Falha ao serializar campos: {}", e.getMessage());
        }
    }

    private List<CampoSpec> parsearCamposJson(String camposJson,
                                              List<String> atributosFiltro) {
        try {
            List<CampoSpec> todos = objectMapper.readValue(
                    camposJson,
                    objectMapper.getTypeFactory()
                            .constructCollectionType(List.class, CampoSpec.class)
            );

            if (atributosFiltro == null || atributosFiltro.isEmpty()) {
                return todos;
            }

            return atributosFiltro.stream()
                    .map(attr -> todos.stream()
                            .filter(c -> attr.equalsIgnoreCase(c.campo()))
                            .findFirst()
                            .orElse(CampoSpec.naoEncontrado(attr)))
                    .toList();

        } catch (Exception e) {
            log.error("Falha ao parsear campos JSON: {}", e.getMessage());
            return atributosFiltro != null
                    ? atributosFiltro.stream()
                    .map(CampoSpec::naoEncontrado).toList()
                    : List.of();
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
            historicoRepository.save(historico);

        } catch (Exception e) {
            log.error("Falha ao registrar histórico: {}", e.getMessage());
        }
    }

    /**
     * Calcula o nível de confiança geral da ficha.
     */
    private String calcularConfidenceGeral(List<CampoSpec> campos) {
        if (campos.isEmpty()) return "BAIXA";

        long alta = campos.stream()
                .filter(c -> "ALTA".equals(c.confianca())).count();
        long naoEncontrado = campos.stream()
                .filter(c -> "NAO_ENCONTRADO".equals(c.confianca())).count();

        double proporcaoAlta = (double) alta / campos.size();
        double proporcaoNaoEncontrado = (double) naoEncontrado / campos.size();

        if (proporcaoAlta >= 0.8) return "ALTA";
        if (proporcaoNaoEncontrado >= 0.5) return "BAIXA";
        if (proporcaoAlta >= 0.5) return "MEDIA";
        return "PARCIAL";
    }

    private CampoSpec buscarCampo(List<CampoSpec> campos, String atributo) {
        return campos.stream()
                .filter(c -> atributo.equalsIgnoreCase(c.campo()))
                .findFirst()
                .orElse(null);
    }

    /**
     * Determina o vencedor de um atributo entre dois veículos.
     */
    private String determinarVencedor(CampoSpec campo1, CampoSpec campo2,
                                      String nomeVeiculo1,
                                      String nomeVeiculo2) {
        if (campo1 == null || campo1.valor() == null) return nomeVeiculo2;
        if (campo2 == null || campo2.valor() == null) return nomeVeiculo1;

        try {
            double v1 = extrairNumero(campo1.valor());
            double v2 = extrairNumero(campo2.valor());
            if (v1 > v2) return nomeVeiculo1;
            if (v2 > v1) return nomeVeiculo2;
            return "EMPATE";
        } catch (NumberFormatException e) {
            return "N/A";
        }
    }

    private double extrairNumero(String valor) {
        String numeroStr = valor.replaceAll("[^0-9.,]", "")
                .replace(",", ".");
        if (numeroStr.isBlank()) throw new NumberFormatException();
        return Double.parseDouble(numeroStr);
    }

    private List<String> sanitizarAtributos(List<String> atributos) {
        return atributos.stream()
                .map(String::trim)
                .filter(a -> !a.isBlank())
                .map(a -> a.replaceAll("[^a-zA-ZÀ-ÿ0-9\\s\\-_]", ""))
                .filter(a -> !a.isBlank())
                .toList();
    }
}