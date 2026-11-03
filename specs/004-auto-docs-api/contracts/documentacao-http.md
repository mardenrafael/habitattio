# Contrato HTTP: rotas de documentação

**Feature**: `004-auto-docs-api` | Superfície nova permitida por FR-010/FR-011.

Estas rotas são da **documentação**, não da API: não aparecem na própria descrição (D3 de
[descricao-api.md](descricao-api.md)).

| Rota | Modo rápido (sem profile) | Modo produção (`prod`) |
|---|---|---|
| `GET /v3/api-docs` | 200, JSON da descrição, sem autenticação | 404, para anônimo e autenticado |
| `GET /v3/api-docs.yaml` | 200, YAML da descrição, sem autenticação | 404, para anônimo e autenticado |
| `GET /swagger-ui.html` | redireciona para a UI, sem autenticação | 404, para anônimo e autenticado |
| `GET /swagger-ui/index.html` | 200, página navegável, sem autenticação | 404, para anônimo e autenticado |

- Nenhuma rota de documentação exige CSRF (são todas `GET`).
- A UI, no modo rápido, envia `X-XSRF-TOKEN` com o valor do cookie `XSRF-TOKEN` nas chamadas
  "Try it out" (R8). É desejável, não obrigatório; a verificação é manual.
- Nenhuma outra rota muda: `/actuator` continua expondo só `health`; as 8 operações da API
  mantêm método, caminho, status e regras de autenticação.
- Resíduo aceito em `prod` (research R7): os arquivos estáticos em `/webjars/swagger-ui/**`
  continuam acessíveis a proprietário autenticado, sem a descrição da API.

Verificação: `DocumentacaoApiTest` (modo rápido, MockMvc) e `DocumentacaoApiProducaoTest`
(profile `prod`, Testcontainers — exige Docker).
