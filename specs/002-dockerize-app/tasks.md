---

description: "Tasks de dockerize-app: imagem de container do habitattio (primeira fatia)"
---

# Tasks: Imagem de container do habitattio (primeira fatia)

**Input**: Design documents from `specs/002-dockerize-app/`

**Prerequisites**: [plan.md](./plan.md), [spec.md](./spec.md), [research.md](./research.md), [data-model.md](./data-model.md), [contracts/imagem-container.md](./contracts/imagem-container.md), [contracts/saude-http.md](./contracts/saude-http.md), [quickstart.md](./quickstart.md)

**Tests**: **obrigatórios**. A constitution (Princípio II), o AGENTS.md §3 e a spec (FR-021,
US6) exigem TDD: nenhuma mudança de produção sem um teste visto falhar pelo motivo certo antes.
Isso vale para o `pom.xml`, o `application.properties`, o `Dockerfile` e
`docker/VerificarSaude.java`. Um teste visto falhar fica **read-only**: não pode ser editado,
apagado nem marcado `@Disabled` para o build passar. Testes existentes não podem ser alterados.

**Exceção decidida pelo dono (2026-10-30):** o `.dockerignore` fica fora da regra de TDD.
Nenhum teste observa o contexto de build (research R8 item 6). Ele é verificado manualmente em
T026 (quickstart V6), e a exceção é declarada no relatório.

**Organization**: tasks agrupadas por user story da spec, em ordem de prioridade e de
dependência: US1, US2 e US6 (P1); US3 (P2); US4 e US5 (P3).

## Format: `[ID] [P?] [Story] Description`

- **[P]**: pode rodar em paralelo (arquivo diferente, sem dependência pendente)
- **[Story]**: a user story atendida (US1–US6)
- Caminhos relativos à raiz do repositório. Pacote de testes: `br.com.marden.habitattio`
  (`src/test/java/br/com/marden/habitattio/`).

## Decisões de sequenciamento (TDD)

- **Receita antes da saúde**: a US1 cria o Dockerfile **sem** `HEALTHCHECK` e prova subida,
  recusa, não-root, só-runtime, labels e plataforma, com espera por log
  (`Started HabitattioApplication`) ou inspeção da imagem. A US2
  acrescenta o Actuator, o verificador e o `HEALTHCHECK`. Os cenários de saúde do container
  ficam vermelhos com `State.Health` nulo até lá (plan, Ordem TDD 3–4).
- **Contrato HTTP antes do container**: `SaudeAplicacaoTest` (rápido, MockMvc) justifica a
  dependência e as propriedades `management.*`. O teste da imagem justifica o verificador e o
  `HEALTHCHECK`.
- **Testes de caracterização**: alguns cenários passam de primeira porque uma decisão anterior já
  os atende, como a base `nonroot` e o padrão do Actuator de esconder detalhes. Eles **não
  justificam código**; cada mudança de produção tem ao menos um teste vermelho próprio. Os casos
  previsíveis estão marcados nas tasks e **devem ser declarados no relatório**.
- **Builder do teste**: o `ImageFromDockerfile` usa o builder **clássico**; o operador usa o
  BuildKit (research R3). O Dockerfile não pode usar nada exclusivo do BuildKit: sem
  `RUN --mount`, sem `# syntax=`, sem `COPY --link`, sem heredocs.

---

## Phase 1: Setup

**Purpose**: confirmar o ponto de partida antes de qualquer mudança.

- [X] T001 Rodar `./mvnw test` com Docker no ar e registrar no relatório que a suíte atual (`HabitattioApplicationTests`, `ModoRapidoTest`, `EstruturaBancoProducaoTest`, testes de `adapters/`, `application/` e `domain/`) está verde **antes** de qualquer alteração. Se não estiver, pare e reporte.
- [X] T002 Conferir o ambiente de build e registrar as versões no relatório: `docker version` (Engine 29.x, `linux/amd64`), `docker buildx version` (BuildKit disponível) e `DOCKER_BUILDKIT=0 docker build --help` sem erro (builder clássico ainda disponível, necessário para o `ImageFromDockerfile`). Puxar as bases fixadas em research R1/R2 e conferir que os digests ainda resolvem: `docker pull eclipse-temurin:25-jdk@sha256:83f4a7fc27df94dcc7b8de04d14cbbba8da14406f921e966969c3b09000aa507` e `docker pull gcr.io/distroless/java25-debian13:nonroot@sha256:fc6f4aec634ee433ff16a5d18814181e7928f4be9f434b44b48231e2eb7becee`.

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: a estrutura do teste da imagem, compartilhada por US1, US2, US4 e US6.

**⚠️ CRITICAL**: nenhum cenário de imagem pode ser escrito antes desta fase.

