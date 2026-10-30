# Research: Imagem de container do habitattio (primeira fatia)

**Feature**: [spec.md](./spec.md) | **Plan**: [plan.md](./plan.md) | **Date**: 2026-10-30

Fontes verificadas nesta etapa (2026-10-30):

- Docker local: Engine/CLI 29.9.1, `linux/amd64`, image store do containerd ligado e **sem o
  plugin `buildx`**.
- Imagens puxadas e inspecionadas: `eclipse-temurin:25-jdk`, `eclipse-temurin:25-jre`,
  `gcr.io/distroless/java25-debian13:latest` e `:nonroot`.
- POM do Maven Central: `spring-boot-starter-actuator:4.1.2`.
- Bytecode do Testcontainers 2.0.5, lido do `~/.m2`.
- Um **spike descartável**, fora do repositório (`$CLAUDE_JOB_DIR/tmp/spike`): um Dockerfile
  multi-stage construído a partir de um `git archive` do HEAD, cold build, rebuild só de código
  e subida no modo rápido.

Itens marcados como "conhecimento prévio" não foram reconferidos e serão provados pelos testes.

---

## R1 — Imagem base de execução: distroless Java 25, variante `nonroot`

- **Decision**: `gcr.io/distroless/java25-debian13:nonroot`, fixada por digest
  `sha256:fc6f4aec634ee433ff16a5d18814181e7928f4be9f434b44b48231e2eb7becee`
  (Temurin 25.0.5+8, Debian 13).
- **Rationale**:
  - Tem só o JRE (sem `jdk.compiler`, sem `javac`), o que atende FR-002.
  - Já roda como uid **65532** (`nonroot`), então FR-009 sai sem `useradd` no Dockerfile.
  - Não tem shell, gerenciador de pacotes, `curl` nem `wget`, o que reduz a superfície que o scan
    da segunda fatia vai analisar.
  - No spike, a imagem final ficou com **136 MB** de conteúdo. A `eclipse-temurin:25-jre` tem
    479 MB de base, contra 306 MB da distroless (tamanhos de disco no store do containerd).
- **Verificado**:
  - `java -version` mostra Temurin 25.0.5+8.
  - `Config.User=65532` e `ENTRYPOINT [/usr/bin/java -jar]`.
  - `--list-modules` traz `java.net.http` e não traz `jdk.compiler`.
  - No spike, `docker top` mostrou o processo da aplicação com UID 65532.
- **Alternatives considered**:
  - *`eclipse-temurin:25-jre`* (Ubuntu 26.04): tem shell, o que ajuda a depurar, mas roda como
    root por padrão (exige criar usuário), é maior e também não tem `curl`/`wget`/`nc`. O
    healthcheck exigiria a mesma solução de R4. Foi rejeitada pelo tamanho e pela superfície.
  - *`:latest` da distroless*: roda como root (`Config.User=0`). Foi rejeitada por FR-009.
- **Custo aceito**: sem shell não há `docker exec ... sh` para depurar na VPS. A depuração fica
  pelos logs (`docker logs`) e pelo endpoint de saúde.

## R2 — Imagem base de build: `eclipse-temurin:25-jdk`

- **Decision**: `eclipse-temurin:25-jdk@sha256:83f4a7fc27df94dcc7b8de04d14cbbba8da14406f921e966969c3b09000aa507`.
- **Rationale**: é o mesmo JDK do host de desenvolvimento (Temurin 25). Tem shell para rodar o
  `./mvnw` e `javac` para compilar o verificador de saúde (R4). Só aparece no estágio de build,
  então não entra na imagem final.
- **Alternatives considered**: imagem `maven:*-temurin-25`, rejeitada porque o projeto usa o
  wrapper (AGENTS.md §1: "sempre `./mvnw`") e a versão do Maven deve vir de
  `.mvn/wrapper/maven-wrapper.properties`, não da imagem.

## R3 — Builder clássico; sem recursos exclusivos do BuildKit

- **Decision**: o Dockerfile usa **só instruções suportadas pelo builder clássico**: sem
  `RUN --mount=type=cache`, sem `# syntax=`, sem `COPY --link`, sem heredocs.
