# Implementation Plan: Validação automática de PRs com alvo `main`

**Branch**: `004-pr-validation-ci` | **Date**: 2026-11-02 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `/specs/004-pr-validation-ci/spec.md`

## Summary

Todo PR com alvo `main` passa a ter um check obrigatório, **`validar`**. Ele vem de um workflow
do GitHub Actions (`.github/workflows/validacao-pr.yml`) com um único job numa máquina
`ubuntu-24.04` hospedada, que já tem Docker. O job faz o checkout do merge ref do PR com `main`,
instala o Temurin 25 e roda `./mvnw -B verify`, o "build completo" do AGENTS.md (suíte completa
pelo surefire, mais o empacotamento). Depois, o verificador `.github/scripts/VerificarTestesPulados.java`
(Java de arquivo único, só JDK) reprova se algum teste aparecer como pulado. Na falha, os
relatórios do surefire são anexados como artefato. A execução usa token só de leitura, não usa
segredos e fixa as ações por SHA, o que a torna segura para PRs de fork num repositório público.
Ela tem teto de 30 min e cancela a execução anterior do mesmo PR.

O TDD vale para os dois arquivos, e nenhuma exceção é pedida:
- o `ValidacaoPullRequestWorkflowTest` (JUnit puro com SnakeYAML, que já está no classpath)
  verifica as invariantes W1–W16 do workflow antes de ele existir;
- o `VerificarTestesPuladosTest` executa o verificador como caixa-preta (W17) antes de ele
  existir.

A prova final são PRs reais, aprovado e propositalmente reprovado. Tornar o check obrigatório é passo manual do dono no ruleset
`block-main`, que hoje só bloqueia exclusão e force-push (research R13).

## Technical Context

**Language/Version**: YAML de workflow do GitHub Actions; Java 25 para o verificador de testes
pulados (programa de arquivo único) e para os testes.

**Primary Dependencies**: ações oficiais `actions/checkout` v7.0.1, `actions/setup-java` v6.1.0 e
`actions/upload-artifact` v7.0.2, fixadas por SHA. No teste, SnakeYAML 2.6, transitivo via
`spring-boot-starter` e não declarado no `pom.xml`.

**Storage**: N/A (nenhum dado da aplicação). Os artefatos de relatório ficam 14 dias no GitHub.

**Testing**: JUnit 5, sem Spring e sem Docker: `ValidacaoPullRequestWorkflowTest` (contrato do
workflow) e `VerificarTestesPuladosTest` (verificador executado via `ProcessBuilder`, com
`@TempDir`). Também cenários de ponta a ponta com PRs reais ([quickstart.md](quickstart.md)).

**Target Platform**: máquina hospedada do GitHub Actions `ubuntu-24.04`, com Docker Engine
pré-instalado.

**Project Type**: serviço web (Spring Boot). Esta feature é só de infraestrutura de entrega e não
toca `src/main`.

**Performance Goals**: nenhuma meta (decisão do dono). Duração só observada (SC-007).

**Constraints**: sem segredos, `contents: read`, sem `pull_request_target`, sem mudança no
`pom.xml` nem na organização da suíte, o mesmo comando do desenvolvimento local, teto de 30 min.

**Scale/Scope**: um colaborador, volume baixo de PRs. Repositório público, com minutos gratuitos.

Nenhum item ficou como NEEDS CLARIFICATION: todos foram resolvidos no [research.md](research.md)
(R1 a R14).

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Princípio | Avaliação | Status |
|---|---|---|
| I. Arquitetura Hexagonal | Nenhuma mudança em `src/main`. O teste novo fica em `src/test`, na raiz, como o `ImagemContainerTest`, e não importa camadas da aplicação. | ✅ N/A |
| II. TDD | O `ValidacaoPullRequestWorkflowTest` e o `VerificarTestesPuladosTest` são escritos e vistos falhar (arquivos ausentes) antes do workflow e do verificador. Os testes de reprovação do verificador verificam a mensagem, para não ficarem verdes de primeira. Um comportamento por teste, nomes em português, AAA. Depois que falham pelo motivo certo, ficam read-only. Nenhuma exceção ao TDD é pedida (research R6, R12), e o AGENTS.md:59 continua verdadeiro. | ✅ |
| III. SDD | Fluxo spec → plan → tasks → analyze. A spec só teve o Contexto corrigido pelo achado R13, sem mudança de escopo. | ✅ |
| IV. Simplicidade/YAGNI | Um workflow, um job, três ações oficiais, um verificador só com JDK, nenhuma dependência nova no `pom.xml`. O cache Maven é uma linha na ação já usada. O SnakeYAML é usado como transitivo; declará-lo exigiria acordo. | ✅ |
| V. Nomenclatura | Classe e métodos de teste em português (`deveRodarSuiteCompletaEEmpacotarPeloWrapper`). Mensagens de asserção em português. | ✅ |
| Stack/Restrições | Usa `./mvnw` (nunca `mvn`). Integração com Docker de verdade no runner. Nenhuma alteração em `.specify/scripts` ou `.specify/templates`. Actuator e `pom.xml` intactos. | ✅ |
| DoD | O `./mvnw test` inclui o teste novo. A FR-017 atualiza o AGENTS.md (§1) e cria `docs/validacao-pr.md`. | ✅ |

