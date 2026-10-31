# Idea Intake: Padronizar commits, PRs e issues (Conventional Commits + templates)

- **Slug**: contribution-conventions
- **Created**: 2026-10-30
- **Source**: pasted text (argumento de `/speckit-assess-intake`); contexto do repositório lido em `main` @ `eb5e715`
- **Type**: improvement

## Idea (as captured)

> "atualmente os PR's estão sem nenhum template, as issues tbm, apesar de estarem em um bom formato, ainda corre o risco de algum agente criar de uma forma completamente contraria. e em caso de bug report tbm não existe nenhum padrão, os commits são feitos fora do conventional commits, dificultando automação de releseas no modelo semver. a ideia é padronizar commits no conventional commits, padronizar PR para facilitar a criação de changelogs no futuro e cria issue template"

## Restated

Propõe-se padronizar a forma de contribuir no repositório: mensagens de commit no formato
Conventional Commits, um template de pull request pensado para alimentar changelogs futuros, e
templates de issue (incluindo bug report). A motivação declarada é evitar que agentes (ou pessoas)
criem artefatos em formatos divergentes e viabilizar, mais adiante, automação de releases com
versionamento SemVer.

## Origin & Context

- **Raised by**: dono do repositório (Marden Rafael), via sessão do Claude Code
- **Trigger**: percepção de ausência de padrão: PRs e issues sem template, bug reports sem
  formato definido, e commits que (segundo o autor) fogem do Conventional Commits, o que
  dificulta automatizar releases no modelo SemVer. Grande parte do trabalho é feita por agentes
  de código, o que aumenta o risco de formatos divergentes.
- **Estado observado no repositório (fato, sem avaliação)**:
  - Não existe diretório `.github/` (nenhum template de PR/issue, nenhum workflow).
  - Dos 54 commits do histórico de `main`, 47 já seguem o padrão `tipo(escopo): descrição`;
    os 7 restantes são 4 merge commits gerados pelo GitHub (`Merge pull request #…`) e 3 com
    prefixo `merge:`, que não é um tipo do Conventional Commits.
  - Mensagens de commit e documentação estão em português; o AGENTS.md não define convenção
    de commit nem de PR.
  - Fluxo SDD (Spec Kit) e worktrees de agentes já existem; tasks podem virar issues via
    `/speckit-taskstoissues`.

## First-Glance Unknowns

- [NEEDS CLARIFICATION: O padrão deve ser só documentado (AGENTS.md/CONTRIBUTING) ou também
  imposto automaticamente (hook local, verificação em CI, regra de branch)?]
- [NEEDS CLARIFICATION: Quais tipos e escopos de commit são aceitos? Os escopos atuais
  (`requisitos`, `persistencia`, `dominio`, `tasks`, `agents`…) viram lista fechada?]
- [NEEDS CLARIFICATION: Idioma da descrição do commit/PR/issue: português (como hoje) ou inglês?]
- [NEEDS CLARIFICATION: Estratégia de merge de PR: merge commit, squash ou rebase? Isso define
  qual mensagem entra no histórico e alimenta o changelog (título do PR vs. commits individuais).]
- [NEEDS CLARIFICATION: Como tratar commits de merge entre worktrees de agentes (hoje `merge: …`)?]
- [NEEDS CLARIFICATION: Quais templates de issue: bug report, feature/spec, task? Devem se ligar
  ao fluxo SDD (`/speckit-specify`, `/speckit-taskstoissues`) e ao fluxo de bug
  (`/speckit-bug-assess`)?]
- [NEEDS CLARIFICATION: Automação de release/changelog entra no escopo agora ou fica só como
  objetivo futuro habilitado pela padronização?]
- [NEEDS CLARIFICATION: Qual versão inicial / ponto de partida do SemVer, dado que o projeto não
  tem tags nem releases?]
- [NEEDS CLARIFICATION: O histórico existente fica como está (sem reescrita)?]
- [NEEDS CLARIFICATION: Adicionar ferramenta (ex.: commitlint) conflita com o limite do
  AGENTS.md §9 de não adicionar dependências sem acordo, ou fica fora do `pom.xml`?]
