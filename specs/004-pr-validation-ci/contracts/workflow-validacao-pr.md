# Contrato: workflow `validacao-pr` e check `validar`

**Feature**: [spec.md](../spec.md) | **Decisões**: [research.md](../research.md)

A feature expõe três interfaces:
- o **arquivo de workflow**, que o teste `ValidacaoPullRequestWorkflowTest` verifica;
- o **verificador de testes pulados** (`.github/scripts/VerificarTestesPulados.java`), que o
  `VerificarTestesPuladosTest` verifica;
- o **check** que aparece no PR e que o ruleset de `main` exige.

Este contrato lista as invariantes de cada uma. Cada invariante dos arquivos corresponde a pelo
menos um teste (um comportamento por teste, nome em português).

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
| W12 | Um passo executa `./mvnw` com o objetivo `verify`, sem `-DskipTests`, `-Dmaven.test.skip`, `-Dtest=`, `-Dgroups`, `-DexcludedGroups` nem `-fae`/`-fn`. | FR-004, FR-006, FR-007 | `deveRodarSuiteCompletaEEmpacotarPeloWrapper` |
| W13 | Um passo **posterior** ao do `./mvnw` executa `VerificarTestesPulados` (`java .github/scripts/VerificarTestesPulados.java target/surefire-reports`). O comportamento do programa é W17. | FR-005, SC-003 | `deveReprovarQuandoAlgumTesteForPulado` |
| W14 | Um passo `actions/upload-artifact` com `name: relatorios-de-teste`, `if: failure()` e `path` cobrindo `target/surefire-reports`. | FR-010, SC-004 | `deveAnexarRelatoriosDeTesteQuandoFalhar` |
| W15 | Todo `uses:` está fixado por SHA de 40 caracteres hexadecimais. | R8 | `deveFixarAcoesPorSha` |
| W16 | Nenhum passo tem `continue-on-error: true`. | FR-007, FR-012 | `deveReprovarAoPrimeiroPassoQueFalhar` |

Observações para a implementação:
- O SnakeYAML (YAML 1.1) lê a chave `on` como `Boolean.TRUE` (research R12; verificado com o
  SnakeYAML 2.6).
- Mapeamentos com expressão `${{ }}` vão em **forma de bloco**, uma chave por linha, nunca em
  `{ }` numa linha só: as chaves da expressão quebram o mapeamento (verificado: `ParserException`).

## 1b. Verificador `.github/scripts/VerificarTestesPulados.java` (W17)

Programa Java de arquivo único, executado com `java <arquivo> <diretório-de-relatórios>`. Usa só
o JDK (`javax.xml.parsers`, `java.nio.file`) e lê o atributo `skipped` do elemento-raiz
`<testsuite>` de cada `TEST-*.xml` (research R6).

| Situação | Saída | Mensagem | Teste |
|----------|-------|----------|-------|
| ≥1 `TEST-*.xml`, todos com `skipped="0"` | 0 | — | `deveAprovarQuandoNenhumTesteFoiPulado` |
| Algum `TEST-*.xml` com `skipped` > 0 | 1 | `Testes pulados (proibido pela constitution, FR-005):` + nomes dos arquivos | `deveReprovarQuandoAlgumTesteFoiPulado` |
| Diretório sem nenhum `TEST-*.xml` | 1 | `Nenhum relatório de teste encontrado` | `deveReprovarQuandoNaoHaRelatorios` |
| Diretório inexistente | 1 | `Nenhum relatório de teste encontrado` | `deveReprovarQuandoDiretorioNaoExiste` |

Os testes de reprovação verificam também a **mensagem**, porque `java` com o arquivo ausente já
sai com 1 e o código de saída sozinho daria um verde falso.

## 2. Check `validar` (visto no PR e pelo ruleset)

| # | Garantia | Origem |
|---|----------|--------|
| C1 | O nome exibido é sempre `validar`, em todo PR e toda execução. | FR-009 |
| C2 | O check fica associado ao SHA validado. Um commit novo gera um check novo. | FR-008, FR-014 |
| C3 | Conclusão `success` só se o `verify` e a verificação de testes pulados passarem. Qualquer outro desfecho (falha, timeout, cancelamento, erro de máquina) não é `success`. | FR-007, FR-012, FR-013 |
| C4 | O link "Details" do check leva à execução, com log completo e, na falha, o artefato de relatórios. | FR-010 |
| C5 | Não existe check `validar` para PR com alvo diferente de `main`, nem para PR com conflito (não há merge ref). | FR-002, edge cases |

## 3. Fora do contrato

O contrato não cobre a configuração do ruleset (passo manual do dono, research R13), o tempo de
execução (só observado, SC-007), anotação inline e publicação de imagem.
