# Quickstart: validar `imoveis` como tabela-modelo

Guia para provar a feature de ponta a ponta. Detalhes de estrutura estão em
[data-model.md](./data-model.md) e as garantias em [contracts/imoveis-schema.md](./contracts/imoveis-schema.md).

## Pré-requisitos

- Java 25 e o wrapper `./mvnw` (nunca um `mvn` global).
- Docker rodando: os testes de persistência e de subida usam Testcontainers com
  `postgres:18.6` e `redis:8.10.3`. Sem Docker, rode só o cenário 1 e declare no relatório
  que os de integração não rodaram.

## 1. Regra nova do domínio (sem Docker)

```bash
./mvnw test -Dtest=ImovelTest
```

Esperado: verde, incluindo os testes novos de 255 (aceita) e 256 (rejeita com
"O endereço do imóvel deve ter no máximo 255 caracteres.") caracteres. Os testes
existentes de `ImovelTest` não foram alterados.

## 2. Regras no banco de produção (G1–G10)

```bash
./mvnw test -Dtest=ImoveisPostgresConstraintsTest
```

Esperado: um teste por linha do contrato G1–G10, todos verdes. Nenhum lê `ativo`.

## 3. Conferência na subida (S1–S4)

```bash
./mvnw test -Dtest=EstruturaBancoProducaoTest
```

Esperado: banco vazio sobe (S1); a segunda subida não reaplica (S2); a coluna removida
impede a subida e continua ausente (S3); o checksum adulterado impede a subida (S4).

## 4. Suíte completa

```bash
./mvnw test
```

Esperado: tudo verde, incluindo `HabitattioApplicationTests` **inalterado**.

## 5. Modo rápido continua o padrão (manual)

```bash
./mvnw spring-boot:run
curl -i -X POST localhost:8080/imoveis -H 'Content-Type: application/json' \
  -d '{"endereco":"Rua A, 100, apto 101","valorSugerido":1500.00}'
```

Esperado: a aplicação sobe com H2, sem PostgreSQL e sem Flyway no log, e responde
`201 Created`.

## 6. Modo produção (manual, opcional)

```bash
./mvnw spring-boot:test-run      # TestHabitattioApplication: containers fixos
```

Para um PostgreSQL próprio, ative o profile e informe a conexão por variáveis de ambiente:

```bash
SPRING_PROFILES_ACTIVE=prod \
SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/habitattio \
SPRING_DATASOURCE_USERNAME=... SPRING_DATASOURCE_PASSWORD=... \
./mvnw spring-boot:run
```

Esperado: o log mostra o Flyway aplicando `V1__cria_tabela_imoveis` na primeira subida e
"Schema ... is up to date" nas seguintes. Nenhum `alter table`/`create table` do Hibernate.

> `spring-boot:test-run` sobe pelo `TestHabitattioApplication`. Se ele não ativar o
> profile `prod`, passe `-Dspring-boot.run.profiles=prod`. A decisão final fica na task.