**Reavaliação pós-design (Phase 1)**: nada mudou. O contrato
([contracts/workflow-validacao-pr.md](contracts/workflow-validacao-pr.md)) só detalha o que os
testes verificam, e o data model confirma que não há dado persistido.

**Ajustes do `/speckit-analyze` (2026-11-02)**:
- D1: o script bash de testes pulados virou um verificador Java testado, sem exceção ao TDD.
- I1: `concurrency` em forma de bloco.
- I2: Contexto da spec corrigido.
- I3: branch `004-pr-validation-ci` criado.
- I4: `verify` no lugar de `package`.

Os pontos de atenção (não violações) são estes:

1. O passo de testes pulados (R6) faz o CI exigir algo além do comando local: um `@Disabled`
   passa localmente, mas reprova no PR. Isso é coerente com a constitution, que proíbe
   `@Disabled` para fazer o build passar.
2. O SnakeYAML é usado sem ser declarado (R12). Se ele sumir numa atualização do Spring Boot, a
   decisão de declará-lo volta ao dono.

## Project Structure

### Documentation (this feature)

```text
specs/004-pr-validation-ci/
├── plan.md              # este arquivo
├── research.md          # Phase 0 — decisões R1–R14
├── data-model.md        # Phase 1 — entidades da spec ↔ objetos do GitHub
├── quickstart.md        # Phase 1 — roteiro Q1–Q10 (local + PRs reais)
├── contracts/
│   └── workflow-validacao-pr.md   # invariantes W1–W16 do workflow, W17 do verificador, C1–C5 do check
├── checklists/
│   └── requirements.md
└── tasks.md             # Phase 2 (/speckit-tasks)
```

### Source Code (repository root)

```text
.github/
├── workflows/
│   └── validacao-pr.yml                     # NOVO — workflow "Validação de PR", job/check `validar`
└── scripts/
    └── VerificarTestesPulados.java          # NOVO — verificador de testes pulados (W17), só JDK

src/test/java/br/com/marden/habitattio/
├── ValidacaoPullRequestWorkflowTest.java    # NOVO — contrato W1–W16, JUnit puro + SnakeYAML
└── VerificarTestesPuladosTest.java          # NOVO — W17, caixa-preta via ProcessBuilder + @TempDir

docs/
└── validacao-pr.md                          # NOVO — operação: o que roda, falha, re-run, ruleset, SHAs

AGENTS.md                                    # ALTERADO — §1 ganha subseção curta apontando para docs/validacao-pr.md
```

**Structure Decision**: projeto único existente. Nada em `src/main`, `pom.xml`, `Dockerfile` ou
`.dockerignore` muda. O teste fica na raiz do pacote de testes, junto dos outros testes de
infraestrutura (`ImagemContainerTest`, `ModoRapidoTest`), e lê o workflow pelo caminho relativo
à raiz do projeto, que é o diretório de trabalho do surefire, como o `ImagemContainerTest` faz
com o `Dockerfile`.

### Ordem de implementação (para o `/speckit-tasks`)

1. Escrever `ValidacaoPullRequestWorkflowTest` (W1–W16) e `VerificarTestesPuladosTest` (W17).
   Rodar e ver falharem porque os arquivos não existem (Q1).
2. Criar `.github/scripts/VerificarTestesPulados.java` e `.github/workflows/validacao-pr.yml`
   até os testes passarem (Q2). Rodar `./mvnw -B verify` completo localmente e o verificador
   sobre os relatórios reais (Q3).
3. Escrever `docs/validacao-pr.md` e a subseção do AGENTS.md (FR-017).
4. Abrir o PR da feature e verificar Q4/Q5. Depois, os PRs descartáveis Q6, Q7 e Q9 e o re-run
   Q10.
5. Dono: configurar o ruleset (Q8). Registrar a duração observada e o estado do ruleset no
   relatório.

## Complexity Tracking

Sem violações da constitution: esta seção não se aplica.
