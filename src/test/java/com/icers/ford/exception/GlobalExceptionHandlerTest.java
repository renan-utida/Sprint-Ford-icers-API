package com.icers.ford.exception;

import com.icers.ford.dto.response.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.util.unit.DataSize;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("Testes - GlobalExceptionHandler")
public class GlobalExceptionHandlerTest {

    @Mock
    private HttpServletRequest request;

    private GlobalExceptionHandler handler;

    private static final String ENDPOINT = "/api/v1/specs/query";

    @BeforeEach
    public void setUp() {
        handler = new GlobalExceptionHandler();
        when(request.getRequestURI()).thenReturn(ENDPOINT);
    }

    // 422 — MethodArgumentNotValidException

    @Test
    @DisplayName("handleValidation deve retornar 422 com os campos inválidos mapeados")
    public void testHandleValidation() {
        FieldError erroMarca = new FieldError("obj", "marca", "Marca inválida");
        FieldError erroModelo = new FieldError("obj", "modelo", "Modelo inválido");

        BindingResult bindingResult = mock(BindingResult.class);
        when(bindingResult.getFieldErrors()).thenReturn(List.of(erroMarca, erroModelo));

        MethodArgumentNotValidException ex = mock(MethodArgumentNotValidException.class);
        when(ex.getBindingResult()).thenReturn(bindingResult);

        ResponseEntity<ErrorResponse> resposta = handler.handleValidation(ex, request);

        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, resposta.getStatusCode());
        assertEquals("VALIDATION_ERROR", resposta.getBody().codigoErro());
        assertEquals(ENDPOINT, resposta.getBody().endpoint());
        assertEquals("Marca inválida", resposta.getBody().camposInvalidos().get("marca"));
        assertEquals("Modelo inválido", resposta.getBody().camposInvalidos().get("modelo"));
    }

    @Test
    @DisplayName("handleValidation deve usar 'Campo inválido' quando o FieldError não tem mensagem padrão")
    public void testHandleValidationSemMensagemPadrao() {
        FieldError erroSemMensagem = new FieldError("obj", "versao", null);

        BindingResult bindingResult = mock(BindingResult.class);
        when(bindingResult.getFieldErrors()).thenReturn(List.of(erroSemMensagem));

        MethodArgumentNotValidException ex = mock(MethodArgumentNotValidException.class);
        when(ex.getBindingResult()).thenReturn(bindingResult);

        ResponseEntity<ErrorResponse> resposta = handler.handleValidation(ex, request);

        assertEquals("Campo inválido", resposta.getBody().camposInvalidos().get("versao"));
    }

    // 400 — HttpMessageNotReadableException

    @Test
    @DisplayName("handleJsonInvalido deve retornar 400 com mensagem genérica fixa")
    public void testHandleJsonInvalido() {
        HttpMessageNotReadableException ex = new HttpMessageNotReadableException("json malformado");

        ResponseEntity<ErrorResponse> resposta = handler.handleJsonInvalido(ex, request);

        assertEquals(HttpStatus.BAD_REQUEST, resposta.getStatusCode());
        assertEquals("VALIDATION_ERROR", resposta.getBody().codigoErro());
        assertEquals("Corpo da requisição inválido ou malformado.", resposta.getBody().mensagem());
        assertEquals(ENDPOINT, resposta.getBody().endpoint());
    }

    // 404 — FichaNaoEncontradaException

    @Test
    @DisplayName("handleFichaNaoEncontrada deve retornar 404 com sugestões similares")
    public void testHandleFichaNaoEncontradaComSugestoes() {
        FichaNaoEncontradaException ex = new FichaNaoEncontradaException(
                "Ford", "Ranger", "XLT", List.of("Ford Ranger Raptor", "Ford Ranger Limited"));

        ResponseEntity<ErrorResponse> resposta = handler.handleFichaNaoEncontrada(ex, request);

        assertEquals(HttpStatus.NOT_FOUND, resposta.getStatusCode());
        assertEquals("NOT_FOUND", resposta.getBody().codigoErro());
        assertEquals(2, resposta.getBody().sugestoesSimilares().size());
        assertTrue(resposta.getBody().mensagem().contains("Ford Ranger XLT"));
    }

    @Test
    @DisplayName("handleFichaNaoEncontrada deve retornar lista vazia de sugestões quando não há nenhuma")
    public void testHandleFichaNaoEncontradaSemSugestoes() {
        FichaNaoEncontradaException ex = new FichaNaoEncontradaException("Ford", "Bronco", "Wildtrak");

        ResponseEntity<ErrorResponse> resposta = handler.handleFichaNaoEncontrada(ex, request);

        assertTrue(resposta.getBody().sugestoesSimilares().isEmpty());
    }

    // 404 — UsuarioNaoEncontradoException

    @Test
    @DisplayName("handleUsuarioNaoEncontrado deve retornar 404 sem campo de sugestões")
    public void testHandleUsuarioNaoEncontrado() {
        UsuarioNaoEncontradoException ex = new UsuarioNaoEncontradoException(99L);

        ResponseEntity<ErrorResponse> resposta = handler.handleUsuarioNaoEncontrado(ex, request);

        assertEquals(HttpStatus.NOT_FOUND, resposta.getStatusCode());
        assertEquals("NOT_FOUND", resposta.getBody().codigoErro());
        assertTrue(resposta.getBody().mensagem().contains("99"));
        assertNull(resposta.getBody().sugestoesSimilares());
    }

    // 409 — AutoAnonimizacaoException / AutoDesativacaoException / EmailJaCadastradoException

    @Test
    @DisplayName("handleAutoAnonimizacao deve retornar 409 com código SELF_ANONYMIZATION_BLOCKED")
    public void testHandleAutoAnonimizacao() {
        ResponseEntity<ErrorResponse> resposta =
                handler.handleAutoAnonimizacao(new AutoAnonimizacaoException(), request);

        assertEquals(HttpStatus.CONFLICT, resposta.getStatusCode());
        assertEquals("SELF_ANONYMIZATION_BLOCKED", resposta.getBody().codigoErro());
    }

    @Test
    @DisplayName("handleAutoDesativacao deve retornar 409 com código SELF_DEACTIVATION_BLOCKED")
    public void testHandleAutoDesativacao() {
        ResponseEntity<ErrorResponse> resposta =
                handler.handleAutoDesativacao(new AutoDesativacaoException(), request);

        assertEquals(HttpStatus.CONFLICT, resposta.getStatusCode());
        assertEquals("SELF_DEACTIVATION_BLOCKED", resposta.getBody().codigoErro());
    }

    @Test
    @DisplayName("handleEmailJaCadastrado deve retornar 409 com código EMAIL_ALREADY_EXISTS")
    public void testHandleEmailJaCadastrado() {
        ResponseEntity<ErrorResponse> resposta =
                handler.handleEmailJaCadastrado(new EmailJaCadastradoException(), request);

        assertEquals(HttpStatus.CONFLICT, resposta.getStatusCode());
        assertEquals("EMAIL_ALREADY_EXISTS", resposta.getBody().codigoErro());
    }

    // 401 — AuthenticationException

    @Test
    @DisplayName("handleAuthentication deve retornar 401 com mensagem genérica (não revela detalhe da exceção)")
    public void testHandleAuthentication() {
        AuthenticationException ex = new BadCredentialsException("detalhe interno sensível");

        ResponseEntity<ErrorResponse> resposta = handler.handleAuthentication(ex, request);

        assertEquals(HttpStatus.UNAUTHORIZED, resposta.getStatusCode());
        assertEquals("UNAUTHORIZED", resposta.getBody().codigoErro());
        assertFalse(resposta.getBody().mensagem().contains("detalhe interno sensível"));
    }

    // 403 — AccessDeniedException

    @Test
    @DisplayName("handleAccessDenied deve retornar 403 com mensagem genérica")
    public void testHandleAccessDenied() {
        AccessDeniedException ex = new AccessDeniedException("motivo interno");

        ResponseEntity<ErrorResponse> resposta = handler.handleAccessDenied(ex, request);

        assertEquals(HttpStatus.FORBIDDEN, resposta.getStatusCode());
        assertEquals("FORBIDDEN", resposta.getBody().codigoErro());
    }

    // 422 — ArquivoInvalidoException

    @Test
    @DisplayName("handleArquivoInvalido deve retornar 422 com a mensagem real da exceção")
    public void testHandleArquivoInvalido() {
        ArquivoInvalidoException ex = new ArquivoInvalidoException("Arquivo não é um PDF válido");

        ResponseEntity<ErrorResponse> resposta = handler.handleArquivoInvalido(ex, request);

        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, resposta.getStatusCode());
        assertEquals("VALIDATION_ERROR", resposta.getBody().codigoErro());
        assertEquals("Arquivo não é um PDF válido", resposta.getBody().mensagem());
    }

    // 413 — MaxUploadSizeExceededException

    @Test
    @DisplayName("handleArquivoMuitoGrande deve retornar 413 com o limite configurado em MB na mensagem")
    public void testHandleArquivoMuitoGrande() {
        ReflectionTestUtils.setField(handler, "tamanhoMaximoArquivo", DataSize.ofMegabytes(5));
        MaxUploadSizeExceededException ex = new MaxUploadSizeExceededException(6_000_000L);

        ResponseEntity<ErrorResponse> resposta = handler.handleArquivoMuitoGrande(ex, request);

        assertEquals(HttpStatus.PAYLOAD_TOO_LARGE, resposta.getStatusCode());
        assertEquals("PAYLOAD_TOO_LARGE", resposta.getBody().codigoErro());
        assertTrue(resposta.getBody().mensagem().contains("5MB"));
    }

    // 503 — LlmUnavailableException

    @Test
    @DisplayName("handleLlmUnavailable deve retornar mensagem genérica para endpoints comuns")
    public void testHandleLlmUnavailableEndpointGenerico() {
        when(request.getRequestURI()).thenReturn("/api/v1/specs/query");
        LlmUnavailableException ex = new LlmUnavailableException("Gemini 503");

        ResponseEntity<ErrorResponse> resposta = handler.handleLlmUnavailable(ex, request);

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, resposta.getStatusCode());
        assertFalse(resposta.getBody().mensagem().contains("PDF"));
    }

    @Test
    @DisplayName("handleLlmUnavailable deve retornar mensagem específica para /specs/from-pdf")
    public void testHandleLlmUnavailableFromPdf() {
        when(request.getRequestURI()).thenReturn("/api/v1/specs/from-pdf");
        LlmUnavailableException ex = new LlmUnavailableException("Gemini 503");

        ResponseEntity<ErrorResponse> resposta = handler.handleLlmUnavailable(ex, request);

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, resposta.getStatusCode());
        assertTrue(resposta.getBody().mensagem().contains("PDF"));
    }

    @Test
    @DisplayName("handleLlmUnavailable nunca deve expor a mensagem interna da exceção (não revela a tecnologia usada)")
    public void testHandleLlmUnavailableNaoExpoeDetalheInterno() {
        LlmUnavailableException ex = new LlmUnavailableException("Gemini API key inválida — detalhe sensível");

        ResponseEntity<ErrorResponse> resposta = handler.handleLlmUnavailable(ex, request);

        assertFalse(resposta.getBody().mensagem().contains("Gemini"));
        assertFalse(resposta.getBody().mensagem().contains("API key"));
    }

    // Cliente desconectado — sem corpo de resposta

    @Test
    @DisplayName("handleClienteDesconectado não deve lançar (conexão já não existe do outro lado)")
    public void testHandleClienteDesconectadoNaoLanca() {
        AsyncRequestNotUsableException ex =
                new AsyncRequestNotUsableException("cliente desconectou");

        assertDoesNotThrow(() -> handler.handleClienteDesconectado(ex, request));
    }

    // 500 — catch-all

    @Test
    @DisplayName("handleGeneric deve retornar 500 com mensagem genérica, nunca a mensagem real da exceção")
    public void testHandleGenericNaoExpoeDetalheInterno() {
        RuntimeException ex = new RuntimeException("NullPointerException em SpecService linha 42");

        ResponseEntity<ErrorResponse> resposta = handler.handleGeneric(ex, request);

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, resposta.getStatusCode());
        assertEquals("INTERNAL_ERROR", resposta.getBody().codigoErro());
        assertFalse(resposta.getBody().mensagem().contains("NullPointerException"));
        assertFalse(resposta.getBody().mensagem().contains("SpecService"));
    }

    // 429 — RateLimitExceededException

    @Test
    @DisplayName("handleRateLimit deve retornar 429 com header Retry-After correto")
    public void testHandleRateLimit() {
        RateLimitExceededException ex = new RateLimitExceededException(42L);

        ResponseEntity<ErrorResponse> resposta = handler.handleRateLimit(ex, request);

        assertEquals(HttpStatus.TOO_MANY_REQUESTS, resposta.getStatusCode());
        assertEquals("RATE_LIMIT_EXCEEDED", resposta.getBody().codigoErro());
        assertEquals("42", resposta.getHeaders().getFirst("Retry-After"));
    }
}
