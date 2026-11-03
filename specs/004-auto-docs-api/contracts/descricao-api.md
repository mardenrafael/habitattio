# Contrato: conteúdo mínimo da descrição gerada

**Feature**: `004-auto-docs-api` | Verificado por `DescricaoApiTest` (modo rápido, sem Docker).

Este é o piso que a descrição servida em `/v3/api-docs.yaml` (e exportada para
`docs/api/openapi.yaml`) MUST cumprir. Tudo o que não está aqui (ordem, redação exata, esquemas
inferidos pelo gerador) pode variar sem quebrar o teste.

## D1 — Operações obrigatórias e marcação de segurança (FR-002, FR-004)

A descrição MUST conter **ao menos** estas 8 operações. A coluna "Segurança" é o valor exato de
`security` na operação: `sessao` = cookie `JSESSIONID`; `csrf` = cabeçalho `X-XSRF-TOKEN`;
`[]` = pública e sem CSRF.

| # | Operação | Origem | Segurança | Corpo de entrada | Respostas mínimas |
|---|---|---|---|---|---|
| 1 | `POST /proprietarios` | controller | `csrf` | JSON `CadastrarProprietarioRequest` | 201, 400, 403, 409 |
| 2 | `GET /sessao` | controller | `sessao` | — | 200, 401 |
| 3 | `POST /sessao` | manual (R2) | `csrf` | `application/x-www-form-urlencoded` `email`, `senha` | 204, 401, 403 |
| 4 | `DELETE /sessao` | manual (R2) | `sessao` + `csrf` | — | 204, 401, 403 |
| 5 | `POST /imoveis` | controller | `sessao` + `csrf` | JSON `CadastrarImovelRequest` | 201, 400, 401, 403, 409 |
| 6 | `GET /imoveis/{id}` | controller | `sessao` | — | 200, 400, 401, 404 |
| 7 | `POST /imoveis/{id}/inativacao` | controller | `sessao` + `csrf` | — | 200, 400, 401, 403, 404 |
| 8 | `GET /actuator/health` | manual (R2) | `[]` | — | 200, 503 |

Regras derivadas, verificadas por operação:

- Toda operação com 401 na tabela referencia a resposta compartilhada `NaoAutenticado`, exceto
  `GET /sessao` e `POST /sessao`, que têm 401 próprio (texto diferente).
- Toda operação com 403 referencia `CsrfInvalido`; toda com 404 referencia `ImovelNaoEncontrado`.
- Nenhuma operação tem parâmetro que represente o proprietário autenticado (R9).
- Toda operação tem `summary` e `tags` não vazios; toda resposta de erro usa
  `application/problem+json`.
- Toda operação que recebe ou devolve corpo tem ao menos um `example` (FR-006).

## D2 — Guia e textos globais (FR-005)

- `info.title` = `Habitattio API`.
- `info.description` MUST conter o guia de sessão/CSRF em português, com pelo menos: `GET /sessao`
  como forma de obter o cookie `XSRF-TOKEN`; o cabeçalho `X-XSRF-TOKEN` em `POST`/`DELETE`; o
  aviso de que o login emite um **novo** `XSRF-TOKEN`; a expiração de 30 min; o formato de erro
  `application/problem+json` com `detail` em português; o 404 para imóvel de outro proprietário.
- `info.description` MUST dizer que o arquivo é **gerado** pela suíte (`./mvnw test`) e não deve
  ser editado à mão, e que é a referência do contrato HTTP (FR-012).
- `components.securitySchemes` MUST ter `sessao` (apiKey, cookie, `JSESSIONID`) e `csrf` (apiKey,
  header, `X-XSRF-TOKEN`).

## D3 — Exclusões (FR-011, edge case "rotas internas")

- Nenhum caminho começa com `/v3/api-docs` ou `/swagger-ui`.
- O único caminho sob `/actuator` é `/actuator/health`.
- Nenhum caminho `/error`.

## D4 — Servidor e estabilidade (FR-009)

- `servers` tem exatamente um item: `http://localhost:8080`.
- `openapi` começa com `3.1`.
- Duas leituras consecutivas de `/v3/api-docs.yaml` no mesmo contexto produzem texto idêntico.

## D5 — Exportação (FR-007, FR-008)

- O arquivo `docs/api/openapi.yaml` é o texto servido em `/v3/api-docs.yaml`, byte a byte.
- É gravado só depois de D1–D4 passarem, por arquivo temporário no mesmo diretório + movimento
  atômico. Falhou a verificação → o arquivo existente fica intacto.
