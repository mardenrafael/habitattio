# Research: Validação automática de PRs com alvo `main`

**Feature**: [spec.md](spec.md) | **Plan**: [plan.md](plan.md) | **Date**: 2026-11-02

Fatos verificados em 2026-11-02, salvo onde marcado como suposição:

- Repositório `mardenrafael/habitattio`, **público**, branch padrão `main` (`gh repo view`).
- `main` **não** tem branch protection clássica (`GET .../branches/main/protection` → 404). A
  única regra ativa é o ruleset `block-main` (alvo `~DEFAULT_BRANCH`), que contém apenas
  `deletion` e `non_fast_forward`. Ver R13.
- Os testes de integração **não** usam `disabledWithoutDocker`, `assume*` nem `@Disabled`: sem
  Docker, eles falham em vez de serem pulados.
- `org.yaml:snakeyaml:2.6` já está no classpath (escopo `compile`, via
  `spring-boot-starter-data-jpa → spring-boot-starter`). `./mvnw dependency:tree` confirmou.
- Últimas versões das ações oficiais, com SHA do commit da tag: `actions/checkout` v7.0.1
  (`3d3c42e5aac5ba805825da76410c181273ba90b1`), `actions/setup-java` v6.1.0
  (`b843ebf9b913146b38da2eaa7aeddf76074ae1ad`) e `actions/upload-artifact` v7.0.2
  (`cf430e030ddbb5b0abf93d22962f4752f3646cd9`). As três rodam em `node24`.

---

## R1 — Gatilho

- **Decision**: `on: pull_request` com `branches: [main]` e os tipos padrão (`opened`,
  `synchronize`, `reopened`). Sem `push`, sem `pull_request_target`, sem `workflow_dispatch`.
- **Rationale**: cobre a FR-001 (abrir, reabrir e novos commits) e a FR-002 (o filtro `branches`
  em `pull_request` filtra pelo **alvo** do PR). O re-run da FR-015 já existe na interface do
  Actions ("Re-run jobs") para o mesmo commit, sem gatilho extra. PRs em rascunho disparam
  `pull_request` normalmente, conforme o edge case da spec.
- **Alternatives considered**: `pull_request_target`, rejeitado porque roda com token de escrita
  e segredos no contexto da base, e executar código de PR de fork nesse contexto é o vetor
  clássico de ataque (FR-011). `workflow_dispatch` para re-run, desnecessário porque o re-run
  nativo já atende.

## R2 — Estado validado (PR combinado com `main`)

- **Decision**: usar o checkout padrão do evento `pull_request`, que aponta para
  `refs/pull/<n>/merge`, o commit de merge de teste do PR com a ponta de `main`.
- **Rationale**: atende à FR-003 sem configuração. Com conflito, o GitHub não gera o merge ref e
  o workflow não roda. O check obrigatório fica "esperado" e o merge continua bloqueado, que é o
  comportamento do edge case. A política de exigir o branch atualizado quando `main` avança é
  opção do ruleset (R13), fora desta feature.
- **Alternatives considered**: `ref: ${{ github.event.pull_request.head.sha }}`, rejeitado
  porque validaria só o branch, e o dono definiu "estado exato" como o merge.

## R3 — Máquina de execução

- **Decision**: `runs-on: ubuntu-24.04`, fixo e não `ubuntu-latest`.
- **Rationale**: a máquina hospedada Linux já tem Docker Engine, de que precisam o
  Testcontainers e o build clássico do `ImagemContainerTest`. Fixar a versão evita que a troca
  silenciosa do alias `latest` mude o ambiente entre execuções (SC-005, reprodutibilidade). Para
  repositório público, a máquina padrão é gratuita.
- **Alternatives considered**: `ubuntu-latest`, que muda sozinho de versão. Máquina
  auto-hospedada é perigosa em repositório público, porque PRs de fork executariam código na
  máquina do dono.
- **Suposição a validar na 1ª execução**: Testcontainers e o build clássico funcionam sem ajuste
  (por exemplo, sem configurar `DOCKER_HOST` nem Ryuk).

## R4 — Java 25 e cache

- **Decision**: `actions/setup-java` com `distribution: temurin`, `java-version: '25'` e
  `cache: maven`.
- **Rationale**: Temurin é a mesma distribuição da imagem de build do `Dockerfile`
  (`eclipse-temurin:25-jdk`). `cache: maven` guarda `~/.m2/repository` por hash do `pom.xml`. Isso
  é opcional pela spec, mas custa uma linha e reduz downloads do Maven Central, que são uma fonte
  de falha de infraestrutura (FR-012). A ação já define `MAVEN_ARGS=-ntp`, então o log fica
  limpo.
