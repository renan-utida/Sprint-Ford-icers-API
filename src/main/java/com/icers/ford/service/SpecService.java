package com.icers.ford.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.icers.ford.client.LlmClient;
import com.icers.ford.dto.request.SpecFromPdfRequest;
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
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

@Slf4j
@Service
public class SpecService {

    private final FichaTecnicaRepository fichaTecnicaRepository;
    private final HistoricoConsultaRepository historicoRepository;
    private final LlmClient llmClient;
    private final ConfigService configService;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;
    private final int requestsPerMinute;
    private final int requestsPerMinuteFromPdf;

    // Cache de buckets por usuário — um bucket por user_id. Separado por
    // endpoint (não compartilhado): /from-pdf é uma chamada bem mais cara
    // (payload maior, timeout maior — ver Grupo 9) e merece um orçamento
    // próprio e mais restrito, em vez de disputar o mesmo teto de /query.
    private final ConcurrentHashMap<Long, Bucket> bucketsPorUsuario =
            new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, Bucket> bucketsPdfPorUsuario =
            new ConcurrentHashMap<>();

    public SpecService(
            FichaTecnicaRepository fichaTecnicaRepository,
            HistoricoConsultaRepository historicoRepository,
            LlmClient llmClient,
            ConfigService configService,
            AuditService auditService,
            ObjectMapper objectMapper,
            @Value("${ratelimit.user.requests-per-minute:60}") int requestsPerMinute,
            @Value("${ratelimit.user.requests-per-minute-from-pdf:10}") int requestsPerMinuteFromPdf
    ) {
        this.fichaTecnicaRepository = fichaTecnicaRepository;
        this.historicoRepository = historicoRepository;
        this.llmClient = llmClient;
        this.configService = configService;
        this.auditService = auditService;
        this.objectMapper = objectMapper;
        this.requestsPerMinute = requestsPerMinute;
        this.requestsPerMinuteFromPdf = requestsPerMinuteFromPdf;
    }

    // QUERY — verifica cache, chama LLM se necessário

    /**
     * Consulta especificações de um veículo.
     * Fluxo: rate limit → cache → LLM (se miss) → salva → retorna.
     */
    @Transactional
    public SpecResponse query(SpecQueryRequest request,
                              Usuario usuario, String ip) {
        String marca = request.marca().trim();
        String modelo = request.modelo().trim();
        String versao = request.versao().trim();
        List<String> atributos = sanitizarAtributos(request.atributos());

        log.info("Query — usuário: {} | veículo: {} {} {}",
                usuario.getEmail(), marca, modelo, versao);

        // Rate limiting por usuário — antes de qualquer operação
        verificarRateLimitUsuario(usuario.getId(), ip, "/api/v1/specs/query");

        return resolverComCache(
                marca, modelo, versao, atributos, usuario, ip,
                "/api/v1/specs/query",
                atributosParaBuscar -> llmClient.consultarEspecificacoes(
                        marca, modelo, versao, atributosParaBuscar
                )
        );
    }

    // QUERY FROM PDF — mesmo fluxo cache-primeiro do query(), trocando
    // só a fonte da extração (PDF anexado em vez de conhecimento do
    // modelo). Ver investigação do Grupo 9: falhas do Gemini aqui viram
    // o mesmo LlmUnavailableException/503 de sempre — não um caso
    // especial — mas com uma dica adicional pro analista (ver
    // GlobalExceptionHandler).

    /**
     * Consulta especificações extraindo de um PDF anexado.
     * Fluxo idêntico ao query(): rate limit → cache → LLM (com o PDF,
     * se miss/incompleto) → salva → retorna. Se o cache já tem tudo que
     * foi pedido, o PDF nem chega a ser processado.
     */
    @Transactional
    public SpecResponse queryFromPdf(SpecFromPdfRequest request, byte[] pdfBytes,
                                     Usuario usuario, String ip) {
        String marca = request.marca().trim();
        String modelo = request.modelo().trim();
        String versao = request.versao().trim();
        List<String> atributos = sanitizarAtributos(request.atributos());

        log.info("Query (PDF) — usuário: {} | veículo: {} {} {}",
                usuario.getEmail(), marca, modelo, versao);

        verificarRateLimitUsuarioPdf(usuario.getId(), ip, "/api/v1/specs/from-pdf");

        return resolverComCache(
                marca, modelo, versao, atributos, usuario, ip,
                "/api/v1/specs/from-pdf",
                atributosParaBuscar -> llmClient.consultarEspecificacoesDePdf(
                        pdfBytes, marca, modelo, versao, atributosParaBuscar
                )
        );
    }

