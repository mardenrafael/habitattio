# Contrato: workflow `validacao-pr` e check `validar`

**Feature**: [spec.md](../spec.md) | **Decisões**: [research.md](../research.md)

A feature expõe duas interfaces: o **arquivo de workflow**, que o teste
`ValidacaoPullRequestWorkflowTest` verifica, e o **check** que aparece no PR e que o ruleset de
`main` exige. Este contrato lista as invariantes de cada uma. Cada invariante do arquivo
corresponde a pelo menos um teste (um comportamento por teste, nome em português).

## 1. Arquivo `.github/workflows/validacao-pr.yml`

| # | Invariante | Origem | Teste sugerido |
|---|------------|--------|----------------|
| W1 | O único gatilho é `pull_request`. Não existe `push`, `pull_request_target`, `workflow_dispatch` nem `schedule`. | FR-001, FR-002, FR-011 | `deveDispararSomenteEmPullRequest` |
| W2 | `pull_request.branches` é exatamente `[main]`. | FR-002 | `deveValidarSomentePullRequestComAlvoMain` |
| W3 | `pull_request.types` está ausente (padrão: opened, synchronize, reopened) ou os contém. | FR-001 | `deveRevalidarANovosCommitsEReabertura` |
| W4 | `permissions` no topo é exatamente `{contents: read}`. | FR-011 | `deveTerPermissaoSomenteDeLeitura` |
| W5 | O texto do arquivo não contém `secrets.`. | FR-011 | `deveNaoUsarSegredos` |
| W6 | Há **um único** job, id `validar`, com `name: validar` e sem `strategy.matrix`. | FR-007, FR-009 | `deveTerUmUnicoJobComNomeEstavel` |
| W7 | O job tem `runs-on: ubuntu-24.04` (fixo). | R3, SC-005 | `deveRodarEmMaquinaLinuxDeVersaoFixa` |
| W8 | O job tem `timeout-minutes` numérico, > 0 e ≤ 60. | FR-013 | `deveTerTetoDeDuracao` |
| W9 | `concurrency.group` contém `github.event.pull_request.number` e `cancel-in-progress: true`. | FR-014 | `deveCancelarValidacaoAnteriorDoMesmoPr` |
| W10 | O passo de checkout usa `actions/checkout` sem `ref` (merge ref padrão) e com `persist-credentials: false`. | FR-003, FR-011 | `deveValidarOEstadoCombinadoComMain`, `deveNaoPersistirCredenciais` |
| W11 | `actions/setup-java` com `distribution: temurin` e `java-version: '25'`. | R4 | `deveUsarJava25` |
| W12 | Um passo executa `./mvnw` com o objetivo `package`, sem `-DskipTests`, `-Dmaven.test.skip`, `-Dtest=`, `-Dgroups`, `-DexcludedGroups` nem `-fae`/`-fn`. | FR-004, FR-006, FR-007 | `deveRodarSuiteCompletaEEmpacotarPeloWrapper` |
| W13 | Um passo **posterior** ao `package` falha se algum relatório `target/surefire-reports/TEST-*.xml` tiver `skipped` diferente de 0. | FR-005, SC-003 | `deveReprovarQuandoAlgumTesteForPulado` |
| W14 | Um passo `actions/upload-artifact` com `name: relatorios-de-teste`, `if: failure()` e `path` cobrindo `target/surefire-reports`. | FR-010, SC-004 | `deveAnexarRelatoriosDeTesteQuandoFalhar` |
| W15 | Todo `uses:` está fixado por SHA de 40 caracteres hexadecimais. | R8 | `deveFixarAcoesPorSha` |
| W16 | Nenhum passo tem `continue-on-error: true`. | FR-007, FR-012 | `deveReprovarAoPrimeiroPassoQueFalhar` |

Observação para a implementação do teste: o SnakeYAML (YAML 1.1) lê a chave `on` como
`Boolean.TRUE` (research R12).

## 2. Check `validar` (visto no PR e pelo ruleset)

| # | Garantia | Origem |
|---|----------|--------|
| C1 | O nome exibido é sempre `validar`, em todo PR e toda execução. | FR-009 |
| C2 | O check fica associado ao SHA validado. Um commit novo gera um check novo. | FR-008, FR-014 |
| C3 | Conclusão `success` só se o `package` e a verificação de testes pulados passarem. Qualquer outro desfecho (falha, timeout, cancelamento, erro de máquina) não é `success`. | FR-007, FR-012, FR-013 |
| C4 | O link "Details" do check leva à execução, com log completo e, na falha, o artefato de relatórios. | FR-010 |
| C5 | Não existe check `validar` para PR com alvo diferente de `main`, nem para PR com conflito (não há merge ref). | FR-002, edge cases |

## 3. Fora do contrato

O contrato não cobre a configuração do ruleset (passo manual do dono, research R13), o tempo de
execução (só observado, SC-007), anotação inline e publicação de imagem.
