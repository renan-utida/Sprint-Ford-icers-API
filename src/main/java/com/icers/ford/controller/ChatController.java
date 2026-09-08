package com.icers.ford.controller;

import com.icers.ford.dto.request.ChatMessageRequest;
import com.icers.ford.dto.response.ErrorResponse;
import com.icers.ford.model.Usuario;
import com.icers.ford.repository.UsuarioRepository;
import com.icers.ford.service.ChatService;
import com.icers.ford.service.ChatService.ChatResponse;
import io.swagger.v3.oas.annotations.Operation;
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

@Slf4j
@RestController
@RequestMapping("/api/v1/chat")
@Tag(name = "Chat", description = "Interface conversacional para consultas em linguagem natural")
@SecurityRequirement(name = "bearerAuth")
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;
    private final UsuarioRepository usuarioRepository;

    // POST /api/v1/chat/message

    @Operation(
            summary = "Enviar mensagem",
            description = "Processa uma mensagem em linguagem natural e retorna " +
                    "a ficha técnica do veículo identificado na mensagem. " +
                    "Exemplo: 'Quais são as especificações da Ford Ranger Raptor?'"
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200",
                    description = "Mensagem processada com sucesso",
                    content = @Content(schema = @Schema(implementation = ChatResponse.class))),
            @ApiResponse(responseCode = "400",
                    description = "Corpo da requisição malformado (JSON inválido)",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "422",
                    description = "Mensagem inválida ou muito curta",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401",
                    description = "Token ausente ou inválido",
                    content = @Content),
            @ApiResponse(responseCode = "503",
                    description = "Serviço de extração indisponível",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping("/message")
    @PreAuthorize("hasAnyRole('ANALYST', 'ADMIN')")
    public ResponseEntity<ChatResponse> message(
            @Valid @RequestBody ChatMessageRequest request,
            @AuthenticationPrincipal UserDetails userDetails,
            HttpServletRequest httpRequest
    ) {
        Usuario usuario = resolverUsuario(userDetails.getUsername());
        String ip = extrairIp(httpRequest);

        ChatResponse response = chatService.processar(
                request.mensagem(), usuario, ip
        );

        // Se não encontrou veículo na mensagem, retorna 200 com sucesso=false
        // O app mobile trata o campo 'sucesso' para exibir a mensagem de ajuda
        return ResponseEntity.ok(response);
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