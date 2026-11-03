---

description: "Tasks de pr-validation-ci: validação automática e obrigatória de PRs com alvo main"
---

# Tasks: Validação automática de PRs com alvo `main`

**Input**: Design documents from `specs/004-pr-validation-ci/`

**Prerequisites**: [plan.md](./plan.md), [spec.md](./spec.md), [research.md](./research.md), [data-model.md](./data-model.md), [contracts/workflow-validacao-pr.md](./contracts/workflow-validacao-pr.md), [quickstart.md](./quickstart.md)

**Branch**: `004-pr-validation-ci` (criado a partir do trabalho de assessment; todo o trabalho
desta feature acontece nele).

**Tests**: **obrigatórios**. A constitution (Princípio II), o AGENTS.md §3 e a research (R6,
R12) exigem TDD. O workflow e o verificador de testes pulados só são escritos depois de os
testes (`ValidacaoPullRequestWorkflowTest` e `VerificarTestesPuladosTest`) serem vistos falhar
pelo motivo certo. A partir daí, esses testes são **read-only**: não podem ser editados, apagados
nem marcados `@Disabled` para passar. Testes existentes não podem ser alterados. **Nenhuma exceção
ao TDD e nenhuma verificação manual**: o script de testes pulados tem teste próprio
(`VerificarTestesPuladosTest`), então o AGENTS.md:59 continua verdadeiro, com o `.dockerignore`
como única exceção.

**Organization**: tasks agrupadas por user story da spec, em ordem de prioridade.
- **US1** (P1) concentra quase todo o contrato (W1–W13, W15, W16, W17). Ela inclui W6, o job
  único com nome estável, que também atende à US2.
- **US2** (P2) não tem código próprio. Ela é entregue pelo nome estável (W6, na US1), pela
  documentação (T018) e pelo passo manual do dono no ruleset (T028–T029).
- **US3** (P3) é o upload dos relatórios (W14).
- As provas de ponta a ponta dependem de um PR real e ficam juntas na Phase 6, com o rótulo da
  story que provam.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: pode rodar em paralelo (arquivo diferente, sem dependência pendente)
- **[Story]**: a user story atendida (US1–US3)
- Caminhos relativos à raiz do repositório. Pacote de testes: `br.com.marden.habitattio`
  (`src/test/java/br/com/marden/habitattio/`).

## Decisões de sequenciamento (TDD)

- **Todos os testes da US1 antes dos arquivos**: T004–T008 escrevem os testes do workflow
  (W1–W13, W15, W16) e do verificador (W17). Em T009, todos falham pelo motivo esperado
  (quickstart Q1):
  - os do workflow, com `NoSuchFileException` em `.github/workflows/validacao-pr.yml`;
  - os do verificador, porque `.github/scripts/VerificarTestesPulados.java` não existe e a
    mensagem esperada não aparece.

  Uma falha por compilação ou por outro motivo exige corrigir o **teste** antes de considerá-lo
  visto falhar. Só depois disso ele fica read-only.
- **Armadilha do verificador**: com o `.java` ausente, `java <arquivo>` já sai com 1. Por isso os
  testes de reprovação de T008 verificam também a **mensagem**, não só o código de saída. Sem
  isso, eles passariam de primeira pelo motivo errado (research R6, contrato W17).
- **Verificador antes do workflow**: T010 cria o verificador até o `VerificarTestesPuladosTest`
  ficar verde. T011 cria o workflow que o chama.
- **US3 com vermelho próprio**: o teste de W14 (T014) é escrito com o workflow já existente e
  falha por **asserção** (não há passo de upload), não por arquivo ausente. Por isso a US3 vem
  depois da US1.
- **Testes de caracterização**: nenhum é previsto. Se algum teste passar de primeira, declare no
  relatório. Ele não justifica código.
- **SnakeYAML (YAML 1.1)**: a chave `on` vira `Boolean.TRUE` no `Map` (verificado com o SnakeYAML
  2.6). O helper de T003 trata isso, e nenhum teste deve procurar a string `"on"` sozinha
  (research R12).
