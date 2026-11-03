# Feature Specification: Convenções de contribuição verificáveis (commits, PRs, issues e versão)

**Feature Branch**: `002-contribution-conventions`

**Created**: 2026-11-02

**Status**: Draft

**Input**: User description: "use this file .specify/assessments/contribution-conventions/decision.md as base to a new feature"

**Origem**: assessment `contribution-conventions` (veredito **go**, opção B). Artefatos:
`.specify/assessments/contribution-conventions/{intake,problem,concept,decision}.md`
(sem `research.md`; etapa pulada).

## Contexto

Commits, PRs e issues do habitattio são produzidos em grande parte por agentes de código e não
seguem um formato único nem verificável. Medido em `origin/main` @ `9ddabcf` (2026-11-02):
12 de 89 commits não-merge fora do padrão, 3 merges com mensagem `merge: …`, 4 de 8 títulos de
PR fora do formato, PR #131 sem descrição, dois formatos de título nas 125 issues de task
(`T0xx: …` e `[003] T0xx: …`, ambos em português). Não existe template de PR ou issue nem
workflow de CI. A versão declarada do projeto é `0.0.1-SNAPSHOT` desde o início e não carrega
significado.

Esta feature torna o formato **declarado, imposto no momento do commit e seguido pelos
agentes**, e faz a versão do projeto passar a ser derivada do histórico a cada atualização da
`main`. O projeto é de prática: o valor é exercitar o processo de versionamento; o único
consumidor é o dono.

Algumas decisões já tomadas pelo dono no assessment são citadas pelo nome porque são
restrições, não escolhas desta spec: formato Conventional Commits, barreira local no
`commit-msg` com commitlint (exceção aceita à regra de dependências do AGENTS.md §9, sem
dependência nova no `pom.xml`), merges por merge commit, versão gravada no `pom.xml` e sincronizada
pela CI. Como cada uma é implementada fica para o `plan.md`.

## Clarifications

### Session 2026-11-02

- Q: Qual o formato do título de issue de task? → A: `[NNN] TXXX: <description>` — número da feature entre colchetes, id da task, descrição em inglês (ex.: `[002] T001: Add commit-msg hook`).
- Q: Commits só de tipos que não são `feat`/`fix` alteram a versão? → A: Sim, geram PATCH. Todo conjunto com ao menos um commit válido altera a versão.
- Q: Breaking change enquanto a versão é `0.x` vai para `1.0.0`? → A: Não. Em `0.x`, breaking change incrementa MINOR; a passagem para `1.0.0` é decisão explícita do dono.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Commit fora do padrão é recusado na hora (Priority: P1)

Um agente de código (ou o dono) cria um commit em qualquer worktree do repositório. Se a
mensagem segue a convenção, o commit é criado normalmente. Se não segue, o commit é recusado no
mesmo instante, com uma mensagem que diz qual regra foi violada e mostra um exemplo válido, para
que o autor corrija e tente de novo.

**Why this priority**: é a base de todo o resto. Sem mensagens classificáveis, a versão (US3) não
pode ser derivada do histórico, e sem barreira cada nova sessão de agente pode introduzir um
formato novo. Sozinha, fecha as métricas de commits e merges.

**Independent Test**: num clone recém-preparado, tentar commitar com mensagens válidas e
inválidas (sem escopo, tipo desconhecido, sem descrição, `merge: …`) em dois worktrees
diferentes e observar aceitação/recusa. Entrega valor sem nenhuma outra story.

**Acceptance Scenarios**:

1. **Given** um worktree do repositório com a barreira ativa, **When** o autor commita
   `feat(fatura): add late fee calculation`, **Then** o commit é criado.
2. **Given** a barreira ativa, **When** o autor commita `feat: add late fee calculation` (sem
   escopo), **Then** o commit é recusado e a mensagem de erro indica que o escopo é obrigatório.
