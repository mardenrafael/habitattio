# Decision: Validação automática de PRs com alvo `main`

- **Decided**: 2026-11-02
- **Slug**: validation-ci
- **Verdict**: go
- **Artifacts reviewed**: intake.md | problem.md | concept.md (research.md ausente — etapa pulada)

## Scorecard

| Criterion | Rating | Justification |
|-----------|--------|---------------|
| Problem validity | strong | Fato verificado: não há CI no repositório; 7 PRs já entraram em `main` sem verificação independente, e `main` só recebe mudanças via PR. |
| Evidence strength | adequate | Sem `research.md`, mas o problema é interno e as afirmações são fatos do próprio repositório (ausência de `.github/`, estrutura do `pom.xml`, 14 testes com Testcontainers, repo público via `gh`) e decisões diretas do único stakeholder. Não há mercado ou usuário externo a pesquisar. O que resta são suposições técnicas sobre o runner, verificáveis na implementação. |
| Value vs. inaction | strong | Inação mantém todas as métricas no baseline (0% de PRs validados) e deixa a declaração de "suíte verde" sem conferência — justamente o que a constitution proíbe falsear. |
| Feasibility / appetite | strong | Option A é `small`: `./mvnw package` já roda a suíte inteira pelo surefire; runner Linux hospedado já tem Docker; gratuito por o repo ser público. |
| Strategic fit | strong | A constitution exige nunca declarar a suíte verde sem executá-la e reconhece que integração depende de Docker; o CI torna isso verificável. Não adiciona dependência ao `pom.xml`. |
| Risk posture | adequate | Riscos identificados no concept (lentidão/instabilidade do `ImagemContainerTest`, limite de pull de imagens, Java 25 no runner, PRs de fork em repo público) e contidos por escopo (sem segredos, sem permissão de escrita, sem anotação por terceiros); a mitigação real só se confirma na primeira execução. |

## Verdict & Rationale

**Go.** O problema é real e verificado no próprio repositório, o único stakeholder respondeu todas
as perguntas em aberto, e há uma opção `small` (Option A, confirmada pelo dono) que atende todas as
metas do `problem.md` sem mexer na organização da suíte. A evidência é `adequate`, não `strong`: a
etapa de research foi pulada porque não havia o que pesquisar fora do repositório, e as suposições
remanescentes são técnicas (comportamento no runner), não de valor — cabem na especificação e na
implementação, não justificam voltar a uma etapa anterior.

## If needs-clarification

Não se aplica.

## If go — Handoff to `/speckit-specify`

- **Problem**: PRs com alvo `main` são mergeados sem evidência independente de que a suíte completa
  (incluindo testes de integração com Docker) passa e de que o projeto empacota.
- **Chosen approach**: Option A — uma validação única no GitHub Actions, em runner Linux hospedado,
  disparada por PRs com alvo `main`, executando `./mvnw package` (que roda toda a suíte); um único
  check, obrigatório para merge; relatórios de teste acessíveis a partir do PR quando falhar.
- **In scope**: gatilho em PR com alvo `main`; suíte completa com Docker (Testcontainers e
  `ImagemContainerTest`); veredito único testes+empacotamento; check estável e marcável como
  obrigatório; relatórios de teste disponíveis na falha; funcionar sem segredos nem permissão de
  escrita (repo público, PRs de fork possíveis).
- **Out of scope**: build/publicação da imagem de produção; deploy/release; gatilho em push para
  `main`; PRs com outro alvo; análise estática, cobertura mínima, scan de segurança; reorganizar a
  suíte (failsafe, tags, paralelização); meta de tempo; anotação inline por ações de terceiros;
  configurar a proteção de branch (incluir o check como obrigatório é passo manual do dono).
- **Success metrics**: 100% dos PRs com alvo `main` com veredito antes do merge (baseline 0%); 0
  PRs mergeados com validação falhando; todos os testes de integração executados (nenhum pulado por
  falta de Docker); 100% das falhas com relatório de teste acessível; tempo até o veredito apenas
  observado.
- **Carried-forward open questions** (suposições a validar, não bloqueantes):
  - O runner hospedado executa Testcontainers e o `ImagemContainerTest` (builder clássico) sem
    ajuste de ambiente.
  - Java 25 está disponível no runner via ação de setup padrão.
  - O tempo total de `./mvnw package` no runner é tolerável para o dono.
  - Limites de pull de imagens públicas não geram falhas recorrentes.
  - "Estado exato do PR" = commit de merge do PR com `main` (não só a cabeça do branch).
