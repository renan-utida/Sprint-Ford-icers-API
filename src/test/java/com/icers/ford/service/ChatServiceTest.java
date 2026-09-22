package com.icers.ford.service;

import com.icers.ford.dto.request.SpecQueryRequest;
import com.icers.ford.dto.response.CampoSpec;
import com.icers.ford.dto.response.SpecResponse;
import com.icers.ford.model.Usuario;
import com.icers.ford.model.enums.Role;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

// A extração de intenção (extrairMarca/extrairModelo/extrairVersao/
// extrairAtributos) é toda privada — só testável indiretamente através de
// processar(), verificando o SpecQueryRequest efetivamente montado e
// passado pro SpecService mockado. Não cobre as 44 palavras-chave do mapa
// de atributos nem todas as marcas/modelos — só uma amostra representativa
// de cada categoria, o que já é suficiente pra travar o contrato de
// comportamento observável (é puro reconhecimento por keyword, gap já
// documentado no projeto).
@ExtendWith(MockitoExtension.class)
@DisplayName("Testes - ChatService")
public class ChatServiceTest {

    @Mock
    private SpecService specService;

    @Mock
    private ConfigService configService;

    @InjectMocks
    private ChatService chatService;

    private Usuario usuario;
    private static final String IP = "127.0.0.1";

    @BeforeEach
    public void setUp() {
        usuario = Usuario.builder()
                .id(1L)
                .email("analyst@specradar.com")
                .role(Role.ANALYST)
                .build();
    }

    private SpecResponse fichaPadrao(boolean cacheHit, List<CampoSpec> campos) {
        return new SpecResponse(
                1L, "Ford", "Ranger", "Raptor",
                campos, "ALTA", LocalDateTime.now(), cacheHit
        );
    }

    // Veículo não identificado

    @Test
    @DisplayName("Deve retornar semVeiculo quando não reconhece nenhuma marca na mensagem")
    public void testProcessarSemMarcaReconhecida() {
        ChatService.ChatResponse resposta =
                chatService.processar("Quero saber sobre motores", usuario, IP);

        assertFalse(resposta.sucesso());
        assertNull(resposta.ficha());
        assertTrue(resposta.mensagem().contains("Não consegui identificar"));
        verify(specService, never()).query(any(), any(), anyString());
    }

    @Test
    @DisplayName("Deve retornar semVeiculo quando reconhece a marca mas não o modelo")
    public void testProcessarComMarcaSemModeloReconhecido() {
        ChatService.ChatResponse resposta =
                chatService.processar("Especificações da Ford", usuario, IP);

        assertFalse(resposta.sucesso());
        verify(specService, never()).query(any(), any(), anyString());
    }

    // Fluxo completo — extração + montagem da request

    @Test
    @DisplayName("Deve extrair marca/modelo/versão e delegar pro SpecService corretamente")
    public void testProcessarFluxoCompleto() {
        when(configService.getAtributosPadrao()).thenReturn(List.of("motor", "potencia"));
        when(specService.query(any(SpecQueryRequest.class), eq(usuario), eq(IP)))
                .thenReturn(fichaPadrao(false, List.of(
                        CampoSpec.encontrado("motor", "V6 3.0L", "ALTA", "url", "2026-01-01"))));

        ChatService.ChatResponse resposta = chatService.processar(
                "Especificações da Ford Ranger Raptor", usuario, IP);

        assertTrue(resposta.sucesso());
        assertNotNull(resposta.ficha());
        assertTrue(resposta.mensagem().contains("Ford Ranger Raptor"));

        ArgumentCaptor<SpecQueryRequest> captor = ArgumentCaptor.forClass(SpecQueryRequest.class);
        verify(specService, times(1)).query(captor.capture(), eq(usuario), eq(IP));

        SpecQueryRequest request = captor.getValue();
        assertEquals("Ford", request.marca());
        assertEquals("Ranger", request.modelo());
        assertEquals("Raptor", request.versao());
    }

    @Test
    @DisplayName("Deve reconhecer marca/modelo mesmo com a mensagem em maiúsculas")
    public void testProcessarCaseInsensitive() {
        when(configService.getAtributosPadrao()).thenReturn(List.of("motor"));
        when(specService.query(any(), any(), anyString())).thenReturn(fichaPadrao(false, List.of()));

        chatService.processar("FORD RANGER RAPTOR", usuario, IP);

        ArgumentCaptor<SpecQueryRequest> captor = ArgumentCaptor.forClass(SpecQueryRequest.class);
        verify(specService).query(captor.capture(), any(), anyString());
        assertEquals("Ford", captor.getValue().marca());
        assertEquals("Ranger", captor.getValue().modelo());
    }

    @Test
    @DisplayName("Deve usar 'base' como versão quando não reconhece versão nem ano")
    public void testProcessarSemVersaoUsaBaseComoFallback() {
        when(configService.getAtributosPadrao()).thenReturn(List.of("motor"));
        when(specService.query(any(), any(), anyString())).thenReturn(fichaPadrao(false, List.of()));

        chatService.processar("Especificações da Ford Ranger", usuario, IP);

        ArgumentCaptor<SpecQueryRequest> captor = ArgumentCaptor.forClass(SpecQueryRequest.class);
        verify(specService).query(captor.capture(), any(), anyString());
        assertEquals("base", captor.getValue().versao());
    }