- [X] T003 Criar `src/test/java/br/com/marden/habitattio/ImagemContainerTest.java` **sem métodos de teste**, só a infraestrutura, sem contexto Spring (como `EstruturaBancoProducaoTest`):
  - (a) Um campo estático `IMAGEM = new ImageFromDockerfile("habitattio:teste", false).withDockerfile(Path.of("Dockerfile")).withBuildArg("VERSION", "teste").withBuildArg("REVISION", "teste")`. Os build args alimentam o cenário dos labels (T004). O `false` mantém a imagem e o cache; o `LazyFuture` constrói uma única vez por JVM.
  - (b) Uma `Network` estática compartilhada.
  - (c) `novoPostgres()`: `PostgreSQLContainer` com `TestcontainersConfiguration.POSTGRES_IMAGE`, `.withNetwork(rede).withNetworkAliases("postgres")`.
  - (d) `containerProducao(PostgreSQLContainer pg)`: `GenericContainer<>(IMAGEM)` na mesma rede, com `withExposedPorts(8080)` e as variáveis de ambiente:
    - `SPRING_PROFILES_ACTIVE=prod`;
    - `SPRING_DATASOURCE_URL=jdbc:postgresql://postgres:5432/` + `pg.getDatabaseName()`;
    - `SPRING_DATASOURCE_USERNAME` / `SPRING_DATASOURCE_PASSWORD` do container.

    **Sem Redis** e sem estratégia de espera (cada teste define a sua).
  - (e) `containerRapido()`: `GenericContainer<>(IMAGEM)` com `withExposedPorts(8080)`, sem nenhuma variável e sem rede de banco.
  - (f) `get(GenericContainer<?> c, String caminho)`: devolve status e corpo via `java.net.http.HttpClient` em `http://` + `c.getHost()` + `:` + `c.getMappedPort(8080)` + caminho.
  - (g) `estadoDeSaude(GenericContainer<?> c)`: lê `c.getCurrentContainerInfo().getState().getHealth()` e devolve `null` se o container não tem healthcheck, ou o `getStatus()` (`starting`/`healthy`/`unhealthy`).

  Imports explícitos, sem wildcard. Rodar `./mvnw test-compile` e confirmar que compila.

**Checkpoint**: infraestrutura do teste da imagem pronta.

---

## Phase 3: User Story 1 - Subir a aplicação em produção a partir da imagem (Priority: P1) 🎯 MVP

**Goal**: construir a imagem a partir do repositório e rodá-la no modo produção, ligada ao
PostgreSQL, num host só com o runtime de containers, sem root e só com o JRE.

**Independent Test**: `./mvnw test -Dtest=ImagemContainerTest` com os cenários desta fase;
quickstart V1 e V4 (parte "subida").

### Tests for User Story 1 (escrever ANTES da receita) ⚠️

- [X] T004 [US1] Em `src/test/java/br/com/marden/habitattio/ImagemContainerTest.java`, adicionar os cenários abaixo, um comportamento por teste, em Arrange/Act/Assert:
  - `deveSubirEmProducaoComPostgresSemRedis`: `containerProducao` com `Wait.forLogMessage(".*Started HabitattioApplication.*", 1)` e timeout de 2 min. Afirma `isRunning()` e que `GET /imoveis` responde com status diferente de 5xx: 405 é aceito, porque o endpoint só tem `POST`; a afirmação é "a aplicação responde na porta configurada".
  - **Estratégia comum aos dois cenários de falha a seguir** (o container precisa continuar existindo para o código de saída ser lido; não confie em `ContainerLaunchException`, porque o Testcontainers remove o container quando o `start()` falha):
    1. `waitingFor(Wait.forLogMessage(".*Application run failed.*", 1))` com timeout de 2 min;
    2. depois do `start()`, poll de até 30s em `getCurrentContainerInfo().getState().getRunning()` até ficar `false`;
    3. afirmar `getState().getExitCodeLong()` ≠ 0.
  - `deveRecusarSubidaEmProducaoQuandoBancoDiverge`: antes de subir a aplicação, cria no PostgreSQL, por JDBC direto, `CREATE TABLE imoveis (id bigint)`, o que deixa o schema não vazio e sem histórico Flyway. Usa a estratégia acima e afirma que o container parou com código ≠ 0. Spec US1 cenário 2; contrato E9.
  - `naoDeveSubirEmProducaoSemVariaveisDeConexao`: container com só `SPRING_PROFILES_ACTIVE=prod`, sem `SPRING_DATASOURCE_*`. Usa a estratégia acima, afirma que o container parou com código ≠ 0 e que o log **não** contém `Started HabitattioApplication`. Edge case da spec: não cai em silêncio para o banco em memória; contrato E10.
  - `deveRodarProcessoDaAplicacaoSemSerRoot`: sobe o **seu próprio** `containerProducao`, com `Wait.forLogMessage(".*Started HabitattioApplication.*", 1)`, e roda `DockerClientFactory.instance().client().topContainerCmd(id).exec()`. Na coluna `UID` (índice obtido de `getTitles()`), a linha do processo `java` vale `"65532"` e não `"0"`/`"root"`. FR-009; data-model "Usuário: uid 65532 (`nonroot`)".
  - `deveConterSoORuntimeJava`: `execInContainer("java", "--list-modules")` termina com 0 e o stdout **não** contém `jdk.compiler`; `copyFileFromContainer("/app/pom.xml", …)`, `copyFileFromContainer("/app/src", …)` e `copyFileFromContainer("/app/target", …)` lançam exceção. FR-002; data-model "não contém `jdk.compiler`, `src/`, `pom.xml` nem `target/`".
  - `deveRegistrarVersaoERevisaoNosLabels`: `DockerClientFactory.instance().client().inspectImageCmd("habitattio:teste").exec()` (força a construção antes, por exemplo com `IMAGEM.get()`). `getConfig().getLabels()` contém `org.opencontainers.image.version=teste` e `org.opencontainers.image.revision=teste`. Contrato C6; é o teste que justifica o `LABEL` de T007.
  - `deveSerConstruidaParaLinuxAmd64`: o mesmo `inspectImageCmd` dá `getOs()` igual a `"linux"` e `getArch()` igual a `"amd64"`. FR-008; contrato C7.
