# Bug Verification: `Imovel` volta como `ativo = true` após salvar, mesmo inativado

- **Slug**: imovel-ativo-perdido
- **Tested**: 2026-10-29
- **Assessment**: ./assessment.md
- **Fix**: ./fix.md
- **Result**: verified

## Summary

O sintoma não reproduz mais: salvar um `Imovel` inativado pelo `ImovelRepositoryAdapter` devolve
`isAtivo() == false`, e a linha relida do banco também tem `ativo = false`. A suíte completa
(42 testes, integração incluída) passa e nenhuma regressão foi encontrada. Durante a verificação
apareceu um problema de configuração que já existia antes da correção (dialeto H2 forçado contra
o PostgreSQL dos testes). Ele não afeta este fix, mas está registrado em Residual Risks.

## Checks Performed

| Check | Command / Action | Result | Notes |
|-------|------------------|--------|-------|
| Reproduction (post-fix) | `./mvnw test -Dtest=ImovelRepositoryAdapterTest` | pass | Equivalente automatizado dos passos 1–4 do assessment: cria, `inativar()`, `adapter.salvar(...)`, confere o retorno e relê do banco após `flush()`/`clear()`. Conexão real `org.postgresql.jdbc.PgConnection` (Testcontainers `postgres:latest`). Antes da correção esse mesmo teste falhava com `expected: <false> but was: <true>` (registrado em fix.md) |
| New / updated tests | `./mvnw clean verify` (inclui `ImovelTest`, `ImovelRepositoryAdapterTest`, `CadastrarImovelUseCaseTest`) | pass | `ImovelTest` 11/11, `ImovelRepositoryAdapterTest` 2/2, `CadastrarImovelUseCaseTest` 2/2 |
| Regression suite | `./mvnw clean verify` | pass | 42 testes, 0 falhas, 0 erros, 0 skipped; `HabitattioApplicationTests` (contexto completo + Testcontainers) incluído; jar gerado; BUILD SUCCESS |
| Arquitetura hexagonal | `grep` por `import org.springframework\|jakarta` em `domain/` e `application/`; `grep` por `JpaEntity` fora de `adapters/outbound/persistence` | pass | Nenhuma ocorrência nos dois casos |
| Lint / type-check | n/a | not-run | O projeto não tem linter configurado. A compilação `javac` do `clean verify` passou |

## Output Excerpts

```
[INFO] Tests run: 2, Failures: 0, Errors: 0, Skipped: 0 -- in br.com.marden.habitattio.adapters.outbound.persistence.ImovelRepositoryAdapterTest
[INFO] Tests run: 11, Failures: 0, Errors: 0, Skipped: 0 -- in br.com.marden.habitattio.domain.model.ImovelTest
[INFO] Tests run: 42, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

Log do teste de adapter (conexão e SQL efetivos):

```
HikariPool-1 - Added connection org.postgresql.jdbc.PgConnection@21bd6fd1
insert into imoveis (ativo, endereco, valor_sugerido, id) ...
```

Problema de configuração que já existia, não causado pelo fix:

```
StrategySelectionException: Could not instantiate named dialect class [org.hibernate.dialect.H2Dialect]
Caused by: java.lang.NumberFormatException: For input string: "(Debian"
Database dialect: H2Dialect / Database version: 2.1.214
```

## Residual Risks

- **Dialeto H2 forçado nos testes com PostgreSQL.** `application.properties` fixa
  `spring.jpa.database-platform=org.hibernate.dialect.H2Dialect`. Nos testes com Testcontainers a
  conexão é PostgreSQL, mas o Hibernate gera SQL de H2. Ao tentar ler a versão do servidor
  Postgres, ele lança `NumberFormatException` (vira só um WARN) e cai no padrão H2 2.1.214. Para a
  tabela simples `imoveis` o SQL é compatível e os testes passam, mas os testes de integração não
  validam o SQL que o PostgreSQL de produção receberia. É anterior a este fix e também afeta
  `HabitattioApplicationTests`. O AGENTS.md §9 proíbe mexer em `application.properties` sem
  acordo, por isso não foi tocado.
- Ainda não existe fluxo exposto (endpoint/caso de uso) de inativação nem de leitura por id, então
  a correção foi verificada no nível do adapter, que é onde o bug estava, e não ponta a ponta pela
  API.
- `Contrato` e `Fatura` têm o mesmo padrão de construtor latente. Por decisão do usuário, ficam
  para quando ganharem persistência.
- Imagem `postgres:latest` sem versão fixa: o teste pode mudar de comportamento quando sair uma
  nova versão.

## Recommendation

Fechar o bug: a correção foi verificada no adapter contra um PostgreSQL real, com o teste que
reproduzia o sintoma agora verde e a suíte completa sem regressões. Recomendo abrir um item
separado (precisa de acordo, AGENTS.md §9) para tirar o `H2Dialect` fixo do
`application.properties`, ou sobrescrevê-lo no perfil de teste, para que os testes de integração
validem o SQL real do PostgreSQL.
