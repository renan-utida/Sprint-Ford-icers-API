package com.icers.ford.service;

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
import com.icers.ford.model.enums.Role;
import com.icers.ford.repository.FichaTecnicaRepository;
import com.icers.ford.repository.HistoricoConsultaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

// SpecService é a classe mais complexa do projeto — cobre resolução de
// cache (hit/miss/parcial/expirada), a regressão de concorrência do
// saveAndFlush, rate limiting por usuário, busca direta, comparação entre
// veículos (incluindo o parser de valores numéricos com unidade) e as
// operações de histórico/exclusão.
@ExtendWith(MockitoExtension.class)
@DisplayName("Testes - SpecService")
public class SpecServiceTest {

    @Mock
    private FichaTecnicaRepository fichaTecnicaRepository;

    @Mock
    private HistoricoConsultaRepository historicoRepository;

    @Mock
    private LlmClient llmClient;

    @Mock
    private ConfigService configService;

    @Mock
    private AuditService auditService;

    private ObjectMapper objectMapper;
    private SpecService specService;

    private Usuario usuario;
    private static final String IP = "127.0.0.1";
    private static final String MARCA = "Ford";
    private static final String MODELO = "Ranger";
    private static final String VERSAO = "Raptor";

    @BeforeEach
    public void setUp() {
        objectMapper = new ObjectMapper();
        // requestsPerMinute alto de propósito — os testes de cache não
        // devem esbarrar em rate limit; isso é coberto isolado na Parte 2.
        specService = new SpecService(
                fichaTecnicaRepository, historicoRepository, llmClient,
                configService, auditService, objectMapper,
                1000, 1000
        );

        usuario = Usuario.builder()
                .id(1L)
                .email("analyst@specradar.com")
                .role(Role.ANALYST)
                .build();
    }

    private String jsonDeCampos(List<CampoSpec> campos) throws Exception {
        return objectMapper.writeValueAsString(campos);
    }

    private FichaTecnica fichaValida(List<CampoSpec> campos, int intervaloDias,
                                     LocalDateTime verificadoEm) throws Exception {
        return FichaTecnica.builder()
                .id(10L)
                .marca(MARCA)
                .modelo(MODELO)
                .versao(VERSAO)
                .camposJson(jsonDeCampos(campos))
                .confidenceGeral(ConfidenceLevel.ALTA)
                .intervaloReverificacaoDias(intervaloDias)
                .verificadoEm(verificadoEm)
                .criadoPor(usuario)
                .build();
    }

    private SpecQueryRequest requestPadrao(List<String> atributos) {
        return new SpecQueryRequest(MARCA, MODELO, VERSAO, atributos);
    }

    private FichaTecnica fichaComVeiculo(Long id, String marca, String modelo, String versao,
                                         List<CampoSpec> campos) throws Exception {
        return FichaTecnica.builder()
                .id(id)
                .marca(marca)
                .modelo(modelo)
                .versao(versao)
                .camposJson(jsonDeCampos(campos))
                .confidenceGeral(ConfidenceLevel.ALTA)
                .intervaloReverificacaoDias(15)
                .verificadoEm(LocalDateTime.now())
                .criadoPor(usuario)
                .build();
    }

    // ================= CACHE HIT (não expirada, sem faltar nada) =================

    @Test
    @DisplayName("Cache hit: não deve chamar o LLM quando a ficha tem tudo que foi pedido e não expirou")
    public void testCacheHitNaoChamaLlm() throws Exception {
        List<CampoSpec> camposSalvos = List.of(
                CampoSpec.encontrado("motor", "V6 3.0L", "ALTA", "url", "2026-01-01"),
                CampoSpec.encontrado("potencia", "397cv", "ALTA", "url", "2026-01-01")
        );
        FichaTecnica ficha = fichaValida(camposSalvos, 15, LocalDateTime.now().minusDays(1));

        when(fichaTecnicaRepository.findFirstByMarcaIgnoreCaseAndModeloIgnoreCaseAndVersaoIgnoreCase(
                MARCA, MODELO, VERSAO)).thenReturn(Optional.of(ficha));

        SpecResponse resposta = specService.query(
                requestPadrao(List.of("motor", "potencia")), usuario, IP);

        assertTrue(resposta.cacheHit());
        assertEquals(10L, resposta.id());
        assertEquals(2, resposta.campos().size());
        verify(llmClient, never()).consultarEspecificacoes(any(), any(), any(), any());
        verify(fichaTecnicaRepository, never()).saveAndFlush(any());
        verify(fichaTecnicaRepository, never()).save(any());
    }

    @Test
    @DisplayName("Cache hit: deve filtrar a resposta só pelos atributos pedidos, mesmo com mais campos salvos")
    public void testCacheHitFiltraApenasAtributosPedidos() throws Exception {
        List<CampoSpec> camposSalvos = List.of(
                CampoSpec.encontrado("motor", "V6 3.0L", "ALTA", "url", "2026-01-01"),
                CampoSpec.encontrado("potencia", "397cv", "ALTA", "url", "2026-01-01"),
                CampoSpec.encontrado("preco", "R$ 499.000", "ALTA", "url", "2026-01-01")
        );
        FichaTecnica ficha = fichaValida(camposSalvos, 15, LocalDateTime.now().minusDays(1));

        when(fichaTecnicaRepository.findFirstByMarcaIgnoreCaseAndModeloIgnoreCaseAndVersaoIgnoreCase(
                MARCA, MODELO, VERSAO)).thenReturn(Optional.of(ficha));

        SpecResponse resposta = specService.query(
                requestPadrao(List.of("motor")), usuario, IP);

        assertEquals(1, resposta.campos().size());
        assertEquals("motor", resposta.campos().get(0).campo());
    }