- [X] T005 [US1] Rodar `./mvnw test -Dtest=ImagemContainerTest` e **ver todos falharem pelo motivo certo**: o build da imagem falha porque o `Dockerfile` não existe. Registrar a mensagem no relatório. A partir daqui os sete testes são read-only.

### Implementation for User Story 1

- [X] T006 [P] [US1] **(Exceção de TDD decidida pelo dono, sem teste vermelho; verificado em T026.)** Criar `.dockerignore` na raiz em **lista de permissão** (research R6), exatamente com: `*`, `!mvnw`, `!pom.xml`, `!.mvn`, `!src` e `!docker`. FR-006: `.env*`, `target/`, `.git`, `specs/`, `.specify/` e `.claude/` ficam fora por construção.
- [X] T007 [US1] Criar `Dockerfile` na raiz, **sem `HEALTHCHECK`** (vem na US2), só com instruções comuns ao clássico e ao BuildKit (research R3, R5, R11):
  - **Topo**:
    - `ARG BASE_BUILD=eclipse-temurin:25-jdk@sha256:83f4a7fc27df94dcc7b8de04d14cbbba8da14406f921e966969c3b09000aa507`;
    - `ARG BASE_RUNTIME=gcr.io/distroless/java25-debian13:nonroot@sha256:fc6f4aec634ee433ff16a5d18814181e7928f4be9f434b44b48231e2eb7becee`.
  - **Estágio `build`** (`FROM ${BASE_BUILD} AS build`, `WORKDIR /build`), nesta ordem:
    1. `COPY mvnw pom.xml ./`;
    2. `COPY .mvn .mvn`;
    3. `RUN ./mvnw -B -q dependency:go-offline`;
    4. `COPY src src`;
    5. `RUN ./mvnw -B -q -o package -DskipTests`. Offline e sem testes, FR-003;
    6. `RUN java -Djarmode=tools -jar target/*.jar extract --layers --launcher --destination extracted`.
  - **Estágio final** (`FROM ${BASE_RUNTIME}`, `WORKDIR /app`):
    - quatro `COPY --from=build /build/extracted/<camada>/ ./`, na ordem `dependencies`, `spring-boot-loader`, `snapshot-dependencies` e `application` (FR-004);
    - `EXPOSE 8080`;
    - `ARG VERSION=dev`, `ARG REVISION=dev` e `LABEL org.opencontainers.image.version=${VERSION} org.opencontainers.image.revision=${REVISION}`, **depois** das cópias, para não invalidar o cache;
    - `ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]`. Sem flags de JVM; o ajuste, se precisar, vem por `JAVA_TOOL_OPTIONS` em runtime (research R5).
  - **Proibições**: nenhum `ENV` com credencial (FR-011) e nenhum `USER`, porque a base já é uid 65532.
