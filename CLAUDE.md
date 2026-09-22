# SpecRadar — Contexto do Projeto

> Este arquivo existe para que uma sessão futura do Claude Code não precise
> reconstruir este contexto do zero. Mantenha-o atualizado conforme os grupos
> da Fase B forem concluídos. Fonte de verdade mais detalhada e atualizada:
> `docs/README-provisorio-do-que-foi-feito.md` — leia-o também.

## O que é este projeto

SpecRadar: API Java 21 / Spring Boot 3.5.14 + Oracle que consulta, cacheia e
compara especificações técnicas de veículos concorrentes via Google Gemini.
Challenge acadêmico Ford × FIAP 2026 (Engenharia de Software).

**Aluno responsável por este chat:** Renan Dias Utida (RM 558540) —
Cybersecurity / Arquitetura Orientada a Serviços (SOA).

## Diretório "Código 1" — NÃO é o entregável

Em `C:\Users\ronal\Downloads\ford-sprint-api-icers\specradar` há um segundo
projeto Java/Spring Boot (CRUD simples de veículos/specs, sem IA) que foi
entregue na Sprint 1 como alternativa de emergência, porque este projeto
("Código 2", este diretório) estava com a integração ao Gemini quebrada.
O Código 1 tirou nota 10 em SOA/Cyber naquela sprint e serve **só como
referência técnica pontual** daqui pra frente (ex.: padrão de HTTPS/TLS,
Grupo 7). Nunca tratar o Código 1 como escopo a entregar nem misturar lógica
dos dois além do que for pedido explicitamente.

## Como este chat prefere trabalhar

- Grupos pequenos, um de cada vez.
- Se envolver decisão de design, apresentar as opções **antes** de codificar
  e esperar a escolha do usuário.
- Depois de cada mudança: resumo do que foi feito, lista de arquivos
  alterados/substituídos, e um roteiro de teste manual detalhado. O usuário
  testa manualmente (via Insomnia/navegador) e só então seguimos pro próximo
  item — não presumir sucesso nem prosseguir sem o retorno do teste.

## Estado atual

**Fase A — Validar que funciona de verdade:** ✅ Concluída. Todos os fluxos
principais testados manualmente (login, query com Gemini real, cache
hit/miss, compare, history, chat, from-pdf stub, erros 401/400/422/404/429).

**Fase B — Confrontar com requisitos formais de Cyber/SOA e a proposta do
grupo:** ✅ Concluída — Grupos 1-9 de 9 concluídos e testados (Grupo 8
avaliado e descartado com evidência; os demais implementados e testados).

| Grupo | Escopo | Status |
|---|---|---|
| 1 | Correções triviais (retry-after, handler JSON malformado, rate limit configs separadas, 422 em vez de 400) | ✅ |
| 2 | AuditService plugado no login (brute force), RBAC real via `@PreAuthorize` | ✅ |
| 3 | AES-256-GCM em `campos_json`, pseudonimização HMAC-SHA256, `UsuarioController` (CRUD + anonimização/desativação) | ✅ |
| 4 | Idempotência (índice único + header `Idempotency-Key`), `IpResolver` (nunca confiar em X-Forwarded-For sem proxy confiável) | ✅ |
| 5 | `vencedor` do `/compare` corrigido (comparação numérica real), `sugestoes_similares` no 404, cache sempre completo, reverificação periódica configurável (2-31 dias) | ✅ |
| 6 | Revogação real de refresh token (`sr_refresh_tokens_usados` por `jti`) | ✅ |
| **7** | **HTTPS/TLS** — portar padrão do Código 1 (perfil `prod` com PKCS12 self-signed via keytool; `dev` continua HTTP) | ✅ Concluído |
| **8** | **Grounding real no Gemini (item 1) + sensor de demanda preditiva (item 2)** — investigados, **descartados** por limitação confirmada da conta | ✅ Avaliado e descartado (evidência: 7 testes) |
| **9** | **`POST /specs/from-pdf`** — multipart, cache-primeiro, rate limit próprio (10/min), validação de arquivo antes da chamada cara | ✅ Concluído — testado (7 cenários + independência de rate limit confirmada na prática) |

