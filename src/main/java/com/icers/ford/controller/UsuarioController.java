package com.icers.ford.controller;

import com.icers.ford.dto.request.UsuarioCreateRequest;
import com.icers.ford.dto.request.UsuarioUpdateRequest;
import com.icers.ford.dto.response.ErrorResponse;
import com.icers.ford.dto.response.UsuarioResponse;
import com.icers.ford.exception.AutoAnonimizacaoException;
import com.icers.ford.model.Usuario;
import com.icers.ford.repository.UsuarioRepository;
import com.icers.ford.service.AuditService;
import com.icers.ford.service.UsuarioService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Gestão administrativa de usuários — CRUD completo + ações especiais
 * de LGPD (anonimizar) e suspensão reversível (desativar/reativar).
 * Todos os endpoints são exclusivos de ADMIN.
 */
@RestController
@RequestMapping("/api/v1/usuarios")
@Tag(name = "Usuários", description = "Gestão administrativa de usuários — apenas ADMIN")
@SecurityRequirement(name = "bearerAuth")
@RequiredArgsConstructor
public class UsuarioController {

    private final UsuarioService usuarioService;
    private final UsuarioRepository usuarioRepository;
    private final AuditService auditService;

    @Operation(summary = "Listar usuários [ADMIN]",
            description = "Retorna todos os usuários cadastrados, ativos e desativados.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Lista retornada"),
            @ApiResponse(responseCode = "401", description = "Token ausente ou inválido", content = @Content),
            @ApiResponse(responseCode = "403", description = "Apenas ADMIN", content = @Content)
    })
    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<UsuarioResponse>> listar() {
        return ResponseEntity.ok(usuarioService.listar());
    }

    @Operation(summary = "Buscar usuário por ID [ADMIN]")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Usuário encontrado"),
            @ApiResponse(responseCode = "401", description = "Token ausente ou inválido", content = @Content),
            @ApiResponse(responseCode = "403", description = "Apenas ADMIN", content = @Content),
            @ApiResponse(responseCode = "404", description = "Usuário não encontrado",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<UsuarioResponse> buscarPorId(
            @Parameter(description = "ID do usuário", example = "2")
            @PathVariable Long id
    ) {
        return ResponseEntity.ok(usuarioService.buscarPorId(id));
    }

    @Operation(summary = "Registrar novo usuário [ADMIN]",
            description = "Cria uma nova conta ANALYST ou ADMIN. A senha é hasheada com " +
                    "BCrypt antes de ser gravada — nunca fica em texto puro em lugar nenhum.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Usuário criado"),
            @ApiResponse(responseCode = "400", description = "Corpo da requisição malformado (JSON inválido)",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Token ausente ou inválido", content = @Content),
            @ApiResponse(responseCode = "403", description = "Apenas ADMIN", content = @Content),
            @ApiResponse(responseCode = "409", description = "Email já cadastrado",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "422", description = "Dados de entrada inválidos",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<UsuarioResponse> criar(
            @Valid @RequestBody UsuarioCreateRequest request,
            @AuthenticationPrincipal UserDetails userDetails,
            HttpServletRequest httpRequest
    ) {
        Usuario admin = resolverUsuario(userDetails.getUsername());
        String ip = extrairIp(httpRequest);

        UsuarioResponse criado = usuarioService.criar(request);

        auditService.logAdminAction(admin.getId(), ip, "CRIAR_USUARIO",
                "Usuário id=" + criado.id() + " criado com role=" + criado.role());

        return ResponseEntity.status(201).body(criado);
    }

    @Operation(summary = "Atualizar usuário [ADMIN]",
            description = "Atualiza email e/ou role. Não altera senha — isso não é feito " +
                    "por este endpoint.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Usuário atualizado"),
            @ApiResponse(responseCode = "400", description = "Corpo da requisição malformado (JSON inválido)",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Token ausente ou inválido", content = @Content),
            @ApiResponse(responseCode = "403", description = "Apenas ADMIN", content = @Content),
            @ApiResponse(responseCode = "404", description = "Usuário não encontrado",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "Email já pertence a outro usuário",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "422", description = "Dados de entrada inválidos",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<UsuarioResponse> atualizar(
            @Parameter(description = "ID do usuário", example = "2")
            @PathVariable Long id,
            @Valid @RequestBody UsuarioUpdateRequest request,
            @AuthenticationPrincipal UserDetails userDetails,
            HttpServletRequest httpRequest
    ) {
        Usuario admin = resolverUsuario(userDetails.getUsername());
        String ip = extrairIp(httpRequest);

        UsuarioResponse atualizado = usuarioService.atualizar(id, request);

        auditService.logAdminAction(admin.getId(), ip, "ATUALIZAR_USUARIO",
                "Usuário id=" + id + " atualizado");

        return ResponseEntity.ok(atualizado);
    }

    @Operation(summary = "Desativar usuário [ADMIN]",
            description = "Suspensão REVERSÍVEL — bloqueia login (ativo='N') sem tocar no " +
                    "email. Diferente de anonimizar: use isto para afastamento temporário, " +
                    "não para descarte de dado pessoal. Reverte com PATCH /{id}/reativar.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Usuário desativado"),
            @ApiResponse(responseCode = "401", description = "Token ausente ou inválido", content = @Content),
            @ApiResponse(responseCode = "403", description = "Apenas ADMIN", content = @Content),
            @ApiResponse(responseCode = "404", description = "Usuário não encontrado",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "Não é permitido desativar a própria conta",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> desativar(
            @Parameter(description = "ID do usuário", example = "2")
            @PathVariable Long id,
            @AuthenticationPrincipal UserDetails userDetails,
            HttpServletRequest httpRequest
    ) {
        Usuario admin = resolverUsuario(userDetails.getUsername());
        String ip = extrairIp(httpRequest);

        usuarioService.desativar(id, admin.getId());

        auditService.logAdminAction(admin.getId(), ip, "DESATIVAR_USUARIO",
                "Usuário id=" + id + " desativado");

        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Reativar usuário [ADMIN]",
            description = "Reverte uma desativação — volta ativo='S', login volta a funcionar.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Usuário reativado"),
            @ApiResponse(responseCode = "401", description = "Token ausente ou inválido", content = @Content),
            @ApiResponse(responseCode = "403", description = "Apenas ADMIN", content = @Content),
            @ApiResponse(responseCode = "404", description = "Usuário não encontrado",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PatchMapping("/{id}/reativar")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> reativar(
            @Parameter(description = "ID do usuário", example = "2")
            @PathVariable Long id,
            @AuthenticationPrincipal UserDetails userDetails,
            HttpServletRequest httpRequest
    ) {
        Usuario admin = resolverUsuario(userDetails.getUsername());
        String ip = extrairIp(httpRequest);

        usuarioService.reativar(id);

        auditService.logAdminAction(admin.getId(), ip, "REATIVAR_USUARIO",
                "Usuário id=" + id + " reativado");

        return ResponseEntity.noContent().build();
    }

    @Operation(
            summary = "Anonimizar usuário [ADMIN]",
            description = "Remove permanentemente o email do usuário (substituído por um " +
                    "placeholder único) e desativa a conta. Ação IRREVERSÍVEL — diferente " +
                    "de desativar. A linha permanece no banco para preservar integridade " +
                    "referencial com fichas técnicas e histórico já registrados."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Usuário anonimizado com sucesso"),
            @ApiResponse(responseCode = "401", description = "Token ausente ou inválido",
                    content = @Content),
            @ApiResponse(responseCode = "403", description = "Apenas ADMIN pode anonimizar usuários",
                    content = @Content),
            @ApiResponse(responseCode = "404", description = "Usuário não encontrado",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "Não é permitido anonimizar a própria conta",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PatchMapping("/{id}/anonimizar")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> anonimizar(
            @Parameter(description = "ID do usuário", example = "2")
            @PathVariable Long id,
            @AuthenticationPrincipal UserDetails userDetails,
            HttpServletRequest httpRequest
    ) {
        Usuario admin = resolverUsuario(userDetails.getUsername());
        String ip = extrairIp(httpRequest);

        if (admin.getId().equals(id)) {
            throw new AutoAnonimizacaoException();
        }

        usuarioService.anonimizar(id);

        auditService.logAdminAction(admin.getId(), ip, "ANONIMIZAR_USUARIO",
                "Usuário id=" + id + " anonimizado");

        return ResponseEntity.noContent().build();
    }

    // MÉTODOS AUXILIARES

    private Usuario resolverUsuario(String email) {
        return usuarioRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException(
                        "Usuário autenticado não encontrado no banco"
                ));
    }

    private String extrairIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        return forwarded != null && !forwarded.isBlank()
                ? forwarded.split(",")[0].trim()
                : request.getRemoteAddr();
    }
}