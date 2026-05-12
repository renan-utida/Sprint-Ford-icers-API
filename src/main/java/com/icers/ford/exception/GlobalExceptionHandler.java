package com.icers.ford.exception;

import com.icers.ford.dto.response.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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

    // 400 — Validação de campos (Bean Validation)

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
                .status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponse.ofValidation(
                        request.getRequestURI(),
                        camposInvalidos
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
                        ex.getMessage() + ". Use POST /api/v1/specs/query " +
                                "para consultar e armazenar a ficha deste veículo.",
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
}