3. **Given** a barreira ativa, **When** o autor commita `feature(fatura): add late fee` (tipo
   fora da lista), **Then** o commit é recusado e a mensagem lista os tipos aceitos.
4. **Given** a barreira ativa, **When** o autor commita `refactor(api)!: rename endpoints` ou uma
   mensagem com rodapé `BREAKING CHANGE: …`, **Then** o commit é aceito e é reconhecido como
   breaking change.
5. **Given** a barreira ativa, **When** o autor conclui um merge com a mensagem que o git gera
   por padrão (`Merge branch '…'` / `Merge pull request #… from …`), **Then** o merge é aceito.
6. **Given** a barreira ativa, **When** o autor faz um merge com mensagem própria
   `merge: integrate feature x`, **Then** o merge é recusado.
7. **Given** um worktree novo criado a partir de um clone já preparado, **When** um agente
   commita nele sem nenhum passo adicional, **Then** a mesma barreira se aplica.

---

### User Story 2 - PRs e issues chegam com estrutura previsível (Priority: P2)

Quem abre um PR, um bug report, um feature request ou issues de task (o dono pelo formulário web,
ou um agente por linha de comando) segue uma estrutura fixa: título no padrão e descrição em
seções conhecidas. O próximo leitor, quase sempre um agente rodando `/speckit-specify`,
`/speckit-bug-assess`/`/speckit-bug-fix` ou revisando o PR, encontra o que precisa sem pedir
informação básica de volta.

**Why this priority**: fecha as métricas de PR e issue e prepara a matéria-prima de um changelog
futuro. Depende só da convenção definida na US1 (o formato do título de PR é o mesmo do commit),
não da barreira em si.

**Independent Test**: abrir um PR e uma issue de cada tipo pelo formulário web e confirmar que a
estrutura aparece pré-preenchida; pedir a um agente que abra um PR e uma issue por linha de
comando e confirmar que o resultado tem o mesmo título e as mesmas seções; gerar issues de task a
partir de um `tasks.md` e confirmar o formato do título.

**Acceptance Scenarios**:

1. **Given** o repositório no GitHub, **When** o dono abre um PR pelo formulário web, **Then** a
   descrição já vem com as seções padrão do PR.
2. **Given** o repositório no GitHub, **When** o dono escolhe abrir uma issue, **Then** pode
   escolher entre "feature request" e "bug report", cada um com seus campos.
3. **Given** um agente seguindo as instruções do projeto, **When** abre um PR por linha de
   comando, **Then** o título está no formato `tipo(escopo)[!]: descrição`, em inglês, e a
   descrição contém todas as seções padrão do PR.
4. **Given** um agente seguindo as instruções do projeto, **When** abre um bug report ou feature
   request por linha de comando, **Then** a issue tem o título no padrão e todas as seções do
   template correspondente.
5. **Given** um `tasks.md` em português, **When** as issues de task são geradas a partir dele,
   **Then** cada título segue o formato padrão único, em inglês, e o corpo preserva a descrição
   original da task.
6. **Given** as 125 issues de task e os PRs já existentes, **When** a feature é entregue,
   **Then** eles mantêm os títulos e corpos atuais.

---

### User Story 3 - A versão do projeto acompanha a `main` (Priority: P3)

A cada atualização da `main` (merge de PR ou push direto), a automação do repositório lê os
commits que chegaram desde a última sincronização, calcula a nova versão SemVer a partir dos
tipos e breaking changes, grava a versão no `pom.xml` sem sufixo `-SNAPSHOT` e devolve isso à
`main` num commit que também segue a convenção. O dono passa a saber, só olhando a versão, o
tamanho da mudança acumulada.

**Why this priority**: é a meta final (a prática de versionamento) mas depende de a US1 já estar
em vigor, tem a maior incerteza (primeiro workflow de CI do repositório) e pode ser adiada sem
desperdiçar US1 e US2.