- **YAML em forma de bloco**: todo mapeamento que contém expressão `${{ }}` é escrito uma chave
  por linha, nunca em `{ }` numa linha só. As chaves da expressão quebram o mapeamento
  (verificado: `ParserException`, achado I1 do `/speckit-analyze`).
- **PRs descartáveis** (T024–T026, T029): saem de `004-pr-validation-ci`, não de `main`, enquanto
  a feature não estiver mergeada. O `pull_request` usa o workflow do merge ref, e só esse branch
  tem o arquivo. Testes descartáveis são **arquivos novos** nesses branches; nenhum teste
  existente é editado (guardrail). Esses PRs são fechados **sem merge** e os branches apagados.

---

## Phase 1: Setup

**Purpose**: confirmar o ponto de partida antes de qualquer mudança.

- [ ] T001 Confirmar que o branch atual é `004-pr-validation-ci` (`git branch --show-current`). Rodar `./mvnw test` com Docker no ar e registrar no relatório que a suíte atual está verde **antes** de qualquer alteração (número de testes e duração). Se o Docker não estiver disponível, **pare e reporte**: esta feature exige a suíte completa (FR-004/FR-005) e não pode ser validada só com os testes de domínio.
- [ ] T002 [P] Conferir que os SHAs fixados na research continuam apontando para as tags e registrar no relatório: `gh api repos/actions/checkout/commits/v7.0.1 --jq .sha` → `3d3c42e5aac5ba805825da76410c181273ba90b1`; `gh api repos/actions/setup-java/commits/v6.1.0 --jq .sha` → `b843ebf9b913146b38da2eaa7aeddf76074ae1ad`; `gh api repos/actions/upload-artifact/commits/v7.0.2 --jq .sha` → `cf430e030ddbb5b0abf93d22962f4752f3646cd9`. Se algum divergir, pare e reporte, sem trocar SHA por conta própria. Registrar também as regras atuais do ruleset: `gh api repos/mardenrafael/habitattio/rulesets/26249037 --jq '.rules[].type'` (esperado hoje: `deletion`, `non_fast_forward`).

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: a estrutura do teste do contrato do workflow, compartilhada por US1 e US3.

**⚠️ CRITICAL**: nenhum método de teste antes desta fase.

- [ ] T003 Criar `src/test/java/br/com/marden/habitattio/ValidacaoPullRequestWorkflowTest.java` **sem métodos de teste**, só a infraestrutura: JUnit 5 puro, sem contexto Spring, sem Docker, imports explícitos e Javadoc curto em português citando `specs/004-pr-validation-ci/contracts/workflow-validacao-pr.md`. Helpers estáticos:
  - (a) `CAMINHO = Path.of(".github/workflows/validacao-pr.yml")`, relativo à raiz do projeto, como o `ImagemContainerTest` faz com o `Dockerfile`.
  - (b) `texto()`: `Files.readString(CAMINHO)`, deixando a `NoSuchFileException` propagar (é o motivo esperado do vermelho).
  - (c) `workflow()`: `Map<String, Object>` de `new org.yaml.snakeyaml.Yaml().load(texto())`.
  - (d) `gatilhos()`: o `Map` sob a chave `Boolean.TRUE`, ou sob `"on"` se ela existir (YAML 1.1).
  - (e) `job()`: `jobs.validar` como `Map`.
  - (f) `passos()`: `List<Map<String, Object>>` de `job().get("steps")`.
  - (g) `passoQueUsa(String acao)`: o passo cujo `uses` começa com `acao + "@"`, falhando com mensagem em português se não houver.
  - (h) `indiceDoPassoQueRoda(String trecho)`: o índice do primeiro passo cujo `run` contém o trecho, ou -1.

  Confirmar que compila: `./mvnw test-compile`. O SnakeYAML já está no classpath (transitivo de `spring-boot-starter`, research R12). **Não alterar o `pom.xml`.**

