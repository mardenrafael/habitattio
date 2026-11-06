# Validação de PR

Guia de operação da validação automática de PRs com alvo `main` (feature
`specs/004-pr-validation-ci/`). Em caso de dúvida, o workflow e os testes são a referência.

Arquivos de referência:

- Workflow: `.github/workflows/validacao-pr.yml`
- Verificador de testes pulados: `.github/scripts/VerificarTestesPulados.java`
- Testes: `ValidacaoPullRequestWorkflowTest` (contrato do workflow) e `VerificarTestesPuladosTest`
  (comportamento do verificador), em `src/test/java/br/com/marden/habitattio/`
- Garantias formais: `specs/004-pr-validation-ci/contracts/workflow-validacao-pr.md`

---

## O que roda

Todo PR com alvo `main` dispara o check **`validar`** ao ser aberto, reaberto ou receber um commit
novo, inclusive em rascunho. PRs com outro alvo e pushes não disparam nada.

O job roda numa máquina `ubuntu-24.04` hospedada pelo GitHub, que já tem Docker, sobre o **merge do
PR com o `main` atual**, não só sobre o branch:

1. instala o Temurin 25;
2. roda `./mvnw -B verify`, o build completo do AGENTS.md: a suíte inteira, incluindo Testcontainers
   e `ImagemContainerTest`, e o empacotamento;
3. roda o verificador de testes pulados.

O check só aprova se todos os passos passarem. PR com conflito não gera check: não existe merge
para validar.

## Como ler uma falha

No PR, abra **Details** ao lado do check `validar`:

- o log mostra o passo e o erro;
- se a falha veio depois de os testes rodarem, a página da execução traz o artefato
  `relatorios-de-teste` (relatórios do surefire, por 14 dias).

```bash
gh run download <id> -n relatorios-de-teste
```

Uma falha de compilação não gera artefato: o log é a fonte.

## Rodar de novo

Use **Re-run jobs** na página da execução, ou:

```bash
gh run rerun <id>
```

A nova execução usa o mesmo commit. Uma falha de infraestrutura, como um download de imagem que
falhou, reprova e nunca aprova. Não há retentativa automática.

## Teto de duração

O job é encerrado como reprovado aos **30 minutos**. Revise o valor se a duração observada passar de
~10 minutos. Como referência, a suíte completa local levou 8:41 min em 2026-11-05.

## Segurança

O repositório é público e aceita PRs de fork, por isso o workflow:

- usa `pull_request`, nunca `pull_request_target`;
- tem token só de leitura (`contents: read`) e não persiste credenciais no checkout;
- não usa segredos;
- fixa as ações por SHA completo.

Para atualizar uma ação, troque o SHA e o comentário de versão juntos:

```bash
gh api repos/<ação>/releases/latest --jq .tag_name
gh api repos/<ação>/commits/<tag> --jq .sha
```

O `ValidacaoPullRequestWorkflowTest` reprova ação sem SHA.

## Tornar o check obrigatório (passo do dono)

O workflow só informa. Quem bloqueia o merge é o ruleset, configurado à mão no GitHub. Hoje o
`block-main` só impede exclusão e force-push: não exige PR nem verificação.

Em *Settings → Rules → Rulesets → `block-main`*:

1. ative **Require status checks to pass** e adicione o check `validar` (fonte: GitHub Actions);
2. opcionalmente, ative **Require a pull request before merging**, que fecha o push direto.

Para conferir:

```bash
gh api repos/mardenrafael/habitattio/rulesets/26249037 --jq '.rules[].type'
# esperado: inclui required_status_checks
```

## Por que um `@Disabled` reprova o PR

A constitution proíbe pular teste para fazer o build passar. Localmente, `./mvnw verify` aceita um
teste pulado. No PR, o verificador reprova. Para rodar a mesma verificação antes de abrir o PR:

```bash
./mvnw -B verify
java .github/scripts/VerificarTestesPulados.java target/surefire-reports   # esperado: saída 0
```