- [X] T008 [US1] Rodar `./mvnw test -Dtest=ImagemContainerTest` até os sete cenários da US1 ficarem verdes, sem tocar nos testes. Declarar no relatório como **caracterização** os cenários que já passam só por decisões anteriores, para que não justifiquem código: `deveRodarProcessoDaAplicacaoSemSerRoot` (base `nonroot`), `deveRecusarSubidaEmProducaoQuandoBancoDiverge` e `naoDeveSubirEmProducaoSemVariaveisDeConexao` (comportamento já existente do profile `prod`) e `deveSerConstruidaParaLinuxAmd64` (o host de build já é amd64). Declarar também a exceção de TDD do `.dockerignore` (T006). Rodar também `docker build -t habitattio:local .` (BuildKit) e confirmar que constrói (contrato C1/C2).

**Checkpoint**: a imagem sobe em produção, recusa banco divergente e configuração ausente, roda
sem root e só tem o JRE. A US1 é entregável sozinha (sem healthcheck).

---

## Phase 4: User Story 2 - O orquestrador sabe se a aplicação está saudável (Priority: P1)

**Goal**: endpoint de saúde só com o estado geral, considerando o banco e ignorando o Redis, e um
healthcheck de container que funciona sem `curl`/`wget`.

**Independent Test**: `./mvnw test -Dtest=SaudeAplicacaoTest` (segundos) e
`./mvnw test -Dtest=ImagemContainerTest` (cenários de saúde); quickstart V2, V3 e V4.

### Tests for User Story 2 — contrato HTTP (escrever ANTES do Actuator) ⚠️

- [X] T009 [P] [US2] Criar `src/test/java/br/com/marden/habitattio/SaudeAplicacaoTest.java` com `@SpringBootTest` e `@AutoConfigureMockMvc` (`org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc`). Ele roda no modo rápido, **sem importar `TestcontainersConfiguration`**, ou seja, sem Redis rodando. Contrato [contracts/saude-http.md](./contracts/saude-http.md). Cenários:
  - `deveResponderSaudavelSemRedis`: `GET /actuator/health` responde 200 com `$.status` igual a `"UP"`.
  - `deveResponderSoComEstadoGeral`: o corpo de `GET /actuator/health` não tem `components`, `details` nem `groups` (`jsonPath(...).doesNotExist()`).
  - `naoDeveExporOutrosEndpointsOperacionais`: `/actuator`, `/actuator/env`, `/actuator/metrics`, `/actuator/info`, `/actuator/beans` e `/actuator/configprops` respondem 404 cada um.
- [X] T010 [US2] Rodar `./mvnw test -Dtest=SaudeAplicacaoTest` e **ver falhar pelo motivo certo**: `/actuator/health` responde 404, porque não há Actuator. Os três testes ficam read-only daqui em diante.

### Implementation for User Story 2 — contrato HTTP

- [X] T011 [US2] Adicionar ao `pom.xml`, junto dos outros starters, `org.springframework.boot:spring-boot-starter-actuator` **sem versão** (BOM 4.1.2; acordo do dono no decision.md; constitution §IV). Nenhuma dependência `-test` nova. Rodar `./mvnw test -Dtest=SaudeAplicacaoTest` e registrar qual cenário ainda está vermelho e por quê. Esperado:
  - `naoDeveExporOutrosEndpointsOperacionais` vermelho, porque `/actuator` (discovery) responde 200;
  - `deveResponderSaudavelSemRedis` provavelmente vermelho, porque o indicador de Redis fica `DOWN` sem Redis.

  Quem passar só com a dependência (por exemplo `deveResponderSoComEstadoGeral`, pelo padrão do Boot) é declarado como caracterização.
- [X] T012 [US2] Adicionar a `src/main/resources/application.properties` (base, vale nos dois modos), com um comentário curto em português no estilo do arquivo, exatamente:
  ```properties
  management.endpoints.web.exposure.include=health
  management.endpoints.web.discovery.enabled=false
  management.endpoint.health.show-details=never
  management.endpoint.health.show-components=never
  management.health.redis.enabled=false
  ```
  São os valores de research R7. Rodar `./mvnw test -Dtest=SaudeAplicacaoTest` até ficar verde. Se o nome `management.health.redis.enabled` não tiver efeito no Boot 4.1.2, o teste continua vermelho: corrija a **propriedade** (procure o nome equivalente no `spring-boot-data-redis` 4.1.2), nunca o teste.

### Tests for User Story 2 — saúde do container (escrever ANTES do HEALTHCHECK) ⚠️

