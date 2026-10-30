# Quickstart — validar o módulo 2

**Feature**: [spec.md](./spec.md) · **Contratos**: [contracts/api-http.md](./contracts/api-http.md), [contracts/schema-v2.md](./contracts/schema-v2.md)

Roteiro para provar, depois da implementação, que o módulo entrega o que a spec pede. Não
contém código de produção.

## Pré-requisitos

- Java 25; Docker rodando (testes de integração e profile `prod`).
- Se você tem um PostgreSQL de dev com linhas criadas pela V1, recrie o banco: a V2 exige
  `imoveis` vazia (contracts/schema-v2.md).

## 1. Suíte automatizada

```bash
./mvnw test
```

Esperado: tudo verde, incluindo os testes que cobrem:

| Prova | Onde | Spec |
|---|---|---|
| Regras de `Usuario`, `Senha`, `Imovel` | `domain/model/*Test` | FR-002…FR-005, FR-013, FR-015 |
| Cadastro, autenticação, consulta e inativação com fakes | `application/usecase/*Test` | US1–US3 |
| Garantias G-U1…G-I7 e G-S1 no PostgreSQL | testes de persistência no profile `prod` | US4, SC-006 |
| Tradução de corrida de unicidade | testes dos adapters | FR-025 |
| Status e mensagens por rota (400/401/403/404/409) | `adapters/inbound/rest/*Test` | FR-026, SC-007 |
| Isolamento ponta a ponta com dois proprietários | teste `@SpringBootTest` no `prod` | SC-002, SC-003 |

Testes existentes adaptados (só dados, Clarifications Q2): `ImovelTest`,
`CadastrarImovelUseCaseTest`, `ImovelRepositoryAdapterTest`, `ImoveisPostgresConstraintsTest`
— conferir no diff que nenhuma asserção nem nome de teste mudou.

Se o Docker não estiver disponível, rodar só domínio e aplicação e **dizer no relatório** que a
integração não rodou:

```bash
./mvnw test -Dtest='**/domain/**/*Test,**/application/**/*Test'
```

## 2. Roteiro manual (modo rápido)

```bash
./mvnw spring-boot:run
```

Com `curl` e um arquivo de cookies (`-b c.txt -c c.txt`). Em cada `POST`/`DELETE`, envie
`-H "X-XSRF-TOKEN: <valor do cookie XSRF-TOKEN em c.txt>"`.

| # | Passo | Esperado |
|---|---|---|
| 1 | `GET /sessao` | 401 "Autenticação necessária."; cookie `XSRF-TOKEN` gravado |
| 2 | `POST /imoveis` sem sessão | 401; nada gravado (SC-003) |
| 3 | `POST /proprietarios` Ana (`Ana@Exemplo.com `, `segredo123`) | 201, e-mail `ana@exemplo.com`, sem senha/hash |
| 4 | repetir o passo 3 com `ana@exemplo.com` | 409 |
| 5 | `POST /proprietarios` com senha `curta` | 400 "A senha deve ter no mínimo 8 caracteres." |
| 6 | `POST /sessao` com senha errada; depois com `bruno@exemplo.com` | as duas: 401 "E-mail ou senha inválidos." — idênticas (SC-005) |
| 7 | `POST /sessao` Ana correta | 204; `JSESSIONID` diferente do anterior |
| 8 | `POST /imoveis` "Apto 101" sem `X-XSRF-TOKEN` | 403 |
| 9 | `POST /imoveis` "Apto 101" com token | 201, `ativo: true` |
| 10 | `POST /imoveis` " apto 101 " | 409 |
| 11 | `POST /imoveis` com `valorSugerido: 0` | **400** (antes: 500 — D3) |
| 12 | sair (`DELETE /sessao`), cadastrar e entrar como Bruno | 204 / 201 / 204 |
| 13 | Bruno: `POST /imoveis` "Apto 101" | 201 (unicidade é por dono) |
| 14 | Bruno: `GET /imoveis/{id da Ana}` e `GET /imoveis/999999` | os dois: 404 "Imóvel não encontrado." — idênticos |
| 15 | Bruno: `POST /imoveis/{id da Ana}/inativacao` | 404; entrar como Ana e `GET` mostra `ativo: true` |
| 16 | Ana: `POST /imoveis/{id}/inativacao` duas vezes | 200 / 200, `ativo: false` |
| 17 | `DELETE /sessao`; `GET /imoveis/{id}` com o mesmo cookie | 401 |

Conferências de privacidade (SC-004): no console da aplicação, procurar por `segredo123` — não
pode aparecer. Nenhuma resposta dos passos acima contém `senha` nem `$2`.

## 3. Profile `prod`

```bash
# sobe a app com PostgreSQL e Redis em containers
./mvnw spring-boot:test-run   # ou executar TestHabitattioApplication na IDE
```

Esperado: log do Flyway aplicando V1 e V2; a subida passa no `ddl-auto=validate`. Repetir os
passos 3, 9, 13 e 14 da seção 2. Em `psql`, `SELECT senha_hash FROM usuarios` mostra só valores
`$2…` de 60 caracteres.