**Independent Test**: com a versão em um valor conhecido, mesclar na `main` PRs com combinações
de commits (só `fix`, um `feat`, um `!`, só `docs`) e verificar a versão gravada depois de cada
um, e que o commit de sincronização não dispara outra sincronização.

**Acceptance Scenarios**:

1. **Given** a `main` com versão `0.0.1-SNAPSHOT` e a feature recém-entregue, **When** ocorre a
   primeira sincronização, **Then** a versão passa a ser `0.0.1` mais o efeito dos commits
   trazidos, sem sufixo.
2. **Given** a versão `0.4.2`, **When** um merge traz um ou mais `fix` e nenhum `feat` nem
   breaking change, **Then** a versão passa a `0.4.3`.
3. **Given** a versão `0.4.2`, **When** um merge traz ao menos um `feat` e nenhum breaking
   change, **Then** a versão passa a `0.5.0`.
4. **Given** a versão `0.4.2`, **When** um merge traz ao menos um breaking change, **Then** a
   versão passa a `0.5.0` (em `0.x`, breaking change incrementa MINOR).
5. **Given** a versão `0.4.2`, **When** um merge traz só commits de tipos que não são `feat` nem
   `fix` (ex.: `docs`, `test`), **Then** a versão passa a `0.4.3`.
6. **Given** uma sincronização acabou de gravar a versão na `main`, **When** o commit de
   sincronização chega à `main`, **Then** nenhuma nova sincronização altera a versão.
7. **Given** dois merges chegam à `main` em sequência rápida, **When** as sincronizações rodam,
   **Then** a versão final reflete os commits dos dois merges, cada commit contado exatamente
   uma vez.
8. **Given** a versão `0.9.3`, **When** o dono promove a versão para `1.0.0` por um commit que a
   altera explicitamente, **Then** a sincronização mantém `1.0.0` e, a partir daí, breaking
   change incrementa MAJOR.

---

### Edge Cases

- **Clone sem a barreira preparada**: um clone em que a preparação nunca rodou não tem barreira.
  A preparação faz parte do passo normal de preparação do projeto, e as instruções para agentes
  dizem como verificá-la (FR-008).
- **Ferramenta da barreira indisponível** na máquina de quem commita: o commit é **recusado**
  com uma mensagem dizendo o que falta e como resolver, em vez de passar em silêncio (FR-007).
- **Commits gerados pelo próprio git** além do merge (ex.: `Revert "…"`, `fixup!`/`squash!`
  antes de um rebase): são aceitos como o merge padrão; o `revert` não conta para a versão.
- **Mensagem com corpo e rodapé**: só o cabeçalho (primeira linha) passa pela regra de formato;
  o rodapé `BREAKING CHANGE:` é reconhecido em qualquer posição do rodapé.
- **Bypass explícito da barreira** (o autor desliga a verificação de propósito): não é impedido
  localmente (a CI não valida, por decisão do dono); a sincronização de versão trata commits
  fora do padrão como sem efeito sobre a versão e não falha por causa deles.
- **Merge que não traz nenhum commit que conte** (só merges, reverts, o commit de sincronização
  ou commits fora do padrão): a versão não muda e nenhum commit de sincronização é criado.
- **Promoção manual com versão menor ou igual à atual**, ou que não seja `1.0.0` a partir de
  `0.x`: tratada como versão inválida (FR-027), sem gravar nada.
- **Versão no `pom.xml` editada à mão** fora do formato SemVer: a sincronização falha de forma
  visível, sem gravar nada, em vez de inventar uma versão.
- **Push da automação recusado** (ex.: a `main` ganhou proteção de branch no futuro): a
  sincronização falha de forma visível; a versão não é gravada em outro lugar.
- **Task cuja descrição em português não tem tradução óbvia** (nomes de domínio como `Fatura`,
  `diaVencimento`): o título em inglês mantém os termos de domínio e identificadores como estão.

## Requirements *(mandatory)*