**Grupo 8 — investigado e descartado (não implementado). Detalhe completo,
com a tabela dos 7 testes, em `docs/README-provisorio-do-que-foi-feito.md`
— seção "Grupo 8 — Investigação de grounding (evidência completa)".
Essa seção deve sobreviver integralmente (tabela incluída) na reescrita do
README na Fase D — não resumir.**

Resumo:
- **Divergência real entre `ideia-inicial.txt` e os PDFs vigentes**: a
  `ideia-inicial.txt` descarta explicitamente um pipeline de scraping
  ("Proposta 1B") por risco técnico. Os PDFs vigentes citam "sensor de
  demanda preditiva" como diferencial já entregue. Resolvido: o sensor real
  seria construído em cima do grounding, nunca com parsing de HTML de
  terceiros — mas isso ficou sem efeito prático porque o item 1 não avançou.
- **Sete chamadas diretas à API do Gemini** (controles pareados: mesmo
  modelo, com/sem `google_search`) confirmaram que a conta atual (tier
  gratuito, sem faturamento) é estruturalmente incapaz de usar grounding:
  toda variante da família Gemini 3.x chamável tem cota de grounding **zero**
  (`429 RESOURCE_EXHAUSTED` imediato); toda variante da família Gemini 2.x
  com cota de grounding disponível (1.500/28 dias, confirmado no painel do
  Google AI Studio) está **bloqueada por elegibilidade de conta** (`404
  "no longer available to new users"`). Não há nome de modelo que contorne
  isso.
- **Decisão — Caminho A (sem faturamento habilitado por agora):** item 1 não
  implementado, `LlmClient` continua sem `google_search`; item 2 avaliado e
  descartado no mesmo padrão do `bulk-import` do Grupo 3 (dependia do
  grounding pra evitar o risco de scraping da Proposta 1B). Revisitar se o
  faturamento for habilitado no futuro (Caminho B).
- **Compliance do Google, registrado mesmo sem implementação**: resposta
  grounded exigiria exibir `searchEntryPoint` ("Google Search Suggestions")
  — responsabilidade do app mobile se o grounding for revisitado depois.

**Grupo 9 — ✅ concluído e testado. Detalhe completo da investigação, com a
tabela dos 16 testes, em `docs/README-provisorio-do-que-foi-feito.md` —
seção "Grupo 9 — Investigação de confiabilidade multimodal (evidência
completa)". Essa seção também deve sobreviver integralmente na Fase D.**

Teste manual — 7 cenários + 1 bônus, todos confirmados: arquivo inválido
(422), arquivo grande (413), PDF texto cache miss→hit (200, specs corretas,
`fonte: "PDF anexado"`), PDF com foto (503 com mensagem específica — resultado
esperado, não falha), rate limit 429 (variação 11→13 tentativas é normal,
bucket4j usa refill contínuo), RBAC ANALYST+ADMIN (200 nos dois). **Bônus
importante:** com `/from-pdf` travado em 429, `/query` respondeu 200 normal
~2,5s depois, dentro da janela de espera reportada — confirma a independência
dos dois buckets de rate limit **na prática**, não só na leitura do código.

Resumo da investigação:
- Antes de desenhar o endpoint, testamos envio de PDF via `inlineData` no
  `generateContent` — diferente do grounding, é capacidade nativa do modelo,
  não uma *tool* separada, então a expectativa era não ter surpresa.
- **16 tentativas, com controles pareados**: PDF **sem nenhuma imagem
  embutida** (specs recriadas como texto puro) → **200 OK, 1/1**, com 100%
  dos campos batendo com o gabarito da Ranger Raptor (inclusive reproduziu
  fielmente um erro de digitação do slide original, "R$499.00" em vez de
  "R$499.000" — evidência de que o modelo transcreve, não "corrige" por
  conta própria). PDF **com qualquer imagem embutida** (foto real da Raptor,
  a mesma foto recomprimida, ou uma imagem sintética nova sem relação com o
  arquivo original) → **503 em 15 de 16 tentativas**, incluindo depois de 2
  dias de intervalo (descarta sobrecarga transitória) e com arquivo
  totalmente novo (descarta corrupção específica de um arquivo). **Não
  isolamos "textura fotográfica" como causa exclusiva** — só a correlação
  forte entre presença de imagem embutida e falha, já que não testamos uma
  imagem isolada sem texto associado.

