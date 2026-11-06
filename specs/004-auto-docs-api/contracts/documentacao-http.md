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
| `GET /webjars/swagger-ui/**` (arquivos estáticos da UI) | servidos conforme o padrão do Spring Boot | igual a uma rota inexistente: 401 para anônimo (como qualquer rota não pública), 404 para autenticado |

- Nenhuma rota de documentação exige CSRF (são todas `GET`).
- A UI, no modo rápido, envia `X-XSRF-TOKEN` com o valor do cookie `XSRF-TOKEN` nas chamadas
  "Try it out" (R8): `GET /swagger-ui/swagger-initializer.js` contém o `requestInterceptor` que
  copia o cookie para o cabeçalho (verificado por teste). O uso ponta a ponta no navegador é
  desejável, não obrigatório, e verificado à mão.
- Nenhuma outra rota muda: `/actuator` continua expondo só `health`; as 8 operações da API
  mantêm método, caminho, status e regras de autenticação.
- Em `prod`, nenhum arquivo da Swagger UI fica online (research R7): a descrição existe só no
  repositório, em `docs/api/openapi.yaml`.
- Em `prod`, o anônimo recebe 404 nas rotas de documentação (públicas por regra) e 401 nas demais
  rotas inexistentes, incluindo `/webjars/**`. Os dois significam "não disponível" e nenhum expõe
  conteúdo; a diferença foi aceita pelo dono (analyze I3, 2026-11-02).

Verificação: `DocumentacaoApiTest` (modo rápido, MockMvc) e `DocumentacaoApiProducaoTest`
(profile `prod`, Testcontainers — exige Docker).