### Functional Requirements

**Convenção de commit (US1)**

- **FR-001**: O repositório MUST declarar, num lugar lido por humanos e agentes, a convenção de
  mensagem de commit: cabeçalho `tipo(escopo)[!]: descrição`, em inglês.
- **FR-002**: O tipo MUST ser um de `feat`, `fix`, `docs`, `style`, `refactor`, `perf`, `test`,
  `build`, `ci`, `chore`, em minúsculas. Qualquer outro tipo é recusado.
- **FR-003**: O escopo MUST estar presente e não vazio; o valor é livre (não há lista fechada).
- **FR-004**: A descrição MUST estar presente e não vazia; o cabeçalho inteiro MUST ter no máximo
  100 caracteres.
- **FR-005**: Breaking change MUST ser indicada por `!` antes dos dois-pontos, por um rodapé
  `BREAKING CHANGE: <texto>`, ou pelos dois; ambas as formas são aceitas e reconhecidas.
- **FR-006**: Mensagens geradas pelo git por padrão para merge e revert MUST ser aceitas sem
  passar pela regra de formato; uma mensagem de merge escrita à mão fora do padrão do git (ex.:
  `merge: …`) MUST ser recusada.
- **FR-007**: Todo commit criado em qualquer worktree de um clone preparado MUST ser verificado
  antes de ser gravado; um commit fora da convenção MUST ser recusado com mensagem que diga qual
  regra falhou e mostre um exemplo válido. Se a verificação não puder rodar (ferramenta
  ausente), o commit MUST ser recusado com instrução de como resolver.
- **FR-008**: Preparar a barreira num clone novo MUST exigir no máximo um passo, documentado nas
  instruções do projeto, e MUST valer para todos os worktrees daquele clone sem passo adicional.
  As instruções MUST dizer como verificar se a barreira está ativa.
- **FR-009**: A barreira MUST NOT exigir dependência nova no `pom.xml`.

**PRs e issues (US2)**

- **FR-010**: O título de PR MUST seguir o mesmo formato do cabeçalho de commit (FR-001 a FR-005),
  em inglês, e descrever a mudança como um todo.
- **FR-011**: O repositório MUST oferecer um template de descrição de PR com, no mínimo, as
  seções: resumo da mudança; mudanças notáveis (para changelog); breaking changes (ou "None");
  spec/issue relacionadas; como foi verificado. Em inglês.
- **FR-012**: O repositório MUST oferecer um template de **feature request** com os campos que
  `/speckit-specify` precisa: problema/motivação, comportamento desejado, quem se beneficia,
  fora de escopo, critérios de aceitação sugeridos. Em inglês.
- **FR-013**: O repositório MUST oferecer um template de **bug report** com os campos que
  `/speckit-bug-assess`/`/speckit-bug-fix` precisam: descrição, passos para reproduzir,
  resultado esperado, resultado obtido, modo de execução (rápido ou produção), evidência
  (log/erro). Em inglês.
- **FR-014**: As instruções do projeto para agentes MUST mandar que todo PR, bug report e
  feature request criado por linha de comando ou integração (onde o GitHub não aplica o
  template) use o título no padrão e todas as seções do template correspondente.
- **FR-015**: Issues de task geradas a partir de um `tasks.md` MUST ter título num formato
  único, em inglês: `[NNN] TXXX: <description>`, onde `NNN` é o número de três dígitos da
  feature (o prefixo do diretório em `specs/`), `TXXX` é o id da task no `tasks.md` e
  `<description>` é a descrição da task em inglês (ex.: `[002] T001: Add commit-msg hook`).
- **FR-016**: O corpo de uma issue de task MUST preservar a descrição original da task do
  `tasks.md`, em português, além do título em inglês. `tasks.md`, specs e docs continuam em
  português.
- **FR-017**: O ajuste do fluxo `tasks.md → issues` MUST NOT editar `.specify/templates/` nem
  `.specify/scripts/`.
