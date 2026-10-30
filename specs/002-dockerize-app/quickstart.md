# Quickstart / validação: Imagem de container do habitattio

**Feature**: [spec.md](./spec.md) | **Contratos**: [imagem-container](./contracts/imagem-container.md), [saude-http](./contracts/saude-http.md)

Roteiro para provar a feature de ponta a ponta. V1–V7 rodam na máquina de desenvolvimento;
V8–V10 envolvem o Docker Hub e a VPS. O guia permanente de operação é `docs/container.md`
(criado na implementação); este arquivo é só a validação.

## Pré-requisitos

- Docker em execução (testado com 29.9.1, `linux/amd64`, sem o `buildx`).
- JDK 25 **só** para rodar a suíte (`./mvnw test`). A imagem não precisa dele.
- Para V8–V10: um repositório privado `habitattio` no Docker Hub, um token de leitura e escrita
  na máquina de build e um token **só de leitura** na VPS.

```bash
export REPO=docker.io/<usuario-dockerhub>/habitattio
export VERSAO=$(./mvnw -q help:evaluate -Dexpression=project.version -DforceStdout)
export REVISAO=$(git rev-parse --short HEAD)
export TAG="$VERSAO-$REVISAO"
```

## V0 — Suíte completa (inclui o teste da imagem)

```bash
./mvnw test
```

**Esperado**: verde, incluindo `SaudeAplicacaoTest` e `ImagemContainerTest`. Sem Docker, os
testes de integração (inclusive o da imagem) falham e o relatório diz isso explicitamente.

## V1 — Build sem testes, no builder clássico (C1, C2)

```bash
git status --porcelain     # deve ser vazio antes de gerar uma tag de versão
docker build --build-arg VERSION=$VERSAO --build-arg REVISION=$REVISAO -t "$REPO:$TAG" .
```

**Esperado**: `Successfully tagged …`. Nenhuma linha `Tests run:` do Surefire no log. O aviso
"legacy builder is deprecated" é normal neste host (research R3).

## V2 — Healthcheck declarado (H1, H2)

```bash
docker image inspect "$REPO:$TAG" --format '{{json .Config.Healthcheck}}'
docker image inspect "$REPO:$TAG" --format '{{.Config.User}}'
```

**Esperado**:

- O healthcheck tem `Test` começando com `["CMD","java",…,"VerificarSaude"]` e
  `Interval=15s`, `Timeout=5s`, `StartPeriod=60s` e `Retries=3` (em nanossegundos no JSON).
- O usuário é `65532`.

## V3 — Modo rápido, sem JDK nem banco (E1, H3, US4)

```bash
docker run -d --name hab-rapido -p 8080:8080 "$REPO:$TAG"
# aguarde ~10s
docker inspect hab-rapido --format '{{.State.Health.Status}}'   # healthy (após o 1º ciclo)
curl -s -i localhost:8080/actuator/health                       # 200 {"status":"UP"}
curl -s -o /dev/null -w '%{http_code}\n' localhost:8080/actuator/env   # 404
docker top hab-rapido                                           # UID 65532
docker rm -f hab-rapido
```

## V4 — Modo produção com PostgreSQL e sem Redis (E2, E3, H3, H4, US1, US2)

```bash
docker network create hab-net
docker run -d --name hab-pg --network hab-net -e POSTGRES_DB=habitattio \
  -e POSTGRES_USER=habitattio -e POSTGRES_PASSWORD=segredo-local postgres:18.6
docker run -d --name hab-prod --network hab-net -p 8080:8080 \
  -e SPRING_PROFILES_ACTIVE=prod \
  -e SPRING_DATASOURCE_URL=jdbc:postgresql://hab-pg:5432/habitattio \
  -e SPRING_DATASOURCE_USERNAME=habitattio -e SPRING_DATASOURCE_PASSWORD=segredo-local \
  "$REPO:$TAG"
docker inspect hab-prod --format '{{.State.Health.Status}}'   # healthy
docker stop hab-pg
# aguarde até ~45s (3 × 15s)
docker inspect hab-prod --format '{{.State.Health.Status}}'   # unhealthy
curl -s -i localhost:8080/actuator/health                     # 503 {"status":"DOWN"}
docker rm -f hab-prod hab-pg && docker network rm hab-net
```

## V5 — Rebuild só de código reaproveita dependências (C4, US5, SC-005)

Altere um arquivo em `src/main/java` (uma mudança real, não só um comentário) e reconstrua:

```bash
docker build -t habitattio:rebuild . 2>&1 | grep -E "Step|Using cache"
```

**Esperado**: `Using cache` no passo `dependency:go-offline` e nas cópias de `dependencies`,
`spring-boot-loader` e `snapshot-dependencies`. Só `COPY src`, `package`, `extract` e a camada
`application` são refeitos. Nenhuma linha `Downloading from central`. Desfaça a alteração
depois.

## V6 — Contexto de build enxuto (C5, FR-006)

```bash
touch .env.sentinela        # arquivo que NÃO deve entrar no contexto
docker build -t habitattio:ctx . 2>&1 | grep "Sending build context"
rm .env.sentinela
```

**Esperado**: contexto na ordem de centenas de kB, e não de dezenas de MB (`target/` + `.git`).

## V7 — Bases por digest e labels (C3, C6)

```bash
grep -nE '^(ARG|FROM)' Dockerfile    # toda base com @sha256:
docker image inspect "$REPO:$TAG" --format '{{json .Config.Labels}}'
```

**Esperado**: `org.opencontainers.image.version=$VERSAO` e
`org.opencontainers.image.revision=$REVISAO`.

## V8 — Publicação com tag imutável (P1, P2, US3)

```bash
docker manifest inspect "$REPO:$TAG" && echo "TAG JÁ EXISTE — não publique" || echo "ok, tag livre"
docker push "$REPO:$TAG"      # anote a linha "digest: sha256:…"
export DIGEST=sha256:...       # o valor anotado
```

**Esperado**: a tag está livre antes do push, e o push imprime o digest. Nada é publicado como
`latest`.

## V9 — Pull autenticado na VPS (P3)

Na VPS:

```bash
docker logout
docker pull "$REPO:$TAG"                                   # negado (repositório privado)
docker login -u <usuario-dockerhub>                        # cole o token SÓ DE LEITURA
docker pull "$REPO:$TAG@$DIGEST"                           # ok
```

## V10 — A VPS roda exatamente a imagem publicada (P4, SC-007)

Com o container de produção em execução na VPS (nome `habitattio`):

```bash
docker image inspect --format '{{json .RepoDigests}}' \
  "$(docker inspect --format '{{.Image}}' habitattio)"
```

**Esperado**: a lista contém `"$REPO@$DIGEST"`, o mesmo valor anotado em V8.
