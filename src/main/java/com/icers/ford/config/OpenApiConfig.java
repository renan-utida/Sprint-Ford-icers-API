package com.icers.ford.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.examples.Example;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(buildInfo())
                .servers(buildServers())
                .components(buildComponents())
                .addSecurityItem(
                        new SecurityRequirement().addList("bearerAuth")
                );
    }

    // INFO

    private Info buildInfo() {
        return new Info()
                .title("SpecRadar API")
                .version("1.0.0")
                .description("""
                        ## SpecRadar — Inteligência Competitiva Automotiva
                        
                        API RESTful desenvolvida para a **Sprint Ford FIAP 2026**.
                        Elimina o processo manual de coleta de especificações técnicas \
                        de veículos concorrentes — de ~60 minutos para menos de 10 segundos.
                        
                        ---
                        
                        ## Como usar
                        
                        **1. Autentique-se:**
                        
                        POST /api/v1/auth/login
                        { "email": "analyst@specradar.com", "senha": "Analyst@2026" }
                        
                        **2. Copie o `access_token` retornado**
                        
                        **3. Clique em "Authorize" (cadeado) e cole o token**
                        
                        **4. Use os endpoints de consulta:**
                        
                        POST /api/v1/specs/query
                        POST /api/v1/chat/message
                        
                        ---
                        
                        ## Usuários disponíveis
                        
                        | Email | Senha | Role |
                        |-------|-------|------|
                        | admin@specradar.com | Admin@2026 | ADMIN |
                        | analyst@specradar.com | Analyst@2026 | ANALYST |
                        
                        ---
                        
                        ## Níveis de confiança dos campos
                        
                        | Nível | Significado |
                        |-------|-------------|
                        | ALTA | Dado verificado em fonte oficial |
                        | MEDIA | Dado encontrado em fonte confiável |
                        | INFERIDA | Dado estimado com base em fontes similares |
                        | NAO_ENCONTRADO | Dado não disponível — campo retornado como null |
                        
                        ---
                        
                        ## Equipe
                        **ICERS — FIAP 3ESPW 2026**
                        - Camila Pedroza da Cunha – RM 558768
                        - Isabelle Dallabeneta Carlesso – RM 554592
                        - Nicoli Amy Kassa – RM 559104
                        - Pedro Almeida e Camacho – RM 556831
                        - Renan Dias Utida – RM 558540
                        """)
                .contact(new Contact()
                        .name("ICERS — FIAP 3ESPW")
                        .url("https://github.com/sprint-ford-icers-api"))
                .license(new License()
                        .name("MIT License")
                        .url("https://opensource.org/licenses/MIT"));
    }

    // SERVERS

    private List<Server> buildServers() {
        return List.of(
                new Server()
                        .url("http://localhost:8080")
                        .description("Desenvolvimento local"),
                new Server()
                        .url("https://api.specradar.ford.fiap")
                        .description("Produção (Sprint 4)")
        );
    }

    // COMPONENTS — Security + Examples

    private Components buildComponents() {
        return new Components()
                .addSecuritySchemes("bearerAuth", buildSecurityScheme())
                .addExamples("ExemploLoginRequest", exemploLoginRequest())
                .addExamples("ExemploLoginResponse", exemploLoginResponse())
                .addExamples("ExemploSpecQuery", exemploSpecQuery())
                .addExamples("ExemploSpecResponse", exemploSpecResponse())
                .addExamples("ExemploChatMessage", exemploChatMessage())
                .addExamples("ExemploErro400", exemploErro400())
                .addExamples("ExemploErro404", exemploErro404())
                .addExamples("ExemploErro429", exemploErro429())
                .addExamples("ExemploErro503", exemploErro503());
    }

    private SecurityScheme buildSecurityScheme() {
        return new SecurityScheme()
                .name("bearerAuth")
                .type(SecurityScheme.Type.HTTP)
                .scheme("bearer")
                .bearerFormat("JWT")
                .description(
                        "Token JWT obtido em POST /api/v1/auth/login. " +
                                "Formato: Bearer {token}"
                );
    }

    // EXEMPLOS DE REQUEST

    private Example exemploLoginRequest() {
        return new Example()
                .summary("Login como analista")
                .value(Map.of(
                        "email", "analyst@specradar.com",
                        "senha", "Analyst@2026"
                ));
    }

    private Example exemploSpecQuery() {
        return new Example()
                .summary("Consulta da Ford Ranger Raptor")
                .description("Caso de validação oficial do brief da Ford")
                .value(Map.of(
                        "marca", "Ford",
                        "modelo", "Ranger",
                        "versao", "Raptor",
                        "atributos", List.of(
                                "motor", "potencia", "torque",
                                "transmissao", "tracao", "amortecedores",
                                "aceleracao", "modos_conducao",
                                "farois", "rodas_pneus", "preco"
                        )
                ));
    }

    private Example exemploChatMessage() {
        return new Example()
                .summary("Consulta em linguagem natural")
                .value(Map.of(
                        "mensagem",
                        "Quais são as especificações de motor e suspensão " +
                                "da Ford Ranger Raptor?"
                ));
    }

    // EXEMPLOS DE RESPONSE — SUCESSO

    private Example exemploLoginResponse() {
        return new Example()
                .summary("Token JWT retornado")
                .value(Map.of(
                        "access_token", "eyJhbGciOiJIUzI1NiJ9...",
                        "refresh_token", "eyJhbGciOiJIUzI1NiJ9...",
                        "token_type", "Bearer",
                        "expires_in", 28800,
                        "role", "ANALYST"
                ));
    }

    private Example exemploSpecResponse() {
        return new Example()
                .summary("Ficha técnica da Ford Ranger Raptor")
                .description("Validação oficial do brief — todos os campos presentes")
                .value(Map.of(
                        "marca", "Ford",
                        "modelo", "Ranger",
                        "versao", "Raptor",
                        "confidence_geral", "ALTA",
                        "consultado_em", "2026-05-12T14:30:00",
                        "cache_hit", false,
                        "campos", List.of(
                                Map.of("campo", "motor",
                                        "valor", "V6 3.0L Nano bi turbo",
                                        "confianca", "ALTA",
                                        "fonte", "https://www.ford.com.br",
                                        "verificado_em", "2026-05-12"),
                                Map.of("campo", "potencia",
                                        "valor", "397cv @ 5650 RPM",
                                        "confianca", "ALTA",
                                        "fonte", "https://www.ford.com.br",
                                        "verificado_em", "2026-05-12"),
                                Map.of("campo", "torque",
                                        "valor", "583 Nm @ 3500 RPM",
                                        "confianca", "ALTA",
                                        "fonte", "https://www.ford.com.br",
                                        "verificado_em", "2026-05-12"),
                                Map.of("campo", "preco",
                                        "valor", "R$ 499.000",
                                        "confianca", "MEDIA",
                                        "fonte", "https://www.ford.com.br",
                                        "verificado_em", "2026-05-12")
                        )
                ));
    }

    // EXEMPLOS DE RESPONSE — ERRO

    private Example exemploErro400() {
        return new Example()
                .summary("Erro de validação")
                .value(Map.of(
                        "codigo_erro", "VALIDATION_ERROR",
                        "mensagem", "Um ou mais campos estão inválidos",
                        "timestamp", "2026-05-12T14:30:00",
                        "endpoint", "/api/v1/specs/query",
                        "campos_invalidos", Map.of(
                                "marca", "Marca deve conter apenas letras, espaços e hífens",
                                "atributos", "Máximo de 20 atributos por consulta"
                        )
                ));
    }

    private Example exemploErro404() {
        // Map.of(...) rejeita valores null — usamos HashMap aqui porque
        // campos_invalidos é intencionalmente null neste exemplo
        // (reflete o formato real do ErrorResponse em erros sem
        // validação de campo).
        Map<String, Object> valor = new HashMap<>();
        valor.put("codigo_erro", "NOT_FOUND");
        valor.put("mensagem", "Ficha técnica não encontrada para: " +
                "Toyota Hilux GR-Sport. " +
                "Use POST /api/v1/specs/query para consultar.");
        valor.put("timestamp", "2026-05-12T14:30:00");
        valor.put("endpoint", "/api/v1/specs/Toyota/Hilux/GR-Sport");
        valor.put("campos_invalidos", null);

        return new Example()
                .summary("Veículo não encontrado no banco")
                .value(valor);
    }

    private Example exemploErro429() {
        Map<String, Object> valor = new HashMap<>();
        valor.put("codigo_erro", "RATE_LIMIT_EXCEEDED");
        valor.put("mensagem", "Limite de requisições excedido. " +
                "Aguarde antes de tentar novamente.");
        valor.put("timestamp", "2026-05-12T14:30:00");
        valor.put("endpoint", "/api/v1/specs/query");
        valor.put("campos_invalidos", null);

        return new Example()
                .summary("Rate limit excedido")
                .value(valor);
    }

    private Example exemploErro503() {
        Map<String, Object> valor = new HashMap<>();
        valor.put("codigo_erro", "SERVICE_UNAVAILABLE");
        valor.put("mensagem", "O serviço está temporariamente indisponível. " +
                "Tente novamente em instantes.");
        valor.put("timestamp", "2026-05-12T14:30:00");
        valor.put("endpoint", "/api/v1/specs/query");
        valor.put("campos_invalidos", null);

        return new Example()
                .summary("Serviço de extração indisponível")
                .value(valor);
    }
}