    @Test
    @DisplayName("Cache hit: deve registrar histórico e auditoria com cacheHit=true")
    public void testCacheHitRegistraHistoricoEAuditoriaComCacheHitVerdadeiro() throws Exception {
        List<CampoSpec> camposSalvos = List.of(
                CampoSpec.encontrado("motor", "V6 3.0L", "ALTA", "url", "2026-01-01"));
        FichaTecnica ficha = fichaValida(camposSalvos, 15, LocalDateTime.now().minusDays(1));

        when(fichaTecnicaRepository.findFirstByMarcaIgnoreCaseAndModeloIgnoreCaseAndVersaoIgnoreCase(
                MARCA, MODELO, VERSAO)).thenReturn(Optional.of(ficha));

        specService.query(requestPadrao(List.of("motor")), usuario, IP);

        ArgumentCaptor<HistoricoConsulta> captor = ArgumentCaptor.forClass(HistoricoConsulta.class);
        verify(historicoRepository).save(captor.capture());
        assertEquals("S", captor.getValue().getCacheHit());

        verify(auditService).logConsulta(
                eq(1L), eq("/api/v1/specs/query"), eq(IP),
                eq(MARCA), eq(MODELO), eq(VERSAO), eq(true), eq(200));
    }

    // ================= CACHE MISS (nenhuma ficha encontrada) =================

    @Test
    @DisplayName("Cache miss: deve chamar o LLM e salvar uma ficha nova")
    public void testCacheMissChamaLlmESalva() {
        when(fichaTecnicaRepository.findFirstByMarcaIgnoreCaseAndModeloIgnoreCaseAndVersaoIgnoreCase(
                MARCA, MODELO, VERSAO)).thenReturn(Optional.empty());
        when(configService.getAtributosPadrao()).thenReturn(List.of("motor", "torque"));
        when(configService.getIntervaloReverificacaoDias()).thenReturn(15);
        when(llmClient.consultarEspecificacoes(eq(MARCA), eq(MODELO), eq(VERSAO), anyList()))
                .thenReturn(List.of(
                        CampoSpec.encontrado("motor", "V6 3.0L", "ALTA", "url", "2026-01-01"),
                        CampoSpec.encontrado("torque", "583Nm", "ALTA", "url", "2026-01-01")
                ));

        FichaTecnica salva = FichaTecnica.builder().id(20L).build();
        when(fichaTecnicaRepository.saveAndFlush(any(FichaTecnica.class))).thenReturn(salva);

        SpecResponse resposta = specService.query(
                requestPadrao(List.of("motor")), usuario, IP);

        assertFalse(resposta.cacheHit());
        assertEquals(20L, resposta.id());
        verify(fichaTecnicaRepository, times(1)).saveAndFlush(any(FichaTecnica.class));
    }

    @Test
    @DisplayName("Cache miss: deve buscar no LLM a união dos atributos pedidos com os padrão, sem duplicar")
    public void testCacheMissBuscaUniaoComAtributosPadrao() {
        when(fichaTecnicaRepository.findFirstByMarcaIgnoreCaseAndModeloIgnoreCaseAndVersaoIgnoreCase(
                MARCA, MODELO, VERSAO)).thenReturn(Optional.empty());
        when(configService.getAtributosPadrao()).thenReturn(List.of("motor", "potencia"));
        when(configService.getIntervaloReverificacaoDias()).thenReturn(15);
        when(llmClient.consultarEspecificacoes(eq(MARCA), eq(MODELO), eq(VERSAO), anyList()))
                .thenReturn(List.of(CampoSpec.encontrado("motor", "V6", "ALTA", "url", "2026-01-01")));
        when(fichaTecnicaRepository.saveAndFlush(any(FichaTecnica.class)))
                .thenReturn(FichaTecnica.builder().id(20L).build());

        // Pediu só "motor" — mas motor já está nos padrão, então a união
        // não deve duplicar
        specService.query(requestPadrao(List.of("motor")), usuario, IP);

        ArgumentCaptor<List<String>> captor = ArgumentCaptor.forClass(List.class);
        verify(llmClient).consultarEspecificacoes(eq(MARCA), eq(MODELO), eq(VERSAO), captor.capture());

        List<String> atributosBuscados = captor.getValue();
        assertEquals(2, atributosBuscados.size());
        assertTrue(atributosBuscados.contains("motor"));
        assertTrue(atributosBuscados.contains("potencia"));
    }

