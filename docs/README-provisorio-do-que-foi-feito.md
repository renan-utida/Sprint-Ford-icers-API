# SpecRadar

> API de inteligência competitiva para especificações técnicas de veículos — desenvolvida para o desafio Ford × FIAP 2026.

**Status:** 🚧 Em desenvolvimento ativo — Fase B concluída (Grupos 1–9 de 9). Fase C concluída — schema portável, perfil `dev-h2` e suíte de testes unitários (171/171 passando) implementados e testados.
**Este README é provisório.** Será revisado, completado e organizado de forma definitiva na Fase D, depois que o projeto estiver totalmente estabilizado (ver [Roadmap](#roadmap-do-projeto) abaixo).

**Aluno responsável:** Renan Dias Utida (RM 558540) — Cybersecurity / Arquitetura Orientada a Serviços (SOA)
**Repositório base:** `sprint-ford-api`

---

## Sobre o projeto

O SpecRadar é uma API REST que consulta, armazena em cache e compara especificações técnicas de veículos — pensada como ferramenta de inteligência competitiva para a Ford acompanhar as fichas técnicas de veículos concorrentes (e dos próprios), sem depender de coleta manual.

Quando um veículo é consultado pela primeira vez, a API usa um LLM (Google Gemini) para extrair as especificações a partir do seu conhecimento — não há web scraping nem busca ao vivo (ver [limitações conhecidas](#limitações-e-itens-propositalmente-adiados)). O resultado é armazenado, e consultas futuras pelo mesmo veículo são respondidas direto do banco, sem gastar uma nova chamada ao modelo.

## Stack tecnológica

| Camada | Tecnologia |
|---|---|
| Linguagem / Framework | Java 21, Spring Boot 3.5.14 |
| Banco de dados | Oracle (instância FIAP) |
| Migrations | Flyway (V1–V9 atualmente) |
| LLM | Google Gemini (`gemini-3.7-flash`) |
| Autenticação | JWT (JJWT 0.12.6) — access + refresh token |
| Rate limiting | Bucket4j |
| Criptografia de dados | AES-256-GCM (dados em repouso), HMAC-SHA256 (pseudonimização) |
| Documentação da API | springdoc-openapi (Swagger UI) |

## Como rodar o projeto

### Pré-requisitos
- JDK 21
- Acesso a uma instância Oracle (schema FIAP do aluno)
- Uma chave de API do Google Gemini válida, com cota disponível

### Variáveis de ambiente

Copie `.env.example` para `.env` e preencha (nunca commitar o `.env` real):

```
JWT_SECRET=<chave secreta para assinatura dos tokens JWT>
AES_SECRET_KEY=<chave de 32 bytes em Base64 — gerar com: openssl rand -base64 32>
PSEUDONYMIZATION_SALT=<salt de 32 bytes em Base64 — mesmo comando acima>
SSL_KEYSTORE_PASSWORD=<senha do keystore PKCS12 usado no perfil prod — ver Segurança>
```
(mais a chave da API do Gemini, conforme configurado em `application.properties`)

### Subindo o projeto

1. Garanta que o schema Oracle está acessível e vazio (ou já com as migrations anteriores aplicadas).
2. Rode a aplicação — o Flyway aplica as migrations `V1` a `V9` automaticamente na inicialização.
3. Acesse a documentação interativa em `/swagger-ui/index.html`.

### Trocando entre os perfis `dev` e `prod`

**A variável `SPRING_PROFILE` dentro do `.env` não controla de fato qual
`application-{perfil}.properties` é carregado.** A biblioteca
`springboot3-dotenv` lê o `.env` tarde demais no bootstrap do Spring Boot —
depois que o Spring já decidiu qual perfil ativar e quais arquivos de
propriedades carregar. Na prática, editar só o `.env` sempre resulta no
perfil `dev` sendo aplicado de verdade, mesmo que o valor de `SPRING_PROFILE`
diga outra coisa (isso só engana o `Environment` quando consultado depois,
o que inclusive já confundiu o banner de inicialização até isso ser
descoberto).

Para rodar de fato em `prod`, defina `SPRING_PROFILES_ACTIVE=prod` como
**variável de ambiente real** (nunca só no `.env`):

```bash
# Git Bash / terminal
SPRING_PROFILES_ACTIVE=prod ./mvnw spring-boot:run
```

Ou, na IntelliJ, na run configuration: aba **Environment variables** →
`SPRING_PROFILES_ACTIVE=prod`. Para voltar a `dev`, remova a variável (ou
troque o valor) — sem ela, o default `spring.profiles.active=dev` do
`application.properties` continua valendo. Não existe mais nenhuma variável
`SPRING_PROFILE` no `.env`/`.env.example` — foi removida de propósito, pra
não sugerir que trocar de perfil é tão simples quanto editar o `.env`.

**Cuidado ao testar:** nunca edite `spring.profiles.active` direto no
`application.properties` pra "testar prod rapidinho" — isso muda o *default*
pra qualquer execução sem `SPRING_PROFILES_ACTIVE` definida (inclusive puxa
esse valor pro Git se for commitado sem querer). Sempre use a variável de
ambiente pra alternar; o arquivo deve continuar com `dev` fixo.

**Mesma armadilha, outra propriedade:** `server.port` também não pode
depender de uma variável tipo `${SERVER_PORT:8080}` no `application.properties`
base — qualquer `SERVER_PORT` presente no `.env` teria prioridade *maior*
que `application-prod.properties` (mesmo mecanismo de precedência do
`spring.profiles.active`, via *relaxed binding* do Spring Boot entre
`SERVER_PORT` e `server.port`), sobrescrevendo silenciosamente o `8443` do
prod pelo valor do `.env`. Por isso `server.port` agora é fixo por perfil
(`8080` no base/dev, `8443` só em `application-prod.properties`), sem
nenhuma variável de `.env` envolvida. Isso foi detectado durante o teste
manual do Grupo 7: o banner mostrava `HTTPS, porta 8080` — SSL habilitado
corretamente, mas porta presa no valor do `.env`.

### Nota prática: IntelliJ mostrando `application.properties` com acentos corrompidos

Se esse arquivo aparecer com acentos corrompidos (`nÃ£o`, `variÃ¡vel`, etc.)
dentro da IntelliJ mesmo com os bytes do arquivo corretos em UTF-8 (confirmável
lendo o arquivo fora da IDE) e com todas as configurações de encoding da IDE já
certas (Settings → Editor → File Encodings — Global, Project e a seção
"Properties Files" todas em UTF-8), **o problema não está no arquivo nem nas
configurações**: é a IntelliJ mantendo, só para aquele arquivo específico, uma
decisão de encoding cacheada de quando ele genuinamente esteve em ISO-8859-1
(o `application.properties` já teve esse problema real antes de ser corrigido
— ver decisão de design abaixo). Essa decisão por arquivo tem prioridade sobre
qualquer configuração padrão e não é reconsultada automaticamente.

Correção: com o arquivo aberto, clique no indicador de encoding no canto
inferior direito da janela → **File → Invalidate Caches → Invalidate and
Restart**. (Trocar o encoding pelo indicador e escolher "Reload" pode
resolver em alguns casos, mas nesse projeto só o Invalidate Caches funcionou
de fato.) Vale saber disso caso outra pessoa abra o projeto numa instalação
diferente da IntelliJ e veja o mesmo sintoma.

### Resetando o banco do zero

Para um ambiente limpo (útil durante o desenvolvimento), derrube as tabelas e o controle do Flyway antes de reiniciar:

```sql
DROP TABLE sr_audit_logs PURGE;
DROP TABLE sr_historico_consultas PURGE;
DROP TABLE sr_fichas_tecnicas PURGE;
DROP TABLE sr_config PURGE;
DROP TABLE sr_refresh_tokens_usados PURGE;
DROP TABLE sr_usuarios PURGE;

DROP TABLE "flyway_schema_history_llm" PURGE;
```

> **Nota histórica (Fase C):** até a migração pra `GENERATED ... AS IDENTITY`
> (ver [decisão de portabilidade Oracle/H2](#fase-c--decisões-de-portabilidade-e-robustez)),
> V1/V2/V3/V4/V6 criavam sequences manuais (`seq_usuario_id`, `seq_ficha_id`,
> `seq_historico_id`, `seq_audit_id`, `seq_config_id`), que também precisavam
> ser dropadas (`DROP SEQUENCE nome;`, uma por linha) antes de resetar. Como
> as migrations atuais não criam mais nenhuma sequence, esse passo não é
> mais necessário — só relevante pra quem ainda está num schema anterior a
> essa migração de padrão.

## Estrutura de dados

| Tabela | Propósito |
|---|---|
| `sr_usuarios` | Usuários do sistema (ANALYST/ADMIN) — inclui `nome`, email, senha (BCrypt), role, status ativo/inativo |
| `sr_fichas_tecnicas` | Cache de especificações por veículo (`marca`+`modelo`+`versao`) — `campos_json` cifrado em AES-256-GCM; inclui controle de reverificação periódica |
| `sr_historico_consultas` | Registro de toda consulta feita (cache hit ou miss), para auditoria de uso |
| `sr_audit_logs` | Log de segurança (login, falhas, ações administrativas) — identificação do usuário pseudonimizada via HMAC-SHA256 |
| `sr_config` | Configuração editável em runtime pelo ADMIN — atributos padrão do chat, intervalo de reverificação |
| `sr_refresh_tokens_usados` | Refresh tokens já rotacionados — impede reuso mesmo antes da expiração natural |

Índice único (case-insensitive) em `sr_fichas_tecnicas(marca, modelo, versao)` impede ficha duplicada para o mesmo veículo.

## Endpoints da API

### Autenticação (`/api/v1/auth`)
| Método | Endpoint | Descrição |
|---|---|---|
| POST | `/login` | Autentica e retorna par access+refresh token |
| POST | `/refresh` | Rotaciona o par de tokens (refresh token de uso único — ver [Segurança](#segurança)) |

### Especificações (`/api/v1/specs`)
| Método | Endpoint | Papel exigido | Descrição |
|---|---|---|---|
| POST | `/query` | ANALYST, ADMIN | Consulta especificações (cache ou LLM); aceita header opcional `Idempotency-Key` |
| GET | `/{marca}/{modelo}/{versao}` | ANALYST, ADMIN | Busca ficha já armazenada, sem chamar o LLM |
| GET | `/compare` | ANALYST, ADMIN | Compara dois veículos campo a campo |
| GET | `/history` | ANALYST, ADMIN | Lista fichas com filtro opcional de marca/modelo |
| DELETE | `/{id}` | ADMIN | Remove uma ficha técnica |
| GET / PUT | `/config` | ADMIN | Lê/atualiza atributos padrão e intervalo de reverificação |
| POST | `/from-pdf` | ANALYST, ADMIN | Extrai specs de um PDF anexado (multipart); mesmo fluxo cache-primeiro do `/query`; orçamento de rate limit próprio (10/min); ver [limitação de confiabilidade multimodal](#grupo-9--investigação-de-confiabilidade-multimodal-evidência-completa) |
| POST | `/chat/message` | ANALYST, ADMIN | Extração de intenção em linguagem natural (marca/modelo/versão/atributos por palavra-chave) |

### Usuários (`/api/v1/usuarios`) — todos exclusivos de ADMIN
| Método | Endpoint | Descrição |
|---|---|---|
| GET | `/` | Lista usuários |
| GET | `/{id}` | Busca usuário por id |
| POST | `/` | Cria usuário |
| PUT | `/{id}` | Atualiza nome/email/role |
| DELETE | `/{id}` | Desativa (reversível) |
| PATCH | `/{id}/reativar` | Reverte a desativação |
| PATCH | `/{id}/anonimizar` | Remove dado pessoal (**irreversível**, LGPD) |

## Segurança

Resumo do que está implementado hoje (detalhes de cada decisão em [Decisões de design](#decisões-de-design-registradas)):

- **RBAC real** — ANALYST e ADMIN têm permissões efetivamente diferentes, checadas via `@PreAuthorize`.
- **Senhas** — hash com BCrypt.
- **Dados sensíveis em repouso** — `campos_json` das fichas técnicas cifrado com AES-256-GCM (autenticado).
- **Pseudonimização** — identificação de usuário nos logs de auditoria via HMAC-SHA256 com salt secreto (não SHA-256 puro, vulnerável a força bruta em IDs sequenciais).
- **Bloqueio de conta por força bruta** — 5+ falhas de login em 10 min bloqueiam a conta por 30s; detecção não revela se o email existe ou não.
- **Rate limiting em duas camadas independentes** — por IP (`RateLimitFilter`, nível de filtro servlet) e por usuário autenticado (dentro de `SpecService`).
- **`X-Forwarded-For` não confiado por padrão** — só é considerado se a conexão direta vier de um IP cadastrado como proxy confiável (vazio por padrão, já que não há proxy reverso real na frente hoje).
- **Idempotência** — índice único impede ficha duplicada mesmo sob concorrência; header opcional `Idempotency-Key` evita reprocessar uma requisição repetida.
- **Refresh token de uso único** — cada rotação invalida permanentemente o token anterior (ver tabela `sr_refresh_tokens_usados`).
- **HTTPS/TLS** — perfil `prod` roda exclusivamente em HTTPS (porta 8443), com certificado self-signed PKCS12 (RSA 2048, `SHA384withRSA`, gerado via `keytool`, arquivo local nunca commitado); perfil `dev` continua em HTTP puro (porta 8080) para facilitar o desenvolvimento local.
- **LGPD** — endpoint de anonimização (irreversível) e desativação (reversível) de usuário; travas contra auto-anonimização e auto-desativação.
- **Charset UTF-8 explícito** em respostas de erro.

## Roadmap do projeto

O plano de trabalho é dividido em 4 fases. Abaixo, o estado de cada uma.

### Fase A — Validar que funciona de verdade — ✅ CONCLUÍDA

Testes funcionais manuais (via Insomnia), incluindo:
- Teste isolado da chave do Gemini fora da stack, confirmando chave válida e cota disponível.
- Login como ANALYST e ADMIN.
- `POST /specs/query` com a Ford Ranger Raptor (o caso de validação oficial do brief da Ford) — cache miss confirmado com chamada real ao Gemini; segunda consulta confirmando cache hit.
- `findByVeiculo`, `compare`, `history` (com e sem filtro), stub `501` do `from-pdf`, `chat/message`.
- Caminhos de erro: `401` (sem token, expirado, manipulado), `400`/`422` (regex de marca/modelo, mais de 20 atributos, mensagem de chat fora do intervalo), `404` (veículo nunca consultado), `429` nos dois mecanismos de rate limit (IP e usuário, independentes).
- `POST /auth/refresh` usado duas vezes com o mesmo token — **confirmou o gap** que motivou o Grupo 6 (rotação não invalidava o token antigo).

### Fase B — Confrontar com os requisitos e corrigir — ✅ CONCLUÍDA (Grupos 1–9 de 9)

Checklist contra os requisitos formais de Cybersecurity/SOA (documento com pontuação + material gamificado), e contra a proposta do grupo (`SpecRadar_Challenge_Ford.pdf` + `CHALLENGE_FORD.pdf`). Os achados foram organizados em 9 grupos por risco/dependência:

| Grupo | Escopo | Status |
|---|---|---|
| 1 | Correções triviais — arredondamento do retry-after, `HttpMessageNotReadableException`, configs de rate limit separadas, `400` vs `422` | ✅ Concluído |
| 2 | `AuditService` plugado no `AuthController` (ativa detecção de força bruta), RBAC real nos endpoints administrativos | ✅ Concluído |
| 3 | AES-256-GCM em dados sensíveis, pseudonimização reforçada (HMAC-SHA256), `UsuarioController` novo com CRUD + anonimização/desativação | ✅ Concluído |
| 4 | Idempotência (índice único + header `Idempotency-Key`), `X-Forwarded-For` não confiado por padrão | ✅ Concluído |
| 5 | `vencedor` do compare (comparação real por tipo de campo), `sugestoes_similares` no 404 — **escopo cresceu durante a execução** para incluir cache sempre completo (busca padrão na criação + completude sob demanda) e reverificação periódica de fichas | ✅ Concluído |
| 6 | Revogação real de refresh token (tabela de tokens usados, identificados por `jti`) | ✅ Concluído |
| 7 | HTTPS/TLS — portar o padrão já usado em outro projeto do mesmo aluno (perfil `prod`, certificado self-signed) | ✅ Concluído — testado (dev em HTTP:8080, prod em HTTPS:8443, login funcionando via Insomnia) |
| 8 | Grounding real de busca no Gemini (item 1) e sensor de demanda preditiva (item 2) — **investigados e descartados**, não por falta de esforço de design mas por limitação real confirmada da conta/tier (ver evidência abaixo) | ✅ Avaliado e descartado — ver [evidência dos 7 testes](#grupo-8--investigação-de-grounding-evidência-completa) |
| 9 | `POST /specs/from-pdf` implementado (multipart, cache-primeiro, rate limit próprio de 10/min, magic-bytes + content-type validados antes de gastar chamada multimodal, mensagem de erro específica no 503) | ✅ Concluído — testado (arquivo inválido → 422, arquivo grande → 413, cache hit/miss com PDF texto → 200, PDF com foto → 503 com mensagem específica, rate limit 429 independente do `/query` confirmado na prática, RBAC ANALYST+ADMIN) — ver [evidência da investigação de multimodal](#grupo-9--investigação-de-confiabilidade-multimodal-evidência-completa) |

### Fase C — Decisões de portabilidade e robustez — ✅ CONCLUÍDA (schema portável, perfil `dev-h2` e suíte de testes unitários — 171/171 — todos concluídos e testados)

**Decisão sobre banco de desenvolvimento/teste — não é H2.** Avaliamos 3
caminhos pra não depender do Oracle FIAP em testes: H2 em memória,
Testcontainers com Oracle real, e manter Oracle real. Mapeamento das 9
migrations mostrou que **6 delas** (V1, V2, V3, V4, V6 — bloco PL/SQL de
criação defensiva de sequence, que H2 não suporta — e V8 — `ALTER TABLE ...
ADD (...)`/`MODIFY (...)`, sintaxe Oracle sem equivalente direto em H2) não
rodariam em H2 como estavam escritas. H2 exigiria manter duas árvores de
migrations em paralelo pra sempre (toda migration nova, escrita duas vezes);
Testcontainers rodaria as migrations originais sem nenhuma duplicação, mas
com container Oracle real (mais pesado, mais lento de subir).

> **Correção posterior:** esse mapeamento de "6 migrations problemáticas"
> foi feito por leitura/grep, não por boot real contra H2 — e ficou
> incompleto. Só ao efetivamente subir a aplicação contra um H2 de verdade
> (implementação do perfil `dev-h2`, abaixo) apareceu que a **V7** também
> não era portável (índice funcional `UPPER(...)` — H2 não aceita expressão
> nenhuma na lista de colunas de um `CREATE INDEX`, só identificador simples).
> Total real: **7 de 9**, não 6. Fica registrado como lição: análise estática
> de compatibilidade SQL entre bancos tem limite — o teste que importa é
> subir de verdade.

**Caminho escolhido: nenhum dos três — reescrever as 6 migrations num
formato já portável (`GENERATED ... AS IDENTITY`, ANSI SQL, suportado nativamente
por Oracle 12c+ e H2), inspirado no padrão que o Código 1 já usa e comprova
funcionando.** Isso elimina a necessidade de duplicar migrations (H2) e de
manter infraestrutura de container (Testcontainers) — as migrations
continuam sendo um único conjunto de arquivos, rodando sem modificação nos
dois bancos.

O que isso mudou:
- **6 migrations reescritas** (V1, V2, V3, V4, V6, V8) — sequence manual +
  bloco PL/SQL defensivo removidos, coluna `id` passa a usar
  `GENERATED BY DEFAULT AS IDENTITY`. V8 também simplificado: a versão
  original fazia `ADD` (nullable) → `UPDATE` → `MODIFY` NOT NULL, desenhada
  pra uma tabela já populada em produção; como `sr_fichas_tecnicas` está
  sempre vazia nesse ponto da sequência de migrations numa reaplicação do
  zero, virou um único `ADD ... DEFAULT ... NOT NULL` — mesmo estado final,
  sem precisar de `MODIFY`/`ALTER COLUMN` (que não têm sintaxe equivalente
  direta entre Oracle e H2).
- **5 entidades JPA** (`Usuario`, `FichaTecnica`, `HistoricoConsulta`,
  `AuditLog`, `Config`) trocam `@GeneratedValue(strategy = SEQUENCE, ...)` +
  `@SequenceGenerator` por `@GeneratedValue(strategy = IDENTITY)`.
  `RefreshTokenUsado` (chave natural `jti`) não usa sequence, não foi afetada.
- **`spring.jpa.hibernate.ddl-auto` continua `validate`** — decisão explícita
  de não afrouxar essa checagem, mesmo que isso exponha atrito que o Código
  1 (que usa `none`) nunca teve chance de encontrar.
- **Reset feito duas vezes, como previsto**: uma vez pra confirmar boot limpo
  contra o schema atual antes da reescrita, outra depois de reescrever as
  migrations (editar migration já aplicada quebra checksum do Flyway) — as
  duas confirmadas, `flyway validate` e `ddl-auto=validate` passando sem
  atrito, as 9 migrations aplicando limpo contra o Oracle real (19.3) as
  duas vezes.
- **Concorrência real retestada com `IDENTITY` — ✅ confirmada pra
  `sr_usuarios`, aceita por analogia pra `sr_fichas_tecnicas`:**
  - **`sr_usuarios` (e-mail duplicado): reproduzida de verdade** — 18ms de
    intervalo entre as duas requisições, resultado `201` + `409`, nunca
    `500`. Comportamento notavelmente mais limpo que a rodada com
    `SEQUENCE`: nenhuma stack trace de "erro não tratado" no log — a
    sequência foi direto `ORA-00001` → `WARN` do `GlobalExceptionHandler` →
    `EmailJaCadastradoException` resolvida → `409`, sem escapar do método.
    **Ressalva importante**: como `saveAndFlush()` nunca foi removido do
    código (decisão de manter por segurança, ver acima), esse teste
    confirma que **a correção continua funcionando** depois da troca —
    não isola se `IDENTITY` sozinho (sem `saveAndFlush`) já bastaria. Essa
    curiosidade teórica fica em aberto de propósito — não muda nada na
    prática, já que `saveAndFlush()` continua sendo mantido de qualquer
    jeito.
  - **`sr_fichas_tecnicas` (ficha duplicada): não reproduzida por
    concorrência real** — mesmo obstáculo da rodada anterior (instabilidade
    do Gemini interceptando antes das duas chamadas concorrerem pelo mesmo
    INSERT), pela segunda vez. **Aceito por analogia de mecanismo**: a
    tradução de violação de constraint única em `DataIntegrityViolationException`
    pelo Hibernate é ortogonal à estratégia de geração de ID e à coluna/índice
    específico violado — o mesmo raciocínio que já tinha sido aceito (e
    confirmado correto) na rodada com `SEQUENCE` se aplica aqui, agora
    reforçado por uma segunda confirmação direta (o teste de `sr_usuarios`
    acima) de que o padrão generaliza de forma confiável entre as duas
    entidades.
- Implementação de testes unitários automatizados — ver seção dedicada logo abaixo (concluída).

**Perfil `dev-h2` — ✅ Concluído e testado.** O objetivo original da Fase C
incluía um perfil de desenvolvimento com H2 real (Console incluso), pra quem
não tem acesso ao Oracle FIAP conseguir rodar o projeto localmente — igual o
Código 1 já faz. A portabilidade do schema (acima) era pré-requisito, não o
objetivo em si.

**Decisão de timing:** `application-dev.properties` **continua Oracle por
enquanto** — desenvolvimento ainda ativo, toda a rotina de teste manual
depende dele, e trocar isso agora seria risco desnecessário. Em vez de
substituir, foi criado um perfil **adicional**, `dev-h2` (H2 em memória +
Console), como opção paralela. A virada de verdade — `dev` virar H2, igual o
Código 1 — fica **planejada pro fim do projeto**, quando o código estiver
mais estável. Registrado também como comentário no código
(`SprintFordApiApplication`, bloco do banner) pra não se perder de vista.

O que foi implementado:
- `pom.xml` — dependência `com.h2database:h2` (`scope=runtime`).
- `application-dev-h2.properties` (novo) — datasource H2 em memória
  (`jdbc:h2:mem:specradar;DB_CLOSE_DELAY=-1`), dialeto e schema padrão
  sobrescritos para H2 (`H2Dialect`/`PUBLIC` — a base aponta pra
  `OracleDialect`/`RM558540`), Console habilitado em `/h2-console`, resto
  espelhando o perfil `dev` (SQL verboso, log DEBUG, SSL desabilitado).
- `SecurityConfig` — `SecurityFilterChain` dedicada (`@Order(1)`,
  `securityMatcher` só pra `/h2-console/**`) com CSRF desabilitado e
  `X-Frame-Options: SAMEORIGIN` restritos a esse path — a chain principal
  continua com `DENY`/CSRF normais pro resto da API.
- Banner de inicialização (`SprintFordApiApplication`) — linha "Banco:"
  reflete H2 quando o perfil ativo é `dev-h2` (antes fixa em "Oracle FIAP");
  bloco novo só nesse perfil com o link do Console e as credenciais de
  conexão (JDBC URL, usuário `sa`, senha em branco).

**Três problemas reais, todos só descobertos ao subir a aplicação de
verdade contra H2 — nenhum apareceria numa análise estática do código:**
1. **Log completamente mudo no boot** (só o banner ASCII + um `WARN` do
   `jboss-logging`, nada mais, indistinguível de uma trava). Causa:
   `logback-spring.xml` só tinha `<springProfile name="dev">` e
   `name="prod"` — nenhum bloco casava com `dev-h2`, então zero appenders
   eram configurados (Logback descarta tudo silenciosamente nesse caso,
   emitindo só aquele `WARN` interno). Confirmado que a aplicação
   continuava rodando e funcional por baixo (testado via `curl` direto —
   `/api/v1/auth/login` respondia normalmente) mesmo sem nenhum log
   visível. Fix: `<springProfile name="dev,dev-h2">`.
2. **V7 não portável** (ver correção acima) — índice funcional reescrito
   como 3 colunas computadas (`GENERATED ALWAYS AS (UPPER(...))`, sem a
   palavra-chave `VIRTUAL` — testado ao vivo contra o Oracle real da FIAP
   *antes* de assumir que seria opcional lá, já que H2 exige que seja
   omitida) + índice único simples sobre elas, em vez do índice direto
   sobre a expressão.
3. **`ddl-auto=validate` rejeitava a coluna `id` (e mais 5 colunas
   `NUMBER(n)`) só sob H2** — `NUMBER`/`NUMBER(n)` do Oracle e H2 sempre
   reportam como `NUMERIC` via JDBC, mas o `H2Dialect` espera `BIGINT`
   (`Long`)/`INTEGER` (`Integer`) por padrão para esses tipos Java — o
   `OracleDialect` já esperava `NUMERIC`, por isso isso nunca deu problema
   nos dois resets anteriores contra o Oracle real. Fix: `@JdbcTypeCode(SqlTypes.NUMERIC)`
   em 6 campos (`id` das 5 entidades com `IDENTITY`, mais
   `HistoricoConsulta.tempoRespostaMs`, `AuditLog.statusResposta`, e os dois
   `intervaloReverificacaoDias` de `Config`/`FichaTecnica`) — força
   Hibernate a validar como `NUMERIC` nos dois bancos, sem mudar nenhuma
   migration.
4. **H2 Console retornava `401` mesmo com a chain dedicada e `permitAll`
   configurados** — o `securityMatcher("/h2-console/**")` baseado em string
   resolve por padrão via `MvcRequestMatcher` (dispatch do Spring MVC), mas
   o H2 Console é um Servlet puro, registrado fora do `@RequestMapping` —
   o matcher nunca reconhecia o caminho e a requisição caía na chain
   principal. Fix: `securityMatcher(new AntPathRequestMatcher("/h2-console/**"))`
   explícito.

**Testado — evidência real, 3º reset do schema Oracle** (a reescrita da V7
muda o checksum Flyway da migration; o projeto evita `flyway repair`, então
reset completo de novo):
- Perfil `dev` (Oracle) pós-reset: boot limpo, 9 migrations aplicando do
  zero, login com `admin@specradar.com` funcionando.
- Perfil `dev-h2`: boot limpo, 9 migrations aplicando (V7 reescrita
  incluída), H2 Console acessível em `/h2-console` com as 7 tabelas
  visíveis, login funcionando via navegador e via Insomnia. Log de bind
  confirmando `NUMERIC` nos dois bancos.
- Banner: `Perfil ativo: DEV-H2`, `Banco: H2 em memória (usuário: sa)`,
  bloco do Console (`H2 Console`/`JDBC URL`/`Usuário`) aparecendo só nesse
  perfil.

**Testes unitários automatizados — ✅ Concluído. 171/171 passando, 0
falhas.** Suíte no padrão JUnit Platform Suite (`@Suite`+`@SelectPackages`),
espelhando a estrutura já usada no Código 1: classe central
`com.icers.ford.SuiteDeTestesGeral` na raiz de `src/test/java`, um arquivo
de teste por classe de produção, organizado nas mesmas pastas de pacote de
`src/main/java`. Todos os repositories mockados via Mockito — **nenhum
teste toca o Oracle real da FIAP**; o único teste que sobe um `ApplicationContext`
de verdade (`SprintFordApiApplicationTests`, o smoke test padrão do Spring
Initializr) foi corrigido com `@ActiveProfiles("dev-h2")` para nunca
apontar pro Oracle.

11 classes de produção cobertas: `AesEncryptionService`, `JwtService`,
`CamposJsonEncryptedConverter`, `IdempotencyService`, `LoginLockoutService`,
`ConfigService`, `UsuarioService`, `AuditService`, `GlobalExceptionHandler`,
`ChatService`, `SpecService` (a maior e mais complexa — cache
hit/miss/parcial/expirada, concorrência, rate limiting, comparação entre
veículos, escrita dividida em 3 partes ao longo da sessão por causa do
tamanho).

Dois testes de **regressão** travando bugs reais já corrigidos nesta mesma
sessão de trabalho (achados extras, fora dos 9 grupos da Fase B):
- `UsuarioServiceTest` — a race condition de e-mail duplicado
  (`GenerationType.SEQUENCE`/`IDENTITY` + `save()` só alocava o ID, o
  INSERT real acontecia tarde demais pro `catch` pegar — corrigido com
  `saveAndFlush()`). Simula `saveAndFlush()` lançando
  `DataIntegrityViolationException` e confirma que vira `409`
  (`EmailJaCadastradoException`), nunca um `500`.
- `AuditServiceTest` — os placeholders fixos inválidos de
  `logAdminAction` (`"POST/PUT/DELETE"`/`"/api/v1/admin/**"`, que violavam
  o `CHECK` de `metodo_http` e nunca gravavam nada de verdade). Confirma
  que os valores gravados são exatamente os parâmetros reais recebidos do
  controller.

**Gaps conhecidos, documentados como `// TODO` nos próprios arquivos de
teste** (decisão consciente, não esquecimento — registrado pra não se
perder de vista):
- `IdempotencyServiceTest` e `LoginLockoutServiceTest` não cobrem a
  expiração real de suas entradas em memória (24h e 30s, respectivamente) —
  sem um `Clock` injetável nas classes de produção, a única forma seria
  reflection nos `Instant` internos (frágil em `IdempotencyService`, que
  guarda um record privado aninhado; mais simples em `LoginLockoutService`,
  que usa um `Map<String, Instant>` direto). Decisão: não vale o esforço
  agora — reconsiderar junto com uma eventual refatoração pra `Clock`
  injetável, como melhoria de design separada.
- `ChatServiceTest` cobre a extração de intenção por amostragem
  representativa, não as ~44 palavras-chave nem todas as marcas/modelos do
  mapa hardcoded — o próprio reconhecimento por keyword já é um gap de
  design documentado (ver achados extras da Fase B), não algo que a
  cobertura de teste devesse mascarar como "resolvido".

### Fase D — Fechamento — ⏳ NÃO INICIADA

- Montagem da versão final deste README — arquitetura definitiva, como rodar, decisões de segurança — depois de tudo estabilizado.
- **Revisão geral e melhorias finais.**
  - **Gap do `ChatService`** (reconhece intenção só por palavras-chave hardcoded, não cobre consultas comparativas cruzando o histórico — ex.: "qual pick-up concorrente tem mais torque abaixo de R$300 mil", do exemplo de "modo chat" da proposta original): adiado pra cá **por priorização de tempo, não por dependência técnica** — nada bloqueia resolver isso antes, só não é a prioridade agora.
  - **Grupo 8 (grounding) — possível revisão condicionada ao tier pago**: se o Gemini pago for usado pra apresentação da banca, a cota zero de grounding (que motivou o "avaliado e descartado" do Grupo 8) deixa de se aplicar — vale reavaliar o item 1 (grounding real) nesse caso. **O status atual do Grupo 8 continua correto pro tier gratuito de hoje** — essa nota só registra a condição que mudaria essa decisão, não muda o status agora.
- Conferência contra os requisitos da Sprint 3 — que é, na prática, a última sprint com requisitos técnicos específicos por matéria (a Sprint 4 será só um vídeo pitch, sem entrega de código, com nota distribuída para todas as disciplinas).
- Espaço reservado para a versão completa do fluxo conversacional de identificação de veículo por "Ano" (ver [limitações](#limitações-e-itens-propositalmente-adiados)).

## Decisões de design registradas

Decisões que exigiram discussão e trade-offs explícitos ao longo do desenvolvimento — registradas aqui para não se perderem:

- **Chaves simétricas (AES/HMAC) via `.env`**, mesmo padrão já usado para `JWT_SECRET` — nunca commitadas, geradas com `openssl rand -base64 32`.
- **Índice único de idempotência usa `UPPER(marca, modelo, versao)`**, não uma `UNIQUE` simples — a aplicação já trata essas colunas como case-insensitive em toda consulta; uma constraint comum deixaria essa mesma brecha aberta.
- **`X-Forwarded-For` com lista de proxies confiáveis vazia por padrão** — sem proxy reverso real na frente hoje, a postura segura é nunca confiar no header, só habilitando por IP explicitamente cadastrado se/quando isso mudar.
- **Revogação de refresh token via tabela de tokens usados (`jti`), não contador de versão no usuário** — o problema é rotação individual (cada refresh deveria matar só aquele token específico), não revogação em massa; um contador de versão resolveria um problema diferente.
- **`vencedor` do compare só compara `potencia`, `torque`, `aceleracao`, `preco`, `consumo`** — cada um com extração por regex da unidade esperada (torque aceita `Nm` e `kgfm`, convertendo pra base comum). Os demais campos (`motor`, `transmissao`, `tracao`, `amortecedores`, `modos_conducao`, `farois`, `rodas_pneus`, `dimensoes`, `modos_volante`, `modos_escapamento`, `modos_amortecedor`) são descritivos ou multivalorados — comparação numérica produziria um resultado tão arbitrário quanto o bug original que motivou a correção. `transmissao` foi deliberadamente deixado de fora por ora (mais marchas nem sempre é "melhor").
- **Cache sempre busca pelo menos o conjunto de atributos padrão na primeira consulta de um veículo** (mesmo se o usuário pediu menos) — evita que uma pergunta estreita deixe o cache incompleto para consultas futuras mais amplas do mesmo veículo.
- **Reverificação periódica é preguiçosa** (só na próxima consulta daquele veículo específico), não uma varredura agendada em massa — evita custo desnecessário de reverificar veículos que ninguém está mais consultando. Intervalo configurável pelo ADMIN (2–31 dias), travado por ficha na criação e **renovado** para o valor global vigente a cada reverificação (não travado para sempre).
- **`bulk-import` (endpoint de ADMIN mencionado na proposta original) avaliado e descartado** — sem especificação suficiente em nenhum documento de referência para implementar com segurança.
- **Troca de perfil `dev`/`prod` via variável de ambiente real (`SPRING_PROFILES_ACTIVE`), não via `.env`** — a biblioteca `springboot3-dotenv` roda como o `EnvironmentPostProcessor` de menor prioridade do Spring Boot, ou seja, depois que o `ConfigDataEnvironmentPostProcessor` (altíssima prioridade) já decidiu qual `application-{perfil}.properties` carregar. Confirmado via decompilação da biblioteca durante o teste do Grupo 7 (HTTPS/TLS): o valor de `SPRING_PROFILE` no `.env` só afeta o que `Environment.getProperty()` retorna depois que a aplicação já subiu, nunca a decisão real de bootstrap. Ver [Como rodar o projeto](#trocando-entre-os-perfis-dev-e-prod).
- **`pom.xml` declara `UTF-8` explicitamente** (`project.build.sourceEncoding`/`project.reporting.outputEncoding`) — sem isso, compilação e cópia de resources ficam à mercê do encoding padrão do ambiente que builda, o que já causou corrupção de acentos tanto em `application.properties` (achado extra original) quanto, mais seriamente, em strings compiladas de `.java` (`OpenApiConfig`, texto do Swagger) quando compilado com encoding diferente de UTF-8.
- **Grupo 8 (grounding + sensor de demanda) — investigado e descartado, não implementado.** Ver seção dedicada [Grupo 8 — Investigação de grounding (evidência completa)](#grupo-8--investigação-de-grounding-evidência-completa) logo abaixo — motivo, evidência dos testes e decisão.
- **Requisito de compliance do Google (grounding), registrado para o dia em que houver faturamento habilitado**: resposta "grounded" exige exibir o elemento de atribuição (`searchEntryPoint` — "Google Search Suggestions") — é obrigação de uso da funcionalidade do Gemini, não sugestão. Seria responsabilidade de quem *exibe* a ficha ao usuário final (o app mobile), não deste backend. Fica registrado aqui mesmo sem implementação atual, para não virar surpresa de compliance se o grounding for revisitado depois.
- **`/specs/from-pdf` reaproveita o mesmo fluxo cache-primeiro do `/query`** via um método privado compartilhado (`resolverComCache`, em `SpecService`), parametrizado por uma função que decide COMO buscar os atributos que faltam (texto vs. PDF) — evita duplicar ~100 linhas de lógica de cache hit/expirada/parcial/miss entre os dois endpoints, sem alterar o comportamento já testado de `/query`.
- **`/specs/from-pdf` tem bucket de rate limit PRÓPRIO e mais restrito (10/min) que `/query` (60/min)**, não compartilhado — chamada multimodal é bem mais cara (payload maior, timeout maior). A validação do arquivo (content-type + assinatura `%PDF-`) roda ANTES do rate limit, no mesmo espírito de `/query` (onde a Bean Validation também roda antes) — um arquivo inválido nunca chega perto de custar uma chamada ao Gemini, então não faz sentido gastar esse orçamento escasso nele; quem protege contra martelamento bruto de requisições (válidas ou não) é o `RateLimitFilter` por IP, em outra camada.
- **RestTemplate dedicado para chamadas multimodais (`restTemplatePdf`), com timeout maior (120s vs. 60s do texto)** — investigação do Grupo 9 mostrou respostas multimodais demorando bem mais; um RestTemplate separado evita aumentar o timeout de TODAS as chamadas (inclusive texto) só para acomodar o caso mais lento.
- **Arquivo grande demais (`MaxUploadSizeExceededException`) retorna 413, não 422** — diferente de `ArquivoInvalidoException` (422, conteúdo inválido), aqui o problema é só tamanho; mesma precisão de status HTTP que o resto da API já pratica (422 vs 400, por exemplo). A mensagem de erro injeta o limite configurado via `@Value(DataSize)` em vez de um número fixo no texto, para nunca ficar desatualizada se o limite mudar.
- **`IpResolver` migrado para `ChatController` e `RequestLoggingFilter`, e `resolverUsuario` extraído para `UsuarioResolver`** (achados extras fora dos 9 grupos) — os dois seguem o mesmo padrão: `RequestLoggingFilter` não é `@Component` (é instanciado manualmente em `FilterConfig`, igual ao `RateLimitFilter`), então recebe `IpResolver` por construtor em vez de injeção automática; `UsuarioResolver` (`@Component`, em `com.icers.ford.util`, mesmo pacote do `IpResolver`) substitui a injeção direta de `UsuarioRepository` em `ChatController`/`SpecController`/`UsuarioController` — confirmado antes de remover que esses três só usavam o repositório para essa finalidade. `AuthController` mantém `UsuarioRepository` próprio (uso diferente, login).
- **Bug real encontrado durante o teste do achado acima, corrigido junto: `AuditService.logAdminAction` nunca gravava log de ação administrativa nenhuma.** Usava placeholders fixos `"POST/PUT/DELETE"` (15 caracteres) e `"/api/v1/admin/**"` como método/endpoint — a coluna `metodo_http` é `VARCHAR2(10)` **com CHECK `IN ('GET','POST','PUT','PATCH','DELETE')`** desde a V4, então esse valor nunca poderia ter sido gravado (ORA-12899 por tamanho, e ainda violaria a CHECK por valor — ORA-02290). O `catch` genérico de `salvar()` engolia o erro silenciosamente (comportamento intencional: falha de audit não deve derrubar o fluxo principal), então as 6 ações administrativas que chamam `logAdminAction` — criar/atualizar/desativar/reativar/anonimizar usuário, e `DELETE /specs/{id}` — **nunca tiveram log de auditoria de verdade gravado no banco desde que foram implementadas**, mesmo a ação em si sempre funcionando normalmente (o `2xx` da resposta nunca refletiu essa falha). Corrigido passando `httpRequest.getMethod()`/`httpRequest.getRequestURI()` reais (já disponíveis em todos os 6 call sites) em vez dos placeholders. **Isso invalida o ✅ que a Parte 2 do checklist da Fase B deu para "Trilha de auditoria para ações críticas"** — só passou a ser verdade a partir desta correção; registros de auditoria de ações administrativas anteriores a ela não existem e não podem ser reconstituídos retroativamente. Confirmado em teste manual (log do servidor + `SELECT` direto em `sr_audit_logs`): `ADMIN_CRIAR_USUARIO` e `ADMIN_DELETE_FICHA` gravados com `metodo_http`/`endpoint` reais.
- **`status_resposta` de `logAdminAction` também era fixo em `200`, incorreto para `criar` (201) e para `desativar`/`reativar`/`anonimizar`/`deletar` (204)** — mesma classe do bug acima, encontrada ao conferir os registros do teste anterior direto no banco. Corrigida junto: `logAdminAction` agora recebe o status real como parâmetro, passado por cada um dos 6 call sites.
- **`SpecResponse` não expunha o `id` da ficha técnica — resolvido.** Descoberto ao testar `DELETE /specs/{id}`: sem acesso ao log bruto do servidor ou ao banco, não havia como saber qual `id` usar pra deletar uma ficha sabendo só marca/modelo/versão. Mapeamento prévio confirmou só 6 call sites, todos em `SpecService` (5 com `id` trivialmente disponível no escopo local, 1 exigindo mudar `salvarFicha()` de `void` pra `Long`) e nenhum outro chamador de `salvarFicha()` no projeto. `ChatService`/`from-pdf`/`IdempotencyService` cobertos automaticamente (reusam os mesmos métodos/objeto, sem serialização JSON intermediária no caso do idempotency). Campo `id` (novo, primeiro do record) adicionado a `SpecResponse` — mudança aditiva no contrato JSON, documentada automaticamente no Swagger via `@Schema`. **Testado de ponta a ponta**: cache miss e cache hit retornam o mesmo `id`; `history` lista os ids corretos; `compare` (não usa `SpecResponse` na raiz) sem regressão; `DELETE /specs/{id}` funcionando com o `id` vindo direto da resposta da API, sem precisar de log ou banco.
- **Race condition de e-mail duplicado em `UsuarioController` virava `500` genérico em vez de `409` — e a primeira tentativa de correção (só `try/catch` em volta de `save()`) não resolveu.** Descoberto ao criar dois usuários com o mesmo e-mail com 22ms de diferença: o check `existsByEmail()`/`findByEmail()` e o `save()` não são atômicos em `UsuarioService.criar()`/`atualizar()`, então duas requisições concorrentes passam no check antes de qualquer uma commitar, e a constraint única do banco rejeita a segunda com `ORA-00001`. Um primeiro fix envolveu `save()` num `try/catch(DataIntegrityViolationException)` — mas continuou dando `500` em teste real. **Causa raiz real:** `Usuario`/`FichaTecnica` usam `GenerationType.SEQUENCE` — o `save()` só aloca o ID da sequence, o **INSERT de verdade fica pendente pro flush/commit do Hibernate**, que só acontece depois que o método `@Transactional` já retornou (dentro de `JpaTransactionManager.doCommit()`), fora de qualquer `try/catch` do método. **Corrigido de verdade**: `saveAndFlush()` em vez de `save()` nos dois métodos, forçando o INSERT a executar de forma síncrona dentro do bloco `try`.
- **A mesma causa raiz existia em `SpecService.salvarFicha()`** (usado por `query()` no cache miss) — o `catch (DataIntegrityViolationException e)` do Grupo 5 pra corrida de criação de ficha **nunca foi validado sob concorrência real e tinha o mesmo defeito**: `fichaTecnicaRepository.save(ficha)` também só aloca o ID da sequence, sem forçar o INSERT. Corrigido junto, trocando por `saveAndFlush(ficha)`. **Isso corrige silenciosamente uma lacuna no que o Grupo 5 declarava como testado** — a "corrida de concorrência detectada" ali nunca tinha sido exercitada por concorrência de verdade até agora.
  **Status do teste: corrigido por mecanismo comprovado, não por reprodução direta da corrida real.** Três tentativas de reproduzir concorrência genuína em `POST /specs/query` esbarraram na instabilidade já documentada do Gemini (Grupos 8/9) — as duas chamadas competiam por responder antes (503 "alta demanda" ou JSON malformado), nunca chegando ambas ao `saveAndFlush()` ao mesmo tempo. Aceito como suficiente porque o mecanismo do fix (`GenerationType.SEQUENCE` + `saveAndFlush` forçando o INSERT síncrono) é idêntico, determinístico e já comprovado no Teste 1 (`UsuarioService`, com prova de log real) — não há lógica específica de `FichaTecnica` que mudaria esse comportamento; a diferença entre constraint simples (`email`) e índice único funcional (`UPPER(marca,modelo,versao)`) não afeta como o Hibernate traduz a violação em `DataIntegrityViolationException`.

## Grupo 8 — Investigação de grounding (evidência completa)

> **Nota para a Fase D:** esta seção deve sobreviver **integralmente** (tabela
> incluída) na reescrita final do README — é evidência técnica forte demais
> para virar um resumo genérico tipo "grounding não disponível". Copiar, não
> resumir.

**O que se queria fazer:** habilitar busca real (grounding) nas chamadas ao
Gemini via `tools: [{"google_search": {}}]`, endereçando o achado #4 da Fase B
(dados desatualizados/incorretos por falta de busca ao vivo — ex.: motor
errado da Ranger Raptor) e, em cima disso, um sensor de demanda preditivo
mínimo (item 2) que reverificasse proativamente uma watchlist pequena e
explícita de veículos, reaproveitando a reverificação periódica do Grupo 5 —
nunca fazendo scraping direto de terceiros, resolvendo assim a divergência
entre a `ideia-inicial.txt` (que descarta um pipeline de scraping, "Proposta
1B", por risco técnico) e os PDFs vigentes (que citam "sensor de demanda
preditiva" como diferencial já entregue, inclusive no slide 6 do pitch).

**Por que não foi implementado:** sete chamadas diretas à API do Gemini (fora
da stack, mesmo espírito do teste isolado da Fase A), com controles pareados
(mesmo modelo, com e sem a ferramenta de grounding), mostraram que a conta
atual (tier gratuito, sem faturamento habilitado) é **estruturalmente incapaz
de usar grounding hoje**:

| # | Modelo | `google_search` | HTTP | Resultado |
|---|---|:---:|:---:|---|
| A | `gemini-3.7-flash` | Sim | **429** | `RESOURCE_EXHAUSTED` — cota de grounding zerada pra geração Gemini 3 |
| B | `gemini-3.7-flash` | Não | 200 | Funciona normal (controle) |
| C | `gemini-2.5-flash` | Sim | **404** | `"no longer available to new users. Use gemini-3.6-flash"` |
| D | `gemini-3.6-flash` | Sim | **429** | `RESOURCE_EXHAUSTED` — mesmo padrão |
| E | `gemini-3.6-flash` | Não | 200 | Funciona normal (controle) |
| F | `gemini-2.0-flash` | Sim | **404** | `"no longer available. Use gemini-3.6-flash"` |
| G | `gemini-2.5-flash-lite` | Sim | **404** | `"no longer available to new users. Use gemini-3.5-flash-lite"` |

Um `GET /v1beta/models` confirmou que `gemini-2.5-flash`, `gemini-2.5-pro` e
`gemini-2.5-flash-lite` continuam **listados como ativos**, com
`generateContent` em `supportedGenerationMethods` — ou seja, não foram
removidos do catálogo, só bloqueados especificamente para contas/chaves
classificadas como "novas" (provavelmente pela data de criação do projeto/chave).

**A conclusão, batendo com o painel de rate limits do Google AI Studio**
(seção Ferramentas → Pesquisar conteúdo de embasamento: geração "Gemini 3"
mostra cota 0 pra grounding; "Gemini 2"/"Gemini 2.5" mostram 1.500/28 dias):
os únicos modelos que esta chave consegue efetivamente chamar (família
Gemini 3.x) têm cota de grounding **zero**; os únicos modelos com cota de
grounding disponível (família Gemini 2.x) estão **bloqueados por elegibilidade
de conta**. Não há combinação de nome de modelo que contorne isso — é uma
limitação estrutural da conta/tier atual, não um erro de configuração ou
escolha de modelo.

**Decisão (Caminho A — sem faturamento habilitado por agora):**
- **Item 1 (grounding):** não implementado. `LlmClient` continua sem
  `google_search`. O achado #4 da Fase B ("sem grounding real de busca")
  permanece registrado como limitação — agora com causa raiz **confirmada
  por teste**, não só suposição.
- **Item 2 (sensor de demanda preditiva):** avaliado e descartado, no mesmo
  padrão do `bulk-import` do Grupo 3 (decisão registrada, endpoint/feature
  não implementado por falta de base segura). O desenho do sensor dependia
  do grounding para evitar reabrir o risco de scraping que a proposta
  original já rejeitou (Proposta 1B); sem grounding disponível nesta conta,
  não existe caminho seguro de implementar o sensor sem reintroduzir esse
  mesmo risco.
- Se o faturamento for habilitado no futuro (Caminho B, não adotado agora),
  revisitar esta seção antes de reabrir o item 1 — a cota pode se comportar
  de forma diferente fora do tier gratuito.

## Grupo 9 — Investigação de confiabilidade multimodal (evidência completa)

> **Nota para a Fase D:** assim como a seção do Grupo 8, esta tabela deve
> sobreviver **integralmente** na reescrita final do README. Copiar, não
> resumir.

**Contexto:** antes de desenhar `POST /specs/from-pdf`, testamos (fora da
stack, mesmo espírito do teste isolado da Fase A e do Grupo 8) se envio de
PDF via `inlineData` no `generateContent` funciona de forma confiável nesta
conta — diferente do grounding do Grupo 8, isso é uma capacidade nativa do
modelo (multimodal), não uma *tool* separada com cota própria, então a
expectativa inicial era de que funcionasse sem surpresa.

**O que os testes mostraram — não exatamente o esperado:**

| # | Arquivo | Contém imagem embutida | Tentativas | Resultado |
|---|---|:---:|:---:|---|
| A | `FordV1-Alunos.pdf` (868 KB, documento real; não continha a ficha da Raptor) | Sim | 1 | **200 OK** |
| B | `FORD_apresentacao.pdf` (10 MB, deck completo de slides) | Sim | 4 | **503** × 4 |
| C | `ford-ranger-raptor.pdf` (foto real da Raptor, slide único extraído, 1,4 MB) | Sim | 7 | 1 timeout + **503** × 6 (incluindo repetição após 2 dias, cota diária já resetada) |
| D | Mesmo slide, recomprimido a 180 KB (resolução menor, mesma foto) | Sim | 3 | **503** × 3 |
| E | Specs da Raptor recriadas como **texto puro em fundo branco**, sem nenhuma imagem real | Não | 1 | **200 OK** — 100% dos campos batendo com o gabarito conhecido (motor, potência, torque, transmissão, tração, amortecedores, 0-100, modos, faróis, rodas/pneus; inclusive reproduziu fielmente o "R$499.00" do slide original, que tem um erro de digitação da Ford — evidência de que o modelo transcreve, não "corrige" por conta própria) |
| F | Imagem **sintética nova**, gerada do zero, sem nenhuma relação com o arquivo original + texto sobreposto | Sim | 1 | **503** |

**Conclusão (com o cuidado de não superafirmar causalidade):** PDF **sem
nenhuma imagem embutida** funcionou 100% das vezes (1/1) com extração
perfeita. PDF **com qualquer imagem embutida** (foto real, a mesma foto
recomprimida, ou uma imagem sintética sem relação nenhuma com o arquivo
original) falhou 15 de 16 vezes, incluindo depois de 2 dias de intervalo
(descartando sobrecarga transitória como explicação única) e com um arquivo
completamente novo (descartando corrupção específica daquele arquivo como
explicação única). **O que os testes provam é uma correlação forte entre
"presença de imagem embutida" e falha — não isolamos "textura fotográfica"
como causa exclusiva**, porque não testamos uma imagem sem nenhum texto
associado isoladamente. O único sucesso com imagem (`FordV1-Alunos.pdf`) foi
o primeiro teste desta investigação inteira, o que também é compatível com
alguma forma de degradação que começou depois dele e nunca se recuperou
durante os testes.

**Implicação de design, já implementada:** o endpoint `/specs/from-pdf` trata
essa falha como mais um caso de serviço externo indisponível — mesmo padrão
de `LlmUnavailableException`/`503` que o `LlmClient` já usa para qualquer
instabilidade do Gemini, não um caso especial — mas com uma mensagem de erro
específica avisando o analista que PDFs com fotos grandes têm chance de
falha maior que catálogos tabulares/texto (ver decisões de design abaixo).

**Teste manual — ✅ confirmado (7 cenários + 1 bônus):**
- Arquivo inválido (vazio/não-PDF) → `422`; arquivo acima do limite → `413`.
- PDF de texto puro: cache miss → `200` com specs corretas (`fonte: "PDF anexado"`,
  confiança `ALTA`); consulta seguinte → cache hit (`200`, ~260ms, sem chamar o Gemini).
  Uma tentativa intermediária bateu no mesmo `503` de "alta demanda" da
  investigação (confirmado no log do servidor — `HttpServerErrorException`
  genuíno vindo do Gemini, não um bug de implementação) — instabilidade
  conhecida, não do código; reiniciar e tentar de novo resolveu.
- PDF com foto → `503` com a mensagem específica de `/from-pdf` — resultado
  **esperado** para esse cenário (documenta a limitação, não uma falha a
  corrigir).
- Rate limit de `/from-pdf` (10/min) disparou `429` (variação de 11 pra 13
  tentativas é esperada — bucket4j usa refill contínuo/"greedy", não uma
  reposição em lote a cada minuto).
- **Independência dos buckets confirmada na prática, não só no código:**
  com `/from-pdf` travado em `429` (dentro da janela de "aguarde 3 segundos"
  reportada), uma chamada a `/query` ~2,5s depois respondeu `200` normal
  (cache hit) — o orçamento de `/query` não foi afetado.
- RBAC: ANALYST e ADMIN, ambos `200`. Auditoria gravou hash pseudonimizado
  diferente por usuário (Grupo 3) sem interferência com o Grupo 9.

## Limitações e itens propositalmente adiados

- **Sem grounding de busca real** — o Gemini responde com base no próprio conhecimento de treinamento, não com busca ao vivo. Isso já causou dados desatualizados ou levemente incorretos em alguns testes (ex: nome de motor). Investigado no Grupo 8 da Fase B e **confirmado como limitação estrutural da conta/tier atual** (ver [evidência completa](#grupo-8--investigação-de-grounding-evidência-completa)) — não implementado. **Possível revisão na Fase D** se o Gemini pago for adotado pra apresentação da banca (ver nota no roadmap da Fase D) — status atual continua correto pro tier gratuito de hoje.
- **Extração multimodal de PDF é instável quando o arquivo contém imagens embutidas** — confirmado por investigação dedicada do Grupo 9 (ver [evidência completa](#grupo-9--investigação-de-confiabilidade-multimodal-evidência-completa)); PDFs de texto/tabela funcionam de forma confiável.
- **Identificação de veículo por "Ano" não implementada** — avaliada em profundidade, mas até uma versão "simples" (campo opcional) esbarra na mesma pergunta de fundo sem resposta boa sem interação (o que "sem ano" deveria significar — o ano mais recente, ou uma categoria própria?). O fluxo conversacional completo (perguntar ao usuário, sugerir modelos, decidir "mais recente" automaticamente) foi **deliberadamente adiado para o fim da Fase D**, depois que o restante do sistema estiver mais maduro.
- ~~Sem testes automatizados~~ — **resolvido na Fase C**: 171 testes unitários (Mockito, sem tocar o Oracle real), 11 classes de serviço/segurança/exceção cobertas — ver [Fase C](#fase-c--decisões-de-portabilidade-e-robustez).