- **FR-018**: Issues, PRs e commits já existentes MUST permanecer como estão (sem renomear nem
  reescrever).

**Versão (US3)**

- **FR-019**: A cada atualização da `main`, a automação MUST calcular a nova versão a partir dos
  commits que chegaram desde a última sincronização, ignorando mensagens de merge, revert e o
  próprio commit de sincronização.
- **FR-020**: Regra de incremento a partir da versão atual `X.Y.Z`, pelo commit de maior impacto
  no conjunto: breaking change → ver FR-022; algum `feat` → MINOR (`X.(Y+1).0`); algum `fix` →
  PATCH (`X.Y.(Z+1)`); qualquer outro tipo válido → PATCH (FR-021).
- **FR-021**: Conjunto só com tipos que não são `feat` nem `fix` (`docs`, `style`, `refactor`,
  `perf`, `test`, `build`, `ci`, `chore`) MUST incrementar PATCH. Todo conjunto com ao menos um
  commit que conta (FR-019) altera a versão.
- **FR-022**: Breaking change enquanto a versão é `0.x` MUST incrementar MINOR (`0.(Y+1).0`),
  nunca levar a `1.0.0` sozinha. A partir de `1.0.0`, breaking change MUST incrementar MAJOR
  (`(X+1).0.0`).
- **FR-022a**: A passagem para `1.0.0` MUST ser uma ação explícita do dono: um commit na convenção
  que altera a versão declarada para `1.0.0`. A sincronização MUST tomar essa versão como
  resultado daquele ciclo, sem aplicar incremento sobre ela; qualquer outra alteração manual de
  versão é tratada como inválida (FR-027).
- **FR-023**: A versão gravada MUST ser SemVer `X.Y.Z` válida, sem sufixo `-SNAPSHOT` nem outro
  sufixo. Na primeira sincronização, a versão de partida MUST ser `0.0.1` (a atual
  `0.0.1-SNAPSHOT` sem o sufixo).
- **FR-024**: A versão MUST ser gravada no `pom.xml` da `main` por um commit que segue a
  convenção de commit, e esse commit MUST NOT disparar outra alteração de versão.
- **FR-025**: Quando o conjunto de commits não altera a versão, nenhum commit de sincronização
  MUST ser criado.
- **FR-026**: Atualizações próximas da `main` MUST resultar numa versão que conta cada commit
  exatamente uma vez; a versão MUST NOT diminuir.
- **FR-027**: Se a versão atual não for SemVer válida, ou se a gravação na `main` for recusada,
  a sincronização MUST falhar de forma visível no histórico de execuções da automação, sem
  gravar versão parcial.
- **FR-028**: A sincronização MUST NOT criar tags, releases, changelog ou notas de release.

**Instruções (transversal)**

- **FR-029**: As instruções do projeto para agentes (AGENTS.md) MUST descrever a convenção de
  commit, o formato de título e as seções de PR, os templates de issue, o formato de issue de
  task, o passo de preparação da barreira e a regra de versão, no mesmo change set que os
  introduz.

### Key Entities

- **Mensagem de commit**: cabeçalho (tipo, escopo, indicador de breaking, descrição), corpo
  opcional, rodapé opcional (`BREAKING CHANGE:`). Determina o impacto na versão.
- **Pull request**: título (mesmo formato do cabeçalho de commit) e descrição em seções fixas.
- **Issue**: feature request, bug report ou task; cada uma com título no padrão e seções
  próprias. Issue de task referencia uma task de um `tasks.md`.
- **Versão do projeto**: `X.Y.Z` declarada no `pom.xml` da `main`; muda só pela sincronização,
  exceto a promoção explícita para `1.0.0` pelo dono (FR-022a).
- **Commit de sincronização**: commit da automação na `main` que grava a nova versão; segue a
  convenção e não afeta a versão.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: 100% dos commits não-merge novos na `main`, a partir da entrega da US1, seguem a
  convenção (baseline: 77/89 ≈ 87%).