- [X] T013 [US2] Em `src/test/java/br/com/marden/habitattio/ImagemContainerTest.java`, adicionar:
  - `deveFicarSaudavelEmProducaoComPostgresSemRedis`: `containerProducao` com `Wait.forHealthcheck()` e timeout de 2 min. Afirma `estadoDeSaude(c)` igual a `"healthy"` e `GET /actuator/health` com 200 e corpo **exatamente** `{"status":"UP"}`.
  - `deveFicarNaoSaudavelQuandoPostgresFicaInacessivel`: o container de produção encurta o intervalo **só neste teste**, com `withCreateContainerCmdModifier(cmd -> cmd.withHealthcheck(new HealthCheck().withInterval(2_000_000_000L).withRetries(3)))`, sem definir `Test`, para herdar o comando da imagem (contrato H5).
    - Se o daemon não herdar o comando, defina `Test` com o **mesmo** array lido de `docker image inspect` (`Config.Healthcheck.Test`) e registre isso no relatório.
    - Espera `healthy`, chama `postgres.stop()` e espera até 60s, por polling de `estadoDeSaude`, até ficar `"unhealthy"`.
    - Afirma `"unhealthy"` e `GET /actuator/health` com status 503.
- [X] T014 [US2] Rodar `./mvnw test -Dtest=ImagemContainerTest` e **ver os dois cenários novos falharem pelo motivo certo**: sem `HEALTHCHECK`, `estadoDeSaude` é `null` e o `Wait.forHealthcheck()` estoura. Os cinco da US1 continuam verdes. Registrar no relatório. Os dois testes ficam read-only.

### Implementation for User Story 2 — saúde do container

- [X] T015 [P] [US2] Criar `docker/VerificarSaude.java`: classe `final` no pacote padrão, só com `java.base` (research R4).
  - `main` usa a URL fixa `http://localhost:8080/actuator/health`. A porta é a do `EXPOSE` e do data-model; sem leitura de `SERVER_PORT` (YAGNI, research R4).
  - Abre um `HttpURLConnection` `GET` com `setConnectTimeout(2000)` e `setReadTimeout(2000)`.
  - Chama `System.exit(0)` se o status for 200 e `System.exit(1)` em qualquer outro status ou exceção (`catch (Exception e)`).
  - Sem saída obrigatória; nomes e comentário em português; imports explícitos.
- [X] T016 [US2] No `Dockerfile`:
  - **Estágio `build`**: depois do `extract`, `COPY docker/VerificarSaude.java docker/` e `RUN javac --release 25 -d verificacao docker/VerificarSaude.java`.
  - **Estágio final**, antes dos `ARG`/`LABEL`:
    - `COPY --from=build /build/verificacao/ /app/verificacao/`;
    - `HEALTHCHECK --interval=15s --timeout=5s --start-period=60s --retries=3 CMD ["java", "-XX:TieredStopAtLevel=1", "-XX:+UseSerialGC", "-Xmx16m", "-cp", "/app/verificacao", "VerificarSaude"]`, em forma exec, sem shell (contrato H1/H2).
- [X] T017 [US2] Rodar `./mvnw test -Dtest=ImagemContainerTest` e `./mvnw test -Dtest=SaudeAplicacaoTest` até tudo ficar verde, sem tocar em teste. Conferir quickstart V2: `docker image inspect … --format '{{json .Config.Healthcheck}}'` mostra os parâmetros de H2, e `Config.User` é `65532`.

**Checkpoint**: saúde correta no endpoint e no container; Redis ignorado; banco considerado.

---

## Phase 5: User Story 6 - Teste automatizado prova a imagem (Priority: P1)

**Goal**: provar que o teste da imagem roda no ciclo normal e pega uma receita quebrada.

**Independent Test**: `./mvnw test` com Docker; mutação controlada no Dockerfile.

- [X] T018 [US6] **Verificação por mutação** (US6 cenário 4, SC-008):
  1. Trocar temporariamente no `Dockerfile` o `COPY --from=build /build/extracted/application/ ./` por um caminho inexistente (`/build/extracted/aplicacao/`).
  2. Rodar `./mvnw test -Dtest=ImagemContainerTest` e confirmar que **falha** (build da imagem).
  3. Restaurar o `Dockerfile` (`git diff Dockerfile` vazio em relação ao estado de T016) e rodar de novo até ficar verde.

  Registrar os dois resultados no relatório. Não commitar a mutação.
- [X] T019 [US6] Confirmar que `ImagemContainerTest` e `SaudeAplicacaoTest` rodam dentro de `./mvnw test`, sem exclusão no Surefire e sem `@Disabled` ou assume condicional (FR-022). Medir e registrar o tempo da classe `ImagemContainerTest`, frio (após `docker rmi habitattio:teste`) e com cache. Se passar de cerca de 5 min com cache, **não** separe nem pule: reporte ao dono (research R8).

**Checkpoint**: a receita é provada automaticamente a cada `./mvnw test`.

---

## Phase 6: User Story 3 - Garantir que a VPS roda exatamente a imagem publicada (Priority: P2)

**Goal**: procedimento documentado de publicação com tag imutável, pull autenticado e
conferência de digest.

**Independent Test**: quickstart V8–V10 (feito pelo dono, porque depende do Docker Hub e da
VPS).

