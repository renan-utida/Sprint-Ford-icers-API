package com.icers.ford.controller;

import com.icers.ford.dto.request.SpecQueryRequest;
import com.icers.ford.dto.response.ErrorResponse;
import com.icers.ford.dto.response.SpecResponse;
import com.icers.ford.model.Usuario;
import com.icers.ford.repository.UsuarioRepository;
import com.icers.ford.service.AuditService;
import com.icers.ford.service.SpecService;
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
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/v1/specs")
@Tag(name = "Especificações", description = "Consulta e comparação de especificações técnicas de veículos")
@SecurityRequirement(name = "bearerAuth")
@RequiredArgsConstructor
public class SpecController {

    private final SpecService specService;
    private final AuditService auditService;
    private final UsuarioRepository usuarioRepository;

    // POST /api/v1/specs/query

    @Operation(
            summary = "Consultar especificações",
            description = "Consulta especificações técnicas de um veículo. " +
                    "Verifica o cache antes de chamar o LLM. " +
                    "Retorna a ficha padronizada com confidence score por campo."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Ficha técnica retornada com sucesso",
                    content = @Content(schema = @Schema(implementation = SpecResponse.class))),
            @ApiResponse(responseCode = "400", description = "Corpo da requisição malformado (JSON inválido)",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "422", description = "Dados de entrada inválidos",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Token ausente ou inválido",
                    content = @Content),
            @ApiResponse(responseCode = "429", description = "Limite de requisições excedido",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "503", description = "Serviço de extração indisponível",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping("/query")
    @PreAuthorize("hasAnyRole('ANALYST', 'ADMIN')")
    public ResponseEntity<SpecResponse> query(
            @Valid @RequestBody SpecQueryRequest request,
            @AuthenticationPrincipal UserDetails userDetails,
            HttpServletRequest httpRequest
    ) {
        Usuario usuario = resolverUsuario(userDetails.getUsername());
        String ip = extrairIp(httpRequest);

        SpecResponse response = specService.query(request, usuario, ip);

        return ResponseEntity.ok(response);
    }

    // GET /api/v1/specs/{marca}/{modelo}/{versao}

    @Operation(
            summary = "Buscar ficha do banco",
            description = "Retorna a ficha técnica armazenada sem chamar o LLM. " +
                    "Retorna 404 se o veículo ainda não foi consultado."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Ficha encontrada no banco",
                    content = @Content(schema = @Schema(implementation = SpecResponse.class))),
            @ApiResponse(responseCode = "401", description = "Token ausente ou inválido",
                    content = @Content),
            @ApiResponse(responseCode = "404", description = "Veículo não encontrado no banco",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping("/{marca}/{modelo}/{versao}")
    @PreAuthorize("hasAnyRole('ANALYST', 'ADMIN')")
    public ResponseEntity<SpecResponse> findByVeiculo(
            @Parameter(description = "Marca do veículo", example = "Ford")
            @PathVariable String marca,

            @Parameter(description = "Modelo do veículo", example = "Ranger")
            @PathVariable String modelo,

            @Parameter(description = "Versão do veículo", example = "Raptor")
            @PathVariable String versao
    ) {
        SpecResponse response = specService.findByVeiculo(marca, modelo, versao);
        return ResponseEntity.ok(response);
    }

    // GET /api/v1/specs/compare

    @Operation(
            summary = "Comparar dois veículos",
            description = "Compara dois veículos do banco campo a campo. " +
                    "Ambos devem estar no banco (consultados previamente). " +
                    "Retorna vencedor por atributo numérico."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Comparativo gerado com sucesso"),
            @ApiResponse(responseCode = "401", description = "Token ausente ou inválido",
                    content = @Content),
            @ApiResponse(responseCode = "404", description = "Um ou ambos os veículos não encontrados",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping("/compare")
    @PreAuthorize("hasAnyRole('ANALYST', 'ADMIN')")
    public ResponseEntity<Map<String, Object>> compare(
            @Parameter(description = "Marca do veículo 1", example = "Ford")
            @RequestParam String v1Marca,

            @Parameter(description = "Modelo do veículo 1", example = "Ranger")
            @RequestParam String v1Modelo,

            @Parameter(description = "Versão do veículo 1", example = "Raptor")
            @RequestParam String v1Versao,

            @Parameter(description = "Marca do veículo 2", example = "Toyota")
            @RequestParam String v2Marca,

            @Parameter(description = "Modelo do veículo 2", example = "Hilux")
            @RequestParam String v2Modelo,

            @Parameter(description = "Versão do veículo 2", example = "GR-Sport")
            @RequestParam String v2Versao,

            @Parameter(description = "Atributos para comparar (opcional — compara todos se vazio)")
            @RequestParam(required = false) List<String> atributos
    ) {
        Map<String, Object> comparativo = specService.compare(
                v1Marca, v1Modelo, v1Versao,
                v2Marca, v2Modelo, v2Versao,
                atributos
        );
        return ResponseEntity.ok(comparativo);
    }

    // GET /api/v1/specs/history

    @Operation(
            summary = "Histórico de fichas",
            description = "Lista todas as fichas técnicas armazenadas no banco. " +
                    "Aceita filtros opcionais de marca e modelo."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Lista retornada com sucesso"),
            @ApiResponse(responseCode = "401", description = "Token ausente ou inválido",
                    content = @Content)
    })
    @GetMapping("/history")
    @PreAuthorize("hasAnyRole('ANALYST', 'ADMIN')")
    public ResponseEntity<List<SpecResponse>> history(
            @Parameter(description = "Filtrar por marca (opcional)", example = "Ford")
            @RequestParam(required = false) String marca,

            @Parameter(description = "Filtrar por modelo (opcional)", example = "Ranger")
            @RequestParam(required = false) String modelo
    ) {
        List<SpecResponse> historico = specService.listarHistorico(marca, modelo);
        return ResponseEntity.ok(historico);
    }

    // POST /api/v1/specs/from-pdf — ROADMAP Sprint 4

    @Operation(
            summary = "Extrair specs de PDF [Sprint 4]",
            description = "**Não implementado nesta Sprint.** " +
                    "Roadmap Sprint 4: recebe PDF de catálogo e extrai " +
                    "especificações técnicas via modelo multimodal."
    )
    @ApiResponse(responseCode = "501", description = "Funcionalidade prevista para Sprint 4")
    @PostMapping("/from-pdf")
    @PreAuthorize("hasAnyRole('ANALYST', 'ADMIN')")
    public ResponseEntity<ErrorResponse> fromPdf() {
        return ResponseEntity.status(501).body(
                ErrorResponse.of(
                        "NOT_IMPLEMENTED",
                        "Extração de PDF será implementada na Sprint 4. " +
                                "Roadmap: upload de catálogo → OCR → extração via LLM multimodal.",
                        "/api/v1/specs/from-pdf"
                )
        );
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
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}