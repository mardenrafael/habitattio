# Implementation Plan: Imagem de container do habitattio (primeira fatia)

**Branch**: `002-dockerize-app` | **Date**: 2026-10-30 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `specs/002-dockerize-app/spec.md`

## Summary

Empacotar o habitattio numa imagem de container construída a partir de um `Dockerfile` escrito
à mão e versionado na raiz:

- **Build**: estágio de build `eclipse-temurin:25-jdk`, com `./mvnw` offline, sem testes e com
  as dependências numa camada cacheada.
- **Execução**: estágio final `gcr.io/distroless/java25-debian13:nonroot`, só JRE, uid 65532, sem
  shell, com o jar extraído nas 4 camadas do Boot.
- **Bases e builder**: as duas bases são fixadas por digest. O Dockerfile só usa recursos
  comuns ao BuildKit, que o operador usa desde que o `buildx` foi instalado, e ao builder
  clássico, que o `ImageFromDockerfile` do teste usa (R3).
- **Contexto**: o `.dockerignore` é uma lista de permissão.
- **Dois modos, uma imagem**: sem profile, modo rápido com H2; com `SPRING_PROFILES_ACTIVE=prod` e
  `SPRING_DATASOURCE_*` por ambiente, PostgreSQL.
- **Saúde**: o Actuator (dependência nova, com acordo) expõe só `/actuator/health`, sem
  detalhes, com o indicador de Redis desligado. Um `HEALTHCHECK` em forma exec chama uma classe
  Java mínima (`VerificarSaude`), compilada no estágio de build, já que a imagem não tem
  `curl`/`wget`.
- **Testes**: `ImagemContainerTest` (Testcontainers) constrói a imagem do Dockerfile e prova
  saudável e não saudável, os dois modos, não-root e só-runtime. `SaudeAplicacaoTest` (MockMvc)
  prova o contrato HTTP de saúde em segundos.
- **Publicação**: push manual para o Docker Hub privado com tag `<versão>-<sha>` e conferência
  do digest na VPS, documentadas em `docs/container.md`.

Decisões e evidências estão em [research.md](./research.md) (R1–R12). Muitas foram verificadas
num spike descartável: builds clássico e BuildKit, cache, `extract` do Boot 4, atestação e uid.

## Technical Context

**Language/Version**: Java 25 (Temurin 25.0.5+8 nas imagens; 25.0.4 no host).

**Primary Dependencies**: Spring Boot 4.1.2 (Web MVC, Data JPA, Data Redis, Validation,
Flyway). **Nova** (acordo do dono, decision.md): `spring-boot-starter-actuator`, com versão do
BOM. Nenhuma dependência de teste nova: o Testcontainers 2.0.5 (`ImageFromDockerfile`,
`GenericContainer`, `Network`) e o MockMvc já estão no projeto.

**Storage**: inalterado. PostgreSQL no profile `prod` (Flyway, `validate`); H2 em memória no
modo rápido.

**Testing**:

- JUnit 5 + Testcontainers 2.0.5 para a imagem: construída do Dockerfile, com `postgres:18.6`,
  sem Redis.
- `@SpringBootTest` + `@AutoConfigureMockMvc` para o contrato de saúde.

**Target Platform**:

- **Execução**: VPS Linux `linux/amd64`, só com o runtime de containers.
- **Build**: a máquina do dono, Docker 29.9.1 + `buildx` 0.38.1 (BuildKit, driver `docker`),
  `linux/amd64`. O teste constrói no builder clássico do mesmo daemon.

**Project Type**: web-service (monólito Spring Boot, hexagonal) + artefato de empacotamento.

**Performance Goals**:

- Rebuild só de código com o cache reaproveitado: 9s no BuildKit e 17,7s no clássico (spike).
- Subida no modo rápido em cerca de 7s, medida.
- Healthcheck detecta banco fora em até cerca de 45s.

**Constraints**:

- Nenhum teste existente alterado.
- `domain/` e `application/` intocados.
- Sem recursos exclusivos do BuildKit.
- Sem segredo na imagem nem no contexto de build.
- `./mvnw spring-boot:run` e `spring-boot:test-run` inalterados.
- Sem compose, CI ou scan.

**Scale/Scope**:

