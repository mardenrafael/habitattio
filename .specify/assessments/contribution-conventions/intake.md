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

### Rodada 1

1. **Documentar ou impor?** Impor também: commitlint rodando em um hook local (`commit-msg`).
2. **Tipos e escopos**: `feat`, `fix`, `docs`, `style`, `refactor`, `perf`, `test`, `build`,
   `ci`, `chore` (`fix` incluído na rodada 2; `revert` não foi listado). Breaking change
   indicado por `!` imediatamente antes dos dois-pontos (ex.: `feat(auth)!: …`) ou por rodapé
   `BREAKING CHANGE:`. **Escopo obrigatório** (ex.: `feat(auth): …`), com **valor livre**:
   qualquer escopo é aceito, desde que presente.
3. **Idioma**: inglês (commits, PRs e issues).
4. **Estratégia de merge de PR**: merge commit.
5. **Commits de merge (inclusive entre worktrees de agentes)**: seguir a **mensagem padrão do
   git** (`Merge branch '…'` / `Merge pull request #…`); o prefixo `merge:` é abandonado.
   O commitlint ignora essas mensagens por padrão, e o changelog futuro é alimentado pelos
   commits Conventional reais trazidos pelo merge.
6. **Templates de issue**: *feature request*, tratada via `/speckit-specify`; *bug report*,
   tratado via `/speckit-bug-fix`.
7. **Automação de release/changelog**: objetivo futuro, fora do escopo agora; a padronização
   deve apenas habilitá-la.
8. **Versão SemVer**: a fonte é a `<version>` do projeto no `pom.xml` (hoje `0.0.1-SNAPSHOT`).
   A versão segue SemVer **sem o sufixo `-SNAPSHOT`** (ex.: `0.0.1`). A sincronização da
   `<version>` é feita pela **CI**.
9. **Histórico existente**: fica como está, sem reescrita.
10. **Dependência de ferramenta (commitlint)**: conflita com o limite do AGENTS.md §9, mas o dono
    concorda com a exceção neste caso.

### Rodada 2

11. **Título do PR**: também segue o formato Conventional Commits (`tipo(escopo): descrição`).
12. **Instalação do hook num projeto Maven**: o dono não decidiu (commitlint é ferramenta Node)
    e pediu sugestão.
    *Sugestão do agente (não decidida, a validar em `/speckit-assess-shape`)*: versionar o hook
    em `.githooks/commit-msg` e ativá-lo com `git config core.hooksPath .githooks`. Como o
    caminho é relativo e a config do repositório é compartilhada entre worktrees, todo worktree
    de agente usa o hook automaticamente, sem passo extra. O hook chama
    `npx --no-install commitlint --edit "$1"` com um `commitlint.config.*` na raiz, sem
    `package.json` de aplicação. Pontos a pesar no shape: exige Node na máquina de quem commita;
    o `core.hooksPath` pode ser aplicado automaticamente por um plugin Maven na fase `initialize`
    (nova dependência de build) ou ficar como passo único documentado; e, se Node não for
    aceitável, uma alternativa é validar por regex em shell puro, sem commitlint.

## First-Glance Unknowns

- [NEEDS CLARIFICATION: A `<version>` sem `-SNAPSHOT` vale também entre releases (o `pom.xml`
  na `main` sempre com a última versão lançada) ou só nas versões publicadas?]
- [NEEDS CLARIFICATION: Em que gatilho a CI sincroniza a `<version>` (merge na `main`, criação
  de tag, release futura) e ela faz commit de volta no repositório?]
- [NEEDS CLARIFICATION: Com o hook só local, a CI também valida commits e títulos de PR ou o
  hook é a única barreira?]