- **Alternatives considered**: sem cache, que é viável mas mais lento e mais exposto à rede. Um
  cache manual com `actions/cache` é mais uma ação para fixar, sem ganho.

## R5 — Comando

- **Decision**: `./mvnw -B package`, sem nenhum filtro de teste.
- **Rationale**: é a escolha do `decision.md`. Sem failsafe, o `package` passa pela fase `test`
  (surefire, suíte inteira) e pela de empacotamento, e qualquer falha nas duas reprova (FR-004,
  FR-006, FR-007). `verify` daria o mesmo resultado hoje, porque não há nada ligado às fases
  posteriores. `-B` desliga a saída interativa.
- **Alternatives considered**: `./mvnw test` seguido de `package -DskipTests`, rejeitado porque
  são dois comandos e abrem caminho para divergir do comando local.

## R6 — Garantia de que nenhum teste foi pulado

- **Decision**: depois do `package`, um passo verifica nos relatórios do surefire
  (`target/surefire-reports/TEST-*.xml`) que **nenhum** teste aparece como `skipped`. Se algum
  aparecer, o passo falha e reprova a validação.
- **Rationale**: a FR-005 e o SC-003 pedem que nenhum teste de integração seja pulado. Hoje isso
  já vale de forma implícita (sem Docker, eles falham), mas um futuro `assume`/`@Disabled`
  passaria despercebido. A constitution já proíbe `@Disabled` para fazer o build passar, então a
  verificação só torna essa regra objetiva. O critério cobre todos os testes, não só os de
  integração, porque o surefire não distingue categorias e a regra da constitution vale para
  todos.
- **Alternatives considered**: confiar no comportamento atual, que não protege contra regressão
  do próprio controle. Uma flag nativa do surefire para "falhar se houver skip" não existe.
- **Ponto para o dono**: esse passo é a única coisa que o CI exige além do comando local. Um
  teste `@Disabled` passa localmente, mas reprova aqui. Isso é intencional e coerente com a
  constitution, e o dono pode vetar no `/speckit-analyze`.

## R7 — Relatórios na falha

- **Decision**: `actions/upload-artifact` com `name: relatorios-de-teste`, `if: failure()`, `path: target/surefire-reports/`,
  `retention-days: 14` e `if-no-files-found: ignore`.
- **Rationale**: o artefato aparece na página da execução, que é alcançável pelo link
  "Details" do check no PR (FR-010, US3). Numa falha antes dos testes (compilação), não há
  relatório: `ignore` evita um segundo erro que mascararia o primeiro, e o log da execução cobre
  o cenário US3-2. O upload usa o token de runtime do Actions e não precisa de permissão de
  escrita no repositório, então funciona em PR de fork.
- **Alternatives considered**: anotação inline por ação de terceiros, fora de escopo. Upload
  sempre (`always()`), possível mas a spec pede só na falha. 14 dias bastam para diagnóstico.

## R8 — Segurança (repositório público, PRs de fork)

- **Decision**: `permissions: contents: read` no nível do workflow, `persist-credentials: false`
  no checkout, nenhuma referência a `secrets.*` e ações fixadas por **SHA completo** com a versão
  em comentário.
- **Rationale**: atende à FR-011. Com o token só de leitura e sem credencial persistida no
  `.git`, nem o código do PR nem um plugin Maven comprometido conseguem escrever no repositório.
  Fixar por SHA segue a mesma política do `Dockerfile` (bases fixadas por digest) e protege
  contra tag movida. Para PRs de fork, o GitHub já roda `pull_request` sem segredos e pede
  aprovação na primeira contribuição de quem é externo.
- **Alternatives considered**: fixar por tag (`@v7`), que é mutável. Permissões padrão do
  repositório, que podem ser de escrita conforme a configuração.

## R9 — Execuções concorrentes

- **Decision**: `concurrency: group: validacao-pr-${{ github.event.pull_request.number }}` com
  `cancel-in-progress: true`.
- **Rationale**: atende à FR-014. Um commit novo cancela a validação ainda em andamento do commit
  anterior do mesmo PR, e o check exibido é o do commit mais novo. Em qualquer caso, o GitHub
  associa cada check ao SHA validado, então um veredito antigo não aprova um estado novo.
- **Alternatives considered**: sem `concurrency`, que gasta máquina em validações obsoletas sem
  ganho.

## R10 — Teto de duração

