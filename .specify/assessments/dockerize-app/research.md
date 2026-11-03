# Idea Research: Containerizar a aplicação com Dockerfile

- **Slug**: dockerize-app
- **Created**: 2026-10-30
- **Evidence confidence (overall)**: medium — a evidência interna (repositório) é alta e citada;
  a demanda vem de um único interessado; as "melhores práticas de mercado" não foram verificadas
  em fonte externa nesta etapa (ver Sources).

## Users & Demand

- O único interessado registrado é o dono do projeto, que pede o Dockerfile como preparação para
  uma futura arquitetura de microsserviços. É um desejo *declarado*; ainda não há dor
  *observada* de deploy (nenhum ambiente de produção registrado). — [source: `intake.md`]
  (confidence: high quanto ao pedido; low quanto à urgência)
- Hoje a aplicação não tem usuários operando em produção: o README diz "em desenvolvimento
  inicial", e só o agregado `Imovel` tem caso de uso e persistência. — [source: `README.md:9`,
  `AGENTS.md` §6 "Dívida restante"] (confidence: high)
- O volume esperado é pequeno: cerca de 14 imóveis e 14 faturas. Performance e escala não pesam
  nas decisões. — [source: memória do projeto `db-prod-postgres-constraints`, decisão do dono
  em 2026-10-29] (confidence: high)
- Não há registro de quem vai operar o container nem de onde ele vai rodar. —
  [NEEDS CLARIFICATION] (ver Gaps)

## Prior Art

