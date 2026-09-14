# SpecRadar

> API de inteligência competitiva para especificações técnicas de veículos — desenvolvida para o desafio Ford × FIAP 2026.

**Status:** 🚧 Em desenvolvimento ativo — Fase B em andamento (Grupos 1–7 de 9 concluídos).
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

Para um ambiente limpo (útil durante o desenvolvimento), derrube tabelas, sequences e o controle do Flyway antes de reiniciar:

```sql
DROP TABLE sr_audit_logs PURGE;
DROP TABLE sr_historico_consultas PURGE;
DROP TABLE sr_fichas_tecnicas PURGE;
DROP TABLE sr_config PURGE;
DROP TABLE sr_refresh_tokens_usados PURGE;
DROP TABLE sr_usuarios PURGE;

DROP SEQUENCE seq_usuario_id;
DROP SEQUENCE seq_ficha_id;
DROP SEQUENCE seq_historico_id;
DROP SEQUENCE seq_audit_id;
DROP SEQUENCE seq_config_id;

DROP TABLE "flyway_schema_history_llm" PURGE;
```

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
| POST | `/from-pdf` | ANALYST, ADMIN | **Stub — retorna 501** (ver [roadmap](#roadmap-do-projeto), Grupo 9) |
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

### Fase B — Confrontar com os requisitos e corrigir — 🚧 EM ANDAMENTO (Grupos 1–7 de 9 concluídos)

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
| 8 | Grounding real de busca no Gemini; sensor de demanda preditiva (escopo mínimo viável a definir, evitando o risco de web scraping já identificado e descartado na proposta original do grupo) | ⏳ Pendente |
| 9 | `POST /specs/from-pdf` de verdade (hoje é stub `501`) | ⏳ Pendente |

### Fase C — Decisões de portabilidade e robustez — ⏳ NÃO INICIADA

- Avaliar adicionar um perfil de banco H2 em memória para desenvolvimento, para que outros integrantes do grupo não dependam do Oracle FIAP para rodar o projeto localmente. Decisão a ser tomada **antes** dos testes unitários (que não deveriam depender de um Oracle real).
- Implementação de testes unitários automatizados — só depois do comportamento estar validado manualmente (Fases A/B), para não testar um comportamento que ainda pode mudar.

### Fase D — Fechamento — ⏳ NÃO INICIADA

- Montagem da versão final deste README — arquitetura definitiva, como rodar, decisões de segurança — depois de tudo estabilizado.
- Revisão geral e melhorias finais.
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

## Limitações e itens propositalmente adiados

- **Sem grounding de busca real** — o Gemini responde com base no próprio conhecimento de treinamento, não com busca ao vivo. Isso já causou dados desatualizados ou levemente incorretos em alguns testes (ex: nome de motor). Endereçado no Grupo 8 da Fase B.
- **Identificação de veículo por "Ano" não implementada** — avaliada em profundidade, mas até uma versão "simples" (campo opcional) esbarra na mesma pergunta de fundo sem resposta boa sem interação (o que "sem ano" deveria significar — o ano mais recente, ou uma categoria própria?). O fluxo conversacional completo (perguntar ao usuário, sugerir modelos, decidir "mais recente" automaticamente) foi **deliberadamente adiado para o fim da Fase D**, depois que o restante do sistema estiver mais maduro.
- **`POST /specs/from-pdf` é stub** — retorna `501`, aguardando o Grupo 9.
- **Sem testes automatizados ainda** — Fase C, pendente.
