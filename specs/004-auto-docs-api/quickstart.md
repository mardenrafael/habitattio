# Quickstart: validar a descrição da API derivada do código

**Feature**: `004-auto-docs-api`

Cenários executáveis que provam a feature de ponta a ponta. Contratos de referência:
[descricao-api.md](contracts/descricao-api.md) e [documentacao-http.md](contracts/documentacao-http.md).

## Pré-requisitos

- JDK 25 e o wrapper `./mvnw`.
- Docker rodando **só** para o cenário 5 (profile `prod`). Os cenários 1–4 não precisam de Docker.

## Cenário 1 — Regenerar o arquivo com um comando (US1, SC-003)

```bash
./mvnw test -Dtest=DescricaoApiTest
git status --short docs/api/openapi.yaml
```

**Esperado**: suíte verde; `docs/api/openapi.yaml` regravado. Na primeira execução após a
feature, o diff substitui o arquivo manual; nas seguintes, sem mudança de código, `git status` não
mostra o arquivo (SC-005).

## Cenário 2 — Mudança no controller aparece sem editar documentação (US1, SC-002)

1. Acrescente temporariamente um componente ao record `ImovelResponse` (e ao `paraResponse`).
2. `./mvnw test -Dtest=DescricaoApiTest`
3. `git diff docs/api/openapi.yaml` → o campo novo aparece em `ImovelResponse`.
4. Desfaça a mudança e rode de novo: o diff some.

## Cenário 3 — Operação ausente derruba a suíte (US4, SC-004)

1. Comente temporariamente, em `DocumentacaoApiConfiguration`, a montagem de `DELETE /sessao`.
2. `./mvnw test -Dtest=DescricaoApiTest` → **falha** citando `DELETE /sessao`;
   `git status` mostra `docs/api/openapi.yaml` **inalterado**.
3. Desfaça.

## Cenário 4 — Swagger UI e fluxo completo no modo rápido (US2, US3, SC-006)

```bash
./mvnw spring-boot:run
```

1. Abra `http://localhost:8080/swagger-ui.html` sem login → as 8 operações aparecem, com o guia
   no topo.
2. `http://localhost:8080/v3/api-docs.yaml` → mesmo conteúdo de `docs/api/openapi.yaml`.
3. **API client** (Bruno/Insomnia/Postman, cookie jar ligado): importe `docs/api/openapi.yaml`
   num workspace vazio e siga só o guia: `GET /sessao` (401 + cookie) → `POST /proprietarios`
   → `POST /sessao` (204, novo `XSRF-TOKEN`) → `POST /imoveis` (201) → `GET /imoveis/{id}` (200)
   → `DELETE /sessao` (204) → `GET /sessao` (401).
4. **Opcional ("Try it out", R8)**: na UI, execute `GET /sessao` e depois `POST /proprietarios`.
   Se o 403 aparecer, registre no relatório: a UI fica só de leitura, como a spec admite.

## Cenário 5 — Fechado em produção (US3, FR-010)

```bash
./mvnw test -Dtest=DocumentacaoApiProducaoTest   # exige Docker
```

**Esperado**: `/v3/api-docs`, `/v3/api-docs.yaml`, `/swagger-ui.html` e `/swagger-ui/index.html`
respondem 404, anônimo e autenticado.

## Cenário 6 — Nada mais mudou (FR-011)

```bash
./mvnw test        # suíte completa; exige Docker para os testes de integração
```

**Esperado**: todos os testes existentes continuam verdes sem alteração (nenhum teste anterior é
editado), e `GET /actuator` continua sem listar outros endpoints.