    @Test
    @DisplayName("Cache miss: a ficha salva deve travar o intervalo de reverificação global atual")
    public void testCacheMissTravaIntervaloGlobalAtual() {
        when(fichaTecnicaRepository.findFirstByMarcaIgnoreCaseAndModeloIgnoreCaseAndVersaoIgnoreCase(
                MARCA, MODELO, VERSAO)).thenReturn(Optional.empty());
        when(configService.getAtributosPadrao()).thenReturn(List.of("motor"));
        when(configService.getIntervaloReverificacaoDias()).thenReturn(20);
        when(llmClient.consultarEspecificacoes(eq(MARCA), eq(MODELO), eq(VERSAO), anyList()))
                .thenReturn(List.of(CampoSpec.encontrado("motor", "V6", "ALTA", "url", "2026-01-01")));
        when(fichaTecnicaRepository.saveAndFlush(any(FichaTecnica.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        specService.query(requestPadrao(List.of("motor")), usuario, IP);

        ArgumentCaptor<FichaTecnica> captor = ArgumentCaptor.forClass(FichaTecnica.class);
        verify(fichaTecnicaRepository).saveAndFlush(captor.capture());
        assertEquals(20, captor.getValue().getIntervaloReverificacaoDias());
    }

    // ================= CACHE EXPIRADA (passou do prazo de reverificação) =================

    @Test
    @DisplayName("Cache expirada: deve reverificar tudo no LLM e SUBSTITUIR o conteúdo da ficha")
    public void testCacheExpiradaReverificaESubstitui() throws Exception {
        List<CampoSpec> camposAntigos = List.of(
                CampoSpec.encontrado("motor", "V6 3.0L (desatualizado)", "ALTA", "url-antiga", "2025-01-01"));
        // verificadoEm há 20 dias, intervalo de 15 — expirada
        FichaTecnica ficha = fichaValida(camposAntigos, 15, LocalDateTime.now().minusDays(20));

        when(fichaTecnicaRepository.findFirstByMarcaIgnoreCaseAndModeloIgnoreCaseAndVersaoIgnoreCase(
                MARCA, MODELO, VERSAO)).thenReturn(Optional.of(ficha));
        when(configService.getIntervaloReverificacaoDias()).thenReturn(15);
        when(llmClient.consultarEspecificacoes(eq(MARCA), eq(MODELO), eq(VERSAO), anyList()))
                .thenReturn(List.of(
                        CampoSpec.encontrado("motor", "V6 3.0L (atualizado)", "ALTA", "url-nova", "2026-01-01")));

        SpecResponse resposta = specService.query(
                requestPadrao(List.of("motor")), usuario, IP);

        assertFalse(resposta.cacheHit());
        assertEquals("V6 3.0L (atualizado)", resposta.campos().get(0).valor());
        verify(fichaTecnicaRepository, times(1)).save(ficha);
        verify(fichaTecnicaRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("Cache expirada: deve reverificar os campos já conhecidos MAIS os novos faltando, sem duplicar")
    public void testCacheExpiradaReverificaUniaoDeConhecidosEFaltando() throws Exception {
        List<CampoSpec> camposAntigos = List.of(
                CampoSpec.encontrado("motor", "V6", "ALTA", "url", "2025-01-01"));
        FichaTecnica ficha = fichaValida(camposAntigos, 15, LocalDateTime.now().minusDays(20));

        when(fichaTecnicaRepository.findFirstByMarcaIgnoreCaseAndModeloIgnoreCaseAndVersaoIgnoreCase(
                MARCA, MODELO, VERSAO)).thenReturn(Optional.of(ficha));
        when(configService.getIntervaloReverificacaoDias()).thenReturn(15);
        when(llmClient.consultarEspecificacoes(eq(MARCA), eq(MODELO), eq(VERSAO), anyList()))
                .thenReturn(List.of(
                        CampoSpec.encontrado("motor", "V6", "ALTA", "url", "2026-01-01"),
                        CampoSpec.encontrado("potencia", "397cv", "ALTA", "url", "2026-01-01")));

        // Pede "potencia", que não estava na ficha antiga
        specService.query(requestPadrao(List.of("potencia")), usuario, IP);

        ArgumentCaptor<List<String>> captor = ArgumentCaptor.forClass(List.class);
        verify(llmClient).consultarEspecificacoes(eq(MARCA), eq(MODELO), eq(VERSAO), captor.capture());

        List<String> reverificados = captor.getValue();
        assertEquals(2, reverificados.size());
        assertTrue(reverificados.contains("motor"));
        assertTrue(reverificados.contains("potencia"));
    }

    @Test
    @DisplayName("Cache expirada: deve renovar o intervalo da ficha para o valor global ATUAL")
    public void testCacheExpiradaRenovaIntervaloParaValorGlobalAtual() throws Exception {
        List<CampoSpec> camposAntigos = List.of(
                CampoSpec.encontrado("motor", "V6", "ALTA", "url", "2025-01-01"));
        // Ficha foi criada com intervalo 15, mas o ADMIN já mudou o global pra 25
        FichaTecnica ficha = fichaValida(camposAntigos, 15, LocalDateTime.now().minusDays(20));

        when(fichaTecnicaRepository.findFirstByMarcaIgnoreCaseAndModeloIgnoreCaseAndVersaoIgnoreCase(
                MARCA, MODELO, VERSAO)).thenReturn(Optional.of(ficha));
        when(configService.getIntervaloReverificacaoDias()).thenReturn(25);
        when(llmClient.consultarEspecificacoes(eq(MARCA), eq(MODELO), eq(VERSAO), anyList()))
                .thenReturn(List.of(CampoSpec.encontrado("motor", "V6", "ALTA", "url", "2026-01-01")));

        specService.query(requestPadrao(List.of("motor")), usuario, IP);

        assertEquals(25, ficha.getIntervaloReverificacaoDias());
    }

    // ================= CACHE PARCIAL (não expirada, faltam atributos novos) =================

    @Test
    @DisplayName("Cache parcial: deve buscar SÓ o que falta no LLM e mesclar com o que já existia")
    public void testCacheParcialBuscaSoOQueFaltaEMescla() throws Exception {
        List<CampoSpec> camposAntigos = List.of(
                CampoSpec.encontrado("motor", "V6 3.0L", "ALTA", "url", "2026-01-01"));
        // não expirada — verificada ontem, intervalo de 15 dias
        FichaTecnica ficha = fichaValida(camposAntigos, 15, LocalDateTime.now().minusDays(1));

        when(fichaTecnicaRepository.findFirstByMarcaIgnoreCaseAndModeloIgnoreCaseAndVersaoIgnoreCase(
                MARCA, MODELO, VERSAO)).thenReturn(Optional.of(ficha));
        when(llmClient.consultarEspecificacoes(eq(MARCA), eq(MODELO), eq(VERSAO), eq(List.of("potencia"))))
                .thenReturn(List.of(CampoSpec.encontrado("potencia", "397cv", "ALTA", "url", "2026-01-01")));

        SpecResponse resposta = specService.query(
                requestPadrao(List.of("motor", "potencia")), usuario, IP);

        assertFalse(resposta.cacheHit());
        assertEquals(2, resposta.campos().size());
        verify(llmClient, times(1)).consultarEspecificacoes(eq(MARCA), eq(MODELO), eq(VERSAO), eq(List.of("potencia")));
        verify(fichaTecnicaRepository, times(1)).save(ficha);
    }

    @Test
    @DisplayName("Cache parcial: não deve chamar configService.getIntervaloReverificacaoDias (intervalo não muda, só a expirada renova)")
    public void testCacheParcialNaoRenovaIntervalo() throws Exception {
        List<CampoSpec> camposAntigos = List.of(
                CampoSpec.encontrado("motor", "V6 3.0L", "ALTA", "url", "2026-01-01"));
        FichaTecnica ficha = fichaValida(camposAntigos, 15, LocalDateTime.now().minusDays(1));

        when(fichaTecnicaRepository.findFirstByMarcaIgnoreCaseAndModeloIgnoreCaseAndVersaoIgnoreCase(
                MARCA, MODELO, VERSAO)).thenReturn(Optional.of(ficha));
        when(llmClient.consultarEspecificacoes(eq(MARCA), eq(MODELO), eq(VERSAO), anyList()))
                .thenReturn(List.of(CampoSpec.encontrado("potencia", "397cv", "ALTA", "url", "2026-01-01")));

        specService.query(requestPadrao(List.of("motor", "potencia")), usuario, IP);

        assertEquals(15, ficha.getIntervaloReverificacaoDias());
        verify(configService, never()).getIntervaloReverificacaoDias();
    }

    // ================= CONCORRÊNCIA (regressão saveAndFlush) =================

    @Test
    @DisplayName("REGRESSÃO — corrida de concorrência no cache miss: DataIntegrityViolationException do saveAndFlush não deve propagar, deve devolver a ficha da requisição vencedora")
    public void testCorridaDeConcorrenciaDevolveFichaVencedora() throws Exception {
        List<CampoSpec> camposDaVencedora = List.of(
                CampoSpec.encontrado("motor", "V6 3.0L", "ALTA", "url", "2026-01-01"));
        FichaTecnica fichaJaSalvaPelaVencedora = fichaValida(camposDaVencedora, 15, LocalDateTime.now());

        // 1ª chamada (antes do saveAndFlush): cache miss de verdade.
        // 2ª chamada (dentro do catch): a requisição concorrente já commitou.
        when(fichaTecnicaRepository.findFirstByMarcaIgnoreCaseAndModeloIgnoreCaseAndVersaoIgnoreCase(
                MARCA, MODELO, VERSAO))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(fichaJaSalvaPelaVencedora));

        when(configService.getAtributosPadrao()).thenReturn(List.of("motor"));
        when(llmClient.consultarEspecificacoes(eq(MARCA), eq(MODELO), eq(VERSAO), anyList()))
                .thenReturn(List.of(CampoSpec.encontrado("motor", "V6 3.0L", "ALTA", "url", "2026-01-01")));
        when(fichaTecnicaRepository.saveAndFlush(any(FichaTecnica.class)))
                .thenThrow(new DataIntegrityViolationException("uk_sr_ficha_veiculo_ci violada"));

        SpecResponse resposta = specService.query(
                requestPadrao(List.of("motor")), usuario, IP);

        assertTrue(resposta.cacheHit());
        assertEquals(10L, resposta.id());
        verify(fichaTecnicaRepository, times(2))
                .findFirstByMarcaIgnoreCaseAndModeloIgnoreCaseAndVersaoIgnoreCase(MARCA, MODELO, VERSAO);
        verify(fichaTecnicaRepository, never()).save(any());
    }

    @Test
    @DisplayName("REGRESSÃO — se a ficha vencedora não for encontrada nem no retry, deve relançar a exceção original (não mascarar um erro real de banco)")
    public void testCorridaDeConcorrenciaSemFichaEncontradaRelancaExcecaoOriginal() {
        when(fichaTecnicaRepository.findFirstByMarcaIgnoreCaseAndModeloIgnoreCaseAndVersaoIgnoreCase(
                MARCA, MODELO, VERSAO))
                .thenReturn(Optional.empty());

        when(configService.getAtributosPadrao()).thenReturn(List.of("motor"));
        when(llmClient.consultarEspecificacoes(eq(MARCA), eq(MODELO), eq(VERSAO), anyList()))
                .thenReturn(List.of(CampoSpec.encontrado("motor", "V6 3.0L", "ALTA", "url", "2026-01-01")));

        DataIntegrityViolationException excecaoOriginal =
                new DataIntegrityViolationException("erro real de banco, não é corrida");
        when(fichaTecnicaRepository.saveAndFlush(any(FichaTecnica.class)))
                .thenThrow(excecaoOriginal);

        DataIntegrityViolationException lancada = assertThrows(DataIntegrityViolationException.class,
                () -> specService.query(requestPadrao(List.of("motor")), usuario, IP));

        assertSame(excecaoOriginal, lancada);
    }

    // ================= RATE LIMITING POR USUÁRIO =================

    private SpecService specServiceComCapacidade(int capacidade) {
        return new SpecService(
                fichaTecnicaRepository, historicoRepository, llmClient,
                configService, auditService, objectMapper,
                capacidade, capacidade
        );
    }

    @Test
    @DisplayName("Rate limit: não deve lançar enquanto estiver dentro do limite")
    public void testRateLimitDentroDoLimiteNaoLanca() throws Exception {
        SpecService servico = specServiceComCapacidade(2);
        FichaTecnica ficha = fichaValida(
                List.of(CampoSpec.encontrado("motor", "V6", "ALTA", "url", "2026-01-01")),
                15, LocalDateTime.now());
        when(fichaTecnicaRepository.findFirstByMarcaIgnoreCaseAndModeloIgnoreCaseAndVersaoIgnoreCase(
                MARCA, MODELO, VERSAO)).thenReturn(Optional.of(ficha));

        assertDoesNotThrow(() -> servico.query(requestPadrao(List.of("motor")), usuario, IP));
        assertDoesNotThrow(() -> servico.query(requestPadrao(List.of("motor")), usuario, IP));
    }

    @Test
    @DisplayName("Rate limit: deve lançar RateLimitExceededException ao exceder o limite do usuário")
    public void testRateLimitExcedidoLanca() throws Exception {
        SpecService servico = specServiceComCapacidade(2);
        FichaTecnica ficha = fichaValida(
                List.of(CampoSpec.encontrado("motor", "V6", "ALTA", "url", "2026-01-01")),
                15, LocalDateTime.now());
        when(fichaTecnicaRepository.findFirstByMarcaIgnoreCaseAndModeloIgnoreCaseAndVersaoIgnoreCase(
                MARCA, MODELO, VERSAO)).thenReturn(Optional.of(ficha));

        servico.query(requestPadrao(List.of("motor")), usuario, IP);
        servico.query(requestPadrao(List.of("motor")), usuario, IP);

        RateLimitExceededException ex = assertThrows(RateLimitExceededException.class,
                () -> servico.query(requestPadrao(List.of("motor")), usuario, IP));

        assertTrue(ex.getRetryAfterSeconds() > 0);
        verify(auditService, times(1)).logRateLimitExceeded(
                eq(usuario.getId()), eq("/api/v1/specs/query"), eq(IP));
    }

    @Test
    @DisplayName("Rate limit: ao exceder, não deve tocar em cache/LLM — a checagem acontece antes de tudo")
    public void testRateLimitExcedidoNaoTocaCacheNemLlm() throws Exception {
        SpecService servico = specServiceComCapacidade(1);
        FichaTecnica ficha = fichaValida(
                List.of(CampoSpec.encontrado("motor", "V6", "ALTA", "url", "2026-01-01")),
                15, LocalDateTime.now());
        when(fichaTecnicaRepository.findFirstByMarcaIgnoreCaseAndModeloIgnoreCaseAndVersaoIgnoreCase(
                MARCA, MODELO, VERSAO)).thenReturn(Optional.of(ficha));

        servico.query(requestPadrao(List.of("motor")), usuario, IP);
        clearInvocations(fichaTecnicaRepository, llmClient);

        assertThrows(RateLimitExceededException.class,
                () -> servico.query(requestPadrao(List.of("motor")), usuario, IP));

        verifyNoInteractions(llmClient);
        verify(fichaTecnicaRepository, never())
                .findFirstByMarcaIgnoreCaseAndModeloIgnoreCaseAndVersaoIgnoreCase(any(), any(), any());
    }

    @Test
    @DisplayName("Rate limit: buckets são por usuário — esgotar o de um não afeta outro usuário")
    public void testRateLimitEIndependentePorUsuario() throws Exception {
        SpecService servico = specServiceComCapacidade(1);
        FichaTecnica ficha = fichaValida(
                List.of(CampoSpec.encontrado("motor", "V6", "ALTA", "url", "2026-01-01")),
                15, LocalDateTime.now());
        when(fichaTecnicaRepository.findFirstByMarcaIgnoreCaseAndModeloIgnoreCaseAndVersaoIgnoreCase(
                MARCA, MODELO, VERSAO)).thenReturn(Optional.of(ficha));

        Usuario outroUsuario = Usuario.builder().id(2L).email("outro@specradar.com").role(Role.ANALYST).build();

        servico.query(requestPadrao(List.of("motor")), usuario, IP);
        assertThrows(RateLimitExceededException.class,
                () -> servico.query(requestPadrao(List.of("motor")), usuario, IP));

        // usuário diferente, mesmo IP — bucket próprio, não deveria estar esgotado
        assertDoesNotThrow(() -> servico.query(requestPadrao(List.of("motor")), outroUsuario, IP));
    }

    @Test
    @DisplayName("Rate limit: bucket de /query é independente do bucket de /from-pdf, mesmo usuário")
    public void testRateLimitIndependenteEntreQueryEFromPdf() throws Exception {
        SpecService servico = specServiceComCapacidade(1);
        FichaTecnica ficha = fichaValida(
                List.of(CampoSpec.encontrado("motor", "V6", "ALTA", "url", "2026-01-01")),
                15, LocalDateTime.now());
        when(fichaTecnicaRepository.findFirstByMarcaIgnoreCaseAndModeloIgnoreCaseAndVersaoIgnoreCase(
                MARCA, MODELO, VERSAO)).thenReturn(Optional.of(ficha));

        // Esgota o bucket de /query
        servico.query(requestPadrao(List.of("motor")), usuario, IP);
        assertThrows(RateLimitExceededException.class,
                () -> servico.query(requestPadrao(List.of("motor")), usuario, IP));

        // /from-pdf usa um bucket completamente separado — não deveria estar esgotado
        SpecFromPdfRequest requestPdf = new SpecFromPdfRequest(MARCA, MODELO, VERSAO, List.of("motor"));
        assertDoesNotThrow(() -> servico.queryFromPdf(requestPdf, new byte[]{1, 2, 3}, usuario, IP));
    }

    // ================= FIND BY VEICULO =================

    @Test
    @DisplayName("findByVeiculo deve retornar a ficha quando ela existe")
    public void testFindByVeiculoExistente() throws Exception {
        FichaTecnica ficha = fichaValida(
                List.of(CampoSpec.encontrado("motor", "V6", "ALTA", "url", "2026-01-01")),
                15, LocalDateTime.now());
        when(fichaTecnicaRepository.findFirstByMarcaIgnoreCaseAndModeloIgnoreCaseAndVersaoIgnoreCase(
                MARCA, MODELO, VERSAO)).thenReturn(Optional.of(ficha));

        SpecResponse resposta = specService.findByVeiculo(MARCA, MODELO, VERSAO);

        assertEquals(10L, resposta.id());
        assertTrue(resposta.cacheHit());
    }

    @Test
    @DisplayName("findByVeiculo deve lançar FichaNaoEncontradaException sem sugestões quando não há outras versões")
    public void testFindByVeiculoInexistenteSemSugestoes() {
        when(fichaTecnicaRepository.findFirstByMarcaIgnoreCaseAndModeloIgnoreCaseAndVersaoIgnoreCase(
                MARCA, MODELO, VERSAO)).thenReturn(Optional.empty());
        when(fichaTecnicaRepository.findByMarcaIgnoreCaseAndModeloIgnoreCase(MARCA, MODELO))
                .thenReturn(List.of());

        FichaNaoEncontradaException ex = assertThrows(FichaNaoEncontradaException.class,
                () -> specService.findByVeiculo(MARCA, MODELO, VERSAO));

        assertTrue(ex.getSugestoesSimilares().isEmpty());
    }

    @Test
    @DisplayName("findByVeiculo deve sugerir outras versões já cacheadas do mesmo marca+modelo, limitadas a 5")
    public void testFindByVeiculoInexistenteComSugestoesLimitadasA5() throws Exception {
        when(fichaTecnicaRepository.findFirstByMarcaIgnoreCaseAndModeloIgnoreCaseAndVersaoIgnoreCase(
                MARCA, MODELO, VERSAO)).thenReturn(Optional.empty());

        List<FichaTecnica> outrasVersoes = List.of(
                fichaComVeiculo(1L, MARCA, MODELO, "XLT", List.of()),
                fichaComVeiculo(2L, MARCA, MODELO, "Limited", List.of()),
                fichaComVeiculo(3L, MARCA, MODELO, "XL", List.of()),
                fichaComVeiculo(4L, MARCA, MODELO, "Storm", List.of()),
                fichaComVeiculo(5L, MARCA, MODELO, "Black", List.of()),
                fichaComVeiculo(6L, MARCA, MODELO, "Wildtrak", List.of())
        );
        when(fichaTecnicaRepository.findByMarcaIgnoreCaseAndModeloIgnoreCase(MARCA, MODELO))
                .thenReturn(outrasVersoes);

        FichaNaoEncontradaException ex = assertThrows(FichaNaoEncontradaException.class,
                () -> specService.findByVeiculo(MARCA, MODELO, VERSAO));

        assertEquals(5, ex.getSugestoesSimilares().size());
        assertTrue(ex.getSugestoesSimilares().get(0).contains("XLT"));
    }

    // ================= COMPARE / VENCEDOR =================

    @Test
    @DisplayName("compare deve indicar o vencedor certo por atributo: maior vence, menor vence, empate e N/A")
    public void testCompareDeterminaVencedoresCorretamente() throws Exception {
        FichaTecnica ficha1 = fichaComVeiculo(1L, "Ford", "Ranger", "Raptor", List.of(
                CampoSpec.encontrado("potencia", "397cv", "ALTA", "url", "2026-01-01"),
                CampoSpec.encontrado("preco", "R$ 300.000", "ALTA", "url", "2026-01-01"),
                CampoSpec.encontrado("aceleracao", "6.1 segundos", "ALTA", "url", "2026-01-01"),
                CampoSpec.encontrado("motor", "V6 3.0L", "ALTA", "url", "2026-01-01")
        ));
        FichaTecnica ficha2 = fichaComVeiculo(2L, "Toyota", "Hilux", "SRX", List.of(
                CampoSpec.encontrado("potencia", "204cv", "ALTA", "url", "2026-01-01"),
                CampoSpec.encontrado("preco", "R$ 250.000", "ALTA", "url", "2026-01-01"),
                CampoSpec.encontrado("aceleracao", "6.1 segundos", "ALTA", "url", "2026-01-01"),
                CampoSpec.encontrado("motor", "Diesel 2.8L", "ALTA", "url", "2026-01-01")
        ));

        when(fichaTecnicaRepository.findFirstByMarcaIgnoreCaseAndModeloIgnoreCaseAndVersaoIgnoreCase(
                "Ford", "Ranger", "Raptor")).thenReturn(Optional.of(ficha1));
        when(fichaTecnicaRepository.findFirstByMarcaIgnoreCaseAndModeloIgnoreCaseAndVersaoIgnoreCase(
                "Toyota", "Hilux", "SRX")).thenReturn(Optional.of(ficha2));

        Map<String, Object> resultado = specService.compare(
                "Ford", "Ranger", "Raptor", "Toyota", "Hilux", "SRX",
                List.of("potencia", "preco", "aceleracao", "motor"));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> comparativo = (List<Map<String, Object>>) resultado.get("comparativo");

        assertEquals("Ranger", vencedorDoAtributo(comparativo, "potencia")); // maior cv vence
        assertEquals("Hilux", vencedorDoAtributo(comparativo, "preco"));    // menor preço vence
        assertEquals("EMPATE", vencedorDoAtributo(comparativo, "aceleracao")); // mesmo tempo
        assertEquals("N/A", vencedorDoAtributo(comparativo, "motor"));      // texto, fora da lista comparável
    }

    @SuppressWarnings("unchecked")
    private String vencedorDoAtributo(List<Map<String, Object>> comparativo, String atributo) {
        return comparativo.stream()
                .filter(m -> atributo.equals(m.get("atributo")))
                .findFirst()
                .map(m -> (String) m.get("vencedor"))
                .orElseThrow();
    }

    @Test
    @DisplayName("compare deve converter unidades diferentes antes de comparar (torque em Nm vs. kgfm)")
    public void testCompareConverteUnidadesDeTorqueAntesDeComparar() throws Exception {
        FichaTecnica ficha1 = fichaComVeiculo(1L, "Ford", "Ranger", "Raptor", List.of(
                CampoSpec.encontrado("torque", "583Nm", "ALTA", "url", "2026-01-01")));
        // 55 kgfm * 9.80665 ≈ 539.37 Nm — menor que 583 Nm
        FichaTecnica ficha2 = fichaComVeiculo(2L, "Toyota", "Hilux", "SRX", List.of(
                CampoSpec.encontrado("torque", "55kgfm", "ALTA", "url", "2026-01-01")));

        when(fichaTecnicaRepository.findFirstByMarcaIgnoreCaseAndModeloIgnoreCaseAndVersaoIgnoreCase(
                "Ford", "Ranger", "Raptor")).thenReturn(Optional.of(ficha1));
        when(fichaTecnicaRepository.findFirstByMarcaIgnoreCaseAndModeloIgnoreCaseAndVersaoIgnoreCase(
                "Toyota", "Hilux", "SRX")).thenReturn(Optional.of(ficha2));

        Map<String, Object> resultado = specService.compare(
                "Ford", "Ranger", "Raptor", "Toyota", "Hilux", "SRX", List.of("torque"));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> comparativo = (List<Map<String, Object>>) resultado.get("comparativo");
        assertEquals("Ranger", vencedorDoAtributo(comparativo, "torque"));
    }

    @Test
    @DisplayName("compare deve retornar N/A quando o atributo está ausente em um dos veículos")
    public void testCompareRetornaNaQuandoAtributoAusenteEmUmLado() throws Exception {
        FichaTecnica ficha1 = fichaComVeiculo(1L, "Ford", "Ranger", "Raptor", List.of(
                CampoSpec.encontrado("potencia", "397cv", "ALTA", "url", "2026-01-01")));
        FichaTecnica ficha2 = fichaComVeiculo(2L, "Toyota", "Hilux", "SRX", List.of(
                CampoSpec.naoEncontrado("potencia")));

        when(fichaTecnicaRepository.findFirstByMarcaIgnoreCaseAndModeloIgnoreCaseAndVersaoIgnoreCase(
                "Ford", "Ranger", "Raptor")).thenReturn(Optional.of(ficha1));
        when(fichaTecnicaRepository.findFirstByMarcaIgnoreCaseAndModeloIgnoreCaseAndVersaoIgnoreCase(
                "Toyota", "Hilux", "SRX")).thenReturn(Optional.of(ficha2));

        Map<String, Object> resultado = specService.compare(
                "Ford", "Ranger", "Raptor", "Toyota", "Hilux", "SRX", List.of("potencia"));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> comparativo = (List<Map<String, Object>>) resultado.get("comparativo");
        assertEquals("N/A", vencedorDoAtributo(comparativo, "potencia"));
    }

    @Test
    @DisplayName("compare deve retornar N/A quando o valor não bate com nenhuma unidade reconhecida")
    public void testCompareRetornaNaQuandoValorNaoReconhecido() throws Exception {
        FichaTecnica ficha1 = fichaComVeiculo(1L, "Ford", "Ranger", "Raptor", List.of(
                CampoSpec.encontrado("potencia", "muito potente", "MEDIA", "url", "2026-01-01")));
        FichaTecnica ficha2 = fichaComVeiculo(2L, "Toyota", "Hilux", "SRX", List.of(
                CampoSpec.encontrado("potencia", "204cv", "ALTA", "url", "2026-01-01")));

        when(fichaTecnicaRepository.findFirstByMarcaIgnoreCaseAndModeloIgnoreCaseAndVersaoIgnoreCase(
                "Ford", "Ranger", "Raptor")).thenReturn(Optional.of(ficha1));
        when(fichaTecnicaRepository.findFirstByMarcaIgnoreCaseAndModeloIgnoreCaseAndVersaoIgnoreCase(
                "Toyota", "Hilux", "SRX")).thenReturn(Optional.of(ficha2));

        Map<String, Object> resultado = specService.compare(
                "Ford", "Ranger", "Raptor", "Toyota", "Hilux", "SRX", List.of("potencia"));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> comparativo = (List<Map<String, Object>>) resultado.get("comparativo");
        assertEquals("N/A", vencedorDoAtributo(comparativo, "potencia"));
    }

    @Test
    @DisplayName("compare sem atributos explícitos deve usar os atributos da ficha do primeiro veículo")
    public void testCompareSemAtributosUsaOsDaFicha1() throws Exception {
        FichaTecnica ficha1 = fichaComVeiculo(1L, "Ford", "Ranger", "Raptor", List.of(
                CampoSpec.encontrado("potencia", "397cv", "ALTA", "url", "2026-01-01"),
                CampoSpec.encontrado("torque", "583Nm", "ALTA", "url", "2026-01-01")));
        FichaTecnica ficha2 = fichaComVeiculo(2L, "Toyota", "Hilux", "SRX", List.of(
                CampoSpec.encontrado("potencia", "204cv", "ALTA", "url", "2026-01-01")));

        when(fichaTecnicaRepository.findFirstByMarcaIgnoreCaseAndModeloIgnoreCaseAndVersaoIgnoreCase(
                "Ford", "Ranger", "Raptor")).thenReturn(Optional.of(ficha1));
        when(fichaTecnicaRepository.findFirstByMarcaIgnoreCaseAndModeloIgnoreCaseAndVersaoIgnoreCase(
                "Toyota", "Hilux", "SRX")).thenReturn(Optional.of(ficha2));

        Map<String, Object> resultado = specService.compare(
                "Ford", "Ranger", "Raptor", "Toyota", "Hilux", "SRX", List.of());

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> comparativo = (List<Map<String, Object>>) resultado.get("comparativo");
        assertEquals(2, comparativo.size());
    }

    @Test
    @DisplayName("compare deve propagar FichaNaoEncontradaException quando um dos veículos não existe")
    public void testComparePropagaExcecaoQuandoVeiculoNaoExiste() {
        when(fichaTecnicaRepository.findFirstByMarcaIgnoreCaseAndModeloIgnoreCaseAndVersaoIgnoreCase(
                "Ford", "Ranger", "Raptor")).thenReturn(Optional.empty());
        when(fichaTecnicaRepository.findByMarcaIgnoreCaseAndModeloIgnoreCase("Ford", "Ranger"))
                .thenReturn(List.of());

        assertThrows(FichaNaoEncontradaException.class, () -> specService.compare(
                "Ford", "Ranger", "Raptor", "Toyota", "Hilux", "SRX", List.of("potencia")));
    }

    // ================= LISTAR HISTÓRICO =================

    @Test
    @DisplayName("listarHistorico deve mapear as fichas retornadas pelo repository")
    public void testListarHistoricoMapeiaFichas() throws Exception {
        FichaTecnica ficha1 = fichaComVeiculo(1L, "Ford", "Ranger", "Raptor",
                List.of(CampoSpec.encontrado("motor", "V6", "ALTA", "url", "2026-01-01")));
        FichaTecnica ficha2 = fichaComVeiculo(2L, "Ford", "Bronco", "Wildtrak",
                List.of(CampoSpec.encontrado("motor", "V6", "ALTA", "url", "2026-01-01")));

        when(fichaTecnicaRepository.findWithFilters("Ford", null)).thenReturn(List.of(ficha1, ficha2));

        List<SpecResponse> resultado = specService.listarHistorico("Ford", null);

        assertEquals(2, resultado.size());
        assertEquals("Ranger", resultado.get(0).modelo());
        assertEquals("Bronco", resultado.get(1).modelo());
    }

    @Test
    @DisplayName("listarHistorico deve retornar lista vazia quando o repository não encontra nada")
    public void testListarHistoricoVazio() {
        when(fichaTecnicaRepository.findWithFilters(any(), any())).thenReturn(List.of());

        assertTrue(specService.listarHistorico("Marca Inexistente", null).isEmpty());
    }

    // ================= DELETAR FICHA =================

    @Test
    @DisplayName("deletarFicha deve deletar quando o id existe")
    public void testDeletarFichaExistente() {
        when(fichaTecnicaRepository.existsById(10L)).thenReturn(true);

        specService.deletarFicha(10L);

        verify(fichaTecnicaRepository, times(1)).deleteById(10L);
    }

    @Test
    @DisplayName("deletarFicha deve lançar FichaNaoEncontradaException e não deletar quando o id não existe")
    public void testDeletarFichaInexistente() {
        when(fichaTecnicaRepository.existsById(99L)).thenReturn(false);

        assertThrows(FichaNaoEncontradaException.class, () -> specService.deletarFicha(99L));

        verify(fichaTecnicaRepository, never()).deleteById(any());
    }

    // ================= SANITIZAÇÃO DE ATRIBUTOS =================

    @Test
    @DisplayName("sanitizarAtributos deve remover espaços, entradas em branco e caracteres não permitidos")
    public void testSanitizarAtributosLimpaEntradaDoUsuario() throws Exception {
        List<CampoSpec> camposSalvos = List.of(
                CampoSpec.encontrado("motor", "V6", "ALTA", "url", "2026-01-01"),
                CampoSpec.encontrado("potenciascript", "397cv", "ALTA", "url", "2026-01-01")
        );
        FichaTecnica ficha = fichaValida(camposSalvos, 15, LocalDateTime.now());

        when(fichaTecnicaRepository.findFirstByMarcaIgnoreCaseAndModeloIgnoreCaseAndVersaoIgnoreCase(
                MARCA, MODELO, VERSAO)).thenReturn(Optional.of(ficha));

        // " motor " (espaços), "   " (só espaço, deve sumir), "potencia<script>!!" (símbolos removidos)
        SpecResponse resposta = specService.query(
                requestPadrao(List.of(" motor ", "   ", "potencia<script>!!")), usuario, IP);

        assertEquals(2, resposta.campos().size());
        assertEquals("motor", resposta.campos().get(0).campo());
        assertEquals("potenciascript", resposta.campos().get(1).campo());
    }
}
