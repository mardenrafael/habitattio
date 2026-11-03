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
habilitar releases versionadas no futuro. O projeto é de prática: o valor está em exercitar o
processo de versionamento e release, não em atender consumidores externos.

## Affected Users & Stakeholders

- **Users**:
  - *Agentes de código* (sessões do Claude Code e skills do Spec Kit, como `/speckit-implement`
    e `/speckit-taskstoissues`): escrevem commits, PRs e issues. Sem um padrão explícito e
    imposto, cada sessão infere o formato a partir do histórico e pode divergir (ex.: prefixo
    `merge:`, commits sem escopo, PR #131 com título `Feat/speckit and extensions` e corpo vazio).
  - *Dono / mantenedor* (Marden Rafael, único revisor hoje): revisa e mescla PRs e precisaria
    ler o histórico à mão para decidir versão e notas de release.
  - *Quem reporta bug ou pede feature*: só o dono e agentes (sem pessoas externas). Hoje não há
    formato nem caminho definido; nenhum bug report ou feature request foi aberto até agora.
    Como o próximo leitor é quase sempre um agente rodando `/speckit-specify` ou
    `/speckit-bug-fix`, a estrutura importa mais do que a orientação a quem preenche.
- **Stakeholders**:
  - *Dono do repositório*: decide as convenções, aceitou a exceção à regra de dependências
    (AGENTS.md §9) e é o único consumidor das releases e do changelog futuros (projeto de
    prática).

## Goals

- Toda mensagem de commit nova é classificável sem ambiguidade em tipo, escopo e presença de
  breaking change, de modo que o impacto SemVer (MAJOR/MINOR/PATCH) de cada mudança seja
  derivável do histórico.
- Um commit fora do padrão é barrado no momento em que é criado, seja o autor humano ou agente.
- Títulos e descrições de PR têm estrutura previsível, servindo de matéria-prima para um
  changelog futuro.
- Bug reports e feature requests chegam com estrutura previsível, que alimenta diretamente os
  fluxos existentes (feature → `/speckit-specify`; bug → `/speckit-bug-fix`).
- Issues de task geradas a partir do `tasks.md` também seguem o padrão: título em formato único
  e em inglês, independentemente da feature ou da sessão que as gerou.
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
- % de issues de task novas com título no formato padrão, em inglês (baseline: 0/125 em formato
  único; dois formatos coexistem, `T0xx: …` e `[003] T0xx: …`, ambos em português; meta: 100%).
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
- Templates de issue: feature request e bug report; issues de task também entram no padrão
  (resposta do dono, 2026-11-02).
- CI sincroniza a `<version>` do `pom.xml` a cada merge na `main`.
- Quem abre issues: só o dono e agentes. Único consumidor de releases/changelog: o dono
  (projeto de prática). Respostas do dono, 2026-11-02.

## Open Questions

Nenhuma pergunta de problema em aberto. Ficam para o shape:

- (Para o shape) Como padronizar as issues de task: template próprio, formato fixo de título
  aplicado por `/speckit-taskstoissues`, ou ambos. As 125 issues existentes mantêm os títulos
  atuais (assumido por analogia com a decisão de não reescrever o histórico; confirmar no shape).
- (Para o shape) Mecanismo de instalação do hook num projeto Maven sem `package.json` e garantia
  de que agentes em worktrees o executem; sugestão registrada no intake (item 12).
- (Para o shape) Como a CI grava a `<version>` após o merge na `main` (commit de volta com
  mensagem Conventional, sem disparar nova sincronização).
