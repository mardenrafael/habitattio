# Decision: Descrição da API derivada do código, exportável por um comando

- **Slug**: auto-docs-api
- **Decided**: 2026-11-02
- **Verdict**: go
- **Artifacts reviewed**: intake.md | research.md | problem.md | concept.md

## Scorecard

| Criterion | Rating | Justification |
|-----------|--------|---------------|
| Problem validity | adequate | Risco real de divergência (YAML manual, conferido à mão, 3 agregados ainda sem rotas), mas a dor é declarada, não observada: o arquivo tem horas de idade e não há incidente registrado. |
| Evidence strength | adequate | O estado do repo foi verificado diretamente (alta confiança); o comportamento do gerador vem só dos READMEs no GitHub; propriedades, suporte a login por formulário/CSRF e compatibilidade com Java 25 estão UNVERIFIED. |
| Value vs. inaction | adequate | Hoje o custo de não fazer é baixo (7 rotas, um mantenedor); o valor está em evitar o crescimento do custo a cada feature e em ter um comando pronto para um CI futuro. |
| Feasibility / appetite | adequate | Option B tem apetite small → medium com incerteza nomeada (rotas de segurança, texto explicativo); existe fallback barato (Option A) e um corte definido ("Try it out" com CSRF). |
| Strategic fit | strong | Atende às decisões do dono (regenerar, substituir, Swagger UI, dependência acordada) e encaixa no TDD (o teste é a verificação), desde que o conteúdo fique na camada de adapters (Princípio I). Exige emenda da constitution: a seção "Stack Tecnológico" enumera as tecnologias e restringe o Actuator a `health`, como já se fez com o Spring Security na feature 003. |
| Risk posture | adequate | Riscos principais identificados com mitigação: login/logout fora da geração automática (montagem explícita), superfície nova em `prod` (padrão fechado até decisão explícita), CSRF na UI (corte de escopo), compatibilidade (validar logo no início). O risco de compatibilidade segue sem verificação. |

## Verdict & Rationale

**Go, com a Option B.** A validade do problema e a força da evidência estão em `adequate`, e há um
conceito recomendado e escolhido pelo dono. O ajuste estratégico é forte: o dono já tomou as
decisões que normalmente bloqueariam (dependência nova e endpoint de documentação, exceções ao
§9 do AGENTS.md). As incertezas restantes (compatibilidade com Boot 4.1.2/Java 25, descrição fiel
das rotas de filtro, CSRF na Swagger UI) são técnicas e se resolvem no `/speckit-plan` e numa
primeira task de validação, não com mais descoberta. O valor imediato é modesto (custo de inação
baixo hoje), então o escopo precisa ficar no apetite: se as rotas de segurança passarem de alguns
dias, corta-se o "Try it out" funcional, não a descrição.

## If needs-clarification

- Não se aplica (verdict: go).

## If go — Handoff to `/speckit-specify`

- **Problem**: a descrição OpenAPI da API (`docs/api/openapi.yaml`) é escrita e conferida à mão,
  separada do código, e pode divergir em silêncio da API real conforme novas rotas forem expostas.
- **Chosen approach**: Option B: a aplicação produz a própria descrição a partir do código,
  enriquecida com o texto explicativo atual (guia CSRF/sessão, descrições em português, exemplos)
  e com as rotas que não são controllers (`POST /sessao`, `DELETE /sessao`, `GET /actuator/health`).
  Swagger UI navegável com a app rodando. Um teste da suíte normal exporta a descrição para
  `docs/api/openapi.yaml`, que passa a ser artefato gerado: regenerar é rodar a suíte, e um
  `git diff` não vazio indica descrição desatualizada.
- **In scope / out of scope**:
  - Dentro: geração a partir do código; inclusão das rotas de segurança e de saúde; preservação
    do texto explicativo na medida do possível; Swagger UI; teste que exporta o arquivo;
    substituição do YAML manual; nova dependência (acordada); emenda da constitution (seção
    "Stack Tecnológico") e do AGENTS.md (§1/§9) registrando a dependência e o endpoint.
  - Fora: criar pipeline de CI; SDK/publicação para terceiros; sincronizar a coleção Bruno;
    mudar rotas/comportamento/autenticação; documentar `Inquilino`/`Contrato`/`Fatura`; "Try it
    out" funcional com CSRF se exceder o apetite; reproduzir literalmente o YAML atual.
- **Success metrics**:
  - Todas as rotas expostas aparecem na descrição, incluindo login, logout e saúde (7/7).
  - Mudança de rota/campo em controller aparece sem editar arquivo de documentação.
  - Um comando (`./mvnw test`) regenera o arquivo, sem passo manual.
  - Um teste automatizado sustenta a geração (exigência do TDD).
  - Qualitativo: o fluxo CSRF/sessão continua utilizável num API client só com a descrição gerada.
- **Carried-forward open questions**:
  - [NEEDS CLARIFICATION: A Swagger UI e `/v3/api-docs` ficam disponíveis em `prod`? Com ou sem login? (padrão até decidir: fechado em `prod`)]
  - [NEEDS CLARIFICATION: `contracts/api-http.md` da spec 003 continua sendo a fonte de verdade, ou a descrição gerada passa a ser a referência? (afeta README e o texto do YAML)]
  - [NEEDS CLARIFICATION: Qual a forma exata do teste: só exportar, ou exportar e também verificar conteúdo mínimo (ex.: as 7 rotas)?]
  - [NEEDS CLARIFICATION: Quem consome a descrição além dos API clients locais?]
  - [NEEDS CLARIFICATION: A coleção Bruno continua manual (assumido fora de escopo) ou deve ser removida/derivada depois?]
  - Validar no início: compatibilidade do gerador com Spring Boot 4.1.2 / Java 25; subida da app num teste sem Docker/Redis.
