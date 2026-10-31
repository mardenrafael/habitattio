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

## Clarifications (respostas do dono, 2026-10-30)

1. **Documentar ou impor?** Impor também: commitlint rodando em um hook local (`commit-msg`).
2. **Tipos e escopos**: `feat`, `docs`, `style`, `refactor`, `perf`, `test`, `build`, `ci`,
   `chore`. Breaking change indicado por `!` imediatamente antes dos dois-pontos
   (ex.: `feat(auth)!: …`) ou por rodapé `BREAKING CHANGE:`. **Escopo obrigatório**
   (ex.: `feat(auth): …`).
3. **Idioma**: inglês (commits, PRs e issues).
4. **Estratégia de merge de PR**: merge commit.
5. **Commits de merge entre worktrees de agentes**: o dono não decidiu e pediu sugestão.
   *Sugestão do agente (não decidida, a validar em `/speckit-assess-shape`)*: abandonar o
   prefixo `merge:` e manter a mensagem padrão do git (`Merge branch '…'` /
   `Merge pull request #…`), que o commitlint ignora por padrão; assim o changelog é alimentado
   pelos commits Conventional reais trazidos pelo merge, e não por um commit de merge sem tipo.
6. **Templates de issue**: *feature request*, tratada via `/speckit-specify`; *bug report*,
   tratado via `/speckit-bug-fix`.
7. **Automação de release/changelog**: objetivo futuro, fora do escopo agora; a padronização
   deve apenas habilitá-la.
8. **Versão SemVer**: a fonte é a `<version>` do projeto no `pom.xml` (hoje `0.0.1-SNAPSHOT`),
   que deve ser mantida sincronizada com o versionamento/releases.
9. **Histórico existente**: fica como está, sem reescrita.
10. **Dependência de ferramenta (commitlint)**: conflita com o limite do AGENTS.md §9, mas o dono
    concorda com a exceção neste caso.

## First-Glance Unknowns

- [NEEDS CLARIFICATION: `fix` não consta na lista de tipos da resposta 2 (nem `revert`). Sem
  `fix` não há como sinalizar um incremento PATCH no SemVer. Omissão intencional ou deve ser
  incluído?]
- [NEEDS CLARIFICATION: Escopos formam uma lista fechada (validada pelo commitlint) ou são
  livres, desde que presentes?]
- [NEEDS CLARIFICATION: Como o hook local é instalado/distribuído num projeto Maven (sem
  `package.json`) e como garantir que agentes em worktrees o executem?]
- [NEEDS CLARIFICATION: O que significa "manter o pom.xml sincronizado": quem/qual etapa
  atualiza a `<version>` e quando (a cada release futura, a cada merge)?]
- [NEEDS CLARIFICATION: Como o título do PR deve se relacionar com o Conventional Commits, dado
  que o merge é por merge commit (o título também segue o formato?)]
