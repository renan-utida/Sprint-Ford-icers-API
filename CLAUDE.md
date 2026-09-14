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
grupo:** 🚧 Em andamento — Grupos 1-7 de 9 concluídos e testados.

| Grupo | Escopo | Status |
|---|---|---|
| 1 | Correções triviais (retry-after, handler JSON malformado, rate limit configs separadas, 422 em vez de 400) | ✅ |
| 2 | AuditService plugado no login (brute force), RBAC real via `@PreAuthorize` | ✅ |
| 3 | AES-256-GCM em `campos_json`, pseudonimização HMAC-SHA256, `UsuarioController` (CRUD + anonimização/desativação) | ✅ |
| 4 | Idempotência (índice único + header `Idempotency-Key`), `IpResolver` (nunca confiar em X-Forwarded-For sem proxy confiável) | ✅ |
| 5 | `vencedor` do `/compare` corrigido (comparação numérica real), `sugestoes_similares` no 404, cache sempre completo, reverificação periódica configurável (2-31 dias) | ✅ |
| 6 | Revogação real de refresh token (`sr_refresh_tokens_usados` por `jti`) | ✅ |
| **7** | **HTTPS/TLS** — portar padrão do Código 1 (perfil `prod` com PKCS12 self-signed via keytool; `dev` continua HTTP) | ✅ Concluído |
| 8 | Grounding real de busca no Gemini + sensor de demanda preditiva (escopo mínimo a definir) | ⏳ |
| 9 | `POST /specs/from-pdf` de verdade (hoje 501) | ⏳ |

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

**Achados extras (fora dos 9 grupos, também no checklist):**
- `ChatController.extrairIp()` e `RequestLoggingFilter.extrairIp()` nunca
  migrados pro `IpResolver` do Grupo 4 — ainda confiam cegamente em
  `X-Forwarded-For`, diferente de `RateLimitFilter`/`AuthController`/
  `SpecController`/`UsuarioController`.
- `resolverUsuario(String email)` duplicado em pelo menos 3 controllers
  (`ChatController`, `SpecController`, `UsuarioController`) — candidato a
  extrair para um serviço único, no espírito do `IpResolver`. Causa raiz:
  `UserDetailsServiceImpl` usa o `User.builder()` genérico do Spring
  Security em vez de `Usuario implements UserDetails`.
- `application.properties` está salvo em ISO-8859-1 (comentários com acento
  corrompidos) — vale resalvar em UTF-8.
- `ChatService` reconhece intenção só por palavras-chave hardcoded — não
  cobre consultas comparativas cruzando o histórico (ex.: "qual pick-up
  concorrente tem mais torque abaixo de R$300 mil"), do exemplo de "modo
  chat" da proposta original. Gap conhecido, não precisa resolver agora.

**Fase C (não iniciada):** decidir se adiciona perfil H2 em memória para dev
antes de escrever testes unitários (hoje dependeriam do Oracle real).

**Fase D (não iniciada):** README final, revisão geral, conferência contra
os requisitos da Sprint 3 (última sprint com requisitos técnicos específicos
por matéria — Sprint 4 é só vídeo pitch).

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
