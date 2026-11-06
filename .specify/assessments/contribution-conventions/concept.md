# Concept: Convenções de contribuição verificáveis (commits, PRs, issues e versão)

- **Slug**: contribution-conventions
- **Created**: 2026-11-02
- **Recommended option**: B — Convenções completas em duas fatias (padrão + hook primeiro, versão
  na CI depois)

## Evidência considerada (além de intake.md e problem.md)

- `/speckit-taskstoissues` (`.claude/skills/speckit-taskstoissues/SKILL.md`, versionado e
  instalado pelo Spec Kit) gera títulos `T001: <descrição>`, com a descrição copiada do
  `tasks.md`, que está em português. O prefixo `[003]` visto nas issues 79–128 foi um acréscimo
  ad hoc de um agente, fora da skill.
- Agentes criam PRs e issues via `gh`/MCP do GitHub, que **não** aplicam templates
  automaticamente (o template só aparece no formulário web). Template sozinho não guia agente:
  precisa estar também nas instruções que o agente lê (AGENTS.md).
- Não existe `.github/` (nenhum template, nenhum workflow). A `main` não tem proteção de branch.
  Node 24 está disponível na máquina do dono.

## Options

### Option A — Só o padrão de entrada (convenção + hook + templates), sem versão
- **Sketch**: o repositório passa a declarar a convenção de commit (tipos, escopo obrigatório,
  `!`/`BREAKING CHANGE`, inglês, merges com mensagem padrão do git) e a impõe localmente: um
  commit fora do padrão é recusado na hora, em qualquer worktree, por humano ou agente. Ganha
  templates de PR (título Conventional, descrição em seções fixas que servem de matéria-prima
  para changelog), de feature request e de bug report (com os campos que `/speckit-specify` e
  `/speckit-bug-fix` precisam), e o AGENTS.md passa a mandar os agentes usarem esses formatos
  ao criar PR/issue por CLI. A `<version>` do `pom.xml` fica como está.
- **Appetite**: small (1–2 dias).
- **Trade-offs**: ganha a maior parte das métricas (commits, merges, PRs, bug/feature) com o
  menor custo e sem tocar em CI. Sacrifica a meta de versão SemVer sincronizada e o padrão das
  issues de task. Risco: o hook depende de Node instalado em quem commita; sem Node, ou se a
  instalação do hook não acontecer num clone novo, a barreira some em silêncio.
- **Rabbit holes**: tornar a ativação do hook automática num projeto Maven (plugin Maven vs.
  passo documentado); comportamento quando Node não está presente (falhar vs. pular).

### Option B — Convenções completas em duas fatias (recomendada)
- **Sketch**: **Fatia 1** = Option A + padrão das issues de task (título em formato único e em
  inglês, gerado pelo próprio fluxo `tasks.md → issues`, sem prefixo ad hoc). **Fatia 2** = a
  cada merge na `main`, a CI calcula a nova versão SemVer a partir dos commits Conventional
  trazidos pelo merge (`feat` → MINOR, `fix`/demais → PATCH, `!`/`BREAKING CHANGE` → MAJOR),
  grava a `<version>` sem `-SNAPSHOT` no `pom.xml` e devolve isso à `main` em um commit também
  Conventional. Nada de changelog nem release publicada: só a versão passa a ter significado.
- **Appetite**: small para a fatia 1 (1–2 dias) + small para a fatia 2 (2–4 dias, incerteza
  maior por ser o primeiro workflow de CI do repositório). Total dentro de ~1 semana.
- **Trade-offs**: cobre todas as metas e métricas do problem.md, e a fatia 2 é justamente a
  prática de versionamento que motiva o projeto. Fatiar entrega valor (o padrão) antes da parte
  mais incerta (CI) e permite parar após a fatia 1 sem desperdício. Sacrifica um pouco de
  simplicidade: o repositório ganha seu primeiro workflow e um commit automático na `main` a
  cada merge.
- **Rabbit holes**:
  - **Regra de bump**: tipos que não são `feat`/`fix` (`docs`, `chore`, `test`…) geram PATCH ou
    nenhum bump? Semântica de breaking change em `0.x` (MAJOR → `1.0.0` ou MINOR)? Decidir na
    spec, não no código.
  - **Commit de volta da CI**: precisa ser Conventional (o hook local não o valida), não pode
    disparar nova sincronização em loop, e corre contra merges simultâneos. Se a `main` ganhar
    proteção de branch no futuro, o push da CI quebra.
  - **Ponto de partida**: sem tags, a única memória da versão atual é o próprio `pom.xml`; a
    primeira execução precisa de uma versão inicial explícita (ex.: `0.1.0` ou `0.0.1`).
  - **Issues de task em inglês**: a descrição vem do `tasks.md`, escrito em português. Ou os
    `tasks.md` passam a ser em inglês (conflita com specs/docs em português, fora do escopo), ou
    o fluxo traduz/resume ao criar a issue. Mudar a skill gerada pelo Spec Kit pode ser desfeito
    numa atualização do Spec Kit.

