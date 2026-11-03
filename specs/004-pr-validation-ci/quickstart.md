# Quickstart: validar a validação de PR

**Feature**: [spec.md](spec.md) | **Contrato**: [contracts/workflow-validacao-pr.md](contracts/workflow-validacao-pr.md)

Roteiro para provar a feature de ponta a ponta. Os cenários Q1 a Q3 cobrem a parte local (TDD).
Os cenários Q4 a Q9 são executados com PRs reais no GitHub depois do merge da feature, ou no
próprio PR da feature, onde indicado.

## Pré-requisitos

- Docker rodando localmente (para a suíte completa) e `gh` autenticado no repositório
  `mardenrafael/habitattio`.
- Permissão de administrador no repositório (para o ruleset, Q8).

## Local (antes do PR)

**Q1 — teste do contrato falha primeiro.** Com o teste escrito e sem o workflow:

```bash
./mvnw test -Dtest=ValidacaoPullRequestWorkflowTest
```

Esperado: falha porque `.github/workflows/validacao-pr.yml` não existe, e não por erro de
compilação.

**Q2 — teste do contrato passa.** Com o workflow criado, o mesmo comando dá verde.

**Q3 — suíte completa local.** `./mvnw package` dá verde, sem nenhum teste `skipped` nos
relatórios:

```bash
grep -L 'skipped="0"' target/surefire-reports/TEST-*.xml   # esperado: nenhuma linha
```

## No GitHub

**Q4 — aprovado no PR da própria feature (US1-1, US1-3, US1-6).** Abra o PR da feature com
alvo `main`. Esperado: o check `validar` aparece sozinho e termina `success`. No log, o `package`
roda todos os testes, inclusive `ImagemContainerTest` e os de PostgreSQL/Redis, e a verificação
de testes pulados passa. Anote a duração (SC-007).

```bash
gh pr checks <n>          # esperado: validar  pass
```

**Q5 — revalidação a cada commit (US1-2, FR-014).** Envie um commit novo ao PR com a validação
ainda em andamento. Esperado: o run anterior é cancelado e o check `validar` do SHA novo é o
que vale.

**Q6 — reprovado com relatório (US1-4, US3-1, SC-004, SC-006).** Num branch descartável a partir
de `main`, quebre uma asserção de um teste de domínio e abra PR com alvo `main`. Esperado:
`validar` termina `failure`. Em "Details", o artefato `relatorios-de-teste` identifica o teste e
a mensagem. Feche o PR **sem merge** e apague o branch.

**Q7 — falha antes dos testes (US3-2).** Repita o Q6 com um erro de compilação. Esperado:
`failure`, sem artefato. O log mostra a etapa e o erro de compilação.

**Q8 — tornar obrigatório (US2, passo manual do dono, research R13).** Em *Settings → Rules →
Rulesets → `block-main`*, adicione **Require status checks to pass** com o check `validar`
(fonte: GitHub Actions). Opcional: **Require a pull request before merging**. Depois, reabra o
cenário Q6. Esperado: o botão de merge fica bloqueado enquanto `validar` não for `success`, e
também enquanto estiver em andamento (US2-1, US2-2).

```bash
gh api repos/mardenrafael/habitattio/rulesets/26249037 --jq '.rules[].type'
# esperado: inclui required_status_checks
```

**Q9 — fora de escopo não dispara (US1-7, C5).** Abra um PR com alvo diferente de `main` (por
exemplo, entre dois branches de trabalho). Esperado: nenhum check `validar`. Feche sem merge.

**Q10 — re-run (FR-015).** Num run concluído, use "Re-run jobs" (ou `gh run rerun <id>`).
Esperado: nova execução para o mesmo SHA, com o mesmo veredito (SC-005).

## Resultado esperado

Com Q1 a Q10 cumpridos, a feature está provada. Registre no relatório de implementação a duração
observada no Q4 e se o ruleset (Q8) foi configurado. Sem o Q8, a validação informa, mas não
bloqueia.