**Checkpoint**: classe compila sem testes. US1 pode começar.

---

## Phase 3: User Story 1 - Veredito automático da suíte completa em todo PR para `main` (Priority: P1) 🎯 MVP

**Goal**: um workflow que, em todo PR com alvo `main`, roda o build completo (suíte e
empacotamento) sobre o merge ref, reprova se algum teste for pulado e produz um único check
`validar`, seguro para fork, com teto e cancelamento.

**Independent Test**: `ValidacaoPullRequestWorkflowTest` e `VerificarTestesPuladosTest` passam
localmente (Q2). Num PR real, o check `validar` aparece sozinho e dá `success` com todos os
testes executados (Q4, T022).

### Tests for User Story 1 ⚠️ (escrever e ver falhar antes de T010)

T004–T007 ficam no mesmo arquivo, `src/test/java/br/com/marden/habitattio/ValidacaoPullRequestWorkflowTest.java`.
Por isso nenhum deles é [P]. Um comportamento por método, estrutura AAA e mensagens de asserção em
português.

- [ ] T004 [US1] Testes de gatilho:
  - `deveDispararSomenteEmPullRequest` (W1): o conjunto de chaves de `gatilhos()` é exatamente `{"pull_request"}`. Isso exclui `push`, `pull_request_target`, `workflow_dispatch` e `schedule`.
  - `deveValidarSomentePullRequestComAlvoMain` (W2): `gatilhos().pull_request.branches` é exatamente `List.of("main")`.
  - `deveRevalidarANovosCommitsEReabertura` (W3): `pull_request.types` está ausente ou contém `opened`, `synchronize` e `reopened`.
- [ ] T005 [US1] Testes de segurança:
  - `deveTerPermissaoSomenteDeLeitura` (W4): `workflow().permissions` é exatamente `Map.of("contents", "read")`.
  - `deveNaoUsarSegredos` (W5): `texto()` não contém `secrets.`.
  - `deveFixarAcoesPorSha` (W15): todo `uses` dos passos casa com `^[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+@[0-9a-f]{40}$`, e há ao menos um.
  - `deveNaoPersistirCredenciais` (W10, 2ª parte): `passoQueUsa("actions/checkout").with.persist-credentials` é `Boolean.FALSE`.
- [ ] T006 [US1] Testes da forma do job:
  - `deveTerUmUnicoJobComNomeEstavel` (W6, que também atende à US2): as chaves de `jobs` são exatamente `{"validar"}`, `job().name` é `"validar"` e não existe `strategy`.
  - `deveRodarEmMaquinaLinuxDeVersaoFixa` (W7): `runs-on` é `"ubuntu-24.04"`.
  - `deveTerTetoDeDuracao` (W8): `timeout-minutes` é `Integer` entre 1 e 60, inclusive.
  - `deveCancelarValidacaoAnteriorDoMesmoPr` (W9): `concurrency.group` (String) contém `github.event.pull_request.number`, e `concurrency.cancel-in-progress` é `Boolean.TRUE`. A `concurrency` pode estar no topo ou no job; o teste aceita as duas posições.
  - `deveReprovarAoPrimeiroPassoQueFalhar` (W16): nem o job nem nenhum passo tem `continue-on-error` igual a `true`.
- [ ] T007 [US1] Testes do build:
  - `deveValidarOEstadoCombinadoComMain` (W10, 1ª parte): o passo de `actions/checkout` não tem `with.ref`.
  - `deveUsarJava25` (W11): `passoQueUsa("actions/setup-java").with` tem `distribution` igual a `"temurin"` e `String.valueOf(java-version)` igual a `"25"`.
  - `deveRodarSuiteCompletaEEmpacotarPeloWrapper` (W12): existe passo com `run` contendo `./mvnw` e a palavra `verify`. Nenhum `run` contém `-DskipTests`, `-Dmaven.test.skip`, `-Dtest=`, `-Dgroups`, `-DexcludedGroups`, ` -fae`, ` -fn`, `--fail-at-end` nem `--fail-never`.
  - `deveReprovarQuandoAlgumTesteForPulado` (W13): `indiceDoPassoQueRoda("VerificarTestesPulados")` é maior que `indiceDoPassoQueRoda("./mvnw")`, ambos ≥ 0.