- 1 Dockerfile, 1 `.dockerignore` e 1 classe Java auxiliar (`docker/VerificarSaude.java`).
- 1 dependência e 5 propriedades.
- 2 classes de teste novas.
- 1 documento de operação e emendas no AGENTS.md e na constitution (PATCH).

Nenhum `NEEDS CLARIFICATION` restante; todos os riscos do decision.md foram investigados em
research.md:

| Risco do decision.md | Resultado |
|---|---|
| Disponibilidade de JRE 25 mínima | Existe (distroless). Atualização do digest em R11 |
| Healthcheck sem `curl` | Classe Java, R4 |
| Tempo do teste da imagem | Entre 1 e 2,5 min estimados, mantido no ciclo; R8 |
| Desligar só o indicador de Redis | R7, provado por teste |
| Limites do Docker Hub | Registrados, sem mitigação; R10 |

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Princípio | Verificação | Pré-design | Pós-design |
|---|---|---|---|
| I. Hexagonal | Nenhuma mudança em `domain/`, `application/`, `ports/` ou `adapters/`. O Actuator é configuração (`application.properties`), sem classe nova em `src/main/java`. `VerificarSaude` vive em `docker/`, fora da árvore da aplicação, e não entra no jar. | ✅ | ✅ |
| II. TDD | Toda mudança de produção (dependência, propriedades, Dockerfile, verificador) é precedida por um teste visto vermelho pelo motivo certo (ver "Ordem TDD"). **Exceção decidida pelo dono (2026-10-30)**: o `.dockerignore` fica fora da regra, porque nenhum teste observa o contexto de build; ele é verificado manualmente por quickstart V6. Testes que passarem de primeira são de **caracterização** e declarados no relatório. Nenhum teste existente é editado. | ✅ | ✅ (corrigido após `/speckit-analyze` D1) |
| III. SDD | Feito: assessment → specify. Este plan; tasks e analyze vêm a seguir. | ✅ | ✅ |
| IV. YAGNI / acordo | Dependência nova (Actuator) com **acordo explícito** (decision.md, In scope). Sem script de publicação, sem compose, sem grupo de saúde customizado e sem porta de management separada (R7, R10). | ✅ (com acordo) | ✅ |
| V. Convenções | Nomes de teste e da classe auxiliar em português (`VerificarSaude`, `deveFicarNaoSaudavel…`). Imports explícitos. Sem Lombok. | ✅ | ✅ |
| Stack / Restrições | O Actuator passa a integrar o stack. **Ação**: emenda PATCH 1.0.1 → 1.0.2 na seção "Stack Tecnológico e Restrições" e sincronização do AGENTS.md §1/§9 na mesma mudança (Governance). Testes de integração exigem Docker, inclusive o da imagem; a ausência é declarada. | ⚠️ follow-up | ⚠️ follow-up, vira task |

**Resultado**: o gate passa. O único pendente é a atualização documental obrigatória do stack,
que entra como task, sem violação a justificar.

### Ordem TDD

Não há camadas de domínio envolvidas; a ordem segue de dentro (configuração da aplicação) para
fora (empacotamento).

1. **Contrato de saúde**: escrever `SaudeAplicacaoTest`, com os três cenários de R9
   ([contracts/saude-http.md](./contracts/saude-http.md)). Fica **vermelho**: `/actuator/health`
   responde 404 sem o Actuator. Isso justifica a dependência `spring-boot-starter-actuator`.
   Rodar de novo com a dependência: `naoDeveExporOutrosEndpointsOperacionais` deve continuar
   vermelho, porque a página de discovery `/actuator` responde 200 por padrão. Isso justifica
   as propriedades `management.*`. Se algum cenário passar de primeira só com a dependência (o
   padrão do Boot já esconde detalhes, por exemplo), ele é declarado como caracterização, e a
   propriedade correspondente continua explícita por FR-014/015.
   - `deveResponderSaudavelSemRedis`: sem Redis rodando, a hipótese é que o indicador de Redis
     derruba o status. Se derrubar, o teste fica vermelho e justifica
     `management.health.redis.enabled=false`. Se não derrubar, é caracterização, e a
     propriedade ainda é exigida pelo modo produção (cenário 1 da imagem).