- [X] T020 [US3] Criar `docs/container.md` (guia de operação, FR-024) com a seção **"Publicação no Docker Hub"** (research R10, R3). Conteúdo:
  - repositório `docker.io/<usuario-dockerhub>/habitattio` **privado**;
  - tag `<versão do pom>-<sha curto>`, só com `git status --porcelain` vazio, e **nunca `latest`**;
  - build com `--build-arg VERSION=… --build-arg REVISION=…`;
  - checagem prévia com `docker manifest inspect <repo>:<tag>`, que deve **falhar**; se não falhar, a tag já existe e o push não é feito;
  - opção de tags imutáveis do Docker Hub, se o plano permitir (não verificada);
  - `docker push` e anotação do digest impresso, que é o **do índice**, com a atestação de proveniência do BuildKit;
  - na VPS, `docker login` com um **access token só de leitura**, e não a senha;
  - pull por `<repo>:<tag>@<digest>`;
  - conferência com `docker image inspect --format '{{json .RepoDigests}}' "$(docker inspect --format '{{.Image}}' <container>)"`, que deve conter `<repo>@<digest>`;
  - recomendação de fixar `image: <repo>:<tag>@<digest>` no compose (fora de escopo);
  - riscos sem mitigação prévia: limites do plano do Docker Hub e suporte a tags imutáveis.

  Escrever em português, no tom de `docs/persistencia.md`.
- [X] T021 [US3] Registrar no relatório que quickstart V8–V10 (push, pull autenticado, digest na VPS) **não foram executados** por depender da conta do Docker Hub e da VPS do dono, e que ficam para a primeira instalação (SC-001, SC-007).

**Checkpoint**: o operador tem o procedimento completo de publicação e conferência.

---

## Phase 7: User Story 4 - Mesma imagem no modo rápido para desenvolvimento (Priority: P3)

**Goal**: a mesma imagem sobe sem profile, em H2, saudável, sem JDK no host; o fluxo
`./mvnw` atual não muda.

**Independent Test**: `ImagemContainerTest.deveSubirSaudavelNoModoRapidoSemBancoExterno`;
quickstart V3.

- [X] T022 [US4] Em `src/test/java/br/com/marden/habitattio/ImagemContainerTest.java`, adicionar `deveSubirSaudavelNoModoRapidoSemBancoExterno`: `containerRapido()` com `Wait.forHealthcheck()` e timeout de 2 min. Afirma `"healthy"`, `GET /actuator/health` com 200 e o log contendo `No active profile set`. Rodar: passa de primeira, porque FR-010 já é atendido pela configuração base e pelo healthcheck da US2. **Declarar como caracterização** no relatório; nenhum código de produção é escrito por causa dele.
- [X] T023 [US4] Em `docs/container.md`, adicionar a seção **"Execução"**:
  - os dois modos, com a tabela de variáveis do data-model ("Configuração de execução"): `SPRING_PROFILES_ACTIVE`, `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD` e `JAVA_TOOL_OPTIONS`, com obrigatoriedade por modo;
  - exemplos de `docker run` para o modo rápido e para o produção, em que os segredos vêm de variável ou de `.env` fora do git, nunca do Dockerfile;
  - porta 8080;
  - estados de saúde e tempos: até cerca de 60s para `unhealthy` e `start-period` de 60s;
  - depuração sem shell (`docker logs` e `/actuator/health`);
  - **o risco aceito**: sem `SPRING_PROFILES_ACTIVE=prod` a imagem sobe em H2 com dados voláteis, inclusive na VPS, e nada impede isso (FR-012; decisão do dono de 2026-10-30).
- [X] T024 [US4] Confirmar que o fluxo local não mudou (FR-023): `ModoRapidoTest` e `HabitattioApplicationTests` continuam verdes e intactos (`git diff --stat src/test` sem alteração neles), e `./mvnw spring-boot:run` sobe na porta 8080 em H2. Encerrar depois de ver `Started HabitattioApplication`. Rodar também `./mvnw spring-boot:test-run` (`TestHabitattioApplication`, profile `prod` com containers), ver `Started HabitattioApplication` com `The following 1 profile is active: "prod"` e encerrar.

**Checkpoint**: desenvolvimento com a mesma imagem, sem JDK, documentado.

---

## Phase 8: User Story 5 - Rebuild rápido quando só o código mudou (Priority: P3)

**Goal**: comprovar o reaproveitamento de dependências e o contexto enxuto.

**Independent Test**: quickstart V5 e V6 (verificação manual; o teste automatizado não observa
cache nem contexto, research R8 item 6).

