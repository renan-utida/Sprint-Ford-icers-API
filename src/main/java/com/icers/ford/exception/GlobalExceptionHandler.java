package com.icers.ford.exception;

import com.icers.ford.dto.response.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    // 422 — Validação de campos (Bean Validation)
    // Status 422 (não 400) porque o corpo da requisição é sintaticamente
    // válido — só falha em regras semânticas (regex, tamanho, etc.).
    // Alinhado com o que a proposta do grupo especifica para este tipo
    // de erro (401/404/422/500).

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(
            MethodArgumentNotValidException ex,
            HttpServletRequest request
    ) {
        Map<String, String> camposInvalidos = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .collect(Collectors.toMap(
                        FieldError::getField,
                        fieldError -> fieldError.getDefaultMessage() != null
                                ? fieldError.getDefaultMessage()
                                : "Campo inválido",
                        // Se o mesmo campo tiver mais de um erro, mantém o primeiro
                        (primeiro, segundo) -> primeiro
                ));

        log.warn("Erro de validação — endpoint: {} | campos: {}",
                request.getRequestURI(), camposInvalidos.keySet());

        return ResponseEntity
                .status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(ErrorResponse.ofValidation(
                        request.getRequestURI(),
                        camposInvalidos
                ));
    }

    // 400 — Corpo da requisição malformado (JSON inválido)
    // Diferente da validação acima: aqui o corpo nem chega a ser
    // parseado com sucesso, então o erro é sintático, não semântico —
    // por isso 400, não 422. Sem este handler, isso caía no handler
    // genérico (500), o que é incorreto: a culpa é do cliente, não do
    // servidor.

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleJsonInvalido(
            HttpMessageNotReadableException ex,
            HttpServletRequest request
    ) {
        log.warn("Corpo da requisição malformado — endpoint: {} | causa: {}",
                request.getRequestURI(), ex.getMostSpecificCause().getMessage());

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponse.of(
                        "VALIDATION_ERROR",
                        "Corpo da requisição inválido ou malformado.",
                        request.getRequestURI()
                ));
    }

    // 404 — Ficha técnica não encontrada

    @ExceptionHandler(FichaNaoEncontradaException.class)
    public ResponseEntity<ErrorResponse> handleFichaNaoEncontrada(
            FichaNaoEncontradaException ex,
            HttpServletRequest request
    ) {
        log.info("Ficha não encontrada — endpoint: {} | mensagem: {}",
                request.getRequestURI(), ex.getMessage());

        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(ErrorResponse.notFound(
                        ex.getMessage(),
                        request.getRequestURI()
                ));
    }

    @ExceptionHandler(UsuarioNaoEncontradoException.class)
    public ResponseEntity<ErrorResponse> handleUsuarioNaoEncontrado(
            UsuarioNaoEncontradoException ex,
            HttpServletRequest request
    ) {
        log.info("Usuário não encontrado — endpoint: {} | mensagem: {}",
                request.getRequestURI(), ex.getMessage());

        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(ErrorResponse.notFound(
                        ex.getMessage(),
                        request.getRequestURI()
                ));
    }

    // 409 — Auto-anonimização bloqueada

    @ExceptionHandler(AutoAnonimizacaoException.class)
    public ResponseEntity<ErrorResponse> handleAutoAnonimizacao(
            AutoAnonimizacaoException ex,
            HttpServletRequest request
    ) {
        log.warn("Tentativa de auto-anonimização bloqueada — endpoint: {}",
                request.getRequestURI());

        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(ErrorResponse.of(
                        "SELF_ANONYMIZATION_BLOCKED",
                        ex.getMessage(),
                        request.getRequestURI()
                ));
    }

    @ExceptionHandler(AutoDesativacaoException.class)
    public ResponseEntity<ErrorResponse> handleAutoDesativacao(
            AutoDesativacaoException ex,
            HttpServletRequest request
    ) {
        log.warn("Tentativa de auto-desativação bloqueada — endpoint: {}",
                request.getRequestURI());

        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(ErrorResponse.of(
                        "SELF_DEACTIVATION_BLOCKED",
                        ex.getMessage(),
                        request.getRequestURI()
                ));
    }

    @ExceptionHandler(EmailJaCadastradoException.class)
    public ResponseEntity<ErrorResponse> handleEmailJaCadastrado(
            EmailJaCadastradoException ex,
            HttpServletRequest request
    ) {
        log.warn("Tentativa de cadastro com email duplicado — endpoint: {}",
                request.getRequestURI());

        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(ErrorResponse.of(
                        "EMAIL_ALREADY_EXISTS",
                        ex.getMessage(),
                        request.getRequestURI()
                ));
    }

    // 401 — Não autenticado

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ErrorResponse> handleAuthentication(
            AuthenticationException ex,
            HttpServletRequest request
    ) {
        // Mensagem genérica — não revela detalhes internos
        log.warn("Falha de autenticação — endpoint: {}",
                request.getRequestURI());

        return ResponseEntity
                .status(HttpStatus.UNAUTHORIZED)
                .body(ErrorResponse.of(
                        "UNAUTHORIZED",
                        "Autenticação necessária. " +
                                "Faça login em POST /api/v1/auth/login.",
                        request.getRequestURI()
                ));
    }

    // 403 — Sem permissão

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDenied(
            AccessDeniedException ex,
            HttpServletRequest request
    ) {
        log.warn("Acesso negado — endpoint: {} | motivo: {}",
                request.getRequestURI(), ex.getMessage());

        return ResponseEntity
                .status(HttpStatus.FORBIDDEN)
                .body(ErrorResponse.of(
                        "FORBIDDEN",
                        "Você não tem permissão para acessar este recurso.",
                        request.getRequestURI()
                ));
    }

    // 503 — LLM indisponível

    @ExceptionHandler(LlmUnavailableException.class)
    public ResponseEntity<ErrorResponse> handleLlmUnavailable(
            LlmUnavailableException ex,
            HttpServletRequest request
    ) {
        // Log interno com detalhes — nunca exposto ao cliente
        log.error("Serviço de extração indisponível — endpoint: {} | detalhe: {}",
                request.getRequestURI(), ex.getMessage());

        return ResponseEntity
                .status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(ErrorResponse.serviceUnavailable(
                        request.getRequestURI()
                ));
    }

    // 500 — Catch-all para exceções não previstas

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneric(
            Exception ex,
            HttpServletRequest request
    ) {
        // Stack trace completo no log interno para debug
        // NUNCA vai para o response
        log.error("Erro não tratado — endpoint: {} | tipo: {} | mensagem: {}",
                request.getRequestURI(),
                ex.getClass().getSimpleName(),
                ex.getMessage(),
                ex
        );

        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ErrorResponse.of(
                        "INTERNAL_ERROR",
                        "Ocorreu um erro inesperado. " +
                                "Tente novamente ou entre em contato com o suporte.",
                        request.getRequestURI()
                ));
    }

    // 429 — Rate limit excedido

    @ExceptionHandler(RateLimitExceededException.class)
    public ResponseEntity<ErrorResponse> handleRateLimit(
            RateLimitExceededException ex,
            HttpServletRequest request
    ) {
        log.warn("Rate limit excedido — endpoint: {}",
                request.getRequestURI());

        return ResponseEntity
                .status(HttpStatus.TOO_MANY_REQUESTS)
                .header("Retry-After", String.valueOf(ex.getRetryAfterSeconds()))
                .body(ErrorResponse.rateLimitExceeded(
                        request.getRequestURI(),
                        ex.getRetryAfterSeconds()
                ));
    }
}