Resumo da implementação:
- `SpecService.resolverComCache` extraído de `query()` e reaproveitado por
  `queryFromPdf()` (parametrizado por uma função de "como buscar no LLM") —
  evita duplicar a lógica de cache hit/expirada/parcial/miss entre os dois.
- `/from-pdf` tem bucket de rate limit PRÓPRIO (10/min, `bucketsPdfPorUsuario`),
  não compartilhado com o de `/query` (60/min) — chamada multimodal é bem
  mais cara. Validação do arquivo (content-type + assinatura `%PDF-`) roda
  antes do rate limit, no mesmo espírito de `/query` (Bean Validation também
  roda antes) — arquivo inválido nunca chega perto de custar uma chamada ao
  Gemini, então não deveria gastar esse orçamento escasso.
- `RestTemplate` dedicado (`restTemplatePdf`, qualificado por nome do bean —
  não pelo `@Qualifier` no método `@Bean`, que não teria efeito aí) com
  timeout maior (120s vs 60s) só para chamadas multimodais.
- `LlmUnavailableException`/503 em `/from-pdf` usa mensagem diferenciada
  (dica sobre PDFs com fotos grandes) via `request.getRequestURI().endsWith("/from-pdf")`
  dentro do handler único existente — sem tocar a exceção nem duplicar
  handler; `/query`/`/chat/message` continuam com a mensagem genérica de
  sempre (o `else` do `?:` é a chamada original, inalterada).
- Arquivo grande demais (`MaxUploadSizeExceededException`) → **413**, não
  422 (`ArquivoInvalidoException` é 422, conteúdo inválido — tamanho é 413).
  Mensagem injeta o limite via `@Value(DataSize)`, nunca um número fixo.

**Descobertas importantes durante o teste do Grupo 7 (não são achados novos do
checklist, mas afetam qualquer trabalho futuro no projeto):**
- **`SPRING_PROFILE` no `.env` NÃO controla de fato o perfil ativo.** A lib
  `springboot3-dotenv` roda como o `EnvironmentPostProcessor` de menor
  prioridade do Spring Boot — ou seja, depois que `ConfigDataEnvironmentPostProcessor`
  já decidiu qual `application-{perfil}.properties` carregar. Pra rodar em
  `prod` de verdade, é preciso `SPRING_PROFILES_ACTIVE=prod` como **variável
  de ambiente real** (SO ou run configuration da IDE), nunca só no `.env`.
  Decisão registrada em `docs/README-provisorio-do-que-foi-feito.md`.
- **Qualquer variável no `.env` tem prioridade MAIOR que `application-{perfil}.properties`**
  (via *relaxed binding* do Spring Boot — ex.: `SERVER_PORT` no `.env` casa
  com a property `server.port`). Por isso nenhuma propriedade que precisa
  ser diferente por perfil (porta, SSL, etc.) pode depender de uma variável
  `${...}` no `application.properties` base — tem que ser valor fixo, senão
  o `.env` sobrescreve silenciosamente o que o perfil tentou definir. Foi
  exatamente isso que quebrou `server.port` no primeiro teste do Grupo 7
  (`SERVER_PORT=8080` no `.env` vencia o `8443` do `application-prod.properties`).
- **`pom.xml` agora declara `UTF-8` explicitamente** (`project.build.sourceEncoding`
  / `project.reporting.outputEncoding`) — sem isso, build em ambientes/toolchains
  diferentes pode corromper acentos tanto em `.properties` quanto em strings
  compiladas de `.java`. `application.properties` também foi reconvertido de
  ISO-8859-1 pra UTF-8 real nessa mesma correção.
