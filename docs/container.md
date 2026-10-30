# Imagem de container

Guia de operação da imagem do habitattio: como construir, executar, publicar no Docker Hub e
conferir que a VPS roda exatamente o que foi publicado (feature `specs/002-dockerize-app/`).
Em caso de dúvida, o `Dockerfile` e o `ImagemContainerTest` são a referência.

Arquivos de referência:

- Receita: `Dockerfile` e `.dockerignore` (raiz do repositório)
- Verificador de saúde: `docker/VerificarSaude.java`
- Configuração de saúde: `src/main/resources/application.properties` (seção do Actuator)
- Testes: `ImagemContainerTest` (constrói a imagem e prova subida, recusa e saúde) e
  `SaudeAplicacaoTest` (contrato HTTP de saúde), em `src/test/java/br/com/marden/habitattio/`
- Garantias formais: `specs/002-dockerize-app/contracts/imagem-container.md` e
  `specs/002-dockerize-app/contracts/saude-http.md`

O que a imagem é, em resumo:

- dois estágios: build em `eclipse-temurin:25-jdk`, execução em
  `gcr.io/distroless/java25-debian13:nonroot`, só com o JRE, sem shell, `curl` ou `wget`;
- as duas bases fixadas por digest;
- processo com uid **65532** (`nonroot`), nunca root;
- jar extraído nas 4 camadas do Spring Boot (`dependencies`, `spring-boot-loader`,
  `snapshot-dependencies`, `application`);
- plataforma `linux/amd64`;
- nenhum segredo dentro: toda configuração entra por variável de ambiente.

---

## Construção

```bash
./mvnw test                          # a suíte completa roda ANTES, no host (inclui o teste da imagem)
docker build -t habitattio:local .   # BuildKit; não roda testes
```

- O `docker build` **não** roda testes (`-DskipTests`). A garantia vem do `./mvnw test`, que
  inclui o `ImagemContainerTest` e exige Docker.
- O operador constrói com **BuildKit** (`buildx` instalado). O `ImagemContainerTest` constrói a
  mesma receita no **builder clássico** do daemon, que é o que o Testcontainers usa. Por isso o
  `Dockerfile` só tem instruções comuns aos dois: nada de `RUN --mount`, `# syntax=`,
  `COPY --link` nem heredocs.
- O `.dockerignore` é uma **lista de permissão**: só `Dockerfile`, `mvnw`, `pom.xml`, `.mvn/`,
  `src/` e `docker/` entram no contexto. `.env*`, `target/`, `.git` e os artefatos de spec ficam
  fora por construção. O `Dockerfile` precisa estar na lista, porque o docker-java, usado pelo
  teste, recusa construir se o próprio `Dockerfile` for excluído.
- Rebuild depois de mudar só código reaproveita a camada de dependências (passo
  `dependency:go-offline` e as três primeiras camadas da aplicação vêm do cache).

## Execução

A mesma imagem atende aos dois modos; quem escolhe é a variável `SPRING_PROFILES_ACTIVE`.

| Variável | Modo rápido | Modo produção |
|---|---|---|
| `SPRING_PROFILES_ACTIVE` | ausente | `prod` (obrigatória) |
| `SPRING_DATASOURCE_URL` | ausente | obrigatória (`jdbc:postgresql://…`) |
| `SPRING_DATASOURCE_USERNAME` | ausente | obrigatória |
| `SPRING_DATASOURCE_PASSWORD` | ausente | obrigatória (segredo) |
| `JAVA_TOOL_OPTIONS` | opcional | opcional (ajuste fino da JVM, por exemplo `-XX:MaxRAMPercentage=75`) |

Modo rápido (desenvolvimento; H2 em memória, dados se perdem ao parar):

```bash
docker run --rm -p 8080:8080 habitattio:local
```

Modo produção (PostgreSQL; estrutura só por migrações Flyway, `ddl-auto=validate`):

```bash
docker run -d --name habitattio -p 8080:8080 \
  -e SPRING_PROFILES_ACTIVE=prod \
  -e SPRING_DATASOURCE_URL=jdbc:postgresql://<host>:5432/habitattio \
  -e SPRING_DATASOURCE_USERNAME=habitattio \
  -e SPRING_DATASOURCE_PASSWORD \
  <imagem>
```

- Segredos vêm do ambiente do host (`-e VAR` sem valor repassa a variável já exportada) ou de
  um `.env` **fora do git** (o `.gitignore` já ignora `.env*`). Nunca do `Dockerfile`.
- A porta é a **8080**.
- Em produção, a aplicação **recusa subir** se a estrutura do banco divergir do mapeamento, ou
  se faltarem as variáveis de conexão: o container para com código diferente de zero e o motivo
  aparece em `docker logs`.

### Saúde

- Endpoint: `GET /actuator/health` → `200 {"status":"UP"}` ou `503 {"status":"DOWN"}`, só com o
  estado geral, sem detalhes por componente. É o único endpoint operacional exposto.
