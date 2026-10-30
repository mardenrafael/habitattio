# Contrato HTTP — módulo 2

**Feature**: [../spec.md](../spec.md) · **Decisões**: [../research.md](../research.md) (R1, R2, R5, R6, R10)

Convenções:

- JSON em `application/json`; erros em `application/problem+json` (`ProblemDetail`, RFC 9457)
  com `status`, `title` e `detail` em português. Nenhuma resposta contém senha ou hash.
- **Sessão**: cookie `JSESSIONID` (`HttpOnly`, `SameSite=Lax`, `Secure` em `prod`), expira após
  30 min de inatividade.
- **CSRF**: todo `POST`/`DELETE` exige o cabeçalho `X-XSRF-TOKEN` com o valor do cookie
  `XSRF-TOKEN`. O cookie vem em qualquer resposta (o cliente faz `GET /sessao` ao iniciar).
  Sem token ou com token inválido: **403**.
- Campos desconhecidos no JSON são ignorados (inclusive qualquer tentativa de informar o dono).

Respostas de erro comuns a todas as rotas protegidas:

| Status | Quando | `detail` |
|---|---|---|
| 401 | sem sessão, sessão expirada ou encerrada | "Autenticação necessária." |
| 403 | token CSRF ausente/inválido | "Token CSRF ausente ou inválido." |
| 400 | corpo JSON malformado ou com tipo errado | "Corpo da requisição inválido." |

Erro não previsto não tem formato definido neste módulo: a captura global de exceções genéricas
é uma feature à parte (spec, Clarifications C1). Todo erro listado neste contrato é tratado
explicitamente.

---

## Públicas (sem autenticação)

### `POST /proprietarios` — cadastrar proprietário (FR-001…FR-007)

Requisição:

```json
{ "nome": "Ana Souza", "email": "Ana@Exemplo.com ", "senha": "segredo123" }
```

| Status | Quando | Corpo |
|---|---|---|
| 201 | criado (sem `Location`: não há rota de consulta de proprietário neste módulo) | `{ "id": 1, "nome": "Ana Souza", "email": "ana@exemplo.com" }` |
| 400 | nome vazio/longo, e-mail vazio/inválido/longo, senha < 8 caracteres ou > 72 bytes | `detail` = mensagem do domínio |
| 409 | e-mail já cadastrado (comparado normalizado) | "Já existe um proprietário com este e-mail." |

O cadastro **não** autentica: o proprietário entra em seguida por `POST /sessao`.

### `POST /sessao` — entrar (FR-008, FR-009)

Requisição `application/x-www-form-urlencoded`: `email=ana%40exemplo.com&senha=segredo123`
(e-mail em qualquer caixa e com espaços nas pontas).

| Status | Quando | Corpo |
|---|---|---|
| 204 | autenticado; novo `JSESSIONID` (id trocado) e novo `XSRF-TOKEN` | — |
| 401 | e-mail inexistente **ou** senha errada (indistinguíveis) | "E-mail ou senha inválidos." |

---

## Protegidas (exigem sessão)

### `GET /sessao` — quem está autenticado

| Status | Quando | Corpo |
|---|---|---|
| 200 | sessão válida | `{ "id": 1, "nome": "Ana Souza", "email": "ana@exemplo.com" }` |
| 401 | sem sessão | "Autenticação necessária." |

Mesmo respondendo 401 sem sessão, a resposta já traz o cookie `XSRF-TOKEN`: é por ela que o
cliente obtém o token CSRF antes do cadastro e do login.

### `DELETE /sessao` — sair (FR-011)

| Status | Quando |
|---|---|
| 204 | sessão invalidada; requisições seguintes com o mesmo cookie recebem 401 |
| 401 | já não havia sessão |

### `POST /imoveis` — cadastrar imóvel (FR-012…FR-017) — **muda**

Requisição:

```json
{ "identificador": "Apto 101", "endereco": "Rua A, 10", "valorSugerido": 1500.00 }
```

| Status | Quando | Corpo |
|---|---|---|
| 201 | criado para o proprietário da sessão; `Location: /imoveis/{id}` | `ImovelResponse` |
| 400 | identificador vazio/> 50, endereço vazio/> 255, valor ausente/≤ 0 | mensagem do domínio |
| 409 | o proprietário já tem esse identificador (sem distinguir caixa) | "Você já tem um imóvel com o identificador 'Apto 101'." |

`ImovelResponse`:

```json
{ "id": 7, "identificador": "Apto 101", "endereco": "Rua A, 10", "valorSugerido": 1500.0000, "ativo": true }
```

Mudanças em relação ao módulo 1: exige sessão e CSRF; ganha `identificador` na requisição e
`identificador`/`ativo` na resposta; erro de domínio responde 400 em vez de 500 (D3).

### `GET /imoveis/{id}` — consultar imóvel (FR-018, FR-020)

| Status | Quando | Corpo |
|---|---|---|
| 200 | o imóvel é do proprietário da sessão | `ImovelResponse` |
| 404 | não existe **ou** é de outro proprietário (indistinguíveis) | "Imóvel não encontrado." |
| 400 | `{id}` não numérico | "Identificador de imóvel inválido." |

### `POST /imoveis/{id}/inativacao` — inativar imóvel (FR-019, FR-020)

Sem corpo.

| Status | Quando | Corpo |
|---|---|---|
| 200 | inativado (ou já estava inativo — idempotente) | `ImovelResponse` com `"ativo": false` |
| 404 | não existe **ou** é de outro proprietário; nada é alterado | "Imóvel não encontrado." |
| 400 | `{id}` não numérico | "Identificador de imóvel inválido." |

---

## Fora deste contrato

Edição e listagem de imóveis, `GET /proprietarios/{id}`, troca de senha, remoção de conta.