- [ ] T008 [P] [US1] Criar `src/test/java/br/com/marden/habitattio/VerificarTestesPuladosTest.java` (contrato W17): JUnit 5 puro, sem Spring, imports explícitos, Javadoc curto citando o contrato. A infraestrutura são helpers:
  - `executar(Path diretorio)`: roda `new ProcessBuilder(<JVM corrente>, Path.of(".github/scripts/VerificarTestesPulados.java").toAbsolutePath().toString(), diretorio.toString())`. A JVM corrente é `ProcessHandle.current().info().command().orElseThrow()`. O `ProcessBuilder` usa `redirectErrorStream(true)`. O helper espera até 60 s e devolve o `record Resultado(int saida, String texto)`.
  - `relatorio(Path dir, String nome, int pulados)`: escreve `<testsuite name="x" tests="1" failures="0" errors="0" skipped="N"/>` em `dir/nome`.

  Os testes usam `@TempDir`:
  - `deveAprovarQuandoNenhumTesteFoiPulado`: `TEST-a.xml` com `skipped="0"` → `saida == 0`.
  - `deveReprovarQuandoAlgumTesteFoiPulado`: `TEST-a.xml` com 0 e `TEST-b.xml` com 1 → `saida == 1`, o `texto` contém `Testes pulados` e `TEST-b.xml`, e não contém `TEST-a.xml`.
  - `deveReprovarQuandoNaoHaRelatorios`: diretório vazio → `saida == 1` e o `texto` contém `Nenhum relatório de teste encontrado`.
  - `deveReprovarQuandoDiretorioNaoExiste`: `tempDir.resolve("inexistente")` → `saida == 1` e o `texto` contém `Nenhum relatório de teste encontrado`.

  **Toda reprovação verifica a mensagem**, não só a saída.
- [ ] T009 [US1] Rodar `./mvnw test -Dtest='ValidacaoPullRequestWorkflowTest,VerificarTestesPuladosTest'` e ver **todos** falharem pelo motivo esperado (quickstart Q1):
  - T004–T007 com `NoSuchFileException` em `.github/workflows/validacao-pr.yml`;
  - T008 porque o verificador não existe: a aprovação sai com ≠0 e as reprovações não trazem a mensagem esperada.

  Registrar a saída no relatório. Uma falha por outro motivo exige corrigir o teste e repetir. A partir daqui, T004–T008 são **read-only**.

### Implementation for User Story 1

- [ ] T010 [US1] Criar `.github/scripts/VerificarTestesPulados.java`. É um programa Java de arquivo único, só com JDK (`java.nio.file`, `javax.xml.parsers`), com um comentário de cabeçalho em português apontando para o contrato W17. O comportamento:
  - O argumento único é o diretório de relatórios.
  - Lista os `TEST-*.xml` do diretório.
  - Se o diretório não existir ou não houver nenhum `TEST-*.xml`, imprime `Nenhum relatório de teste encontrado em <dir>` e sai com 1.
  - Para cada arquivo, lê com `DocumentBuilderFactory` endurecido (`XMLConstants.FEATURE_SECURE_PROCESSING` ligado e `http://apache.org/xml/features/disallow-doctype-decl` = true) o atributo `skipped` do elemento-raiz. Atributo ausente conta como 0.
  - Se algum arquivo tiver `skipped` > 0, imprime `Testes pulados (proibido pela constitution, FR-005):` e, em seguida, um nome de arquivo por linha, e sai com 1.
  - Caso contrário, sai com 0.

  Rodar `./mvnw test -Dtest=VerificarTestesPuladosTest` até ficar verde. Se só passar mudando o teste, o problema é o verificador.