- **IntelliJ pode continuar mostrando `application.properties` corrompido
  mesmo depois dos bytes corrigidos e de todas as configs de encoding
  certas** — ela cacheia uma decisão de encoding por arquivo (de quando esse
  arquivo genuinamente era ISO-8859-1) que não é reconsultada automaticamente.
  Fix: **File → Invalidate Caches → Invalidate and Restart**. Detalhe em
  `docs/README-provisorio-do-que-foi-feito.md`.

**Achados extras (fora dos 9 grupos, também no checklist) — 3 de 4 resolvidos:**
- ~~`ChatController.extrairIp()` e `RequestLoggingFilter.extrairIp()` nunca
  migrados pro `IpResolver`~~ — **resolvido**: os dois agora usam
  `IpResolver` (injetado via construtor no `ChatController`; passado
  manualmente no `RequestLoggingFilter`, que não é `@Component` — mesmo
  padrão que o `RateLimitFilter` já usava em `FilterConfig`).
- ~~`resolverUsuario(String email)` duplicado em 3 controllers~~ —
  **resolvido**: extraído para `com.icers.ford.util.UsuarioResolver`
  (`@Component`), no espírito do `IpResolver`. `ChatController`,
  `SpecController` e `UsuarioController` agora injetam `UsuarioResolver`
  em vez de `UsuarioRepository` diretamente (que era usado *só* pra isso
  nos três — confirmado antes de remover). `AuthController` continua com
  `UsuarioRepository` próprio — uso diferente (login), fora do escopo
  desse achado.
- ~~`application.properties` em ISO-8859-1~~ — **resolvido**, efeito
  colateral do Grupo 7 (reconvertido pra UTF-8 real).
- `ChatService` reconhece intenção só por palavras-chave hardcoded — **movido
  pra nota da Fase D, item "Revisão geral e melhorias finais"** (adiamento
  de prioridade, não bloqueio técnico — ver `docs/README-provisorio-do-que-foi-feito.md`).

**Bug real achado durante o teste do achado do `IpResolver`/`UsuarioResolver`
(não fazia parte do escopo original, corrigido junto):**
`AuditService.logAdminAction` nunca conseguia gravar no banco — usava
`"POST/PUT/DELETE"` (15 chars) e `"/api/v1/admin/**"` como placeholders
fixos de método/endpoint, mas `metodo_http` é `VARCHAR2(10)` **com CHECK
IN ('GET','POST','PUT','PATCH','DELETE') desde a V4** — nunca poderia ter
funcionado (ORA-12899 por tamanho, ORA-02290 por valor). O catch genérico
de `salvar()` engole isso silenciosamente (intencional — audit não deve
derrubar o fluxo principal), então as 6 ações administrativas que chamam
essa função (criar/atualizar/desativar/reativar/anonimizar usuário,
`DELETE /specs/{id}`) **nunca tiveram log de auditoria gravado**, mesmo a
ação sempre respondendo `2xx` normalmente. Corrigido passando
`httpRequest.getMethod()`/`getRequestURI()` reais (já disponíveis nos 6
call sites) em vez dos placeholders. **Isso invalida o ✅ do checklist da
Fase B para "Trilha de auditoria para ações críticas"** — só passa a ser
verdade a partir de agora; nada anterior pode ser reconstituído. Confirmado
em teste manual + `SELECT` direto em `sr_audit_logs` (`ADMIN_CRIAR_USUARIO`
e `ADMIN_DELETE_FICHA` com `metodo_http`/`endpoint` reais).

**Outro achado do mesmo teste — e a primeira correção não resolveu de
verdade:** race condition de e-mail duplicado em `UsuarioController` virava
`500` em vez de `409`. Um primeiro fix (só `try/catch(DataIntegrityViolationException)`
em volta de `save()`) **continuou dando 500 em teste real** (duas requisições
com 22ms de diferença). Causa raiz: `Usuario`/`FichaTecnica` usam
`GenerationType.SEQUENCE` — `save()` só aloca o ID da sequence, o **INSERT
real fica pendente pro flush/commit**, que só roda depois que o método
`@Transactional` já retornou (`JpaTransactionManager.doCommit()`), fora de
qualquer `try/catch` do método. Fix de verdade: `saveAndFlush()` em vez de
`save()`, forçando o INSERT síncrono dentro do `try`.

