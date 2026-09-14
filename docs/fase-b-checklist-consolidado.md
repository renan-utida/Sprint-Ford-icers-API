# Fase B — Checklist Consolidado
### SpecRadar (Código 2) — SOA · Cybersecurity · Aderência à Proposta do Grupo

> Como usar este documento: a **Parte 0** é o mapa de prioridade — cada achado
> aparece uma vez, com uma nota de qual(is) checklist(s) ele toca. As Partes
> 1-3 são o checklist formal completo, item por item, para referência e
> conferência (útil na hora de montar a apresentação/relatório também).
> Legenda: ✅ atende · ⚠️ atende parcialmente / com ressalva · ❌ não atende

---

## Parte 0 — Mapa de Prioridade (achados que tocam mais de um checklist)

| # | Achado | SOA | Cyber | Proposta | Como foi confirmado |
|---|---|:---:|:---:|:---:|---|
| 1 | RBAC sem diferenciação real ANALYST/ADMIN | | ✅ | | Leitura de código |
| 2 | Detecção de brute force existe mas nunca é chamada | | ✅ | | Leitura de código |
| 3 | Refresh token não é revogado após "rotação" | | ✅ | | **Testado** (Fase A, Etapa 10) |
| 4 | Sem grounding real de busca — dados vêm da memória do LLM | | | ✅ | **Testado** (Fase A, Etapas 2-3) |
| 5 | Campo `vencedor` do compare quebrado (concatenação de dígitos) | | | ✅ | **Testado** (Fase A, Etapa 5) |
| 6 | Charset UTF-8 ausente no 401/403 | | ✅ | | **Testado e corrigido** |
| 7 | Os dois rate limiters compartilham a mesma config | | ✅ | | Leitura de código |
| 8 | `X-Forwarded-For` confiado sem validação | | ✅ | | **Testado** (Fase A) |
| 9 | "Aguarde 0 segundos" — arredondamento no retry-after | | ✅ | | **Testado** (Fase A) |
| 10 | **Novo:** validação retorna 400, proposta pede 422 | ✅ | | ✅ | Leitura de código × doc do grupo |
| 11 | **Novo:** "sensor de demanda preditiva" — diferencial central da proposta, não existe no código | | | ✅ | Leitura de código × doc do grupo |
| 12 | **Novo:** dados sensíveis em repouso sem AES-256 (só senha tem hash) | | ✅ | ✅ | Leitura de código × doc do grupo |
| 13 | **Novo:** sem idempotency key — consulta duplicada em paralelo pode duplicar linha em `sr_fichas_tecnicas` | | ✅ | | Leitura de código |
| 14 | **Novo:** sem endpoint de anonimização de usuário (Código 1 tinha) | | ✅ | | Leitura de código |
| 15 | **Novo:** pseudonimização do `usuario_hash` é mais fraca que a FK do Código 1 (IDs sequenciais pequenos = hash quebrável por força bruta) | | ✅ | | Leitura de código |
| 16 | **Novo:** 404 de veículo não retorna `sugestoes_similares` como a proposta pede | ✅ | | ✅ | Leitura de código × doc do grupo |

---

## Parte 1 — SOA / Arquitetura Orientada a Serviços e Web Services

### Integração por Web Services (50%)
| Item | Status | Nota |
|---|:---:|---|
| Desenho de arquitetura com os componentes (10%) | ⚠️ | Existe arquitetura em camadas no código, mas o *desenho formal* (diagrama) é entregável do TOGAF/Archi, que é responsabilidade da Isabelle em Testing/QA — não é algo pra você resolver sozinho, só confirmar que ela tem os componentes certos pra desenhar (LlmClient como serviço externo, camadas separadas). |
| APIs RESTful (20%) | ✅ | 8 endpoints ao todo (2 auth + 5 specs + 1 chat), todos JSON/REST. |
| Uso adequado de métodos HTTP (10%) | ✅ | GET para leitura (findByVeiculo/compare/history), POST para ações/criação. |
| Documentação com Swagger (10% — **eliminatório**) | ✅ | `OpenApiConfig` completo, com exemplos por endpoint. |