- [ ] T011 [US1] Criar `.github/workflows/validacao-pr.yml` (research R1–R11), na ordem:
  - **Topo do workflow**:
    - `name: Validação de PR`.
    - `on: pull_request` com `branches: [main]` e sem `types`.
    - `permissions: contents: read`.
    - `concurrency` **em forma de bloco**:
      ```yaml
      concurrency:
        group: validacao-pr-${{ github.event.pull_request.number }}
        cancel-in-progress: true
      ```
  - **Job `validar`**:
    - `name: validar`, `runs-on: ubuntu-24.04`, `timeout-minutes: 30`.
  - **Passos do job** (todo `with` em forma de bloco):
    1. `actions/checkout@3d3c42e5aac5ba805825da76410c181273ba90b1 # v7.0.1` com `persist-credentials: false` e sem `ref`.
    2. `actions/setup-java@b843ebf9b913146b38da2eaa7aeddf76074ae1ad # v6.1.0` com `distribution: temurin`, `java-version: '25'` e `cache: maven`.
    3. `name: Build completo (suíte e empacotamento)`, `run: ./mvnw -B verify`.
    4. `name: Nenhum teste pulado`, `run: java .github/scripts/VerificarTestesPulados.java target/surefire-reports`.

  Sem `continue-on-error` em lugar nenhum. Nenhum segredo. Um comentário curto no topo aponta para `docs/validacao-pr.md` e `specs/004-pr-validation-ci/`.
- [ ] T012 [US1] Rodar `./mvnw test -Dtest='ValidacaoPullRequestWorkflowTest,VerificarTestesPuladosTest'` e ver T004–T008 verdes (quickstart Q2). Se algum só passar mudando o teste, o problema é o workflow ou o verificador: corrija-os, não o teste.
- [ ] T013 [US1] Rodar `./mvnw -B verify` completo, com Docker (quickstart Q3), e em seguida `java .github/scripts/VerificarTestesPulados.java target/surefire-reports`. Esperado: verde e saída 0. Registrar o total de testes e a duração local.

**Checkpoint**: US1 completa localmente. O MVP já pode ir para PR (T021) mesmo sem a US3.

---

## Phase 4: User Story 3 - Diagnóstico da falha a partir do PR (Priority: P3)

**Goal**: na falha, os relatórios do surefire ficam acessíveis como artefato do run (FR-010).

**Independent Test**: o teste de W14 passa localmente. Num PR propositalmente reprovado, o
artefato `relatorios-de-teste` identifica o teste e a mensagem (Q6, T024).

### Tests for User Story 3 ⚠️

- [ ] T014 [US3] Em `src/test/java/br/com/marden/habitattio/ValidacaoPullRequestWorkflowTest.java`, adicionar `deveAnexarRelatoriosDeTesteQuandoFalhar` (W14). `passoQueUsa("actions/upload-artifact")` existe e cumpre:
  - `if` é `"failure()"` ou `"${{ failure() }}"`;
  - `with.name` é `"relatorios-de-teste"`;
  - `with.path` contém `target/surefire-reports`;
  - `with.retention-days` é `14`;
  - `with.if-no-files-found` é `"ignore"`.
- [ ] T015 [US3] Rodar `./mvnw test -Dtest=ValidacaoPullRequestWorkflowTest#deveAnexarRelatoriosDeTesteQuandoFalhar` e ver falhar por **asserção** (passo de upload ausente), não por arquivo ausente. Registrar. A partir daqui, T014 é read-only.

### Implementation for User Story 3

- [ ] T016 [US3] Em `.github/workflows/validacao-pr.yml`, adicionar como último passo, com o `with` em forma de bloco:
  - `name: Relatórios de teste`;
  - `if: failure()`;
  - `uses: actions/upload-artifact@cf430e030ddbb5b0abf93d22962f4752f3646cd9 # v7.0.2`;
  - `with`: `name: relatorios-de-teste`, `path: target/surefire-reports/`, `retention-days: 14`, `if-no-files-found: ignore`.
