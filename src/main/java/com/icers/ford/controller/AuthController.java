package com.icers.ford.controller;

import com.icers.ford.dto.request.LoginRequest;
import com.icers.ford.dto.request.RefreshRequest;
import com.icers.ford.dto.response.AuthResponse;
import com.icers.ford.dto.response.ErrorResponse;
import com.icers.ford.model.Usuario;
import com.icers.ford.repository.UsuarioRepository;
import com.icers.ford.security.JwtService;
import com.icers.ford.service.AuditService;
import com.icers.ford.service.LoginLockoutService;
import com.icers.ford.util.IpResolver;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Optional;

@Slf4j
@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Autenticação", description = "Endpoints públicos de login e renovação de token")
@RequiredArgsConstructor
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final UsuarioRepository usuarioRepository;
    private final AuditService auditService;
    private final LoginLockoutService loginLockoutService;
    private final IpResolver ipResolver;

    // Expiração do access token em segundos para o response (8h)
    private static final long ACCESS_TOKEN_EXPIRES_IN = 28800L;

    // -- POST /api/v1/auth/login

    @Operation(
            summary = "Login",
            description = "Autentica o usuário com email e senha. " +
                    "Retorna access token (8h) e refresh token (7d)."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Login bem-sucedido",
                    content = @Content(schema = @Schema(implementation = AuthResponse.class))),
            @ApiResponse(responseCode = "400", description = "Corpo da requisição malformado (JSON inválido)",
                    content = @Content),
            @ApiResponse(responseCode = "422", description = "Dados de entrada inválidos",
                    content = @Content),
            @ApiResponse(responseCode = "401", description = "Credenciais inválidas",
                    content = @Content),
            @ApiResponse(responseCode = "429", description = "Conta temporariamente bloqueada por excesso de tentativas falhas",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping("/login")
    public ResponseEntity<Object> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest httpRequest
    ) {
        String ip = ipResolver.resolverIp(httpRequest);

        // Bloqueio por CONTA (não por IP — ver LoginLockoutService).
        // Checado antes de autenticar, pra não gastar verificação de
        // senha (BCrypt) durante um bloqueio já ativo.
        Optional<Long> bloqueio = loginLockoutService.segundosRestantesDeBloqueio(request.email());
        if (bloqueio.isPresent()) {
            long segundos = bloqueio.get();
            log.warn("Login bloqueado — email: {} | ip: {} | restam: {}s",
                    mascararEmail(request.email()), ip, segundos);

            return ResponseEntity
                    .status(HttpStatus.TOO_MANY_REQUESTS)
                    .header("Retry-After", String.valueOf(segundos))
                    .body(ErrorResponse.accountLocked("/api/v1/auth/login", segundos));
        }

        try {
            // Autentica via Spring Security — valida email + senha com BCrypt
            Authentication auth = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(
                            request.email(),
                            request.senha()
                    )
            );

            // Busca o usuário para obter id e role
            Usuario usuario = usuarioRepository
                    .findByEmailAndAtivo(request.email(), "S")
                    .orElseThrow();

            // Gera os tokens
            String accessToken = jwtService.generateAccessToken(
                    usuario.getId(),
                    usuario.getEmail(),
                    usuario.getRole().name()
            );
            String refreshToken = jwtService.generateRefreshToken(
                    usuario.getEmail()
            );

            // Atualiza último acesso
            usuarioRepository.atualizarUltimoAcesso(
                    usuario.getId(),
                    LocalDateTime.now()
            );

            // Loga o evento de login bem-sucedido — via AuditService, que
            // também cuida do hash do userId de forma consistente com o
            // resto do sistema.
            auditService.logAuthSuccess(usuario.getId(), ip, usuario.getEmail());
            loginLockoutService.registrarSucesso(usuario.getEmail());

            log.info("Login bem-sucedido — email: {} | ip: {}",
                    mascararEmail(usuario.getEmail()), ip);

            return ResponseEntity.ok(
                    AuthResponse.of(
                            accessToken,
                            refreshToken,
                            ACCESS_TOKEN_EXPIRES_IN,
                            usuario.getRole().name()
                    )
            );

        } catch (BadCredentialsException e) {
            // Mensagem genérica — não revela se o email existe ou não
            log.warn("Falha de autenticação — ip: {} | email tentado: {}",
                    ip, mascararEmail(request.email()));

            // AuditService.logAuthFailure já dispara a verificação de
            // brute force internamente (5+ falhas em 10min do mesmo IP)
            // — antes, essa lógica existia no AuditService mas nunca era
            // chamada por ninguém.
            auditService.logAuthFailure(ip, request.email());
            loginLockoutService.registrarFalha(request.email());

            return ResponseEntity
                    .status(HttpStatus.UNAUTHORIZED)
                    .body(ErrorResponse.of(
                            "INVALID_CREDENTIALS",
                            "Email ou senha inválidos.",
                            "/api/v1/auth/login"
                    ));
        }
    }

    // -- POST /api/v1/auth/refresh

    @Operation(
            summary = "Renovar token",
            description = "Recebe um refresh token válido e retorna novo par de tokens. " +
                    "Implementa rotação — o refresh token usado é invalidado."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Tokens renovados",
                    content = @Content(schema = @Schema(implementation = AuthResponse.class))),
            @ApiResponse(responseCode = "400", description = "Corpo da requisição malformado (JSON inválido)",
                    content = @Content),
            @ApiResponse(responseCode = "422", description = "Refresh token ausente",
                    content = @Content),
            @ApiResponse(responseCode = "401", description = "Refresh token inválido ou expirado",
                    content = @Content)
    })
    @PostMapping("/refresh")
    public ResponseEntity<Object> refresh(
            @Valid @RequestBody RefreshRequest request,
            HttpServletRequest httpRequest
    ) {
        String ip = ipResolver.resolverIp(httpRequest);

        try {
            String token = request.refreshToken();

            // Valida que é um refresh token válido e não expirado
            if (!jwtService.isRefreshTokenValid(token)) {
                log.warn("Refresh token inválido ou expirado — ip: {}", ip);
                return ResponseEntity
                        .status(HttpStatus.UNAUTHORIZED)
                        .body(ErrorResponse.of(
                                "INVALID_REFRESH_TOKEN",
                                "Refresh token inválido ou expirado.",
                                "/api/v1/auth/refresh"
                        ));
            }

            String email = jwtService.extractEmail(token);

            // Busca o usuário ativo
            Usuario usuario = usuarioRepository
                    .findByEmailAndAtivo(email, "S")
                    .orElse(null);

            if (usuario == null) {
                log.warn("Refresh token para usuário inexistente/inativo — ip: {}", ip);
                return ResponseEntity
                        .status(HttpStatus.UNAUTHORIZED)
                        .body(ErrorResponse.of(
                                "INVALID_REFRESH_TOKEN",
                                "Refresh token inválido ou expirado.",
                                "/api/v1/auth/refresh"
                        ));
            }

            // Rotação de token — gera novo par completo
            String novoAccessToken = jwtService.generateAccessToken(
                    usuario.getId(),
                    usuario.getEmail(),
                    usuario.getRole().name()
            );
            String novoRefreshToken = jwtService.generateRefreshToken(
                    usuario.getEmail()
            );

            log.debug("Token renovado — email: {} | ip: {}",
                    mascararEmail(email), ip);

            return ResponseEntity.ok(
                    AuthResponse.ofRefresh(
                            novoAccessToken,
                            novoRefreshToken,
                            ACCESS_TOKEN_EXPIRES_IN,
                            usuario.getRole().name()
                    )
            );

        } catch (Exception e) {
            log.warn("Erro ao processar refresh token — ip: {}", ip);
            return ResponseEntity
                    .status(HttpStatus.UNAUTHORIZED)
                    .body(ErrorResponse.of(
                            "INVALID_REFRESH_TOKEN",
                            "Refresh token inválido ou expirado.",
                            "/api/v1/auth/refresh"
                    ));
        }
    }

    // MÉTODOS AUXILIARES

    /**
     * Mascara o email nos logs — exibe só o domínio.
     * ex: renan@ford.com → ***@ford.com
     */
    private String mascararEmail(String email) {
        if (email == null || !email.contains("@")) return "***";
        return "***@" + email.substring(email.indexOf("@") + 1);
    }
}