**Isso revelou que `SpecService.salvarFicha()` (Grupo 5) tinha o mesmo bug**
— o `catch(DataIntegrityViolationException)` da corrida de criação de ficha
nunca foi validado sob concorrência real e tinha o mesmo defeito
(`fichaTecnicaRepository.save()` sem flush). Corrigido junto com
`saveAndFlush()`. **A "corrida de concorrência detectada" que o Grupo 5
declarava como testado nunca tinha sido exercitada por concorrência de
verdade até agora.**

**Status do teste deste fix específico: corrigido por mecanismo comprovado,
não por reprodução direta.** 3 tentativas de forçar concorrência real em
`POST /specs/query` esbarraram na instabilidade já conhecida do Gemini
(503/JSON malformado interceptando antes das duas chamadas chegarem ao
`saveAndFlush()`). Aceito como suficiente — mecanismo idêntico e
determinístico ao já comprovado no `UsuarioService` (mesmo
`GenerationType.SEQUENCE`, mesma tradução de exceção via Hibernate),
nenhuma lógica específica de `FichaTecnica` mudaria esse comportamento.

`status_resposta` também era fixo em `200` no `logAdminAction` (errado pra
criar=201 e desativar/reativar/anonimizar/deletar=204) — mesma classe de
bug, achada ao conferir os registros no banco; corrigida junto, agora vem
como parâmetro real dos 6 call sites.

**Gap de design — resolvido:** `SpecResponse` não expunha o `id` da ficha —
descoberto testando `DELETE /specs/{id}`. Mapeamento prévio confirmou só 6
call sites (todos em `SpecService`; `salvarFicha()` tinha apenas 1 ponto de
chamada em todo o projeto) e nenhum outro consumidor precisando de call
site novo (`ChatService`/`from-pdf` reusam os mesmos métodos;
`IdempotencyService` guarda o objeto em memória, sem JSON). Campo `id`
adicionado (primeiro do record) — mudança aditiva, documentada sozinha no
Swagger. Testado ponta a ponta: cache miss/hit com mesmo id, `history` com
ids corretos, `compare` sem regressão, `DELETE /specs/{id}` funcionando com
o id vindo direto da resposta da API.

**Fase C — ✅ concluída.** Decisão sobre banco de teste: **nem H2 nem
Testcontainers** — mapeamento mostrou que 6 das 9 migrations (V1, V2, V3,
V4, V6, V8, não só as 5 que eu tinha contado originalmente — esqueci o V4
na primeira análise) tinham sintaxe Oracle-específica (bloco PL/SQL de
sequence, `ADD (...)`/`MODIFY (...)`) sem equivalente em H2. **Esse
mapeamento também ficou incompleto** — era só leitura/grep, nunca boot real
contra H2. Só apareceu que a **V7** também não era portável (índice
funcional `UPPER(...)`, sem equivalente em H2) quando o perfil `dev-h2` foi
implementado e testado de verdade (ver abaixo). Total real: **7 de 9**.
Caminho escolhido: reescrever essas migrations num formato já portável
(`GENERATED ... AS IDENTITY`, ANSI, suportado nativamente por Oracle 12c+ e
H2), inspirado no padrão que o Código 1 já usa — elimina duplicação de
migrations (problema do H2) e necessidade de container (problema do
Testcontainers), sem trocar de banco.

Feito: 6 migrations reescritas (sequence/PL/SQL removidos; V8 simplificado
de 3 passos pra 1, já que `sr_fichas_tecnicas` está sempre vazia nesse
ponto da sequência de migrations numa reaplicação do zero); 5 entidades
JPA trocadas de `GenerationType.SEQUENCE` pra `IDENTITY`
(`RefreshTokenUsado` não afetada, usa chave natural). `ddl-auto` continua
`validate`, por decisão explícita.