- [X] T025 [US5] Executar quickstart **V5**:
  1. Construir com `docker build --progress=plain -t habitattio:local .`.
  2. Fazer uma mudança **real** e temporária em `src/main/java` (por exemplo, um método privado não usado em `HabitattioApplication`) e reconstruir.
  3. Confirmar `CACHED` no `dependency:go-offline` e nas cópias de `dependencies`, `spring-boot-loader` e `snapshot-dependencies`, e zero `Downloading from`.
  4. Desfazer a mudança.

  Registrar o tempo do rebuild (SC-005).
- [X] T026 [US5] Executar quickstart **V6**:
  1. `touch .env.sentinela`.
  2. Rodar o build e anotar o tamanho da linha `transferring context` do contexto principal. Deve ser de dezenas a centenas de kB, e não dezenas de MB.
  3. `rm .env.sentinela`.

  Registrar o valor (FR-006, contrato C5).

**Checkpoint**: eficiência de build comprovada.

---

## Phase 9: Polish & Cross-Cutting Concerns

- [X] T027 Em `docs/container.md`, completar as seções restantes:
  - **"Construção"**: `docker build` com BuildKit; o teste usa o builder clássico; sem testes no build, já que a suíte roda antes no host.
  - **"Atualização das imagens base"**, research R11:
    1. `docker pull` das duas tags (`eclipse-temurin:25-jdk` e `gcr.io/distroless/java25-debian13:nonroot`);
    2. `docker image inspect --format '{{index .RepoDigests 0}}'`;
    3. trocar os `ARG BASE_BUILD`/`BASE_RUNTIME`;
    4. `./mvnw test`;
    5. commit.
  - **"Fora de escopo nesta fatia"**: scan de vulnerabilidades (segunda fatia), compose, CI.

  Adicionar um link para `docs/container.md` no `README.md`, seção "Como rodar", com uma subseção curta "Com a imagem de container".
- [X] T028 Atualizar `AGENTS.md`:
  - §1, comandos da imagem: `docker build -t habitattio:local .`, os dois `docker run` (rápido e `prod` por variáveis), a nota de que `ImagemContainerTest` constrói a imagem dentro de `./mvnw test` e exige Docker, e um link para `docs/container.md`;
  - §9, Limites: o Actuator passa a integrar o stack, e só o endpoint `health` é exposto.
- [X] T029 Emendar `.specify/memory/constitution.md`:
  - versão **PATCH 1.0.1 → 1.0.2**;
  - a seção "Stack Tecnológico e Restrições" passa a citar o Spring Boot Actuator (só `health`);
  - o "Sync Impact Report" no topo é reescrito, no formato do atual: origem `002-dockerize-app` (T029), sem mudança de princípio, sincronização com o AGENTS.md feita em T028;
  - a linha final passa a `**Version**: 1.0.2 | **Ratified**: 2026-10-29 | **Last Amended**: <data da implementação>`.
- [X] T030 Rodar a suíte completa `./mvnw test` com Docker e registrar o resultado. Se o Docker não estiver disponível, dizer explicitamente que os testes de integração (inclusive `ImagemContainerTest`) não rodaram.
- [X] T031 Executar o quickstart **V1–V4 e V7** contra a imagem construída com BuildKit (`$REPO:$TAG`, a mesma que iria para o push) e registrar cada resultado esperado/obtido. No V7, incluir `docker image inspect "$REPO:$TAG" --format '{{.Os}}/{{.Architecture}}'`, que deve dar `linux/amd64` (FR-008). Isso fecha a lacuna entre o builder do teste e o do operador (research R3).
- [X] T032 Checklist de Definition of Done (AGENTS.md §8), registrado no relatório:
  - `git diff --stat main -- src/main/java` vazio, ou seja, `domain/`, `application/`, `ports/` e `adapters/` intocados;
  - nenhum teste pré-existente alterado (`git diff main --stat -- src/test` mostra só `ImagemContainerTest.java` e `SaudeAplicacaoTest.java` novos);
  - nenhum `@Disabled`;
  - testes de caracterização declarados (T008, T011, T022);
  - exceção de TDD do `.dockerignore` declarada (decisão do dono, 2026-10-30), com o resultado de T026;
  - Dockerfile sem `RUN --mount`, `# syntax`, `COPY --link`, heredoc, `ENV` com credencial ou flags de JVM no `ENTRYPOINT`;
  - spec, plan e tasks coerentes com o que foi feito;
  - imagens locais de teste/validação removidas, se não forem úteis (`habitattio:local`, `habitattio:rebuild`, `habitattio:ctx`).

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: sem dependências.
- **Foundational (Phase 2)**: depende do Setup e **bloqueia** US1, US2, US4 e US6.
- **US1 (Phase 3)**: depende da Foundational. Cria o `Dockerfile` e o `.dockerignore`.
- **US2 (Phase 4)**: o contrato HTTP (T009–T012) só depende do Setup e pode correr em paralelo à
  US1. A parte de container (T013–T017) depende da US1, porque altera o `Dockerfile` e o mesmo
  arquivo de teste.