- **Rationale**:
  - O host não tem o plugin `buildx` (`docker buildx` → "unknown command"). O `docker build`
    local roda no builder clássico, como o log do spike mostra (`Step n/16`, "Removed
    intermediate container").
  - O Testcontainers 2.0.5 constrói via docker-java (`BuildImageCmd`), que fala com a API
    `/build` no modo clássico. Um Dockerfile só com recursos clássicos funciona do mesmo jeito
    no CLI do operador e no teste (FR-021).
  - O cache de dependências sai da ordem das camadas (R5), sem cache mount.
- **Risco registrado**: o Docker 29.9.1 avisa que "the legacy builder is deprecated and will be
  removed in a future release". Como o Dockerfile não usa nada exclusivo do clássico, ele também
  constrói igual no BuildKit. Se o clássico sumir, basta o operador instalar o `buildx` e o
  Testcontainers seguir o que o daemon oferecer. Esse ponto se reavalia quando acontecer
  (decision.md: riscos tratados quando aparecerem).
- **Alternatives considered**: instalar o `buildx` e usar cache mount do `~/.m2`. Seria mais
  rápido em rebuilds com `pom.xml` alterado, mas é uma mudança de preparo do host (fora de
  escopo) e o docker-java não usa o BuildKit. Fica para quando houver CI.

## R4 — Verificação de saúde do container sem `curl`/`wget`: classe Java compilada no build

- **Decision**:
  - Uma classe Java mínima, `VerificarSaude`, fica em `docker/VerificarSaude.java`, fora de
    `src/main/java`, para não entrar no jar da aplicação.
  - Ela é compilada no estágio de build com `javac --release 25`.
  - Só a `.class` vai para `/app/verificacao/` na imagem final.
  - A classe usa só `java.base` (`HttpURLConnection` com timeout de conexão e de leitura de 2s).
    Faz `GET http://localhost:8080/actuator/health` e sai com 0 se o status for 200 e 1 em
    qualquer outro caso, inclusive exceção.
  - `HEALTHCHECK` em forma exec, sem shell:
    `CMD ["java", "-XX:TieredStopAtLevel=1", "-XX:+UseSerialGC", "-Xmx16m", "-cp", "/app/verificacao", "VerificarSaude"]`.
  - Parâmetros: `--interval=15s --timeout=5s --start-period=60s --retries=3`.
- **Rationale**:
  - Nenhuma das duas bases de execução tem `curl`, `wget` ou `nc` (verificado). A distroless
    também não tem shell.
  - O launcher de código-fonte (`java Arquivo.java`) exige o `jdk.compiler`, que não existe em
    nenhum dos dois JREs (verificado).
  - A JVM curta, com C1 e Serial GC, custa pouco a cada 15s.
  - O timeout de 2s no cliente HTTP fica abaixo do `--timeout=5s` do Docker. Quando o banco cai e
    o Hikari espera até 30s por conexão, o verificador sai com 1 por conta própria, em vez de ser
    morto pelo Docker; os dois casos contam como falha.
  - Subida medida no spike: 6,8s no modo rápido (Started ... in 6.828 seconds). O
    `start-period=60s` dá folga para o modo produção com as migrações Flyway (edge case "subida
    lenta").
  - Tempo até "unhealthy" depois de o banco cair: no máximo cerca de 3 × 15s.
- **Alternatives considered**:
  - *Copiar o `curl` estático de outra imagem*: traz um binário de terceiros a mais para o scan
    acompanhar. Rejeitado.
  - *Usar o próprio jar da aplicação com um main alternativo*: sobe o classpath inteiro do Spring
    a cada verificação. Rejeitado pelo custo.
  - *Base Temurin JRE + `apt-get install curl`*: R1 rejeitada.
- **Teste**: o verificador não tem teste unitário próprio (vive fora de `src/`). Os cenários
  "saudável" e "não saudável" do teste da imagem (R8) o exercitam pelo estado de saúde que o
  Docker reporta, e não só pelo HTTP.

## R5 — Cache de dependências e camadas do jar

- **Decision**: estágio de build, nesta ordem:
  1. `COPY mvnw pom.xml ./` e `COPY .mvn .mvn`.
  2. `RUN ./mvnw -B -q dependency:go-offline`.
  3. `COPY src src`.
  4. `RUN ./mvnw -B -o package -DskipTests`. Offline e sem testes (FR-003).
  5. `java -Djarmode=tools -jar target/*.jar extract --layers --launcher --destination extracted`.

  Estágio final: 4 `COPY --from=build` em ordem de estabilidade (`dependencies`,
  `spring-boot-loader`, `snapshot-dependencies`, `application`) e
  `ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "org.springframework.boot.loader.launch.JarLauncher"]`.
- **Verificado no spike**:
  - O cold build levou 68s. O `-o package` depois do `go-offline` passou, ou seja, o `go-offline`
    resolve tudo o que o `package -DskipTests` precisa.
  - Depois de mudar só um arquivo em `src/`, o rebuild levou **17,7s**. O passo `go-offline` e as
    camadas `dependencies`, `spring-boot-loader` e `snapshot-dependencies` vieram com
    "Using cache" (SC-005).
  - O `extract --layers --launcher` do Boot 4.1.2 gerou as 4 pastas esperadas.
- **Rationale**:
  - O `-o` garante que, depois de mudar só o código, nenhuma dependência é baixada; um
    `go-offline` incompleto falharia o build, em vez de baixar escondido.
  - O `JarLauncher` sobre as pastas extraídas evita reempacotar.
  - `MaxRAMPercentage=75` substitui o padrão de 25% da JVM em container, que deixaria três
    quartos do limite de memória da VPS ociosos. Ainda pode ser sobrescrito por
    `JAVA_TOOL_OPTIONS`.
- **Alternatives considered**:
  - *Copiar o fat jar inteiro*: qualquer mudança de código reenviaria todas as dependências no
    push. Rejeitado por FR-004.
  - *`extract` sem `--launcher`* (`java -jar app.jar` com `lib/`): funciona, mas deixa
    dependências e aplicação numa mesma camada. Rejeitado.

## R6 — `.dockerignore` em lista de permissão

- **Decision**: `.dockerignore` começa com `*` e libera só o que o build usa: `!mvnw`,
  `!pom.xml`, `!.mvn`, `!src` e `!docker`.
- **Rationale**: atende FR-006 por construção. `.env*`, `target/`, `.git`, `.idea`, `specs/`,
  `.specify/`, `.claude/` e qualquer arquivo novo ficam de fora sem precisar lembrar de listá-los.
  Isso protege contra vazar segredo para o contexto de build (FR-011).
- **Verificado em código**: `ImageFromDockerfile.withDockerfile(Path)` repassa o arquivo ao
  `BuildImageCmd` do docker-java, que usa o diretório do Dockerfile como contexto e aplica o
  `.dockerignore` (conhecimento prévio do docker-java). A verificação prática é o tamanho do
  contexto de build (quickstart V6; ver R8, item 6).
- **Alternatives considered**: lista de exclusão (`target/`, `.git`, ...). Rejeitada porque
  falha aberta: um arquivo novo sensível entraria sem aviso.

## R7 — Actuator: só `health`, sem detalhes, ignorando o Redis

- **Decision**:
  - Dependência nova: `org.springframework.boot:spring-boot-starter-actuator`, com versão do BOM
    4.1.2 (acordo do dono: decision.md, In scope). Nenhuma dependência `-test` correspondente:
    os testes usam MockMvc, que já está no projeto.
  - Propriedades em `application.properties` (base, valem nos dois modos):
    ```properties
    management.endpoints.web.exposure.include=health
    management.endpoints.web.discovery.enabled=false
    management.endpoint.health.show-details=never
    management.endpoint.health.show-components=never
    management.health.redis.enabled=false
    ```
- **Rationale**:
  - `exposure.include=health` é o padrão do Boot, mas fica explícito para FR-015 não depender de
    padrão.
  - `discovery.enabled=false` derruba a página `/actuator`, que listaria os links (FR-015).
  - `show-details` e `show-components=never` fazem a resposta ser só `{"status":"UP"}` ou
    `{"status":"DOWN"}` (FR-014).
  - `management.health.redis.enabled=false` desliga só o indicador de Redis (FR-016). O
    indicador `db` continua ativo, porque há `DataSource`.
  - O status HTTP padrão é 200 para `UP` e 503 para `DOWN`/`OUT_OF_SERVICE` (conhecimento prévio),
    e é isso que o verificador (R4) usa.
- **Evidência de que o modo produção sobe sem Redis**: `EstruturaBancoProducaoTest` já sobe o
  profile `prod` com `spring.data.redis.host=localhost` e sem container de Redis, e passa. A
  conexão do Lettuce é preguiçosa e nenhum bean a usa na subida.
- **A provar por teste**:
  - em Boot 4, o nome da propriedade continua `management.health.redis.enabled`;
  - desligar o Redis não desliga o `db`.

  O caso "PostgreSQL caiu → não saudável" (R8) é a prova direta do segundo ponto.
- **Alternatives considered**:
  - *Grupo de saúde customizado (`management.endpoint.health.group.*`)*: mais configuração para
    o mesmo efeito. Rejeitado.
  - *Porta de management separada*: não pedida. Exporia a porta só a quem estivesse na rede do
    container, mas complica o healthcheck. Rejeitado (YAGNI).

## R8 — Teste da imagem com Testcontainers

- **Decision**: classe `ImagemContainerTest` na raiz do pacote de testes (como
  `EstruturaBancoProducaoTest`), sem contexto Spring.
  - A imagem é construída **uma vez por classe** com
    `new ImageFromDockerfile("habitattio:teste", false).withDockerfile(Path.of("Dockerfile"))`.
    O `false` mantém a imagem e o cache de camadas entre execuções.
  - Os cenários sobem `GenericContainer` dessa imagem.
  - O PostgreSQL é o `postgres:18.6` de `TestcontainersConfiguration.POSTGRES_IMAGE`, numa
    `Network` compartilhada com alias `postgres`.
  - **Não sobe Redis** em nenhum cenário.
  - O HTTP usa `java.net.http.HttpClient`, sem dependência nova.

  Cenários, um comportamento por teste:
  1. `deveFicarSaudavelEmProducaoComPostgresSemRedis`: profile `prod` + `SPRING_DATASOURCE_*` por
     ambiente. Espera o estado de saúde **do Docker** (`State.Health.Status`) virar `healthy`.
  2. `deveFicarNaoSaudavelQuandoPostgresFicaInacessivel`: depois de saudável, para o PostgreSQL e
     espera `unhealthy`. O intervalo do healthcheck é encurtado **só nesse container**, por
     `withCreateContainerCmdModifier` (por exemplo, 2s). O comando continua o da imagem: o daemon
     herda o `Test` da imagem quando o container não define um (conhecimento prévio, mesmo
     comportamento do `docker run --health-interval`; a confirmar na implementação).
  3. `deveSubirSaudavelNoModoRapidoSemBancoExterno`: sem nenhuma variável, sem rede de banco.
     Espera `healthy`.
  4. `deveRodarProcessoDaAplicacaoSemSerRoot`: `topContainerCmd`. A coluna UID do processo
     `java` não pode ser `0`/`root`.
  5. `deveConterSoORuntimeJava`: `execInContainer("java", "--list-modules")` não lista o
     `jdk.compiler`. O `copyFileFromContainer` de `/app/pom.xml` e de `/app/src` falha.
  6. O `.dockerignore` **não** tem cenário próprio aqui. Como cada camada só recebe `COPY`
     explícitos, um arquivo fora da lista nunca chegaria à imagem, mesmo sem `.dockerignore`; o
     que ele protege é o **contexto** enviado ao daemon, que o teste não observa. A verificação
     está no [quickstart.md](./quickstart.md) (V6): o builder clássico imprime
     "Sending build context to Docker daemon", e o tamanho deve ser de centenas de kB, e não do
     tamanho de `target/` + `.git`.
  7. `deveResponderSaudeSoComEstadoGeral` e `naoDeveExporOutrosEndpointsOperacionais` ficam
     **também** num teste rápido, sem imagem (R9), para dar retorno em segundos. No teste da
     imagem basta o cenário 1 conferir que o corpo é exatamente `{"status":"UP"}`.
- **Tempo**: o cold build medido no spike foi 68s; com cache, 18s. A subida é de cerca de 8s por
  container. O total estimado da classe fica entre 1 e 2,5 min. Isso é aceitável dentro do
  `./mvnw test`, que já sobe containers em quatro classes. **Não** se separa a classe do ciclo
  (FR-022). Se ficar lento demais, a reavaliação é feita com o dono, sem pular o teste.
- **Docker ausente**: a classe falha como as demais de integração. O relatório declara isso
  (constitution, Stack). Não há `@Disabled` nem assume condicional.
- **Alternatives considered**:
  - *Chamar o `docker build` do CLI por `ProcessBuilder`*: testaria o caminho exato do operador,
    mas foge do Testcontainers, que foi o acordado. Como R3 garante o mesmo builder, o ganho é
    nulo.
  - *Testar contra uma imagem já construída*: não provaria a receita (FR-021). Rejeitado.

## R9 — Teste rápido da configuração de saúde

- **Decision**: `SaudeAplicacaoTest` com `@SpringBootTest` + `@AutoConfigureMockMvc`, no modo
  rápido (H2, sem Redis rodando). Cenários:
  - `deveResponderSaudavelSemRedis`: `GET /actuator/health` → 200 e `$.status == "UP"`.
  - `deveResponderSoComEstadoGeral`: o corpo não tem `components` nem `details`.
  - `naoDeveExporOutrosEndpointsOperacionais`: `/actuator`, `/actuator/env`,
    `/actuator/metrics`, `/actuator/info`, `/actuator/beans` e `/actuator/configprops` → 404.
- **Rationale**: prova FR-014, FR-015 e FR-016 em segundos e fica **vermelho** antes do Actuator
  existir (404 em `/actuator/health`), o que justifica a dependência e as propriedades (TDD). O
  teste da imagem continua sendo a prova de empacotamento.

## R10 — Publicação, tag imutável e conferência do digest

- **Decision**:
  - **Repositório**: `docker.io/<usuario-dockerhub>/habitattio`, privado. O usuário é do dono e
    não é fixado no repositório.
  - **Tag**: `<versão do pom>-<sha curto do commit>`, por exemplo `0.0.1-SNAPSHOT-3ec450c`. Só é
    gerada com a árvore limpa (`git status --porcelain` vazio). A imagem também recebe os labels
    OCI `org.opencontainers.image.version` e `org.opencontainers.image.revision`, por
    `--build-arg`, declarados **só no fim** do estágio final para não invalidar o cache.
  - **Imutabilidade**: antes do push, `docker manifest inspect <repo>:<tag>` deve **falhar**
    (tag inexistente). Se a conta permitir, liga-se também a opção de tags imutáveis do
    repositório no Docker Hub; ela não foi verificada nesta etapa e é complementar. `latest`
    nunca é publicado.
  - **Digest**: o `docker push` imprime `digest: sha256:...`, que o operador registra. Na VPS, a
    conferência é
    `docker image inspect --format '{{json .RepoDigests}}' $(docker inspect --format '{{.Image}}' <container>)`,
    que deve conter `<repo>@<digest do push>`. Funciona com o store clássico e com o do
    containerd. Recomendação para o compose, que está fora de escopo: referenciar
    `image: <repo>:<tag>@<digest>`, o que torna a divergência impossível.
  - **Pull autenticado**: `docker login` na VPS com um **access token de leitura** do Docker Hub,
    e não com a senha da conta.
- **Rationale**: atende FR-018, FR-019 e FR-020 e SC-007 só com comandos do Docker, sem script
  novo (YAGNI). O procedimento fica documentado (FR-024).
- **Riscos registrados, sem mitigação prévia (decision.md)**:
  - limites do plano do Docker Hub para repositório privado e pulls;
  - suporte a tags imutáveis no plano da conta.
- **Alternatives considered**:
  - *Script `publicar-imagem.sh`*: automatiza a checagem, mas é um artefato a mais para um push
    manual raro. Fica para quando houver CI.
  - *Tag só pela versão do pom*: `SNAPSHOT` se repete entre commits. Rejeitado.

## R11 — Atualização do digest das bases

- **Decision**: documentar em `docs/container.md` o passo manual:
  1. `docker pull` das duas tags (`eclipse-temurin:25-jdk` e
     `gcr.io/distroless/java25-debian13:nonroot`).
  2. Ler os digests novos com `docker image inspect --format '{{index .RepoDigests 0}}'`.
  3. Trocar no `Dockerfile`.
  4. Rodar `./mvnw test`, porque o teste da imagem valida a base nova.
  5. Commitar.

  Os dois digests ficam também como `ARG` no topo do Dockerfile, para a troca ser num lugar só.
- **Rationale**: atende o edge case "imagem base envelhecida" sem automação (fora de escopo).
  O scan da segunda fatia dirá **quando** atualizar.
- **Verificado**: no builder clássico do Docker 29.9.1, um `ARG` antes do `FROM` com
  `imagem@sha256:...` funciona, e um `LABEL` alimentado por `--build-arg` aparece em
  `Config.Labels`.

## R12 — Documentação e governança

- **Decision**:
  - novo `docs/container.md`, com o guia de operação (FR-024): build, tag, publicação, pull
    autenticado, execução nos dois modos com a tabela de variáveis, conferência do digest,
    atualização das bases e o **risco aceito** do modo produção esquecido;
  - AGENTS.md §1 ganha os comandos de imagem, e §9 cita o Actuator no stack;
  - a constitution "Stack Tecnológico e Restrições" passa a citar o Actuator, como emenda
    **PATCH 1.0.1 → 1.0.2**, pelo mesmo procedimento de 001 (T019).
- **Rationale**: Governance da constitution: toda emenda sincroniza o AGENTS.md na mesma
  mudança.