**Concluído:** os dois resets feitos (antes e depois da reescrita), os dois
com `flyway validate`/`ddl-auto=validate` limpos contra o Oracle real
(19.3). Concorrência real retestada: **`sr_usuarios` reproduzida de
verdade** (18ms de intervalo, `201`+`409`, sem stack trace de erro não
tratado — mais limpo que a rodada com `SEQUENCE`); **`sr_fichas_tecnicas`
aceita por analogia** (mesmo obstáculo de instabilidade do Gemini
impedindo reprodução direta, pela 2ª vez — mesmo raciocínio de mecanismo
idêntico já usado e confirmado correto na rodada anterior). Nota: como
`saveAndFlush()` nunca foi removido do código, o teste confirma que a
correção continua funcionando, mas não isola se `IDENTITY` sozinho (sem
`saveAndFlush`) já bastaria — curiosidade teórica deixada em aberto de
propósito, sem efeito prático (`saveAndFlush` continua mantido de
qualquer jeito).

**Perfil `dev-h2` — ✅ concluído e testado.** Objetivo original da Fase C
(H2 real + Console pra quem não tem acesso ao Oracle FIAP, igual o Código
1) — a portabilidade do schema acima era só pré-requisito. **Decisão de
timing:** `application-dev.properties` continua Oracle por enquanto
(desenvolvimento ativo, rotina de teste depende dele) — `dev-h2` é um
perfil ADICIONAL, não substitui `dev`. A virada de verdade (`dev` virar H2,
igual Código 1) fica planejada pro **fim do projeto**, quando o código
estiver mais estável — não esquecer disso na hora (também comentado no
código, `SprintFordApiApplication`, bloco do banner).

Feito: dependência H2 no `pom.xml`; `application-dev-h2.properties` novo
(H2 em memória, `H2Dialect`/`PUBLIC` sobrescrevendo a base Oracle-específica,
Console em `/h2-console`); `SecurityConfig` com `SecurityFilterChain`
dedicada só pro Console (CSRF off + `SAMEORIGIN` restritos a esse path);
banner (`SprintFordApiApplication`) mostrando "Banco: H2" e as credenciais
do Console só nesse perfil.

**Três bugs reais, nenhum visível por leitura de código — só apareceram ao
subir de verdade contra H2:**
1. Log completamente mudo no boot (parecia travado) — `logback-spring.xml`
   só tinha `<springProfile name="dev">`/`"prod"`, nenhum casava com
   `dev-h2` → zero appenders. App rodava por baixo (confirmado via `curl`
   direto), só não aparecia nada no console. Fix: `name="dev,dev-h2"`.
2. V7 reescrita (ver correção acima) — 3 colunas computadas
   (`GENERATED ALWAYS AS (UPPER(...))`, sem `VIRTUAL` — testado ao vivo
   contra o Oracle real da FIAP antes de assumir que seria opcional lá) +
   índice único simples sobre elas, em vez do índice direto na expressão.
3. `ddl-auto=validate` rejeitava `id` e mais 5 colunas `NUMBER(n)` **só sob
   H2** — `NUMBER` (Oracle/H2) sempre reporta como `NUMERIC` via JDBC, mas
   `H2Dialect` espera `BIGINT`/`INTEGER` por padrão pra `Long`/`Integer`
   (`OracleDialect` já esperava `NUMERIC`, por isso nunca deu problema nos
   resets anteriores). Fix: `@JdbcTypeCode(SqlTypes.NUMERIC)` em 6 campos.
4. H2 Console dava `401` mesmo com chain dedicada + `permitAll` —
   `securityMatcher` baseado em string resolve via `MvcRequestMatcher`
   (dispatch do Spring MVC), mas o Console é um Servlet puro, fora do
   `@RequestMapping`. Fix: `AntPathRequestMatcher` explícito.