### Option C — Ferramenta pronta de release (comprar em vez de construir)
- **Sketch**: em vez de escrever a sincronização de versão, adotar uma ferramenta de mercado de
  release baseada em Conventional Commits (estilo release-please / semantic-release) que já
  calcula a versão, atualiza o `pom.xml`, gera changelog e abre PRs/tags de release. O padrão de
  entrada (hook, templates, AGENTS.md) é o mesmo da Option A.
- **Appetite**: small a medium (dias, com risco de ajuste fino de configuração).
- **Trade-offs**: menos código próprio e regras de bump já testadas; ganha changelog e releases
  de brinde. Sacrifica a não-meta explícita (changelog/release ficam para depois) e muda o
  modelo decidido: essas ferramentas normalmente versionam por *release PR* ou tag, não "a cada
  merge na `main`". Reduz a parte de prática (o processo fica opaco atrás da ferramenta). Mais
  uma dependência fora do `pom.xml` além do commitlint, sem acordo prévio (AGENTS.md §9).
- **Rabbit holes**: encaixar o modelo da ferramenta na regra "sincroniza a cada merge"; suporte
  a `pom.xml` sem `-SNAPSHOT`; interação com merge commits.

### (Referência) Não fazer nada
Mantém o custo de inação do problem.md: o histórico segue no padrão por imitação (≈87%), sem
barreira, e a versão segue `0.0.1-SNAPSHOT`. Não recomendado: o próprio dono pediu a imposição,
e o custo da Option A é de 1–2 dias.

## Recommendation

**Option B**, em duas fatias. A fatia 1 sozinha já ataca as métricas de commits (87% → 100%),
merges fora do padrão (3 → 0), títulos de PR (4/8 → 100%), estrutura de PR/bug/feature e issues
de task; é barata e independente. A fatia 2 entrega a meta restante (`<version>` SemVer sem
`-SNAPSHOT`, sincronizada a cada merge) exatamente no modelo que o dono decidiu, sem antecipar
changelog/release, que são não-metas. Como o projeto é de prática, construir a sincronização
(em vez de esconder atrás de uma ferramenta, Option C) é parte do valor. Se a fatia 2 estourar o
apetite, parar após a fatia 1 continua sendo um resultado útil.

Sugestão para `/speckit-specify`: uma spec com duas user stories priorizadas (P1 = padrão e
barreira; P2 = versão na CI), ou duas specs separadas; a decisão fica para o decide.

## Out of Scope (for the recommended option)

- Geração de changelog, notas de release, tags e GitHub Releases (objetivo futuro).
- Validação de commits/títulos de PR na CI e regras de proteção de branch.
- Reescrever histórico de commits ou renomear as 125 issues existentes (assumido; ver abaixo).
- Lista fechada de escopos.
- Traduzir specs, docs, `tasks.md` ou código de domínio para o inglês.
- Template de PR/issue para pessoas externas (só dono e agentes abrem issues).
- Ferramentas de release de terceiros (Option C) e qualquer dependência nova no `pom.xml`.

## Assumptions to Validate

- As 125 issues de task existentes mantêm os títulos atuais (por analogia com "histórico fica
  como está"). Confirmar no decide.
- Exigir Node na máquina de quem commita é aceitável (hoje só o dono e agentes na mesma máquina,
  com Node 24); um clone sem Node fica sem barreira ou é bloqueado, a definir na spec.
- Instruções no AGENTS.md bastam para que agentes sigam os templates de PR/issue ao criar por
  CLI/MCP (o GitHub não aplica template fora do formulário web).
- Ajustar o fluxo `tasks.md → issues` para títulos em inglês não exige editar
  `.specify/templates/` nem `.specify/scripts/` (proibido pelo AGENTS.md §9) e sobrevive a
  atualizações do Spec Kit, ou o risco de sobrescrita é aceito.
- A `main` continua sem proteção de branch, permitindo o commit de volta da CI.
- Uma versão inicial explícita será escolhida (o `pom.xml` hoje diz `0.0.1-SNAPSHOT`, que vira
  `0.0.1` ao remover o sufixo).
- A regra de bump para tipos que não são `feat`/`fix` e para breaking change em `0.x` será
  decidida na spec.
