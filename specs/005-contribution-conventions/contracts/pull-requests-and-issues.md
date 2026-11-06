# Contract: pull requests e issues

Cobre FR-010 a FR-018. Tudo em inglês, exceto a linha original da task no corpo de uma issue
de task.

## Pull request — `.github/pull_request_template.md`

**Título**: mesmo formato do header de commit ([commit-message.md](./commit-message.md)),
descrevendo a mudança inteira. Ex.: `feat(conventions): enforce commit format and sync version`.

**Corpo** (cabeçalhos exatos, nesta ordem; nenhum pode faltar):

```markdown
## Summary
<1–3 sentences: what and why>

## Notable changes
- <changelog-ready bullet, one per user-visible change>

## Breaking changes
None | - <what breaks and how to migrate>

## Related
- Spec: specs/NNN-<name>/spec.md | Issue: #N | None

## Verification
- <command run and result, or what was not verified and why>
```

## Feature request — `.github/ISSUE_TEMPLATE/feature_request.yml`

**Título**: `feat(<scope>): <short summary>` (pré-preenchido `feat(<scope>): `).

| Campo (label = cabeçalho `###`) | id | Obrigatório |
|---|---|---|
| Problem / motivation | `problem` | sim |
| Desired behavior | `behavior` | sim |
| Who benefits | `beneficiaries` | sim |
| Out of scope | `out_of_scope` | não |
| Suggested acceptance criteria | `acceptance` | sim |

Próximo passo do fluxo: `/speckit-specify` (ou `/speckit-assess-intake`) a partir da issue.

## Bug report — `.github/ISSUE_TEMPLATE/bug_report.yml`

**Título**: `fix(<scope>): <short summary of the wrong behavior>` (pré-preenchido `fix(<scope>): `).

| Campo (label = cabeçalho `###`) | id | Obrigatório |
|---|---|---|
| Description | `description` | sim |
| Steps to reproduce | `steps` | sim |
| Expected result | `expected` | sim |
| Actual result | `actual` | sim |
| Run mode | `mode` (dropdown: `fast (H2, no profile)`, `prod (PostgreSQL, profile prod)`, `tests`) | sim |
| Evidence (logs, stack trace) | `evidence` | não |

Próximo passo do fluxo: `/speckit-bug-assess` → `/speckit-bug-fix`.

## `.github/ISSUE_TEMPLATE/config.yml`

```yaml
blank_issues_enabled: false
```

Só afeta o formulário web. A criação por API/CLI (issues de task) continua livre.

## Criação por agente (CLI/MCP)

O agente MUST produzir o mesmo título e o mesmo corpo que o formulário web geraria: um bloco
`### <label>` por campo, na ordem da tabela, com `_No response_` nos campos opcionais vazios
(o mesmo texto que o GitHub usa).

```bash
gh pr create --title "<type>(<scope>): <description>" --body-file <file with the 5 sections>
gh issue create --title "fix(<scope>): <summary>" --body-file <file with ### sections>
```

## Issue de task — `.claude/skills/speckit-taskstoissues/SKILL.md`

| Item | Regra |
|---|---|
| Título | `[NNN] TXXX: <description in English>`; ex.: `[005] T004: Add commit-msg hook that runs commitlint` |
| `NNN` | prefixo de 3 dígitos do diretório da feature em `specs/` |
| Descrição | tradução da task; identificadores, caminhos e termos de domínio (`Fatura`, `diaVencimento`) mantidos |
| Corpo | `Task (tasks.md): <linha original em português, sem o checkbox>` + `Source: specs/NNN-<name>/tasks.md` |
| Deduplicação | existe issue cujo título casa `^\[NNN\] TXXX:` → pula e reporta |
| Legado | as issues existentes não são renomeadas (FR-018); títulos `TXXX: …` sem prefixo não contam como da feature atual |