- **US6 (Phase 5)**: depende de US1 + US2 (mutação sobre a receita completa).
- **US3 (Phase 6)**: depende de US1 (há imagem para publicar). A documentação pode começar logo
  após T008.
- **US4 (Phase 7)**: depende de US2 (precisa do `HEALTHCHECK` para `healthy`).
- **US5 (Phase 8)**: depende de US1 (há Dockerfile), e melhor depois de US2, com a receita final.
- **Polish (Phase 9)**: depende de todas.

### Story order

```text
Setup → Foundational → US1 ─┬─▶ US2 (container) ─▶ US6
                            │        └─▶ US4
                            ├─▶ US3 (docs)
                            └─▶ US5
US2 (contrato HTTP, T009–T012) pode correr junto da US1
Todas → Polish
```

### Within Each User Story

- Teste escrito → visto falhar pelo motivo certo → read-only → implementação → verde.
- `ImagemContainerTest.java`, `Dockerfile` e `docs/container.md` são arquivos compartilhados entre
  stories: as tasks que os tocam são **sequenciais** (sem `[P]`).

### Parallel Opportunities

- T006 (`.dockerignore`) em paralelo com o início de T007 (arquivos diferentes).
- T009–T012 (`SaudeAplicacaoTest`, `pom.xml`, `application.properties`) em paralelo com toda a
  US1 (T004–T008), porque não compartilham arquivo.
- T015 (`docker/VerificarSaude.java`) em paralelo com T013/T014.
- T020 (docs de publicação) em paralelo com a US2, depois de T008.

### Parallel Example: US1 + contrato HTTP da US2

```text
Trilha A: T004 → T005 → (T006 ∥ T007) → T008
Trilha B: T009 → T010 → T011 → T012
Junção:   T013 → T014 → T015 ∥ … → T016 → T017
```

---

## Implementation Strategy

### MVP First (User Story 1)

1. Setup (T001–T002) e Foundational (T003).
2. US1 (T004–T008): imagem que sobe em produção, sem root e só com JRE.
3. **Parar e validar**: `./mvnw test -Dtest=ImagemContainerTest` e quickstart V1/V4 (subida). A
   primeira instalação já seria possível, ainda sem healthcheck.

### Incremental Delivery

1. US1 → imagem executável (MVP).
2. US2 → saúde no endpoint e no container (completa o "pronto para produção").
3. US6 → prova por mutação e tempo no ciclo.
4. US3 → publicação e digest documentados (habilita a instalação na VPS).
5. US4 + US5 → modo rápido documentado e eficiência comprovada.
6. Polish → docs, governança e validação final.

---

## Notes

- `[P]` = arquivo diferente e sem dependência pendente.
- Todo teste visto falhar é read-only. Se a implementação parecer exigir mudar um teste, o
  problema é a implementação.
- Se um teste pré-existente quebrar (por exemplo, por causa do Actuator), **pare** e reporte
  antes de tocar nele (AGENTS.md §3).
- Commit sugerido por fase, com mensagens no estilo do histórico (`feat(...)`, `test(...)`,
  `docs(...)`).

## Notas de implementação (2026-10-30)

Registro do que mudou em relação ao texto das tasks. Detalhes em research.md R13.

- **T008**: o `.dockerignore` ganhou `!Dockerfile`, porque o docker-java exige que o
  Dockerfile esteja no contexto.
- **T008**: `ImageFromDockerfile` com `Path.of("Dockerfile").toAbsolutePath()`. É uma edição
  num teste read-only, **autorizada pelo dono**, sem mudança de asserção.
- **T011/T012**:
  - os três cenários de `SaudeAplicacaoTest` ficaram vermelhos com a dependência (discovery
    200, `groups` presente, 503 pelo Redis), então **nenhum** foi de caracterização;
  - `naoDeveExporOutrosEndpointsOperacionais` passava antes da dependência (tudo 404) e ficou
    vermelho com ela;
  - foi necessária a sexta propriedade `management.endpoint.health.probes.enabled=false`.
- **T017**: `spring.datasource.hikari.connection-timeout=3000`, por **decisão do dono**, para o
  503 sair em cerca de 3s com o banco fora.
- **T022**: `deveSubirSaudavelNoModoRapidoSemBancoExterno` passou de primeira, como previsto
  (caracterização).
- **T025**: o refactor que move o `VerificarSaude` para antes do `COPY src` foi aplicado e
  validado em T030.
- **T031**: `unhealthy` em 52s, e não 45s. Os textos foram corrigidos para "até cerca de 60s".