    @Test
    @DisplayName("Deve extrair ano como versão quando não há versão nomeada")
    public void testProcessarExtraiAnoComoVersao() {
        when(configService.getAtributosPadrao()).thenReturn(List.of("motor"));
        when(specService.query(any(), any(), anyString())).thenReturn(fichaPadrao(false, List.of()));

        chatService.processar("Toyota Hilux 2025", usuario, IP);

        ArgumentCaptor<SpecQueryRequest> captor = ArgumentCaptor.forClass(SpecQueryRequest.class);
        verify(specService).query(captor.capture(), any(), anyString());
        assertEquals("2025", captor.getValue().versao());
    }

    // Extração de atributos

    @Test
    @DisplayName("Deve extrair atributos mencionados na mensagem em vez de usar os padrão")
    public void testProcessarExtraiAtributosMencionados() {
        when(specService.query(any(), any(), anyString())).thenReturn(fichaPadrao(false, List.of()));

        chatService.processar(
                "Motor e potência (cv) da Ford Ranger Raptor", usuario, IP);

        ArgumentCaptor<SpecQueryRequest> captor = ArgumentCaptor.forClass(SpecQueryRequest.class);
        verify(specService).query(captor.capture(), any(), anyString());
        assertEquals(List.of("motor", "potencia"), captor.getValue().atributos());
        verify(configService, never()).getAtributosPadrao();
    }

    @Test
    @DisplayName("Deve usar os atributos padrão do ConfigService quando nenhum é mencionado na mensagem")
    public void testProcessarUsaAtributosPadraoQuandoNenhumMencionado() {
        when(configService.getAtributosPadrao())
                .thenReturn(List.of("motor", "potencia", "torque"));
        when(specService.query(any(), any(), anyString())).thenReturn(fichaPadrao(false, List.of()));

        chatService.processar("Especificações da Ford Ranger Raptor", usuario, IP);

        ArgumentCaptor<SpecQueryRequest> captor = ArgumentCaptor.forClass(SpecQueryRequest.class);
        verify(specService).query(captor.capture(), any(), anyString());
        assertEquals(List.of("motor", "potencia", "torque"), captor.getValue().atributos());
        verify(configService, times(1)).getAtributosPadrao();
    }

    @Test
    @DisplayName("Deve reconhecer múltiplos sinônimos mapeando pro mesmo atributo, sem duplicar")
    public void testProcessarSinonimosNaoDuplicamAtributo() {
        when(specService.query(any(), any(), anyString())).thenReturn(fichaPadrao(false, List.of()));

        // "câmbio" e "transmissão" mapeiam os dois pra "transmissao"
        chatService.processar(
                "Câmbio e transmissão da Ford Ranger Raptor", usuario, IP);

        ArgumentCaptor<SpecQueryRequest> captor = ArgumentCaptor.forClass(SpecQueryRequest.class);
        verify(specService).query(captor.capture(), any(), anyString());
        assertEquals(List.of("transmissao"), captor.getValue().atributos());
    }

    // Resposta conversacional

    @Test
    @DisplayName("Deve indicar no texto quando a resposta veio do cache")
    public void testProcessarIndicaCacheHitNoTexto() {
        when(specService.query(any(), any(), anyString()))
                .thenReturn(fichaPadrao(true, List.of()));

        ChatService.ChatResponse resposta = chatService.processar(
                "Ford Ranger Raptor", usuario, IP);

        assertTrue(resposta.mensagem().contains("repositório histórico"));
    }

    @Test
    @DisplayName("Não deve mencionar cache quando a resposta não veio do cache")
    public void testProcessarNaoIndicaCacheQuandoNaoHouver() {
        when(specService.query(any(), any(), anyString()))
                .thenReturn(fichaPadrao(false, List.of()));

        ChatService.ChatResponse resposta = chatService.processar(
                "Ford Ranger Raptor", usuario, IP);

        assertFalse(resposta.mensagem().contains("repositório histórico"));
    }

    @Test
    @DisplayName("Deve mencionar quantos campos não foram encontrados")
    public void testProcessarMencionaCamposNaoEncontrados() {
        List<CampoSpec> campos = List.of(
                CampoSpec.encontrado("motor", "V6", "ALTA", "url", "2026-01-01"),
                CampoSpec.naoEncontrado("garantia"),
                CampoSpec.naoEncontrado("capacidade_carga")
        );
        when(specService.query(any(), any(), anyString())).thenReturn(fichaPadrao(false, campos));

        ChatService.ChatResponse resposta = chatService.processar(
                "Ford Ranger Raptor", usuario, IP);

        assertTrue(resposta.mensagem().contains("2 campo(s) sem dados"));
    }

    @Test
    @DisplayName("Não deve mencionar campos sem dados quando todos foram encontrados")
    public void testProcessarNaoMencionaCamposSemDadosQuandoTodosEncontrados() {
        List<CampoSpec> campos = List.of(
                CampoSpec.encontrado("motor", "V6", "ALTA", "url", "2026-01-01"));
        when(specService.query(any(), any(), anyString())).thenReturn(fichaPadrao(false, campos));

        ChatService.ChatResponse resposta = chatService.processar(
                "Ford Ranger Raptor", usuario, IP);

        assertFalse(resposta.mensagem().contains("sem dados"));
    }

    // Tratamento de erro

    @Test
    @DisplayName("Deve retornar ChatResponse.erro quando o SpecService lançar exceção")
    public void testProcessarErroNoSpecServiceRetornaChatResponseDeErro() {
        when(specService.query(any(), any(), anyString()))
                .thenThrow(new RuntimeException("Gemini indisponível"));

        ChatService.ChatResponse resposta = chatService.processar(
                "Ford Ranger Raptor", usuario, IP);

        assertFalse(resposta.sucesso());
        assertNull(resposta.ficha());
        assertTrue(resposta.mensagem().contains("Não consegui buscar"));
    }
}