### Arquitetura Orientada a Serviços (20%)
| Item | Status | Nota |
|---|:---:|---|
| Organização modular (10%) | ✅ | `SpecService`, `ChatService`, `AuditService`, `LlmClient` — bem separados. |
| Separação apresentação/serviço/dados (10%) | ✅ | Controller → Service → Repository consistente. Única ressalva pequena: `AuthController` grava audit log diretamente via `AuditLogRepository`, em vez de delegar pro `AuditService` (que já existe e faria isso, com bônus da detecção de brute force — ver achado #2). |

### Padrões e Boas Práticas (15%)
| Item | Status | Nota |
|---|:---:|---|
| REST, JSON (8%) | ✅ | Consistente em toda a API. |
| Tratamento de erros e exceções (7%) | ⚠️ | `GlobalExceptionHandler` cobre 400/401/403/404/429/503/500 bem — mas falta um handler específico pra `HttpMessageNotReadableException` (JSON malformado/enum inválido cai no genérico 500 em vez de um 400 claro, diferente do que o Código 1 fazia). |

### Conexão com banco de dados (15%)
| Item | Status | Nota |
|---|:---:|---|
| Dependências e configuração (8%) | ✅ | Oracle + HikariCP configurados corretamente. |
| Controle de migrações (7%) | ✅ | Flyway V1-V5, íntegro (depois de toda a depuração da Fase A). |

---

## Parte 2 — Cybersecurity
*(estrutura espelhando o relatório do Código 1, item por item — usado como padrão de rigor)*

### 1. Segurança de Entrada e Validação de Dados — 20 pts
| Item | Código 1 (nota 10) | Código 2 hoje | Status |
|---|---|---|---|
| Sanitização SQLi/XSS/command injection | Queries parametrizadas (JPA) | Idem — JPA em todo lugar | ✅ |
| Normalização de parâmetros | Enum `MarcaVeiculo` + case-insensitive | `@Pattern` regex em marca/modelo (SpecQueryRequest) | ✅ (abordagem diferente, mesmo objetivo) |
| Limitação de tamanho/formato | `@Size` em tudo | `@Size` em tudo (atributos ≤20, mensagem 3-500) | ✅ |
| Tratamento seguro de erros | Handler específico p/ JSON malformado | **Falta** handler de `HttpMessageNotReadableException** | ⚠️ (mesmo item da Parte 1) |

### 2. Autenticação e Autorização — 20 pts
| Item | Código 1 | Código 2 hoje | Status |
|---|---|---|---|
| JWT com expiração, assinatura forte | HS256, secret 512 bits, 8h | Idem, mais token de refresh (7d) — arquitetura mais rica | ✅ |
| RBAC diferenciando perfis | Tabela de permissões real, endpoints exclusivos de ADMIN | **Todo endpoint de negócio usa `hasAnyRole('ANALYST','ADMIN')`** — nenhuma diferenciação de fato | ❌ **(achado #1)** |
| Renovação controlada | N/A (Código 1 não tinha refresh token) | Comentário no código promete invalidar o token antigo; **testado e confirmado que não invalida** | ❌ **(achado #3)** |

### 3. Proteção de APIs e Serviços — 20 pts
| Item | Código 1 | Código 2 hoje | Status |
|---|---|---|---|
| HTTPS/TLS 1.2+ | Perfil `prod` com certificado PKCS12 | **Não implementado** — só HTTP em qualquer perfil | ❌ (não testamos isso na Fase A porque só rodamos `dev`; vale decidir se entra no escopo antes da Sprint 3) |
| Rate limiting | 20/min por IP | 60/min, dois mecanismos (IP + usuário) — mas **compartilham a mesma config**, não são configuráveis independente | ⚠️ **(achado #7)** |
| CORS | Sem `*`, origens via env | Idem | ✅ |
| Integridade de payload / idempotência | Integridade via assinatura JWT | Integridade via JWT ok, mas **sem idempotency key** — consulta duplicada em paralelo pode gerar linha duplicada em `sr_fichas_tecnicas` (sem constraint UNIQUE em marca+modelo+versao) | ❌ **(achado #13, conceito do material gamificado do SpeedRunners)** |

### 4. Segurança de Dados e Privacidade — 25 pts
| Item | Código 1 | Código 2 hoje | Status |
|---|---|---|---|
| Criptografia em repouso | Só senha (BCrypt) — suficiente pro escopo dele | Só senha (BCrypt). **O documento de escopo final do próprio grupo promete AES-256 nos dados de specs** (`campos_json` fica em texto puro no Oracle) | ❌ **(achado #12 — gap contra a própria proposta, não só o formal)** |
| Política de retenção/descarte | Soft delete + anonimização (endpoint dedicado) | Soft delete existe (`ativo`), mas **não existe endpoint de anonimização** para `Usuario` | ❌ **(achado #14)** |
| Anonimização vs. pseudonimização (clareza) | Muito bem documentado e distinguido: anonimização irreversível (`Usuario`), pseudonimização reversível via FK (`ford_consultas.usuario_id`) | `AuditLog.usuarioHash` é um hash SHA-256 do ID — **mas como os IDs são inteiros sequenciais pequenos, é trivialmente reversível por força bruta** (hashear 1, 2, 3... e comparar). É uma pseudonimização mais fraca que a do Código 1, e sem anonimização real em lugar nenhum | ❌ **(achado #15 — um dos 3 gaps originais que você pediu pra eu revisitar)** |
| Proteção contra exposição acidental | `.env`/certificado fora do Git, sem log sensível | `.env` fora do Git ✅, sem log sensível ✅, mas o próprio `history()` vazava a entidade JPA antes da correção que já fizemos — vale citar como exemplo de vigilância contínua | ✅ (corrigido durante a Fase A) |

### 5. Monitoramento, Logs e Auditoria — 15 pts
| Item | Código 1 | Código 2 hoje | Status |
|---|---|---|---|
| Logs estruturados, sem dado sensível | Prefixos `[AUDITORIA]`/`[SEGURANÇA]` | Log estruturado via SLF4J, sem dado sensível | ✅ |
| Monitoramento de eventos suspeitos | Rate limit + tentativas de acesso logadas | Idem, mais detecção de brute force **implementada no `AuditService` mas nunca chamada** pelo `AuthController` | ❌ **(achado #2)** |
| Trilha de auditoria | Tabela dedicada (`ford_consultas`) | Tabela dedicada (`sr_audit_logs`) + histórico de consultas — estrutura equivalente | ✅ |

---

## Parte 3 — Aderência à Proposta do Grupo
*(`SpecRadar_Challenge_Ford.pdf` + `CHALLENGE_FORD.pdf`)*

| Item pedido pela proposta | Situação no Código 2 | Status |
|---|---|---|
| `POST /api/v1/specs/query` com confidence + fonte + verificado_em | Implementado e testado | ✅ |
| `GET /api/v1/specs/compare` com diff + campo `vencedor` | Implementado, mas `vencedor` está **objetivamente errado** em vários casos (concatenação de dígitos, não comparação numérica real) | ❌ **(achado #5)** |
| `POST /api/v1/specs/from-pdf` | Stub 501, conforme esperado (Sprint 4) | ✅ |
| `GET /api/v1/specs/history` com filtros | Implementado e testado (marca e modelo) | ✅ |
| Respostas de erro 401/404/**422**/500 | Código usa **400**, não 422, para erros de validação — a proposta explicitamente cita 422 | ❌ **(achado #10)** |
| 404 com campo `sugestoes_similares` | `FichaNaoEncontradaException` retorna mensagem sugerindo o endpoint de query, mas **não há campo `sugestoes_similares`** na resposta | ❌ **(achado #16)** |
| Confidence score por campo (ALTA/MEDIA/INFERIDA/NAO_ENCONTRADO) | Implementado corretamente, `CampoSpec` nunca omite campo | ✅ |
| Repositório histórico acumulativo | Funciona — cache confirmado na Fase A | ✅ |
| **Sensor de demanda preditiva** ("monitora menções públicas... atualiza o banco preventivamente") | **Não existe em nenhum lugar do código** — é citado como "diferencial central" no resumo da proposta, mas nunca foi implementado | ❌ **(achado #11 — o mais estrutural dos três novos)** |
| LLM com capacidade de busca na web | `LlmClient` não habilita nenhuma ferramenta de busca — respostas vêm só da memória de treinamento do modelo | ❌ **(achado #4 — testado)** |

---

## Notas finais

- Os itens marcados ❌ na Parte 2 (Cyber) são os que mais pesam pra nota formal — em especial RBAC (#1) e AES-256 (#12), porque são blocos de 20 e 25 pontos respectivamente no critério formal.
- O achado #11 (sensor de demanda) e #4 (sem grounding) são os que mais afetam a **narrativa do produto** — são "diferenciais centrais" da proposta que hoje não existem, o que é mais delicado de explicar numa apresentação do que um bug técnico.
- Nenhum item aqui é sobre "não sobe" — todos são sobre "atende ao que foi prometido/exigido ou não", que é exatamente o proposito da Fase B.
