# Data Model: Descrição da API derivada do código

**Feature**: `004-auto-docs-api` | **Date**: 2026-11-02

Esta feature **não toca o modelo de domínio nem o banco**: nenhuma entidade, porta, caso de uso,
tabela ou migração nova. As "entidades" da spec são artefatos de documentação, todos na camada
de adapters ou fora do código de produção.

## Descrição da API

Documento OpenAPI 3.1 produzido pela aplicação a partir de três fontes:

| Fonte | Onde | Contribui com |
|---|---|---|
| Controllers e records `*Request`/`*Response` | `adapters/inbound/rest/` (anotações) | 5 operações, esquemas de entrada/saída, resumos, descrições, exemplos |
| Configuração da documentação | `adapters/config/DocumentacaoApiConfiguration` | `info` (título, guia), `servers`, `securitySchemes`, respostas e esquemas compartilhados, 3 operações manuais |
| Propriedades | `application.properties` / `application-prod.properties` | ordem estável, respostas genéricas desligadas, CSRF na UI, desligamento em `prod` |

Regras de validade: [contracts/descricao-api.md](contracts/descricao-api.md) (D1–D5).

## Operação

Par método + caminho com `summary`, `description`, `tags`, `security`, `requestBody`
(quando houver) e `responses`. Catálogo atual e marcação de segurança:
[contracts/descricao-api.md](contracts/descricao-api.md#d1--operações-obrigatórias-e-marcação-de-segurança-fr-002-fr-004).

Componentes compartilhados (nomes preservados do arquivo manual, para não quebrar referências
de quem já importou):

| Tipo | Nome | Uso |
|---|---|---|
| securityScheme | `sessao`, `csrf` | marcação por operação |
| response | `NaoAutenticado` | 401 das rotas protegidas |
| response | `CsrfInvalido` | 403 de `POST`/`DELETE` |
| response | `ImovelNaoEncontrado` | 404 de imóvel inexistente ou de outro proprietário |
| response | `IdInvalido` | 400 de `{id}` não numérico |
| schema | `ProblemDetail` | corpo de todo erro (`application/problem+json`) |
| schema | `EntrarForm` | corpo do login (formulário) |
| schema | `Saude` | corpo de `/actuator/health` |

Os esquemas de entrada e saída dos controllers (`CadastrarProprietarioRequest`,
`ProprietarioResponse`, `SessaoResponse`, `CadastrarImovelRequest`, `ImovelResponse`) são
**inferidos** dos records; restrições (tamanhos, mínimos, descrições) vêm de `@Schema` nos
componentes dos records, refletindo as invariantes já validadas no domínio (sem duplicar a
validação: são só descrição).

## Arquivo exportado

`docs/api/openapi.yaml` — cópia exata do YAML servido em `/v3/api-docs.yaml` no modo rápido.
**Gerado**: regravado a cada `./mvnw test`; não editar à mão. Ciclo de vida:

```text
código muda ──► ./mvnw test ──► DescricaoApiTest verifica D1–D4 ──┬─► ok: grava (atômico) ──► git diff mostra a mudança
                                                                   └─► falha: arquivo intacto, suíte vermelha
```
