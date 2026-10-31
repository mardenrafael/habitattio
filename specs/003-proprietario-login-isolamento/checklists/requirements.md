# Specification Quality Checklist: Proprietário, login e isolamento de dados (módulo 2)

**Purpose**: Validate specification completeness and quality before proceeding to planning
**Created**: 2026-10-30
**Feature**: [spec.md](../spec.md)

## Content Quality

- [x] No implementation details (languages, frameworks, APIs)
- [x] Focused on user value and business needs
- [x] Written for non-technical stakeholders
- [x] All mandatory sections completed

## Requirement Completeness

- [x] No [NEEDS CLARIFICATION] markers remain
- [x] Requirements are testable and unambiguous
- [x] Success criteria are measurable
- [x] Success criteria are technology-agnostic (no implementation details)
- [x] All acceptance scenarios are defined
- [x] Edge cases are identified
- [x] Scope is clearly bounded
- [x] Dependencies and assumptions identified

## Feature Readiness

- [x] All functional requirements have clear acceptance criteria
- [x] User scenarios cover primary flows
- [x] Feature meets measurable outcomes defined in Success Criteria
- [x] No implementation details leak into specification

## Notes

- Iteração 1 de validação: todos os itens passam.
- **Detalhes técnicos citados de propósito**: BCrypt (Q25), Spring Security (Q19) e "nova versão de
  migração" (padrão do módulo 1) aparecem porque são decisões já tomadas pelo dono, não escolhas
  desta spec — mesmo critério da spec 001 com o PostgreSQL (ver seção Contexto). Sessão × token,
  rotas, nomes de colunas e o máximo exato da senha ficam para o `plan.md`.
- FR-026 cita "família 4xx" porque o defeito D3 é justamente 500 × 400; é o comportamento
  observável pelo cliente, não um mecanismo.
- Nenhum marcador `[NEEDS CLARIFICATION]` foi usado: os pontos em aberto têm padrão razoável e
  estão listados em "Pontos a confirmar na `/speckit-clarify`" (D3, adaptação dos testes
  existentes de imóvel exigida pelo AGENTS §3, padrões assumidos).
- Iteração 2 (reverificação contra a `main` atualizada, `eb5e715`): premissas confirmadas — só
  a migração V1 existe, `Usuario` só no domínio, sem dependência de autenticação no build, D3
  ainda aberto (nenhum tratador de erro), AGENTS §6 ainda chama `Usuario` de "operador" (D6),
  `002` segue reservado por `002-dockerize-app`. Corrigido: a lista de testes existentes
  afetados não incluía `ImoveisPostgresConstraintsTest`.
- Bloqueio para o plan: a adaptação dos testes existentes de imóvel precisa de decisão
  explícita do dono antes da implementação (AGENTS §3).
- **Conferência pós-implementação (2026-10-30)**:
  - T068 (fronteiras): sem `import org.springframework`/`jakarta.*` em `domain/` e `application/`;
    nenhum `*JpaEntity` fora de `adapters/outbound/persistence`; nenhum `*Request`/`*Response` de
    DTO fora dos controllers (os únicos achados do grep, `HttpServletRequest/Response` e
    `anyRequest()` em `adapters/inbound/security` e `SegurancaConfiguration`, são tipos do
    servlet/Spring Security, não DTOs); `ImovelRepositoryPort` só tem `buscarDoProprietario`
    (sem busca de imóvel sem dono).
  - T069: `./mvnw verify` com BUILD SUCCESS — 185 testes, 0 falhas, 0 erros, 0 ignorados; os
    testes de integração com Testcontainers (PostgreSQL/Docker) rodaram.
  - T070: roteiro do quickstart seção 2 (passos 1–17) executado com `curl`: todos os status e
    mensagens conforme o esperado; `segredo123` ausente do log da aplicação (SC-004); nenhuma
    resposta com `senha` como valor nem `$2`. Seção 3 (`spring-boot:test-run`): Flyway aplicou V1 e
    V2, subida passou na validação, passos 3, 9, 13 e 14 conforme o esperado, `senha_hash` com 60
    caracteres iniciados em `$2a$`, `segredo123` ausente do log.
  - T071: comportamento entregue confere com a spec; pontos avaliados: estratégia de sessão
    (id sempre trocado no login), mensagem de duplicidade de identificador e RF-10 (fora do
    escopo, "Out of Scope" da spec). Nenhuma alteração na spec.
