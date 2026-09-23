# SpecRadar — Segurança da API (Cybersecurity)

> Documentação técnica de como o SpecRadar atende, hoje, aos requisitos
> formais de Cybersecurity desta etapa do Challenge Ford × FIAP — com
> evidência extraída diretamente do código-fonte real do projeto (classes,
> configuração, endpoints e comportamento observável), não de uma descrição
> de intenção.

**Equipe:** Renan Dias Utida (RM 558540), Camila Pedroza da Cunha (RM 558768),
Isabelle Dallabeneta Carlesso (RM 554592), Nicoli Amy Kassa (RM 559104), Pedro
Almeida e Camacho (RM 556831) — FIAP 3ESPW.
**Professor da disciplina:** Vitor Miguel Lasse.

---

## Sumário

- [Contexto e escopo](#contexto-e-escopo)
- [Critérios de avaliação exigidos](#critérios-de-avaliação-exigidos)
- [1. Segurança de Entrada e Validação de Dados (20 pts)](#1-segurança-de-entrada-e-validação-de-dados-20-pts)
- [2. Autenticação e Autorização (20 pts)](#2-autenticação-e-autorização-20-pts)
- [3. Proteção de APIs e Serviços (20 pts)](#3-proteção-de-apis-e-serviços-20-pts)
- [4. Segurança de Dados e Privacidade (25 pts)](#4-segurança-de-dados-e-privacidade-25-pts)
- [5. Monitoramento, Logs e Auditoria (15 pts)](#5-monitoramento-logs-e-auditoria-15-pts)
- [Tabela-resumo de cobertura](#tabela-resumo-de-cobertura)

---

## Contexto e escopo

O SpecRadar recebe marca/modelo/versão e uma lista livre de atributos vinda
do cliente, e devolve especificações técnicas — extraídas via Google Gemini
quando não há cache — ou, num segundo fluxo, extraídas de um PDF anexado
pelo próprio usuário. É uma superfície de entrada grande (texto livre,
upload de arquivo) e um fluxo de autenticação com dois perfis de acesso, o
que torna os cinco critérios abaixo diretamente relevantes ao problema real,
não apenas exigência formal.

Stack: Java 21, Spring Boot 3.5.14, Spring Security 6.x, JWT (`jjwt`
0.12.6), Bucket4j para rate limiting, AES-256-GCM e HMAC-SHA256 para
criptografia/pseudonimização, Oracle 19c em produção.

## Critérios de avaliação exigidos

| # | Critério | Pontos |
|---|---|---:|
| 1 | Segurança de Entrada e Validação de Dados | 20 |
| 2 | Autenticação e Autorização | 20 |
| 3 | Proteção de APIs e Serviços | 20 |
| 4 | Segurança de Dados e Privacidade | 25 |
| 5 | Monitoramento, Logs e Auditoria | 15 |
| | **Total** | **100** |

---

## 1. Segurança de Entrada e Validação de Dados (20 pts)

**Pedido:** validação e sanitização contra SQL Injection/XSS/command
injection/entradas malformadas; normalização e validação de marca, modelo,
versão e atributos; limitação de tamanho e formato contra payload flooding e
buffer overflow; tratamento de erros que nunca revele stack trace, estrutura
interna ou tecnologia usada.

**Sanitização contra injeção.** Toda a persistência passa por Spring Data
JPA com queries parametrizadas — não há concatenação manual de SQL em
nenhum ponto do projeto. A API só fala JSON (nunca renderiza HTML), então
XSS refletido/armazenado no sentido clássico não se aplica à superfície de
ataque real; o controle relevante é a integridade estrutural da entrada.

**Validação declarativa e normalização de marca/modelo/versão/atributos.**
Todo DTO de entrada usa Bean Validation com regex explícito por campo — não
apenas presença/tamanho:

```java
// SpecQueryRequest.java
@NotBlank(message = "Marca é obrigatória")
@Size(min = 2, max = 50, message = "Marca deve ter entre 2 e 50 caracteres")
@Pattern(
        regexp = "^[a-zA-ZÀ-ÿ\\s\\-]+$",
        message = "Marca deve conter apenas letras, espaços e hífens"
)
String marca,

@NotBlank(message = "Versão é obrigatória")
@Size(min = 2, max = 80, message = "Versão deve ter entre 2 e 80 caracteres")
@Pattern(
        regexp = "^[a-zA-ZÀ-ÿ0-9\\s\\-\\.]+$",
        message = "Versão deve conter apenas letras, números, espaços, hífens e pontos"
)
String versao,

@NotEmpty(message = "Lista de atributos é obrigatória")
@Size(max = 20, message = "Máximo de 20 atributos por consulta")
List<String> atributos
```

A lista de atributos é texto livre por natureza do produto (o usuário pode
pedir "motor", "potência", "modos de condução" — não é um conjunto fechado
como marca/modelo), então a normalização acontece em runtime, em
`SpecService`, removendo qualquer caractere fora de um conjunto explícito
antes de qualquer uso do valor (persistência, cache, ou prompt ao LLM):

```java
// SpecService.java
private List<String> sanitizarAtributos(List<String> atributos) {
    return atributos.stream()
            .map(String::trim)
            .filter(a -> !a.isBlank())
            .map(a -> a.replaceAll("[^a-zA-ZÀ-ÿ0-9\\s\\-_]", ""))
            .filter(a -> !a.isBlank())
            .toList();
}
```

**Limitação de tamanho (buffer overflow/payload flooding).** Todo campo
`String` de entrada tem `@Size` com limite máximo explícito. O upload de
PDF (`POST /specs/from-pdf`) tem limite de tamanho de arquivo configurado
via `spring.servlet.multipart.max-file-size`, e um arquivo acima do limite
retorna `413` antes de qualquer processamento — antes até de chegar à
extração de conteúdo. Em conjunto com o rate limiting do critério 3, isso
cobre as duas dimensões de "payload flooding": tamanho de uma requisição
individual e volume de requisições.

**Tratamento seguro de erros.** Um único `@RestControllerAdvice`
(`GlobalExceptionHandler`) intercepta toda exceção da aplicação — 12 tipos
mapeados individualmente, mais um catch-all — e nunca deixa vazar stack
trace, nome de classe interna ou tecnologia para o cliente. O catch-all
loga o stack trace completo internamente via SLF4J, mas devolve só uma
mensagem genérica:

```java
// GlobalExceptionHandler.java
@ExceptionHandler(Exception.class)
public ResponseEntity<ErrorResponse> handleGeneric(Exception ex, HttpServletRequest request) {
    // Stack trace completo no log interno para debug — NUNCA vai para o response
    log.error("Erro não tratado — endpoint: {} | tipo: {} | mensagem: {}",
            request.getRequestURI(), ex.getClass().getSimpleName(), ex.getMessage(), ex);

    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ErrorResponse.of(
            "INTERNAL_ERROR",
            "Ocorreu um erro inesperado. Tente novamente ou entre em contato com o suporte.",
            request.getRequestURI()
    ));
}
```

Vale destacar uma distinção que o handler faz e que evita um erro comum: um
JSON sintaticamente inválido (`HttpMessageNotReadableException`) retorna
`400`, enquanto um JSON válido que viola uma regra de negócio (Bean
Validation) retorna `422` — os dois nunca são tratados como a mesma
categoria de erro.

---

## 2. Autenticação e Autorização (20 pts)

**Pedido:** autenticação segura (JWT/OAuth2) com token expirável, assinado e
com renovação controlada; RBAC diferenciando papéis de acesso.

**JWT com expiração e renovação controlada.** Autenticação via JWT HS256.
Dois tipos de token, cada um com claim `type` própria (`ACCESS`/`REFRESH`) —
um refresh token não pode ser usado como access token nem vice-versa, porque
a validação checa o tipo explicitamente:

```java
// JwtService.java
public String generateAccessToken(Long userId, String email, String role) {
    return buildToken(Map.of("userId", userId, "role", role, "type", "ACCESS"),
            email, accessTokenExpiration);
}

public String generateRefreshToken(String email) {
    return buildToken(Map.of("type", "REFRESH", "jti", UUID.randomUUID().toString()),
            email, refreshTokenExpiration);
}

public boolean isAccessTokenValid(String token, String email) {
    String tokenEmail = extractEmail(token);
    String tokenType = extractTokenType(token);
    return tokenEmail.equals(email) && "ACCESS".equals(tokenType) && !isTokenExpired(token);
}
```

O refresh token carrega um `jti` (UUID único). Cada rotação
(`POST /auth/refresh`) invalida permanentemente o token anterior, registrado
por `jti` na tabela `sr_refresh_tokens_usados` — reuso de um refresh token já
rotacionado é rejeitado mesmo antes da expiração natural do token,
diferente de um esquema onde só a expiração por tempo protege contra reuso.

**RBAC.** Dois papéis (`ADMIN`, `ANALYST`, enum `Role`), aplicados via
`@PreAuthorize` diretamente nos métodos de cada controller — não centralizado
só em `SecurityConfig` por path, mas também garantido método a método, o que
evita que um novo endpoint criado sem essa anotação fique aberto por
esquecimento (o padrão do projeto é "autenticado por padrão" — qualquer rota
sem regra explícita em `SecurityConfig` exige token via `.anyRequest().authenticated()`,
e a autorização fina fica no `@PreAuthorize`):

```java
// UsuarioController.java
@PreAuthorize("hasRole('ADMIN')")
@PostMapping
public ResponseEntity<UsuarioResponse> criar(...) { ... }

// SpecController.java
@PreAuthorize("hasAnyRole('ANALYST', 'ADMIN')")
@PostMapping("/query")
public ResponseEntity<SpecResponse> query(...) { ... }

@PreAuthorize("hasRole('ADMIN')")
@DeleteMapping("/{id}")
public ResponseEntity<Void> deletar(...) { ... }
```

| Ação | ANALYST | ADMIN |
|---|:---:|:---:|
| Login / refresh | ✅ | ✅ |
| `POST /specs/query`, `/from-pdf`, `/chat/message` | ✅ | ✅ |
| `GET /specs/{marca}/{modelo}/{versao}`, `/compare`, `/history` | ✅ | ✅ |
| `DELETE /specs/{id}` | ❌ | ✅ |
| `GET`/`PUT /specs/config` | ❌ | ✅ |
| Qualquer endpoint de `/usuarios/**` | ❌ | ✅ |

Também há uma trava contra força bruta especificamente no login:
`LoginLockoutService` bloqueia uma conta por 30 segundos após 5 ou mais
falhas em 10 minutos, sem revelar ao chamador se o e-mail tentado existe ou
não no sistema (a resposta de falha de login é idêntica nos dois casos) —
uma proteção que não está listada literalmente no critério, mas que reforça
diretamente "renovação controlada" e é parte do mesmo fluxo de autenticação.

---

## 3. Proteção de APIs e Serviços (20 pts)

**Pedido:** HTTPS/TLS 1.2+ obrigatório; rate limiting/throttling; CORS
restrito a domínios autorizados; assinatura/verificação de integridade de
payloads.

**HTTPS/TLS.** Perfil `prod` roda exclusivamente em HTTPS na porta 8443, com
certificado PKCS12 (RSA 2048, `SHA384withRSA`, gerado via `keytool`) —
arquivo `.p12` fora do repositório, senha do keystore via variável de
ambiente:

```properties
# application-prod.properties
server.port=8443
server.ssl.enabled=true
server.ssl.key-store=classpath:specradar-ssl.p12
server.ssl.key-store-password=${SSL_KEYSTORE_PASSWORD}
server.ssl.key-store-type=PKCS12
server.ssl.key-alias=specradar
```

Com `server.ssl.enabled=true`, o Spring Boot desabilita o HTTP por completo
em `prod` — não existe uma porta HTTP aberta em paralelo por engano. O
perfil `dev` roda em HTTP na porta 8080, isolado por configuração de perfil
(nenhuma variável de `.env` controla isso — só a variável de ambiente real
`SPRING_PROFILES_ACTIVE`), justamente para não haver risco de HTTP vazar
para produção por configuração incorreta de uma variável opcional.

**Rate limiting e throttling.** Duas camadas independentes: `RateLimitFilter`
por IP, registrado como filtro de servlet com `Ordered.HIGHEST_PRECEDENCE` —
roda antes de qualquer autenticação, então protege inclusive o login contra
força bruta — e um segundo limite por usuário autenticado, dentro de
`SpecService`, para as chamadas mais caras (consulta ao LLM, upload de PDF
com orçamento próprio de 10/min, separado do limite de 60/min de `/query`):

```java
// config/RateLimitFilter.java
Bucket bucket = bucketsPorIp.computeIfAbsent(ip, this::criarBucket);
ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);

if (probe.isConsumed()) {
    response.addHeader("X-RateLimit-Remaining", String.valueOf(probe.getRemainingTokens()));
    chain.doFilter(request, response);
} else {
    response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
    response.addHeader("Retry-After", String.valueOf(retryAfterSeconds));
    // corpo JSON com codigo_erro RATE_LIMIT_EXCEEDED
}
```

O cliente recebe `X-RateLimit-Remaining` em toda resposta bem-sucedida e
`Retry-After` (em segundos, arredondado para cima) quando excede o limite —
não apenas um `429` sem contexto de quando tentar de novo.

**CORS.** Origens permitidas lidas de variável de ambiente — nunca `*` — com
métodos e headers explicitamente listados, configurado dentro do próprio
`SecurityConfig`:

```java
// SecurityConfig.java
@Value("${cors.allowed-origins:http://localhost:3000,http://localhost:8081}")
private String allowedOriginsStr;

@Bean
public CorsConfigurationSource corsConfigurationSource() {
    CorsConfiguration config = new CorsConfiguration();
    List<String> origins = List.of(allowedOriginsStr.split(","));
    config.setAllowedOrigins(origins);
    config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
    config.setAllowedHeaders(List.of("Authorization", "Content-Type", "Accept", "X-Requested-With"));
    config.setAllowCredentials(true);
    config.setMaxAge(3600L);
    ...
}
```

**Assinatura/verificação de integridade de payloads — nuance registrada.**
Este ponto foi endereçado via a assinatura HS256 do JWT: `JwtService` rejeita
qualquer token cuja assinatura não bata
(`Jwts.parser().verifyWith(signingKey).parseSignedClaims(token)`), garantindo
que o token de autenticação não foi alterado em trânsito. Isso é diferente de
uma assinatura HMAC dedicada sobre o corpo de cada requisição de negócio
(ex.: `POST /specs/query`) — que não existe como mecanismo separado. Na
prática, a integridade do corpo da requisição depende do canal HTTPS
(criptografia de transporte, que por si só já impede adulteração
não-detectada em trânsito) somado à assinatura do JWT que autentica quem
enviou a requisição — não de uma assinatura própria por payload. É a
interpretação adotada para este critério; fica registrada explicitamente
para não sugerir, numa leitura futura, uma camada de assinatura de payload
dedicada que não existe hoje no projeto.

---

## 4. Segurança de Dados e Privacidade (25 pts)

**Pedido:** criptografia de dados sensíveis em repouso; política de retenção
e descarte seguro; anonimização/pseudonimização de dados pessoais; proteção
contra exposição acidental.

**Criptografia em repouso.** Dois mecanismos distintos para dois tipos de
dado sensível. Senhas: BCrypt fator 12. Especificações técnicas
(`campos_json`, que podem conter dado extraído de fontes variadas): AES-256
em modo GCM — modo autenticado, ou seja, além de confidencialidade garante
integridade: se o texto cifrado for adulterado, a descriptografia falha com
exceção em vez de devolver dado corrompido silenciosamente:

```java
// crypto/AesEncryptionService.java
public String encrypt(String textoPlano) {
    byte[] iv = new byte[TAMANHO_IV_BYTES];
    new SecureRandom().nextBytes(iv);
    Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
    cipher.init(Cipher.ENCRYPT_MODE, chave, new GCMParameterSpec(TAMANHO_TAG_BITS, iv));
    byte[] cifrado = cipher.doFinal(textoPlano.getBytes(StandardCharsets.UTF_8));
    // IV || texto cifrado + tag de autenticação, em Base64
}
```

A cifragem/decifragem é aplicada de forma transparente via um
`AttributeConverter` do JPA (`CamposJsonEncryptedConverter`) — a coluna
`campos_json` de `sr_fichas_tecnicas` nunca é gravada em texto plano, e
nenhum código de service precisa lembrar de chamar `encrypt`/`decrypt`
manualmente (o que eliminaria uma classe inteira de erro: esquecer de
cifrar um novo ponto de escrita).

**Política de retenção e descarte.** Fichas técnicas obsoletas não são
mantidas indefinidamente sem revisão — a reverificação periódica
(configurável pelo ADMIN, entre 2 e 31 dias) força uma nova consulta ao LLM
na próxima vez que um veículo com ficha "velha" for pedido, em vez de servir
dado potencialmente desatualizado do cache para sempre.

**Anonimização/pseudonimização.** Dois mecanismos, para dois problemas
diferentes. Anonimização (irreversível, LGPD): endpoint
`PATCH /usuarios/{id}/anonimizar`, restrito a ADMIN e só aplicável a um
usuário já desativado, sobrescreve nome/e-mail/senha preservando o `id`
(para não quebrar FKs de histórico já registrado). Pseudonimização
(reversível com a chave certa, usada nos logs de auditoria): identificação de
usuário em `sr_audit_logs` nunca é o e-mail/nome em texto plano — é um hash
HMAC-SHA256 com salt secreto:

```java
// service/AuditService.java
public String hashUserId(Long userId) {
    Mac mac = Mac.getInstance("HmacSHA256");
    mac.init(new SecretKeySpec(pseudonymizationSalt.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
    byte[] hash = mac.doFinal(userId.toString().getBytes(StandardCharsets.UTF_8));
    // ... hex encode
}
```

HMAC (não SHA-256 puro) é deliberado: IDs de usuário são inteiros
sequenciais pequenos — um hash sem chave secreta seria trivialmente
reversível por força bruta (testar 1, 2, 3... até bater), o que seria
ofuscação, não pseudonimização de verdade. O HMAC exige conhecer o salt
(guardado só no servidor) para sequer tentar reverter.

**Proteção contra exposição acidental.** `UsuarioResponse` nunca inclui o
campo de senha — o DTO de saída simplesmente não tem esse campo, então não
há como vazar por descuido futuro em outro ponto do código; `.env` está no
`.gitignore`; toda mensagem de erro do `GlobalExceptionHandler` é genérica
por design (critério 1); Swagger documenta todo endpoint real — não há rota
"esquecida" sem documentação aumentando a superfície de ataque desconhecida.

---

## 5. Monitoramento, Logs e Auditoria (15 pts)

**Pedido:** logs estruturados e seguros (sem dados sensíveis, com
rastreabilidade); monitoramento de eventos suspeitos; trilha de auditoria
para ações críticas.

**Logs estruturados via SLF4J.** Todo o projeto usa SLF4J com prefixo
consistente por categoria de evento, sem gravar senha ou token em nenhum
log — apenas identificadores (e-mail mascarado, id, IP, endpoint).

**Monitoramento de eventos suspeitos, com detecção de força bruta.**
`AuditService` não só loga falha de autenticação — ele conta falhas do mesmo
IP numa janela de 10 minutos e emite um alerta de nível `ERROR` quando o
limite é atingido:

```java
// service/AuditService.java
private static final int LIMITE_FALHAS_AUTH = 5;
private static final int JANELA_FALHAS_MINUTOS = 10;

private void verificarBruteForce(String ip) {
    long falhas = auditLogRepository.countFalhasAutenticacao(ip, janela);
    if (falhas >= LIMITE_FALHAS_AUTH) {
        log.error("ALERTA BRUTE FORCE — ip: {} | falhas em {}min: {}", ip, JANELA_FALHAS_MINUTOS, falhas);
        salvar(null, "/api/v1/auth/login", "POST", 401, ip,
                "BRUTE_FORCE_ALERT", "IP com " + falhas + " falhas de auth em " + JANELA_FALHAS_MINUTOS + " minutos");
    }
}
```

Esse alerta é o que alimenta, junto com `LoginLockoutService` (critério 2), o
bloqueio efetivo de conta — não é só um log passivo que alguém precisaria
notar depois.

**Trilha de auditoria para ações críticas.** Toda ação relevante grava uma
linha em `sr_audit_logs`, com o usuário identificado de forma pseudonimizada
(critério 4), método HTTP e endpoint reais, status de resposta real, e uma
categoria de ação (`SPEC_QUERY`, `AUTH_SUCCESS`, `AUTH_FAILURE`,
`ADMIN_CRIAR_USUARIO`, `ADMIN_DELETE_FICHA`, `RATE_LIMIT_EXCEEDED`, entre
outras). O registro é assíncrono (`@Async`) e propositalmente nunca lança
exceção para o fluxo principal — uma falha ao gravar auditoria não pode
derrubar a ação de negócio que está sendo auditada:

```java
// service/AuditService.java
private void salvar(String usuarioHash, String endpoint, String metodo,
                    int status, String ip, String acao, String detalhes) {
    try {
        auditLogRepository.save(AuditLog.builder()
                .usuarioHash(usuarioHash).endpoint(endpoint).metodoHttp(metodo)
                .statusResposta(status).ipOrigem(ip).acao(acao).detalhes(detalhes)
                .build());
    } catch (Exception e) {
        log.error("Falha ao salvar audit log — acao: {} | erro: {}", acao, e.getMessage());
    }
}
```

Cada ação administrativa de escrita (criar/atualizar/desativar/reativar/
anonimizar usuário, deletar ficha técnica) chama `logAdminAction` com o
método HTTP, endpoint e status **reais** vindos do próprio controller — não
valores fixos —, o que faz cada linha de auditoria refletir exatamente o que
aconteceu na requisição que a gerou.

---

## Tabela-resumo de cobertura

| Critério | Pontos | O que cobre | Status |
|---|:---:|---|:---:|
| Validação de entrada / sanitização | 20 | Bean Validation com regex, `sanitizarAtributos()`, JPA parametrizado, limite de tamanho de arquivo, erros sem stack trace | ✅ |
| Autenticação e autorização | 20 | JWT com tipos ACCESS/REFRESH, revogação por `jti`, RBAC via `@PreAuthorize`, bloqueio de força bruta no login | ✅ |
| Proteção de APIs e serviços | 20 | HTTPS/TLS por perfil, rate limiting em duas camadas (IP + usuário), CORS por variável de ambiente, integridade via assinatura JWT (nuance registrada acima) | ✅ |
| Segurança de dados e privacidade | 25 | AES-256-GCM transparente via `AttributeConverter`, BCrypt, reverificação periódica, anonimização LGPD, pseudonimização HMAC-SHA256 | ✅ |
| Monitoramento, logs e auditoria | 15 | SLF4J estruturado, detecção ativa de força bruta com contagem por janela, auditoria em banco com dados reais por ação | ✅ |
| **Total** | **100** | | **✅** |