    /**
     * Corpo compartilhado por query() e queryFromPdf() — a decisão de
     * cache hit / expirada / parcial / miss e a persistência são
     * idênticas nos dois fluxos; só COMO os atributos que faltam são
     * buscados no LLM muda (texto vs. PDF), por isso é injetado como
     * função em vez de fixo aqui dentro.
     */
    private SpecResponse resolverComCache(String marca, String modelo, String versao,
                                          List<String> atributos, Usuario usuario, String ip,
                                          String endpointAuditoria,
                                          Function<List<String>, List<CampoSpec>> buscarNoLlm) {
        long inicio = System.currentTimeMillis();

        // Verifica cache no banco
        Optional<FichaTecnica> cache = fichaTecnicaRepository
                .findFirstByMarcaIgnoreCaseAndModeloIgnoreCaseAndVersaoIgnoreCase(
                        marca, modelo, versao
                );

        boolean cacheHit = cache.isPresent();
        SpecResponse response;

        if (cacheHit) {
            FichaTecnica ficha = cache.get();

            // Pega TODOS os campos já salvos (sem filtro), pra saber
            // o que realmente já temos, não só o que foi pedido agora
            List<CampoSpec> camposCache = parsearCamposJson(
                    ficha.getCamposJson(), List.of()
            );

            List<String> atributosFaltando = atributos.stream()
                    .filter(a -> camposCache.stream()
                            .noneMatch(c -> a.equalsIgnoreCase(c.campo())))
                    .toList();

            boolean expirada = LocalDateTime.now().isAfter(
                    ficha.getVerificadoEm()
                            .plusDays(ficha.getIntervaloReverificacaoDias())
            );

            if (!expirada && atributosFaltando.isEmpty()) {
                // Cache tem tudo que foi pedido e ainda está dentro do
                // prazo — hit de verdade, sem chamar o Gemini
                log.info("Cache hit — {} {} {}", marca, modelo, versao);
                List<CampoSpec> campos = filtrarCampos(camposCache, atributos);
                response = SpecResponse.fromCache(
                        ficha.getId(), ficha.getMarca(), ficha.getModelo(), ficha.getVersao(),
                        campos,
                        ficha.getConfidenceGeral().name(),
                        ficha.getVerificadoEm()
                );

            } else if (expirada) {
                // Ficha passou do prazo de reverificação. Reverifica
                // TUDO que já tinha, mais qualquer atributo novo que
                // também esteja faltando, numa única chamada ao
                // Gemini — SUBSTITUI o conteúdo da ficha (não mescla,
                // diferente do cache parcial abaixo), porque o
                // objetivo aqui é confirmar/atualizar o que já existe,
                // não só completar lacuna.
                //
                // O intervalo desta ficha é RENOVADO pro valor global
                // atual do ADMIN neste momento — se o ADMIN mudou a
                // configuração depois que esta ficha foi criada, ela
                // "alcança" o valor novo na primeira reverificação daqui
                // pra frente, em vez de ficar travada pra sempre no
                // valor que tinha quando foi criada.
                int intervaloAnterior = ficha.getIntervaloReverificacaoDias();
                int intervaloAtual = configService.getIntervaloReverificacaoDias();
                log.info("Ficha expirada (mais de {} dias) — reverificando {} {} {} " +
                                "(intervalo renovado: {} -> {})",
                        intervaloAnterior, marca, modelo, versao,
                        intervaloAnterior, intervaloAtual);

                cacheHit = false;

                List<String> nomesJaConhecidos = camposCache.stream()
                        .map(CampoSpec::campo)
                        .toList();
                List<String> atributosParaReverificar = Stream.concat(
                                nomesJaConhecidos.stream(), atributosFaltando.stream())
                        .distinct()
                        .toList();

                List<CampoSpec> camposFrescos = buscarNoLlm.apply(atributosParaReverificar);

                String confidenceGeral = calcularConfidenceGeral(camposFrescos);
                ficha.setIntervaloReverificacaoDias(intervaloAtual);
                atualizarFicha(ficha, camposFrescos, confidenceGeral);

                List<CampoSpec> camposResposta = filtrarCampos(camposFrescos, atributos);
                response = SpecResponse.fromLlm(
                        ficha.getId(), marca, modelo, versao, camposResposta, confidenceGeral
                );

            } else {
                // Não expirada, mas faltam atributos novos — cache
                // PARCIAL (Nível 2). Busca SÓ o que falta no Gemini,
                // mescla com o que já existia, e atualiza a ficha —
                // não cria uma linha nova, e não descarta o que já
                // estava certo.
                log.info("Cache parcial — faltam {} atributo(s) para {} {} {}, buscando no LLM",
                        atributosFaltando.size(), marca, modelo, versao);

                cacheHit = false; // precisou chamar o LLM, não foi hit puro

                List<CampoSpec> camposNovos = buscarNoLlm.apply(atributosFaltando);

                List<CampoSpec> camposMesclados = new ArrayList<>(camposCache);
                camposMesclados.addAll(camposNovos);

                String confidenceGeral = calcularConfidenceGeral(camposMesclados);
                atualizarFicha(ficha, camposMesclados, confidenceGeral);

                List<CampoSpec> camposResposta = filtrarCampos(camposMesclados, atributos);
                response = SpecResponse.fromLlm(
                        ficha.getId(), marca, modelo, versao, camposResposta, confidenceGeral
                );
            }
        } else {
            // Cache miss — chama o LLM
            log.info("Cache miss — chamando LLM para {} {} {}",
                    marca, modelo, versao);

            // Busca pelo menos o conjunto padrão de atributos, mesmo
            // que o usuário tenha pedido menos — garante que a
            // PRIMEIRA consulta de um veículo já deixa o cache
            // completo o bastante pra qualquer consulta futura (do
            // mesmo veículo, atributos diferentes dentro do padrão)
            // não precisar voltar no Gemini à toa. A resposta pra
            // este usuário continua mostrando só o que ele pediu.
            List<String> atributosPadrao = configService.getAtributosPadrao();
            List<String> atributosParaBuscar = Stream.concat(
                            atributos.stream(), atributosPadrao.stream())
                    .distinct()
                    .toList();

            List<CampoSpec> campos = buscarNoLlm.apply(atributosParaBuscar);

            String confidenceGeral = calcularConfidenceGeral(campos);

            try {
                Long idNovo = salvarFicha(marca, modelo, versao, campos, confidenceGeral, usuario);

                List<CampoSpec> camposResposta = filtrarCampos(campos, atributos);
                response = SpecResponse.fromLlm(
                        idNovo, marca, modelo, versao, camposResposta, confidenceGeral
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
                        jaSalva.getId(), jaSalva.getMarca(), jaSalva.getModelo(), jaSalva.getVersao(),
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
                usuario.getId(), endpointAuditoria, ip,
                marca, modelo, versao, cacheHit, 200
        );

        return response;
    }

    // FIND BY VEICULO — consulta direta ao banco

    /**
     * Busca ficha técnica armazenada sem chamar o LLM.
     * Lança FichaNaoEncontradaException se não existir — vira 404,
     * com sugestões de outras versões do mesmo marca+modelo já
     * cacheadas, se existir alguma.
     */
    @Transactional(readOnly = true)
    public SpecResponse findByVeiculo(String marca, String modelo,
                                      String versao) {
        FichaTecnica ficha = fichaTecnicaRepository
                .findFirstByMarcaIgnoreCaseAndModeloIgnoreCaseAndVersaoIgnoreCase(
                        marca, modelo, versao
                )
                .orElseThrow(() -> {
                    List<String> sugestoes = fichaTecnicaRepository
                            .findByMarcaIgnoreCaseAndModeloIgnoreCase(marca, modelo)
                            .stream()
                            .map(f -> f.getMarca() + " " + f.getModelo() + " " + f.getVersao())
                            .limit(5)
                            .toList();
                    return new FichaNaoEncontradaException(marca, modelo, versao, sugestoes);
                });

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
    private void verificarRateLimitUsuario(Long usuarioId, String ip, String endpoint) {
        verificarRateLimit(bucketsPorUsuario, requestsPerMinute, usuarioId, ip, endpoint);
    }

    private void verificarRateLimitUsuarioPdf(Long usuarioId, String ip, String endpoint) {
        verificarRateLimit(bucketsPdfPorUsuario, requestsPerMinuteFromPdf, usuarioId, ip, endpoint);
    }

    private void verificarRateLimit(ConcurrentHashMap<Long, Bucket> buckets, int capacidade,
                                    Long usuarioId, String ip, String endpoint) {
        Bucket bucket = buckets.computeIfAbsent(usuarioId, id -> {
            Bandwidth limite = Bandwidth.builder()
                    .capacity(capacidade)
                    .refillGreedy(capacidade, Duration.ofMinutes(1))
                    .build();
            return Bucket.builder().addLimit(limite).build();
        });

        ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);

        if (!probe.isConsumed()) {
            long retryAfter =
                    (probe.getNanosToWaitForRefill() + 999_999_999L) / 1_000_000_000L;

            auditService.logRateLimitExceeded(usuarioId, endpoint, ip);

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
                ficha.getId(), ficha.getMarca(), ficha.getModelo(), ficha.getVersao(),
                campos,
                ficha.getConfidenceGeral().name(),
                ficha.getVerificadoEm()
        );
    }

    private Long salvarFicha(String marca, String modelo, String versao,
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
                    // Copia o valor GLOBAL atual e trava nesta ficha —
                    // se o ADMIN mudar o valor global depois, esta
                    // ficha específica não é afetada.
                    .intervaloReverificacaoDias(configService.getIntervaloReverificacaoDias())
                    .build();
            // saveAndFlush, não save: com GenerationType.SEQUENCE o save()
            // só aloca o ID da sequence — o INSERT real (e a violação do
            // índice único uk_sr_ficha_veiculo_ci, se houver corrida) só
            // aconteceria no flush/commit do Hibernate, tarde demais pro
            // catch(DataIntegrityViolationException) em query() pegar.
            // Confirmado em teste de concorrência real com o mesmo padrão
            // em UsuarioService (500 em vez do fallback esperado).
            FichaTecnica salva = fichaTecnicaRepository.saveAndFlush(ficha);
            log.info("Ficha salva no banco — {} {} {}",
                    marca, modelo, versao);
            return salva.getId();
        } catch (JsonProcessingException e) {
            log.error("Falha ao serializar campos: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Atualiza uma ficha JÁ EXISTENTE com campos mesclados (cache
     * antigo + atributos novos buscados no Gemini) — diferente de
     * salvarFicha, que sempre cria uma linha nova. Usado no fluxo de
     * "cache parcial" (Nível 2): a ficha já existe, só precisava de
     * mais atributos.
     * <p>
     * verificadoEm é setado explicitamente aqui (não só @PreUpdate,
     * que só toca atualizadoEm) — os atributos novos acabaram de ser
     * verificados de verdade contra o Gemini agora, então faz sentido
     * refletir isso.
     */
    private void atualizarFicha(FichaTecnica ficha, List<CampoSpec> camposMesclados,
                                String confidenceGeral) {
        try {
            String camposJson = objectMapper.writeValueAsString(camposMesclados);
            ficha.setCamposJson(camposJson);
            ficha.setConfidenceGeral(ConfidenceLevel.valueOf(confidenceGeral));
            ficha.setVerificadoEm(LocalDateTime.now());
            fichaTecnicaRepository.save(ficha);
            log.info("Ficha atualizada no banco (atributos novos mesclados) — {} {} {}",
                    ficha.getMarca(), ficha.getModelo(), ficha.getVersao());
        } catch (JsonProcessingException e) {
            log.error("Falha ao serializar campos mesclados: {}", e.getMessage());
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
            return filtrarCampos(todos, atributosFiltro);

        } catch (Exception e) {
            log.error("Falha ao parsear campos JSON: {}", e.getMessage());
            return atributosFiltro != null
                    ? atributosFiltro.stream()
                    .map(CampoSpec::naoEncontrado).toList()
                    : List.of();
        }
    }

    /**
     * Filtra uma lista de campos já em memória pelos atributos
     * pedidos — mesma lógica que parsearCamposJson usa depois de
     * desserializar, extraída aqui pra ser reaproveitada também na
     * primeira consulta (cache miss), quando buscamos mais atributos
     * do Gemini do que o usuário pediu (ver query()), mas a resposta
     * devolvida só deve mostrar o que foi de fato solicitado.
     */
    private List<CampoSpec> filtrarCampos(List<CampoSpec> todos,
                                          List<String> atributosFiltro) {
        if (atributosFiltro == null || atributosFiltro.isEmpty()) {
            return todos;
        }
        return atributosFiltro.stream()
                .map(attr -> todos.stream()
                        .filter(c -> attr.equalsIgnoreCase(c.campo()))
                        .findFirst()
                        .orElse(CampoSpec.naoEncontrado(attr)))
                .toList();
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
     * Campos com valor numérico isolado e comparável, com a(s)
     * unidade(s) que aparece(m) no texto (ex: "397 cv @ 5.650 rpm") e
     * a direção de "quem vence" — maior potência/torque/consumo é
     * melhor, mas menor tempo de aceleração e menor preço são
     * melhores.
     * <p>
     * Alguns campos aceitam mais de uma unidade — torque, por
     * exemplo, já veio como "Nm" e como "kgfm" em respostas reais do
     * Gemini (kgfm é comum em fichas técnicas brasileiras). Cada
     * unidade reconhecida tem seu próprio multiplicador pra converter
     * pra uma base comum (Nm) antes de comparar — sem isso, "55 kgfm"
     * e "583 Nm" pareceriam números direto comparáveis quando não são.
     * As unidades são tentadas na ordem declarada; a primeira que
     * bater no texto é usada (por isso Nm vem antes de kgfm — é a
     * mais comum nas respostas que já vimos).
     * <p>
     * Campos fora desta lista (motor, transmissao, tracao,
     * amortecedores, modos_conducao, farois, rodas_pneus, dimensoes,
     * modos_volante, modos_escapamento, modos_amortecedor) são texto
     * descritivo ou multivalorado (dimensoes tem 3 números na mesma
     * string — comprimento x largura x altura — sem uma direção única
     * de "melhor"), não um número isolado com direção objetiva —
     * tentar comparar produziria um resultado tão arbitrário quanto o
     * bug antigo (concatenar todos os dígitos do texto). Para esses,
     * o vencedor é sempre "N/A".
     */
    private enum CampoNumerico {
        POTENCIA("potencia", true,
                unidade("(?i)(\\d+(?:[.,]\\d+)?)\\s*cv", 1.0)),
        TORQUE("torque", true,
                unidade("(?i)(\\d+(?:[.,]\\d+)?)\\s*nm", 1.0),
                // 1 kgf·m ≈ 9,80665 Nm
                unidade("(?i)(\\d+(?:[.,]\\d+)?)\\s*kgf\\s*\\.?\\s*m", 9.80665)),
        ACELERACAO("aceleracao", false,
                unidade("(?i)(\\d+(?:[.,]\\d+)?)\\s*segundos", 1.0)),
        PRECO("preco", false,
                unidade("(?i)r\\$\\s*(\\d{1,3}(?:\\.\\d{3})*(?:,\\d+)?)", 1.0)),
        // Formato ainda não confirmado com dado real do Gemini — km/l
        // é o padrão brasileiro, mas pode precisar de ajuste assim que
        // virmos a primeira resposta real com esse atributo.
        CONSUMO("consumo", true,
                unidade("(?i)(\\d+(?:[.,]\\d+)?)\\s*km/l", 1.0));

        private final String nomeCampo;
        private final boolean maiorVence;
        private final List<UnidadeReconhecida> unidades;

        CampoNumerico(String nomeCampo, boolean maiorVence, UnidadeReconhecida... unidades) {
            this.nomeCampo = nomeCampo;
            this.maiorVence = maiorVence;
            this.unidades = List.of(unidades);
        }

        private static UnidadeReconhecida unidade(String regex, double multiplicadorParaBase) {
            return new UnidadeReconhecida(Pattern.compile(regex), multiplicadorParaBase);
        }

        static CampoNumerico doCampo(String campo) {
            for (CampoNumerico c : values()) {
                if (c.nomeCampo.equalsIgnoreCase(campo)) return c;
            }
            return null;
        }

        Double extrair(String valor) {
            if (valor == null) return null;
            for (UnidadeReconhecida u : unidades) {
                Matcher m = u.padrao().matcher(valor);
                if (m.find()) {
                    String numero = m.group(1).replace(".", "").replace(",", ".");
                    try {
                        return Double.parseDouble(numero) * u.multiplicador();
                    } catch (NumberFormatException e) {
                        return null;
                    }
                }
            }
            return null;
        }

        private record UnidadeReconhecida(Pattern padrao, double multiplicador) {}
    }

    /**
     * Determina o vencedor de um atributo entre dois veículos.
     * "N/A" sempre que a comparação não for possível ou não fizer
     * sentido — nunca declara vencedor por eliminação (dado ausente
     * de um lado não torna o outro automaticamente "melhor").
     */
    private String determinarVencedor(CampoSpec campo1, CampoSpec campo2,
                                      String nomeVeiculo1,
                                      String nomeVeiculo2) {
        if (campo1 == null || campo2 == null
                || campo1.valor() == null || campo2.valor() == null) {
            return "N/A";
        }

        CampoNumerico config = CampoNumerico.doCampo(campo1.campo());
        if (config == null) {
            return "N/A";
        }

        Double v1 = config.extrair(campo1.valor());
        Double v2 = config.extrair(campo2.valor());
        if (v1 == null || v2 == null) {
            return "N/A";
        }

        if (v1.doubleValue() == v2.doubleValue()) {
            return "EMPATE";
        }

        boolean venceV1 = config.maiorVence ? v1 > v2 : v1 < v2;
        return venceV1 ? nomeVeiculo1 : nomeVeiculo2;
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