- [ ] T017 [US3] Rodar `./mvnw test -Dtest='ValidacaoPullRequestWorkflowTest,VerificarTestesPuladosTest'` e ver todos os 21 métodos verdes: os 17 do workflow (T004–T007 e T014) e os 4 do verificador (T008).

**Checkpoint**: workflow e verificador completos, com contrato W1–W17 coberto.

---

## Phase 5: Polish & Cross-Cutting Concerns

**Purpose**: documentação (FR-017, entrega a parte documental da US2), suíte completa e PR.

- [ ] T018 [P] Criar `docs/validacao-pr.md`, em português e no estilo do `docs/container.md`, com estas seções:
  - (1) **O que roda**: gatilho (PR com alvo `main`; abrir, reabrir, novo commit; inclusive draft), merge ref com `main`, `ubuntu-24.04`, Temurin 25, `./mvnw -B verify` (o "build completo" do AGENTS.md) e o verificador de testes pulados.
  - (2) **Como ler uma falha**: "Details" do check `validar` → log, mais o artefato `relatorios-de-teste` (14 dias). Uma falha de compilação não gera artefato.
  - (3) **Re-run**: "Re-run jobs" ou `gh run rerun <id>`. Uma falha de infraestrutura reprova, nunca aprova.
  - (4) **Teto**: 30 min; revise se a duração observada passar de ~10 min.
  - (5) **Segurança**: token só de leitura, sem segredos, PRs de fork, ações fixadas por SHA. Inclui como atualizar: `gh api repos/<ação>/releases/latest` e `gh api repos/<ação>/commits/<tag> --jq .sha`, atualizando o SHA e o comentário de versão juntos. O `ValidacaoPullRequestWorkflowTest` exige o SHA.
  - (6) **Tornar obrigatório (passo do dono)**: *Settings → Rules → Rulesets → `block-main`* → **Require status checks to pass** → adicionar `validar` (GitHub Actions). Opcionalmente, **Require a pull request before merging**. Registrar o achado: hoje o `block-main` só bloqueia exclusão e force-push (research R13).
  - (7) **Por que um `@Disabled` reprova o PR** (research R6): como rodar o mesmo verificador localmente (`java .github/scripts/VerificarTestesPulados.java target/surefire-reports`).
- [ ] T019 [P] Em `AGENTS.md` §1, logo após a subseção "Imagem de container", adicionar a subseção `### Validação de PR (CI)`. Em no máximo 6 linhas, ela diz que:
  - todo PR com alvo `main` roda o check obrigatório `validar` (`./mvnw -B verify` sobre o merge com `main`, com Docker, no GitHub Actions);
  - um teste pulado reprova o PR, e o verificador pode ser rodado localmente;
  - os relatórios de falha ficam no artefato do run;
  - o `ValidacaoPullRequestWorkflowTest` e o `VerificarTestesPuladosTest` protegem o contrato.

  Fecha com o link para [`docs/validacao-pr.md`](docs/validacao-pr.md). Nenhuma outra seção do AGENTS.md muda. Em particular, a frase do `.dockerignore` como única exceção ao TDD continua verdadeira.
- [ ] T020 Rodar `./mvnw test` completo com Docker e registrar total, falhas (0) e pulados (0). Conferir com `git status`/`git diff --stat` que só mudaram estes arquivos:
  - `.github/workflows/validacao-pr.yml`;
  - `.github/scripts/VerificarTestesPulados.java`;
  - `src/test/java/br/com/marden/habitattio/ValidacaoPullRequestWorkflowTest.java`;
  - `src/test/java/br/com/marden/habitattio/VerificarTestesPuladosTest.java`;
  - `docs/validacao-pr.md`;
  - `AGENTS.md`;
  - `specs/004-pr-validation-ci/tasks.md`.

  Em particular, `pom.xml`, `src/main/`, o `Dockerfile` e os testes existentes ficam intactos.