- **Interno, `better-db-schema` (concluído em 2026-10-29, PR #24):** criou o profile `prod`
  (PostgreSQL, Flyway, `ddl-auto=validate`). Esse profile já lê URL, usuário e senha das
  variáveis `SPRING_DATASOURCE_*`, e não de arquivo. Na prática, isso deixa a aplicação pronta
  para receber configuração do jeito que um container espera (estilo 12-factor). —
  [source: `src/main/resources/application-prod.properties`, `AGENTS.md:29-43`, git log]
  (confidence: high)
- **Interno, Docker já faz parte do fluxo de desenvolvimento:** Testcontainers sobe PostgreSQL e
  Redis para os testes e para `TestHabitattioApplication` (`./mvnw spring-boot:test-run`, já com
  o profile `prod`). As versões das imagens estão fixas (por exemplo `postgres:18.6`). Docker,
  portanto, já é pré-requisito do desenvolvimento; só o empacotamento da aplicação ainda não
  usa container. — [source: `AGENTS.md:24-43`, `.specify/memory/constitution.md:119-123`,
  `specs/001-better-db-schema/plan.md:149`] (confidence: high)
- **Interno, nenhuma tentativa anterior:** não há `Dockerfile`, `.dockerignore`, `compose`,
  workflow de CI nem spec ou assessment anterior sobre containerização. As menções a "Docker" em
  `.specify/` e `specs/` tratam só de Testcontainers. — [source: listagem da raiz do repo e
  `grep -ri docker .specify specs`] (confidence: high)
- **Alternativa já presente no build, Cloud Native Buildpacks:** o `spring-boot-maven-plugin`
  4.1.2, já declarado no `pom.xml`, inclui o goal `build-image` (`BuildImageMojo`). Ele gera uma
  imagem OCI sem Dockerfile e sem dependência nova no `pom.xml`. O mesmo plugin traz
  `process-aot` (`ProcessAotMojo`), base para imagem nativa. —
  [source: `pom.xml:107-113`; conteúdo do jar
  `~/.m2/.../spring-boot-maven-plugin/4.1.2/spring-boot-maven-plugin-4.1.2.jar`]
  (confidence: high quanto à existência; a qualidade da imagem gerada não foi avaliada)
- **Externo, práticas usuais para Spring Boot em container:** build multi-stage (compilar com
  JDK, rodar com JRE ou imagem mínima), usuário não-root, extração em camadas do jar
  (`-Djarmode=tools`/layertools) para aproveitar cache, `.dockerignore`, imagem base com tag
  fixa ou digest, configuração por variável de ambiente, healthcheck (normalmente via
  Actuator), flags de JVM sensíveis a container e scan de vulnerabilidades da imagem. —
  [ASSUMPTION — conhecimento geral, não verificado em fonte nesta etapa] (confidence: medium)

## Market & Context

- Como a aplicação roda hoje: `./mvnw spring-boot:run` no host, com JDK 25 instalado (modo rápido
  H2 ou profile `prod` contra um PostgreSQL próprio). O ambiente do dono tem OpenJDK 25.0.4 e
  Docker 29.9.1. — [source: `AGENTS.md` §1; `java -version` e `docker version` nesta máquina,
  2026-10-30] (confidence: high)
- Custo de não fazer nada: enquanto houver um único desenvolvedor e nenhum ambiente de
  produção, o custo imediato é baixo. Ele cresce quando surgir um deploy real (dependência do JDK
  25 no host, configuração manual) ou mais de um serviço. — [ASSUMPTION] (confidence: medium)
- Microsserviços: a ideia cita uma arquitetura futura, mas não há spec, plano ou divisão de
  serviços registrada. A constitution vigente e o AGENTS.md descrevem um monólito hexagonal. —
  [source: `.specify/memory/constitution.md`, `AGENTS.md` §2; ausência de documento em `specs/`]
  (confidence: high quanto à ausência)
- Containerizar um único serviço Spring Boot é prática difundida e de baixo risco, e é
  pré-requisito comum para orquestradores e PaaS. — [ASSUMPTION] (confidence: medium)

## Data & Constraints

- **Stack:** Java 25, Spring Boot 4.1.2, artefato `habitattio-0.0.1-SNAPSHOT`, porta 8080. A
  imagem base precisa de JDK/JRE 25. — [source: `pom.xml`, `application.properties`]
  (confidence: high quanto à versão; a disponibilidade das imagens base em Java 25 não foi
  verificada)
- **Dois modos de execução:** sem profile = H2 em memória (os dados somem quando o container
  reinicia). `prod` = PostgreSQL externo, obrigatório via `SPRING_DATASOURCE_*`. O container
  precisa escolher ou expor esse modo. — [source: `application.properties`,
  `application-prod.properties`, `AGENTS.md:29-39`] (confidence: high)
- **Redis declarado mas não usado:** `spring-boot-starter-data-redis` está no `pom.xml`, mas não
  há nenhuma referência a Redis em `src/main`. Um compose com Redis não teria consumidor hoje.
  Isso também afeta a auto-configuração na subida do container sem Redis acessível (não
  verificado). — [source: `pom.xml:39`; `grep -rni redis src/main` sem resultado]
  (confidence: high quanto à ausência de uso; low quanto ao efeito na subida)
- **Sem Actuator:** não há `spring-boot-starter-actuator`. Um healthcheck HTTP padrão exigiria
  uma dependência nova, e a constitution (§IV) e o AGENTS.md (§9) exigem acordo explícito para
  isso. — [source: `pom.xml`, `.specify/memory/constitution.md:83-86`] (confidence: high)
- **Restrições de governança:**
  - Constitution §IV / AGENTS.md §9: dependência nova no `pom.xml`, troca de banco ou de
    `ddl-auto` precisam de acordo do usuário.
  - Constitution §III (SDD): feature nova passa por spec.
  - Um Dockerfile não toca `domain/` nem `application/`, então as regras hexagonais não se
    aplicam diretamente.
  - TDD: não está claro como se aplica a artefato de infraestrutura (por exemplo, um teste de
    build ou de subida da imagem).
  - [source: `.specify/memory/constitution.md`, `AGENTS.md`] (confidence: high)
- **Segredos:** a configuração de banco já vem do ambiente. A chave de criptografia do CPF
  (decisão de 2026-10-29) ainda não existe no código, mas também precisará ser injetada no
  container. — [source: memória `db-prod-postgres-constraints`] (confidence: high)
- **Testcontainers dentro de container:** os testes de integração precisam de Docker. Um build
  multi-stage que rode `./mvnw verify` dentro do `docker build` não teria Docker disponível. O
  build da imagem provavelmente teria de pular os testes de integração ou rodá-los fora. —
  [ASSUMPTION, derivada de `AGENTS.md` §1] (confidence: medium)

## Evidence Against the Idea

- **Problema ainda não observado:** não há ambiente de produção, operador ou deploy registrado.
  A motivação ("microsserviços") é um plano futuro sem spec, e o princípio YAGNI da constitution
  (§IV) desaconselha preparar infraestrutura "para o futuro". —
  [source: `.specify/memory/constitution.md` §IV, `intake.md`]
- **Já existe caminho sem Dockerfile:** `./mvnw spring-boot:build-image` gera uma imagem OCI com
  o plugin que já está no projeto. Um Dockerfile escrito à mão é mais um artefato para manter,
  e o ganho sobre os buildpacks não está demonstrado. — [source: `pom.xml`, `BuildImageMojo` no
  jar do plugin]
- **"Bare metal" não é exatamente o estado atual:** o que o host precisa hoje é só um JDK 25 (e
  Docker para os testes). A aplicação não depende de instalação de sistema além disso, então a
  dor de portabilidade é moderada. — [source: `AGENTS.md` §1, `README.md:86`]
- **Microsserviços não decorrem do Dockerfile:** containerizar o monólito atual não aproxima a
  divisão em serviços, que é uma decisão de arquitetura separada e ainda não tomada. —
  [ASSUMPTION]
- **Podem surgir mudanças que pedem acordo:** um healthcheck via Actuator (dependência nova) e a
  definição do modo padrão do container (H2 vs `prod`) esbarram nas regras de governança. —
  [source: constitution §IV]

## Gaps & Open Questions

- [NEEDS CLARIFICATION: Ambiente de destino da imagem (Docker local, Kubernetes, PaaS) e quem
  opera.]
- [NEEDS CLARIFICATION: Dockerfile escrito à mão ou `spring-boot:build-image` (Buildpacks)? A
  ideia pede Dockerfile explicitamente; o motivo da preferência não foi registrado.]
- [NEEDS CLARIFICATION: O container sobe em `prod` por padrão (exige PostgreSQL) ou no modo rápido
  H2?]
- [NEEDS CLARIFICATION: O escopo inclui `compose` com PostgreSQL (e Redis, hoje sem uso)?]
- [NEEDS CLARIFICATION: Healthcheck é requisito? Se for, há acordo para adicionar Actuator?]
- [NEEDS CLARIFICATION: Há CI ou registry de imagens no escopo?]
- [NEEDS CLARIFICATION: Como "TDD" se aplica aqui: um teste que constrói e sobe a imagem conta
  como aceite?]
- [NEEDS CLARIFICATION: Existe cronograma ou spec para os microsserviços que justifique fazer
  agora?]
- Não verificado: disponibilidade e tamanho das imagens base com Java 25 (Temurin, distroless,
  etc.); comportamento da subida sem Redis acessível; qualidade e tamanho da imagem gerada por
  buildpacks.

## Sources

- Repositório local (worktree `assess-dockerize-app`, base `origin/main` em 2026-10-30):
  `pom.xml`, `src/main/resources/application.properties`,
  `src/main/resources/application-prod.properties`, `AGENTS.md`, `README.md`,
  `.specify/memory/constitution.md`, `.specify/assessments/better-db-schema/`,
  `specs/001-better-db-schema/plan.md`, histórico git.
- Jar local `spring-boot-maven-plugin-4.1.2.jar` (repositório Maven local), listagem de classes.
- Memória do projeto `db-prod-postgres-constraints` (decisões do dono, 2026-10-29/30).
- Ambiente local: `java -version` (OpenJDK 25.0.4), `docker version` (29.9.1).
- Nenhuma URL foi buscada. As práticas externas estão marcadas como ASSUMPTION
  `[UNVERIFIED — fetch skipped: o mecanismo de fetch disponível não fixa nem expõe o endereço
  conectado, exigência da URL Trust Policy]`. Fontes candidatas para verificação manual: guia
  oficial de container images do Spring Boot e documentação de boas práticas de build do
  Docker.
