# Decision: Convenções de contribuição verificáveis (commits, PRs, issues e versão)

- **Slug**: contribution-conventions
- **Decided**: 2026-11-02
- **Verdict**: go
- **Artifacts reviewed**: intake.md | problem.md | concept.md (research.md não existe; etapa pulada)

## Scorecard

| Criterion | Rating | Justification |
|-----------|--------|---------------|
| Problem validity | strong | Divergência observada no próprio repositório: 12/89 commits fora do padrão, 3 merges `merge:`, 4/8 títulos de PR fora do formato, PR #131 sem corpo, dois formatos de título de issue de task; nenhum `.github/`. |
| Evidence strength | adequate | Sem research.md, mas a evidência é medição direta do repositório e do GitHub (problem.md) e leitura do fluxo `/speckit-taskstoissues` (concept.md). Para um processo interno com um único dono, isso dispensa pesquisa externa. |
| Value vs. inaction | adequate | Inação mantém ≈87% por imitação, sem barreira, e versão sem significado (`0.0.1-SNAPSHOT`). O valor é de prática e de higiene, não de usuários externos (único consumidor é o dono). |
| Feasibility / appetite | strong | Option B cabe em ~1 semana, em duas fatias pequenas; a fatia 1 é independente e útil sozinha. Node 24 disponível; `main` sem proteção permite o commit de volta da CI. |
| Strategic fit | strong | Escolha explícita do dono (Option B). Alinha com o fluxo SDD/Spec Kit já em uso (templates alimentam `/speckit-specify` e `/speckit-bug-fix`). Exceção à regra de dependências (AGENTS.md §9) para commitlint aceita pelo dono; nenhuma dependência nova no `pom.xml`. |
| Risk posture | adequate | Riscos identificados e com saída: fatiamento permite parar após a fatia 1; regra de bump e versão inicial ficam como decisões de spec; loop/concorrência do commit da CI conhecidos. Em aberto: títulos de task em inglês vs. `tasks.md` em português e sobrescrita da skill numa atualização do Spec Kit. |

## Verdict & Rationale

**Go**, com a Option B escolhida pelo dono. O problema é real e medido no próprio repositório, a
evidência é suficiente para um processo interno (adequate), e a Option B entrega todas as metas
do problem.md dentro de um apetite pequeno, com uma primeira fatia que se sustenta sozinha. O
valor é modesto em termos absolutos (projeto de prática, um único consumidor), mas o custo também
é: o fator decisivo é que a fatia 1 custa 1–2 dias e fecha a maior parte das métricas, e a fatia
2 é a própria prática de versionamento que motiva o projeto. Nenhum critério ficou `weak` ou
`unknown`; os riscos remanescentes são decisões de spec, não bloqueios.

## If needs-clarification

Não se aplica.

## If go — Handoff to `/speckit-specify`

- **Problem**: commits, PRs e issues do habitattio, produzidos em grande parte por agentes, não
  seguem um formato único e verificável, então a versão SemVer e um changelog futuro não podem
  ser derivados do histórico e nada impede um agente de introduzir um formato novo.
- **Chosen approach**: Option B do concept.md, em duas user stories priorizadas (ou duas specs):
  - **P1 (fatia 1)**: convenção de commit imposta por hook local `commit-msg` (commitlint) em todo
    worktree; tipos `feat`, `fix`, `docs`, `style`, `refactor`, `perf`, `test`, `build`, `ci`,
    `chore`; escopo obrigatório e livre; breaking change por `!` ou rodapé `BREAKING CHANGE:`;
    inglês; merges com mensagem padrão do git. Template de PR (título Conventional, descrição
    estruturada para changelog futuro), templates de issue de feature request (→
    `/speckit-specify`) e bug report (→ `/speckit-bug-fix`), padrão de título único e em inglês
    para issues de task, e instruções no AGENTS.md para agentes seguirem esses formatos ao criar
    PR/issue por CLI/MCP.
  - **P2 (fatia 2)**: a cada merge na `main`, a CI calcula a versão SemVer a partir dos commits
    trazidos pelo merge e grava a `<version>` do `pom.xml`, sempre sem `-SNAPSHOT`, em um commit
    Conventional de volta na `main`.
- **In scope / out of scope**: dentro, o descrito acima. Fora: changelog, notas de release, tags e
  GitHub Releases; validação na CI e proteção de branch; reescrita de histórico e renomeação das
  issues existentes; lista fechada de escopos; tradução de specs/docs/`tasks.md`/domínio;
  ferramentas de release de terceiros; dependência nova no `pom.xml`.
- **Success metrics**: 100% dos commits não-merge novos no formato (baseline 77/89); 0 merges novos
  fora da mensagem padrão (baseline 3); 100% dos títulos de PR novos no formato (baseline 4/8);
  100% de PRs, bug reports e feature requests novos na estrutura padrão (baseline: sem estrutura /
  nenhum aberto); 100% das issues de task novas com título padrão em inglês (baseline: dois
  formatos, em português); `<version>` sempre SemVer sem `-SNAPSHOT` e sincronizada a cada merge
  (baseline `0.0.1-SNAPSHOT`).
- **Carried-forward open questions**:
  - Regra de bump: tipos que não são `feat`/`fix` geram PATCH ou nenhum bump? Breaking change em
    `0.x` vai para `1.0.0` ou só MINOR?
  - Versão inicial ao remover o sufixo (`0.0.1` por padrão, ou outra).
  - Ativação do hook num projeto Maven sem `package.json` (sugestão: `.githooks/` +
    `core.hooksPath`, commitlint via `npx`) e comportamento quando Node não está presente.
  - Como o commit de volta da CI evita loop e lida com merges simultâneos.
  - Como obter títulos de task em inglês sendo o `tasks.md` em português, e como fazer o ajuste
    no fluxo `tasks.md → issues` sem editar `.specify/templates/`/`.specify/scripts/` e sem
    perdê-lo numa atualização do Spec Kit.
  - Confirmar que as 125 issues de task existentes mantêm os títulos atuais.
