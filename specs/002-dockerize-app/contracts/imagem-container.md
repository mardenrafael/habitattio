# Contrato: imagem de container `habitattio`

O que a imagem promete a quem a executa (operador, compose da VPS, teste automatizado). Cada
item tem o identificador do teste ou da verificação que o garante.

## Construção

| ID | Garantia | Verificado por |
|---|---|---|
| C1 | `docker build -t <ref> .` na raiz do repositório produz a imagem, sem rodar testes | quickstart V1; `ImagemContainerTest` (build via Testcontainers) |
| C2 | O Dockerfile só usa instruções comuns ao BuildKit e ao builder clássico. O operador constrói com BuildKit e o teste no clássico | quickstart V1 (os dois builds); `ImagemContainerTest` (clássico) |
| C3 | As bases são fixadas por digest (`@sha256:`) | revisão do Dockerfile; quickstart V7 |
| C4 | Depois de mudar só `src/`, o rebuild reaproveita o passo de dependências e as 3 primeiras camadas da aplicação | quickstart V5 |
| C5 | O contexto de build só contém `mvnw`, `pom.xml`, `.mvn/`, `src/` e `docker/` | quickstart V6 (`.dockerignore` em lista de permissão; **exceção de TDD** decidida pelo dono, só verificação manual) |
| C6 | `--build-arg VERSION=… --build-arg REVISION=…` viram os labels OCI `version` e `revision` | `deveRegistrarVersaoERevisaoNosLabels`; quickstart V7 |
| C7 | A imagem é `linux/amd64` | `deveSerConstruidaParaLinuxAmd64`; quickstart V7 |

## Execução

| ID | Garantia | Verificado por |
|---|---|---|
| E1 | Sem `SPRING_PROFILES_ACTIVE`: modo rápido (H2 em memória), sem banco externo | `deveSubirSaudavelNoModoRapidoSemBancoExterno` |
| E2 | `SPRING_PROFILES_ACTIVE=prod` + `SPRING_DATASOURCE_URL/USERNAME/PASSWORD`: PostgreSQL, Flyway, `ddl-auto=validate` | `deveSubirEmProducaoComPostgresSemRedis`; `deveFicarSaudavelEmProducaoComPostgresSemRedis` |
| E3 | Redis ausente não impede subida nem saúde | os dois cenários acima, sem Redis |
| E4 | O processo roda como uid 65532, não root | `deveRodarProcessoDaAplicacaoSemSerRoot` |
| E5 | A imagem final não tem `jdk.compiler`, `src/`, `pom.xml` nem `target/` | `deveConterSoORuntimeJava` |
| E6 | A porta é a 8080 | todos os cenários (mapeamento da porta) |
| E7 | Nenhum segredo embutido; configuração só por ambiente | revisão do Dockerfile (sem `ENV` de credencial) |
| E8 | Nenhuma proteção contra subir sem `prod` (risco aceito) | — (FR-012; documentado em `docs/container.md`) |
| E9 | Em `prod`, com banco divergente (schema não vazio sem histórico Flyway), a aplicação recusa subir: o container para com código ≠ 0 | `deveRecusarSubidaEmProducaoQuandoBancoDiverge` |
| E10 | Em `prod`, sem `SPRING_DATASOURCE_*`, a aplicação não sobe e não cai para o H2: o container para com código ≠ 0 e sem `Started` no log | `naoDeveSubirEmProducaoSemVariaveisDeConexao` |

## Saúde do container

| ID | Garantia | Verificado por |
|---|---|---|
| H1 | `HEALTHCHECK` declarado, em forma exec e sem shell, rodando `VerificarSaude` contra `http://localhost:8080/actuator/health` | `docker image inspect` (quickstart V2) |
| H2 | Parâmetros: `interval=15s`, `timeout=5s`, `start-period=60s`, `retries=3` | quickstart V2 |
| H3 | Fica `healthy` com o banco acessível (nos dois modos) | cenários E1 e E2 |
| H4 | Fica `unhealthy` quando o PostgreSQL deixa de estar acessível | `deveFicarNaoSaudavelQuandoPostgresFicaInacessivel` |
| H5 | Intervalo sobrescrevível em runtime (`--health-interval`) mantendo o comando da imagem | usado pelo cenário H4 |

## Publicação

| ID | Garantia | Verificado por |
|---|---|---|
| P1 | Referência `docker.io/<usuario>/habitattio:<versão>-<sha curto>`; nunca `latest` | quickstart V8 |
| P2 | A tag não existe antes do push (`docker manifest inspect` falha) | quickstart V8 |
| P3 | O pull sem login é negado; com o token de leitura, funciona | quickstart V9 |
| P4 | `RepoDigests` da imagem do container em execução na VPS contém o digest do push (o do índice, com atestação) | quickstart V10 |