- **SC-002**: 0 commits de merge novos com mensagem fora do padrão do git (baseline: 3).
- **SC-003**: Num teste com ao menos 8 mensagens inválidas distintas (uma por regra) e 5
  válidas, 100% das inválidas são recusadas e 100% das válidas aceitas, em dois worktrees
  diferentes do mesmo clone.
- **SC-004**: Preparar a barreira num clone novo leva no máximo um passo e menos de 2 minutos.
- **SC-005**: 100% dos títulos de PR novos no formato (baseline: 4/8) e 100% dos PRs novos com
  todas as seções padrão preenchidas (baseline: sem estrutura; PR #131 vazio).
- **SC-006**: 100% dos bug reports e feature requests novos na estrutura do template; o fluxo
  correspondente (`/speckit-specify` ou `/speckit-bug-assess`) começa a partir da issue sem
  pedir de volta nenhum dos campos do template.
- **SC-007**: 100% das issues de task novas com título no formato padrão único, em inglês
  (baseline: 0/125 num formato único).
- **SC-008**: Após cada atualização da `main` que altera a versão, o `pom.xml` da `main` mostra
  a versão esperada pela regra (FR-020 a FR-023, FR-022a) em até 10 minutos, em 100% dos casos de teste
  da US3; a versão nunca tem sufixo `-SNAPSHOT` (baseline: `0.0.1-SNAPSHOT`).
- **SC-009**: 0 sincronizações em cascata: o commit de sincronização nunca gera uma segunda
  alteração de versão.

## Assumptions

- Quem commita é o dono ou um agente na máquina do dono, que já tem a ferramenta exigida pela
  barreira (Node 24). Exigir essa ferramenta é aceitável; sem ela o commit é recusado (fail
  closed), coerente com a meta de 100%.
- O idioma inglês de commits, PRs e issues é uma convenção instruída aos agentes e conferida em
  revisão; a barreira verifica o formato, não o idioma.
- O limite de 100 caracteres no cabeçalho segue o padrão usual de ferramentas de Conventional
  Commits.
- Instruções no AGENTS.md bastam para que agentes sigam os templates ao criar PR/issue por linha
  de comando (o GitHub não aplica template fora do formulário web).
- O ajuste do fluxo `tasks.md → issues` pode ficar fora de `.specify/templates/` e
  `.specify/scripts/`; o risco de uma atualização do Spec Kit sobrescrever esse ajuste é aceito
  e mitigado pelo registro da regra também no AGENTS.md.
- A `main` continua sem proteção de branch, permitindo que a automação grave nela.
- As 125 issues de task existentes mantêm os títulos (por analogia com "histórico fica como
  está").
- "Atualização da `main`" inclui merge de PR e push direto; nos dois casos contam os commits que
  chegaram desde a última sincronização.
- Como a promoção para `1.0.0` fica num commit dentro de um merge, os demais commits daquele
  merge não somam incremento extra; o ciclo seguinte já parte de `1.0.0`.
- Commits fora do padrão que entrem por bypass explícito da barreira não afetam a versão.
- Esta feature não altera a constitution: ela não trata de convenções de contribuição. Se o
  plano decidir registrar algo lá, a emenda sincroniza o AGENTS.md na mesma mudança.

## Out of Scope

- Changelog, notas de release, tags e GitHub Releases (objetivo futuro; esta feature só os
  habilita).
- Validação de commits ou títulos de PR na CI e regras de proteção de branch.
- Reescrita de histórico e renomeação de issues/PRs existentes.
- Lista fechada de escopos.
- Tradução de specs, docs, `tasks.md` ou código de domínio para o inglês.
- Templates voltados a pessoas externas (só dono e agentes abrem issues).
- Ferramentas de release de terceiros e qualquer dependência nova no `pom.xml`.