Testado com 3º reset completo do Oracle (V7 mudou de conteúdo → checksum
Flyway diferente): perfil `dev` pós-reset com boot limpo + login OK; perfil
`dev-h2` com boot limpo, 9 migrations aplicando, Console acessível com as 7
tabelas, login OK via navegador e Insomnia, banner mostrando as credenciais
do H2 só nesse perfil.

**Testes unitários automatizados — ✅ concluído. 171/171 passando, 0
falhas.** Suíte JUnit Platform Suite (`@Suite`+`@SelectPackages`, mesmo
padrão do Código 1): `com.icers.ford.SuiteDeTestesGeral` na raiz de
`src/test/java`, um arquivo de teste por classe de produção. Todos os
repositories mockados via Mockito — nenhum teste toca o Oracle real;
`SprintFordApiApplicationTests` (smoke test padrão do Initializr, `@SpringBootTest`)
corrigido com `@ActiveProfiles("dev-h2")` — sem isso, subiria contra o
Oracle real por padrão.

11 classes cobertas: `AesEncryptionService`, `JwtService`,
`CamposJsonEncryptedConverter`, `IdempotencyService`, `LoginLockoutService`,
`ConfigService`, `UsuarioService`, `AuditService`, `GlobalExceptionHandler`,
`ChatService`, `SpecService` (a mais complexa, escrita em 3 partes ao longo
da sessão: cache hit/miss/parcial/expirada; concorrência + rate limiting;
findByVeiculo/compare/histórico/deleção).

Dois testes de regressão travando bugs reais desta mesma sessão:
`UsuarioServiceTest` (race condition de e-mail duplicado — `saveAndFlush()`
em vez de `save()`, confirmado simulando `DataIntegrityViolationException`
e checando `409` em vez de `500`) e `AuditServiceTest` (placeholders fixos
inválidos de `logAdminAction` que violavam o `CHECK` de `metodo_http` e
nunca gravavam nada — confirmado checando que os valores gravados são os
parâmetros reais recebidos).

Gaps documentados como `// TODO` nos próprios arquivos de teste (decisão
consciente): `IdempotencyServiceTest`/`LoginLockoutServiceTest` não cobrem
expiração real (24h/30s) por falta de `Clock` injetável nas classes de
produção — reflection nos `Instant` internos foi descartada por frágil;
reconsiderar junto com uma refatoração pra `Clock` injetável, se algum dia
fizer sentido. `ChatServiceTest` cobre extração de intenção por amostragem,
não as ~44 palavras-chave do mapa hardcoded (mesmo gap de design já
documentado acima, não escondido pela cobertura de teste).

**Fase D (não iniciada):** README final, revisão geral, conferência contra
os requisitos da Sprint 3 (última sprint com requisitos técnicos específicos
por matéria — Sprint 4 é só vídeo pitch). Duas notas registradas pro item
"Revisão geral e melhorias finais":
- Gap do `ChatService` (palavras-chave hardcoded, não cobre consultas
  comparativas cruzando histórico) — adiado por prioridade, não bloqueio.
- Grupo 8 (grounding) — reavaliar se o Gemini pago for adotado pra
  apresentação da banca (cota zero era do tier gratuito); status atual
  ("avaliado e descartado") continua correto pro tier gratuito de hoje.

## Onde estão os documentos de referência

- `docs/README.md` — índice de tudo em `docs/`, comece por aqui.
- `docs/README-provisorio-do-que-foi-feito.md` — estado atual mais confiável
  e atualizado (arquitetura, endpoints, segurança, roadmap, decisões de
  design registradas). **Consultar primeiro** em qualquer sessão nova.
- `docs/fase-b-checklist-consolidado.md` — checklist formal item por item
  (SOA + Cyber + aderência à proposta) que originou os 9 grupos.
- `docs/Solucao/` — proposta vigente do grupo (PDFs) — onde divergir de
  `ideia-inicial.txt`, os PDFs vencem.
- `docs/Sprint1/Sprint1-ICERS-FORD-CyberSecurity.pdf` — relatório de Cyber
  nota 10 do Código 1, referência de rigor (não código a copiar).