- **Decision**: `timeout-minutes: 30` no job.
- **Rationale**: atende à FR-013. Não há medição no runner. A estimativa é de minutos, somando o
  download de imagens (PostgreSQL, Redis, bases do `Dockerfile`), o build da imagem no
  `ImagemContainerTest` e a suíte. 30 min dá folga ampla e corta travamentos (o padrão do GitHub
  é 360 min). O tempo real de cada execução fica no próprio run (SC-007). Se o valor observado
  passar de ~1/3 do teto, revise-o.
- **Alternatives considered**: o padrão de 360 min, que deixaria uma execução travada ocupando
  o check por horas.

## R11 — Nome estável do check

- **Decision**: um workflow `name: Validação de PR`, com **um único job**, id `validar` e
  `name: validar`, sem `matrix`. O check exibido e exigido pelo ruleset é **`validar`**.
- **Rationale**: atende à FR-009. O nome do check run é o `name` do job, e uma matrix anexaria
  parâmetros ao nome. Um nome ASCII curto evita erro de digitação ao configurá-lo no ruleset. O
  arquivo é `.github/workflows/validacao-pr.yml`.
- **Alternatives considered**: separar em jobs (Option B do concept, rejeitada no assessment).

## R12 — Como o TDD se aplica

- **Decision**: a definição do workflow ganha um teste JUnit 5 puro,
  `ValidacaoPullRequestWorkflowTest` (raiz de `src/test/java/br/com/marden/habitattio/`, ao lado
  do `ImagemContainerTest`). Ele lê `.github/workflows/validacao-pr.yml` com SnakeYAML e verifica
  cada invariante do [contrato](contracts/workflow-validacao-pr.md). O teste é escrito e visto
  falhar (o arquivo ainda não existe) **antes** do workflow. A prova de ponta a ponta continua
  sendo os PRs reais do [quickstart](quickstart.md).
- **Rationale**: mantém a feature dentro do Princípio II, sem pedir exceção ao dono (a spec
  exige acordo explícito para isso). O precedente é o `ImagemContainerTest`, que já testa
  infraestrutura de entrega dentro do `./mvnw test`. O teste também roda **no próprio CI**, então
  uma edição futura que quebre o contrato (por exemplo, `-DskipTests`, `secrets.*` ou um gatilho
  `push`) reprova o PR que a fizer.
- **Detalhe técnico**: o SnakeYAML segue o YAML 1.1 e lê a chave `on` como o booleano `true`. O
  teste precisa procurar a chave `Boolean.TRUE` (ou aceitar as duas formas). Esse é o tipo de
  armadilha que justifica ver o teste falhar pelo motivo certo.
- **Dependência**: o SnakeYAML é usado direto no teste sem ser declarado no `pom.xml`, porque já
  chega de forma transitiva. Declará-lo seria mudar o `pom.xml`, o que exige acordo (Princípio
  IV). O risco é ele sumir do classpath numa troca futura do Spring Boot. Nesse caso o teste não
  compila, e a decisão volta ao dono.
- **Alternatives considered**: (a) só verificação manual com PRs reais, que exigiria uma exceção
  ao TDD acordada com o dono e não protege contra regressão do arquivo; (b) `actionlint` ou outra
  ferramenta externa, que é dependência nova e só valida sintaxe, não os requisitos.

## R13 — Proteção de `main` (achado)

- **Fato**: o ruleset `block-main` bloqueia só exclusão e force-push. Ele **não** exige PR nem
  status check, e um `git push` comum (fast-forward) para `main` passa. A frase "push direto já
  bloqueado" do `problem.md` não confere com a configuração atual.
- **Decision**: não muda o escopo da feature. Configurar a proteção continua sendo passo manual do
  dono, mas o [quickstart](quickstart.md) e a documentação (R14) descrevem o passo no lugar
  certo: adicionar ao ruleset `block-main` a regra **Require status checks to pass** com o check
  `validar` (fonte: GitHub Actions). Opcionalmente, adicionar também **Require a pull request
  before merging**, que fecha o push direto. Um ruleset com status check obrigatório já bloqueia,
  sozinho, um push direto sem o check.
- **Rationale**: o SC-002 (0 PRs mergeados com a validação reprovada) depende desse passo. Sem
  ele, a validação roda e informa, mas não bloqueia.

## R14 — Documentação

- **Decision**: criar `docs/validacao-pr.md` (o que roda, como ler a falha, re-run, teto,
  configuração do ruleset, atualização dos SHAs das ações) e adicionar uma subseção curta em
  `AGENTS.md` §1 apontando para ele. Esse formato espelha o `docs/container.md`.
- **Rationale**: atende à FR-017. O AGENTS.md é o guia operacional que os agentes leem, e quem
  abre PR precisa saber que o check existe e como diagnosticá-lo.
- **Alternatives considered**: só o README, que os agentes não são instruídos a ler primeiro.