2. **Imagem, cenários da receita**: escrever em `ImagemContainerTest`, antes de existir o
   Dockerfile, os sete cenários da US1 (R8):
   - subida em produção por log;
   - recusa de banco divergente;
   - não subir sem variáveis de conexão;
   - não-root;
   - só-runtime;
   - labels;
   - `linux/amd64`.

   Ficam **vermelhos** porque o build falha sem Dockerfile; é uma falha de build aceita como
   "motivo certo", porque é exatamente a ausência da receita.
3. **Receita sem healthcheck**: criar `Dockerfile` (multi-stage, `ARG` dos digests no topo,
   camadas, distroless `nonroot`, labels OCI no fim, `ENTRYPOINT` sem flags de JVM) e
   `.dockerignore` (exceção de TDD). Os sete ficam **verdes**. `deveRegistrarVersaoERevisaoNosLabels`
   é o que justifica o `LABEL`; não-root, recusa, sem variáveis e amd64 são caracterização.
4. **Imagem, cenários de saúde**: escrever saudável em produção e não saudável sem PostgreSQL.
   Ficam **vermelhos**: sem `HEALTHCHECK`, `State.Health` é nulo e a espera estoura.
5. **Healthcheck**: criar `docker/VerificarSaude.java` (URL fixa na porta 8080), compilá-lo no
   estágio de build e declarar o `HEALTHCHECK`. Os cenários de saúde ficam **verdes**. Depois,
   o cenário do modo rápido saudável é escrito como caracterização (US4).
6. **Suíte completa**: `./mvnw test` com Docker; tudo verde, sem nenhum teste existente
   alterado.
7. **Documentação e governança**:
   - `docs/container.md`, guia de operação (FR-024): build, tag, publicação, pull autenticado,
     os dois modos com a tabela de variáveis, conferência do digest, atualização das bases e o
     risco aceito do modo produção esquecido;
   - AGENTS.md §1/§9;
   - emenda PATCH da constitution.
8. **Validação manual**: [quickstart.md](./quickstart.md) V1–V7 na máquina de build. V8–V10
   ficam com o dono (Docker Hub e VPS), na primeira instalação.

## Project Structure

### Documentation (this feature)

```text
specs/002-dockerize-app/
├── plan.md              # este arquivo
├── research.md          # Phase 0 (R1–R12)
├── data-model.md        # Phase 1: imagem, variáveis, estados de saúde
├── quickstart.md        # Phase 1: validação V0–V10
├── contracts/
│   ├── imagem-container.md   # contrato de construção, execução, saúde e publicação
│   └── saude-http.md         # contrato de GET /actuator/health
├── checklists/
│   └── requirements.md
└── tasks.md             # Phase 2 (/speckit-tasks; não criado aqui)
```

### Source Code (repository root)

```text
Dockerfile                         # NOVO: build multi-stage (classic builder), HEALTHCHECK, labels
.dockerignore                      # NOVO: lista de permissão (mvnw, pom.xml, .mvn, src, docker)
docker/
└── VerificarSaude.java            # NOVO: verificador de saúde do container (só java.base)
pom.xml                            # + spring-boot-starter-actuator
src/main/resources/
└── application.properties         # + management.* (exposure, discovery, show-*, redis)
src/test/java/br/com/marden/habitattio/
├── SaudeAplicacaoTest.java        # NOVO: contrato HTTP de saúde (MockMvc, modo rápido)
└── ImagemContainerTest.java       # NOVO: constrói a imagem do Dockerfile e prova subida e saúde
docs/
└── container.md                   # NOVO: guia de operação da imagem
AGENTS.md                          # §1 comandos de imagem; §9 stack com Actuator
.specify/memory/constitution.md    # PATCH 1.0.2: Actuator no stack
```

**Structure Decision**: o projeto continua um único módulo Maven. Os artefatos de
empacotamento ficam na raiz, onde o `docker build .` e o `ImageFromDockerfile` os procuram. A
classe auxiliar fica em `docker/`, um diretório de empacotamento fora de `src/`, para não
entrar no jar, na arquitetura hexagonal nem no classpath de testes. Os testes novos ficam na
raiz do pacote de testes, junto de `EstruturaBancoProducaoTest` e `ModoRapidoTest`, que também
testam a aplicação inteira e não uma camada.

## Complexity Tracking

Nenhuma violação da constitution a justificar.
