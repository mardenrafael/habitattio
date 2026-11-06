# Data Model: Validação automática de PRs com alvo `main`

**Feature**: [spec.md](spec.md) | **Date**: 2026-11-02

A feature não cria dado persistido pela aplicação: nada muda em `domain/`, no banco ou nas
migrações. As entidades da spec existem como objetos da plataforma GitHub. Este documento as
mapeia, com seus estados e regras.

## Entidades

### Validação de PR → *workflow run* do workflow `Validação de PR`

| Atributo (spec) | Na plataforma | Regra |
|---|---|---|
| PR e commit validado | `pull_request.number`; `head_sha` do run; checkout de `refs/pull/<n>/merge` | Estado combinado com `main` (FR-003) |
| Nome estável | job `validar` → check run `validar` | Fixo, sem matrix (FR-009) |
| Estado | `status` (queued, in_progress, completed) + `conclusion` | Ver transições abaixo |
| Duração | início/fim do run | Só observada (SC-007). Teto de 30 min (FR-013) |
| Registro da execução | log do job | Sempre disponível |
| Relatórios de teste | artefato `relatorios-de-teste` (`target/surefire-reports/`) | Só na falha, 14 dias (FR-010) |

### Veredito → *check run* `validar`, ligado ao SHA

- `success` = **aprovado**. Qualquer outra conclusão (`failure`, `timed_out`, `cancelled`,
  `action_required`, ausência de check) = **não aprovado** para o ruleset.

### Relatórios de teste → artefato do run

- Conteúdo: XML e TXT do surefire, por classe de teste (nome, resultado, mensagem e rastro).

## Transições de estado

```text
PR aberto/reaberto/novo commit (alvo main, sem conflito)
        │
        ▼
   queued ──► in_progress ──┬─► completed/success   → aprovado
                            ├─► completed/failure   → reprovado (+ artefato, se houver relatório)
                            ├─► completed/timed_out → reprovado (teto da FR-013)
                            └─► completed/cancelled → substituído por run de commit mais novo (FR-014)

Re-run manual (FR-015): completed/* ──► queued (mesmo SHA)
PR com conflito: nenhum run → check "esperado" → merge bloqueado (com check obrigatório)
```

## Regras de validação

- Aprovado ⇔ todos os testes passam **e** o empacotamento conclui **e** nenhum teste aparece como
  pulado (FR-007, FR-005, research R6).
- Uma falha de infraestrutura nunca resulta em aprovado (FR-012): todo passo que falha encerra o
  job, sem `continue-on-error`.
