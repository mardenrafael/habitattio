# Problem Definition: Commits, PRs e issues sem padrão verificável

- **Slug**: contribution-conventions
- **Created**: 2026-11-02
- **Inputs used**: intake.md (com 3 rodadas de clarificação do dono); sem research.md (etapa pulada);
  linha de base medida em `origin/main` @ `9ddabcf` e no GitHub (`gh pr list`, `gh issue list`)
  em 2026-11-02

## Problem Statement

O histórico e os artefatos de colaboração do habitattio (commits, PRs e issues), produzidos em
grande parte por agentes de código, não seguem um formato único nem verificável. Por isso, não dá
para derivar a versão SemVer ou um changelog do histórico sem leitura humana, e cada novo agente
pode introduzir um formato diferente sem que nada o impeça. Isso pesa agora porque o volume de
artefatos gerados por agentes cresceu rápido (89 commits não-merge e 125 issues) e o dono quer
habilitar releases versionadas no futuro.

## Affected Users & Stakeholders

- **Users**:
  - *Agentes de código* (sessões do Claude Code e skills do Spec Kit, como `/speckit-implement`
    e `/speckit-taskstoissues`): escrevem commits, PRs e issues. Sem um padrão explícito e
    imposto, cada sessão infere o formato a partir do histórico e pode divergir (ex.: prefixo
    `merge:`, commits sem escopo, PR #131 com título `Feat/speckit and extensions` e corpo vazio).
  - *Dono / mantenedor* (Marden Rafael, único revisor hoje): revisa e mescla PRs e precisaria
    ler o histórico à mão para decidir versão e notas de release.
  - *Quem reporta bug ou pede feature*: hoje não tem formato nem caminho definido. Nenhum bug
    report ou feature request foi aberto até agora, então não há evidência de quem são essas
    pessoas. [NEEDS CLARIFICATION: só o dono e agentes, ou também pessoas externas?]
- **Stakeholders**:
  - *Dono do repositório*: decide as convenções, aceitou a exceção à regra de dependências
    (AGENTS.md §9) e é o beneficiário da futura automação de release.
  - *Consumidores futuros das releases/changelog*: [NEEDS CLARIFICATION: existem hoje? quem
    lerá o changelog?]

## Goals

- Toda mensagem de commit nova é classificável sem ambiguidade em tipo, escopo e presença de
  breaking change, de modo que o impacto SemVer (MAJOR/MINOR/PATCH) de cada mudança seja
  derivável do histórico.
- Um commit fora do padrão é barrado no momento em que é criado, seja o autor humano ou agente.
- Títulos e descrições de PR têm estrutura previsível, servindo de matéria-prima para um
  changelog futuro.
- Bug reports e feature requests chegam com estrutura previsível, que alimenta diretamente os
  fluxos existentes (feature → `/speckit-specify`; bug → `/speckit-bug-fix`).
- A versão declarada no `pom.xml` é uma versão SemVer válida, sem sufixo `-SNAPSHOT`, e
  corresponde ao estado da `main`.

## Non-Goals

- Gerar changelog ou publicar releases automaticamente (objetivo futuro; esta mudança só o
  habilita).
- Reescrever ou corrigir o histórico existente.
- Validar commits ou títulos de PR na CI (a barreira é só local, por decisão do dono).
- Restringir a lista de escopos (qualquer escopo é válido, desde que presente).
- Traduzir para o inglês a documentação, specs e código de domínio existentes (a regra de
  idioma vale para commits, PRs e issues).

## Success Metrics

- % de commits não-merge novos na `main` no formato `tipo(escopo)[!]: descrição` com tipo em
  {`feat`, `fix`, `docs`, `style`, `refactor`, `perf`, `test`, `build`, `ci`, `chore`}
  (baseline: 77/89 ≈ 87% do histórico; 12 sem escopo ou sem tipo; meta: 100% após a adoção).
- Commits de merge com mensagem fora do padrão do git (ex.: `merge: …`)
  (baseline: 3; meta: 0 novos).
- % de PRs novos com título no formato Conventional Commits (baseline: 4/8; meta: 100%).
- % de PRs novos com descrição na estrutura padrão (baseline: não há estrutura, os corpos vão de
  0 a ~7.000 caracteres e o PR #131 está vazio; meta: 100%).
- % de bug reports e feature requests novos na estrutura padrão (baseline: nenhum aberto até
  hoje; meta: 100%). Qualitativo complementar: o fluxo correspondente consegue ser iniciado a
  partir da issue sem pedir informação básica de volta.
- `<version>` do `pom.xml` na `main` em SemVer sem `-SNAPSHOT` (baseline: `0.0.1-SNAPSHOT`;
  meta: sempre sem sufixo, sincronizada a cada merge na `main`).

## Cost of Inaction

O histórico continua majoritariamente no formato certo por imitação, sem garantia: cada nova
sessão de agente pode introduzir uma variação (já houve `merge:`, commits sem escopo, PR sem
descrição, dois formatos de título de issue de task). Quando o dono quiser automatizar releases,
precisará revisar e reclassificar o histórico à mão, e a versão do `pom.xml` seguirá sem
significado (`0.0.1-SNAPSHOT` há todo o projeto). Bugs e pedidos de feature, se chegarem,
chegarão em formato livre e exigirão triagem manual antes de entrar nos fluxos do Spec Kit.
O custo cresce com o volume de artefatos gerados por agentes.

## Decisões já tomadas (registradas no intake; entrada para o shape, não parte do problema)

- Barreira por hook local (`commit-msg`) com commitlint; sem validação na CI.
- Tipos aceitos e escopo obrigatório, como listados nas métricas; breaking change por `!` ou
  rodapé `BREAKING CHANGE:`.
- Idioma inglês para commits, PRs e issues.
- PRs mesclados por merge commit; merges mantêm a mensagem padrão do git.
- Templates de issue: feature request e bug report.
- CI sincroniza a `<version>` do `pom.xml` a cada merge na `main`.

## Open Questions

- [NEEDS CLARIFICATION: As issues de task geradas por `/speckit-taskstoissues` (125 hoje, em dois
  formatos de título: `T0xx: …` e `[003] T0xx: …`) entram no padrão? Precisam de template
  próprio ou de título em formato fixo, em inglês?]
- [NEEDS CLARIFICATION: Quem reporta bugs e pede features: só o dono e agentes, ou também
  pessoas externas? Isso muda o quanto os templates precisam guiar quem preenche.]
- [NEEDS CLARIFICATION: Existe hoje algum consumidor de releases/changelog, ou o beneficiário é só
  o dono?]
- (Para o shape) Mecanismo de instalação do hook num projeto Maven sem `package.json` e garantia
  de que agentes em worktrees o executem; sugestão registrada no intake (item 12).
- (Para o shape) Como a CI grava a `<version>` após o merge na `main` (commit de volta com
  mensagem Conventional, sem disparar nova sincronização).
