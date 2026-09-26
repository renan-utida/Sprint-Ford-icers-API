package com.icers.ford.exception;

import com.icers.ford.dto.response.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.util.unit.DataSize;

import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @Value("${spring.servlet.multipart.max-file-size}")
    private DataSize tamanhoMaximoArquivo;

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
                .body(ErrorResponse.notFoundComSugestoes(
                        ex.getMessage(),
                        request.getRequestURI(),
                        ex.getSugestoesSimilares()
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

    // 409 — Anonimização bloqueada: usuário ainda ativo

    @ExceptionHandler(UsuarioAindaAtivoException.class)
    public ResponseEntity<ErrorResponse> handleUsuarioAindaAtivo(
            UsuarioAindaAtivoException ex,
            HttpServletRequest request
    ) {
        log.warn("Tentativa de anonimizar usuário ainda ativo — id: {} | endpoint: {}",
                ex.getId(), request.getRequestURI());

        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(ErrorResponse.of(
                        "USER_STILL_ACTIVE",
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

    // 422 — Arquivo de POST /specs/from-pdf inválido (content-type,
    // vazio, ou sem assinatura %PDF-) — validado antes de gastar uma
    // chamada multimodal que já sabemos que falharia.

    @ExceptionHandler(ArquivoInvalidoException.class)
    public ResponseEntity<ErrorResponse> handleArquivoInvalido(
            ArquivoInvalidoException ex,
            HttpServletRequest request
    ) {
        log.warn("Arquivo inválido em from-pdf — endpoint: {} | motivo: {}",
                request.getRequestURI(), ex.getMessage());

        return ResponseEntity
                .status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(ErrorResponse.of(
                        "VALIDATION_ERROR",
                        ex.getMessage(),
                        request.getRequestURI()
                ));
    }

    // 413 — Arquivo maior que spring.servlet.multipart.max-file-size.
    // Diferente do ArquivoInvalidoException (422) acima: aqui o problema
    // é só o tamanho, não o conteúdo — Payload Too Large é a semântica
    // HTTP correta, mesma precisão de status que o resto da API já usa
    // (422 vs 400, por exemplo).

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ErrorResponse> handleArquivoMuitoGrande(
            MaxUploadSizeExceededException ex,
            HttpServletRequest request
    ) {
        log.warn("Arquivo maior que o limite permitido — endpoint: {}",
                request.getRequestURI());

        return ResponseEntity
                .status(HttpStatus.PAYLOAD_TOO_LARGE)
                .body(ErrorResponse.of(
                        "PAYLOAD_TOO_LARGE",
                        "Arquivo excede o tamanho máximo permitido ("
                                + tamanhoMaximoArquivo.toMegabytes() + "MB).",
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

        // /specs/from-pdf é intencionalmente diferenciado aqui: a
        // investigação do Grupo 9 confirmou que PDF com imagem embutida
        // tem chance bem maior de falhar nesse serviço do que PDF
        // tabular/texto — vale avisar o analista nessa mensagem
        // específica. Nenhuma outra rota (/query, /chat/message) bate
        // nesse endsWith, então a mensagem genérica de sempre continua
        // valendo pra elas, sem mudança de comportamento.
        boolean isFromPdf = request.getRequestURI().endsWith("/from-pdf");

        ErrorResponse body = isFromPdf
                ? ErrorResponse.serviceUnavailableFromPdf(request.getRequestURI())
                : ErrorResponse.serviceUnavailable(request.getRequestURI());

        return ResponseEntity
                .status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(body);
    }

    // 499-ish — Cliente (navegador/app) fechou a conexão antes da
    // resposta terminar de ser escrita (ex: recarregou a aba do
    // Swagger no meio do carregamento). Não é uma falha nossa — não
    // existe mais ninguém do outro lado pra receber resposta nenhuma,
    // então não faz sentido logar como ERROR nem tentar analisar isso
    // como um bug. DEBUG é suficiente pra rastrear se precisar.

    @ExceptionHandler(AsyncRequestNotUsableException.class)
    public void handleClienteDesconectado(
            AsyncRequestNotUsableException ex,
            HttpServletRequest request
    ) {
        log.debug("Cliente desconectou antes da resposta terminar — endpoint: {}",
                request.getRequestURI());
        // Sem corpo de resposta — a conexão já não existe mais do
        // outro lado, então não há pra quem escrever.
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