- O cálculo considera o banco e **ignora o Redis** (o starter continua no projeto).
- O container declara um `HEALTHCHECK` (`interval=15s`, `timeout=5s`, `start-period=60s`,
  `retries=3`) que roda o `VerificarSaude`, uma classe Java mínima, já que a imagem não tem
  `curl`/`wget`.
- Com o banco fora, o pedido de conexão desiste em 3s (`spring.datasource.hikari.connection-timeout`),
  o endpoint responde 503 e o container vira `unhealthy` em até cerca de 45s (3 × 15s).
- Estado atual: `docker inspect --format '{{.State.Health.Status}}' habitattio`.

### Depuração

A imagem não tem shell: `docker exec … sh` não funciona. Use `docker logs habitattio` e o
endpoint de saúde.

### Risco aceito: modo produção esquecido

Sem `SPRING_PROFILES_ACTIVE=prod`, a imagem sobe no **modo rápido, em H2 com dados voláteis**,
inclusive na VPS, e nada impede isso. É um risco aceito pelo dono em 2026-10-30 (spec FR-012):
a imagem não detecta nem bloqueia essa situação. Confira a variável em toda instalação; o log
de subida mostra `The following 1 profile is active: "prod"` no modo correto e
`No active profile set` no modo rápido.

## Publicação no Docker Hub

Repositório: `docker.io/<usuario-dockerhub>/habitattio`, **privado**. Build e push são manuais.

```bash
export REPO=docker.io/<usuario-dockerhub>/habitattio
export VERSAO=$(./mvnw -q help:evaluate -Dexpression=project.version -DforceStdout)
export REVISAO=$(git rev-parse --short HEAD)
export TAG="$VERSAO-$REVISAO"

git status --porcelain                     # precisa estar vazio: a tag aponta para um commit
docker build --build-arg VERSION=$VERSAO --build-arg REVISION=$REVISAO -t "$REPO:$TAG" .
docker manifest inspect "$REPO:$TAG" && echo "TAG JÁ EXISTE — não publique" || echo "ok, tag livre"
docker push "$REPO:$TAG"                   # anote a linha "digest: sha256:…"
```

- A tag é `<versão do pom>-<sha curto do commit>` e **nunca** se reaproveita. `latest` não é
  publicado.
- O `docker manifest inspect` precisa **falhar** (tag inexistente) antes do push. Se o plano da
  conta permitir, ligue também a opção de tags imutáveis do repositório no Docker Hub (não
  verificada).
- O digest impresso pelo push é o do **índice**: com BuildKit, a imagem sai com uma atestação
  de proveniência junto do manifesto `linux/amd64`. É esse o valor a anotar e a comparar; na
  interface do Docker Hub, use o digest da tag, não o da plataforma.
- A imagem recebe os labels `org.opencontainers.image.version` e
  `org.opencontainers.image.revision`, para identificar o commit de uma imagem em execução.

### Na VPS: pull autenticado e conferência do digest

```bash
docker login -u <usuario-dockerhub>        # cole um access token SÓ DE LEITURA, não a senha
docker pull "$REPO:$TAG@$DIGEST"
# … sobe o container "habitattio" (ver "Execução") …
docker image inspect --format '{{json .RepoDigests}}' "$(docker inspect --format '{{.Image}}' habitattio)"
```

- Sem login, o pull de um repositório privado é negado.
- A lista de `RepoDigests` precisa conter `"$REPO@$DIGEST"`, o mesmo digest anotado no push. Só
  então a VPS roda exatamente a imagem publicada.
- No compose da VPS (fora do escopo desta feature), referencie a imagem com
  `image: <repo>:<tag>@<digest>`: assim a divergência fica impossível.

Riscos registrados, sem mitigação prévia: limites do plano do Docker Hub (repositórios
privados, pulls) e suporte a tags imutáveis no plano da conta.

## Atualização das imagens base

As bases são fixadas por digest e **não** se atualizam sozinhas. Para atualizar:

1. `docker pull eclipse-temurin:25-jdk` e `docker pull gcr.io/distroless/java25-debian13:nonroot`.
2. Leia os digests novos:
   `docker image inspect --format '{{index .RepoDigests 0}}' eclipse-temurin:25-jdk` (idem para a
   distroless).
3. Troque os valores de `ARG BASE_BUILD` e `ARG BASE_RUNTIME` no topo do `Dockerfile`.
4. Rode `./mvnw test`: o `ImagemContainerTest` valida a base nova.
5. Commit.

O scan de vulnerabilidades (segunda fatia) vai indicar **quando** atualizar.

## Fora de escopo nesta fatia

- Scan de vulnerabilidades da imagem (segunda fatia, depois do estudo de ferramentas).
- Arquivo `docker-compose` e definição do PostgreSQL ou de outros serviços.
- CI/CD: build e push continuam manuais.
- Provisionamento da VPS (SO, Docker, firewall, TLS, domínio, proxy reverso).