- [ ] T021 Commitar e fazer push de `004-pr-validation-ci` e abrir um PR **draft** com alvo `main` (`gh pr create --draft --base main --head 004-pr-validation-ci`). A descrição do PR cita a spec e lista o passo manual do ruleset (T028). Anotar o número do PR para a Phase 6.

---

## Phase 6: Prova de ponta a ponta (PRs reais)

**Purpose**: cenários de aceitação da spec no GitHub (quickstart Q4–Q10). Se uma suposição da
research se mostrar falsa no runner (R3: Docker/Testcontainers; R4: Java 25), **pare e reporte**
com o log. Não altere testes nem desligue partes da suíte para contornar.

- [ ] T022 [US1] Q4: no PR draft de T021, `gh pr checks <n>` mostra `validar` como `pass`, o que prova que drafts disparam a validação (edge case). No log do run (`gh run view <id> --log`), confira:
  - o checkout mostra o commit de merge (`HEAD is now at <sha> Merge <sha> into <sha>`, FR-003);
  - o total de testes é igual ao de T020;
  - rodaram `ImagemContainerTest`, os testes de PostgreSQL/Redis, `ValidacaoPullRequestWorkflowTest` e `VerificarTestesPuladosTest`;
  - o passo "Nenhum teste pulado" está verde.

  Registrar a duração (SC-007) e confirmar no relatório as suposições R3 e R4. Depois, `gh pr ready <n>`.
- [ ] T023 [US1] Q5: com uma validação em andamento no PR de T021, enviar um commit novo (por exemplo, marcar tasks concluídas em `specs/004-pr-validation-ci/tasks.md`). Confirmar com `gh run list --workflow validacao-pr.yml` que o run anterior ficou `cancelled` e que o check do SHA novo é o exibido.
- [ ] T024 [US3] Q6: criar o branch `descartavel/validacao-reprovada` **a partir de `004-pr-validation-ci`** e adicionar o arquivo novo `src/test/java/br/com/marden/habitattio/ProvaValidacaoReprovadaTest.java` com um único teste, `deveFalharDePropositoParaProvarAValidacao`, que faz `assertEquals(1, 2, "falha proposital da prova Q6")`. Abrir PR com alvo `main`. Confirmar que `validar` terminou `failure` e que o artefato `relatorios-de-teste` (`gh run download <id> -n relatorios-de-teste`) traz o nome do teste e a mensagem. **Não fechar ainda se T029 for feito em seguida**; se não for, fechar sem merge e apagar o branch.
- [ ] T025 [US3] Q7: criar o branch `descartavel/validacao-nao-compila` a partir de `004-pr-validation-ci`, com o arquivo novo `src/test/java/br/com/marden/habitattio/ProvaNaoCompilaTest.java` contendo um erro de sintaxe. Abrir PR com alvo `main`. Confirmar `failure`, ausência do artefato e o erro de compilação visível no log. Fechar sem merge e apagar o branch.
- [ ] T026 [US1] Q9: criar o branch `descartavel/alvo-nao-main` a partir de `004-pr-validation-ci`, com um commit trivial (arquivo novo de texto em `specs/004-pr-validation-ci/`, apagado junto com o branch). Abrir PR com alvo **`004-pr-validation-ci`**, não `main`. Confirmar que nenhum check `validar` aparece. Fechar sem merge e apagar o branch.
- [ ] T027 [US1] Q10: `gh run rerun <id do run aprovado de T022/T023>` e confirmar uma nova execução no mesmo SHA com o mesmo veredito (SC-005).
- [ ] T028 [US2] **Passo do dono, fora do repositório; o agente não executa:** adicionar ao ruleset `block-main` a regra **Require status checks to pass** com o check `validar` (e, se o dono quiser, **Require a pull request before merging**). Depois, o agente confirma: `gh api repos/mardenrafael/habitattio/rulesets/26249037 --jq '.rules[].type'` inclui `required_status_checks`. Sem este passo, o relatório final **não pode** afirmar o SC-002 e deve dizer isso explicitamente.
- [ ] T029 [US2] Q8, só depois de T028: no PR de T024 (reprovado), `gh pr view <n> --json mergeStateStatus` deve mostrar `BLOCKED`. Disparar um re-run e conferir que, com a validação em andamento, o estado continua bloqueado. Fechar o PR sem merge e apagar o branch `descartavel/validacao-reprovada`.
- [ ] T030 Relatório final, com:
  - as durações (local em T013, runner em T022);
  - as suposições R3/R4 confirmadas ou refutadas;
  - os testes de caracterização, se houver algum;
  - o estado do ruleset (T028 feito ou pendente, e com ele o SC-002);
  - a confirmação de que os PRs descartáveis foram fechados sem merge e os branches apagados.

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: sem dependências. T001 e T002 podem rodar juntas.
- **Foundational (Phase 2)**: depende da Phase 1 e bloqueia T004–T007 e a US3. T008 está em outro arquivo e não depende de T003.
- **US1 (Phase 3)**: depende da Phase 2. É o MVP.
- **US3 (Phase 4)**: depende da US1 (T011), porque precisa do workflow existente para ter um vermelho por asserção.
- **Polish (Phase 5)**: T018 e T019 dependem só do desenho, e podem começar junto da US1. T020 e T021 dependem de US1 e US3.
- **Ponta a ponta (Phase 6)**: depende de T021. T029 depende de T028 (dono) e de T024.

