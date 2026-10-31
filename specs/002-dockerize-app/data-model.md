# Data Model: Imagem de container do habitattio (primeira fatia)

**Feature**: [spec.md](./spec.md) | **Plan**: [plan.md](./plan.md) | **Date**: 2026-10-30

Esta feature **não muda o modelo de domínio** nem o banco: não há entidade, tabela nem
migração nova. As "entidades" da spec são artefatos operacionais, descritos abaixo com os
atributos e as regras que os testes e o guia de operação verificam.

## Imagem da aplicação

| Atributo | Valor / regra | Origem |
|---|---|---|
| Repositório | `docker.io/<usuario-dockerhub>/habitattio`, privado | FR-018, R10 |
| Tag | `<versão do pom>-<sha curto>`, nunca `latest`, nunca reaproveitada | FR-018, R10 |
| Digest | `sha256:...` devolvido pelo push; é o identificador de conteúdo | FR-020, R10 |
| Plataforma | `linux/amd64` | FR-008 |
| Base de execução | `gcr.io/distroless/java25-debian13:nonroot@sha256:fc6f…ee` | FR-002, FR-007, R1 |
| Base de build | `eclipse-temurin:25-jdk@sha256:83f4…07` (só no estágio de build) | FR-002, FR-007, R2 |
| Usuário | uid 65532 (`nonroot`) | FR-009, R1 |
| Camadas da aplicação | `dependencies` → `spring-boot-loader` → `snapshot-dependencies` → `application` | FR-004, R5 |
| Labels | `org.opencontainers.image.version`, `org.opencontainers.image.revision` | R10 |
| Porta | 8080 (`EXPOSE`; `server.port` atual) | Assumptions |
| Segredos | nenhum, porque tudo entra por ambiente | FR-011 |

**Regras de validação**:

- não contém `jdk.compiler`, `src/`, `pom.xml` nem `target/`;
- o processo principal não roda com uid 0;
- a mesma imagem (o mesmo digest) atende aos dois modos.

## Configuração de execução (variáveis de ambiente)

Detalhada no contrato [contracts/imagem-container.md](./contracts/imagem-container.md).

| Variável | Modo rápido | Modo produção |
|---|---|---|
| `SPRING_PROFILES_ACTIVE` | ausente | `prod` (obrigatória) |
| `SPRING_DATASOURCE_URL` | ausente | obrigatória (`jdbc:postgresql://…`) |
| `SPRING_DATASOURCE_USERNAME` | ausente | obrigatória |
| `SPRING_DATASOURCE_PASSWORD` | ausente | obrigatória (segredo) |
| `JAVA_TOOL_OPTIONS` | opcional | opcional (ajuste fino de JVM) |

**Regra**: `SPRING_PROFILES_ACTIVE` ausente → modo rápido, inclusive na VPS. É o risco aceito
(FR-012), e a imagem não o detecta.

## Estado de saúde

Dois níveis que não se confundem:

1. **Endpoint** (`GET /actuator/health`): `UP` → HTTP 200; `DOWN`/`OUT_OF_SERVICE` → HTTP 503.
   O corpo traz só `status` ([contracts/saude-http.md](./contracts/saude-http.md)).
2. **Container** (`State.Health.Status` do Docker): `starting` → `healthy` ⇄ `unhealthy`.

```text
           start-period (60s): falhas não contam
 [starting] ──── verificação OK ─────────────▶ [healthy]
     │                                            │  ▲
     │ 3 falhas seguidas depois do start-period   │  │ 1 verificação OK
     ▼                                            ▼  │
 [unhealthy] ◀──────── 3 falhas seguidas ──── [healthy]
```

- Uma verificação OK é `VerificarSaude` saindo com 0, ou seja, endpoint com HTTP 200 em até 2s.
- Uma falha é qualquer outro status, um timeout, uma conexão recusada ou o verificador passar do
  `--timeout=5s`.
- O indicador de Redis está fora do cálculo; o do banco (`db`) está dentro (FR-016).