### User Story Dependencies

- **US1 (P1)**: independente. Entrega o veredito.
- **US2 (P2)**: o nome estável vem da US1 (T006/T011) e a documentação de T018. A ativação depende do dono (T028).
- **US3 (P3)**: depende do workflow da US1. É testável sozinha a partir daí.

### Within Each User Story

- Testes escritos e vistos falhar (T009, T015) antes de qualquer linha do verificador ou do
  workflow que eles cobrem.
- O verificador (T010) vem antes do workflow que o chama (T011).
- O código muda só até o teste passar. Teste visto falhar não muda.

---

## Parallel Example

```text
# Setup:
T001 (suíte baseline)  ||  T002 (conferir SHAs e ruleset)

# Testes da US1 (arquivos diferentes):
T004–T007 (sequenciais, ValidacaoPullRequestWorkflowTest)  ||  T008 (VerificarTestesPuladosTest)

# Durante a US1:
T009–T013 (sequenciais)  ||  T018 docs/validacao-pr.md  ||  T019 AGENTS.md

# Phase 6 (PRs independentes, depois de T021):
T024 (reprovado)  ||  T025 (não compila)  ||  T026 (alvo não-main)
```

O paralelismo é pequeno de propósito: o contrato vive em quatro arquivos (dois testes, o
verificador e o workflow), editados em sequência dentro de cada par.

---

## Implementation Strategy

### MVP First (US1)

1. Phases 1 e 2.
2. Phase 3 (US1): testes → vermelho → verificador → workflow → verde → suíte local.
3. **Parar e validar**: já é possível abrir o PR (T021) e provar Q4/Q5. O projeto sai do baseline
   de 0% de PRs validados.

### Incremental Delivery

1. US1 → PR com veredito (MVP).
2. US3 → relatórios na falha (mesmo PR, commit seguinte).
3. Docs (T018/T019) → US2 documentada. O dono ativa o ruleset (T028) → US2 entregue (SC-002).
4. Provas Q6–Q10 com PRs descartáveis.

---

## Notes

- Nenhuma task altera `pom.xml`, `src/main/`, `Dockerfile`, `.dockerignore`, testes existentes,
  `.specify/scripts/` ou `.specify/templates/`.
- Configurar o ruleset é decisão e ação do dono (T028). O agente só verifica.
- Commit ao fim de cada fase ou grupo lógico de tasks.
