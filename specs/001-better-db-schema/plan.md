# Implementation Plan: `imoveis` como tabela-modelo com estrutura versionada e regras no banco

**Branch**: `001-better-db-schema` | **Date**: 2026-10-29 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `specs/001-better-db-schema/spec.md`

## Summary

Levar as regras simples de `Imovel` também para o PostgreSQL de produção e tornar a estrutura
do banco versionada e conferida. A estrutura de produção passa a nascer de migrações Flyway em
SQL puro (`V1__cria_tabela_imoveis.sql`): `endereco varchar(255)` não em branco,
`valor_sugerido numeric(19,4) > 0`, `CHECK`s nomeados. Um profile `prod` ativa o Flyway e o
`ddl-auto=validate`. Sem profile, a aplicação continua no modo rápido (H2 + `update`), como
hoje. O domínio ganha uma única regra nova: endereço com no máximo 255 caracteres. As regras
são provadas por testes com SQL direto contra `postgres:18.6`, e a conferência na subida por
testes que sobem a aplicação contra bancos preparados. O padrão fica em
`docs/persistencia.md`. Detalhes e alternativas estão em [research.md](./research.md).

## Technical Context

**Language/Version**: Java 25

**Primary Dependencies**: Spring Boot 4.1.2 (Web MVC, Data JPA / Hibernate 7.4.13, Data Redis,
Validation). **Novas** (acordo FR-014): `spring-boot-starter-flyway` e
`org.flywaydb:flyway-database-postgresql`, as duas gerenciadas pelo BOM (Flyway 12.4.0).

**Storage**: PostgreSQL em produção (estrutura por Flyway, `ddl-auto=validate`). H2 em memória
no modo rápido padrão (estrutura automática, `ddl-auto=update`, Flyway desligado).

**Testing**: JUnit 5. `@DataJpaTest` + Testcontainers (`postgres:18.6`) para as regras.
`SpringApplicationBuilder` + Testcontainers para a subida. JUnit puro para o domínio.

**Target Platform**: servidor Linux (JVM). Provisionar o banco está fora de escopo.

**Project Type**: web-service (monólito Spring Boot, arquitetura hexagonal)

**Performance Goals**: não se aplica (cerca de 14 imóveis; fora de escopo na spec)

**Constraints**: nenhum teste existente alterado. `domain/` e `application/` sem import de
framework. H2 e `ddl-auto` da configuração **base** inalterados. Conexão de produção por
variáveis de ambiente, nunca em arquivo.

**Scale/Scope**: 1 tabela, 1 migração, 1 regra de domínio nova, 2 propriedades de mapeamento,
2 arquivos de configuração, cerca de 3 classes de teste novas e 1 documento de padrão.

Nenhum `NEEDS CLARIFICATION` restante: todas as incógnitas foram resolvidas em
[research.md](./research.md) (R1 a R9).

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Princípio | Verificação | Pré-design | Pós-design |
|---|---|---|---|
| I. Hexagonal | Flyway, profiles e anotações JPA ficam em `resources/` e `adapters/outbound/persistence`. `domain/` ganha só um `if` em Java puro. Nenhuma porta nova, `*JpaEntity` não sai do adapter. | ✅ | ✅ |
| II. TDD | Ordem: teste de domínio (255/256) → teste de constraints no PG → teste de subida → implementação. Todo teste é visto falhando antes (ver "Ordem TDD" abaixo). Nenhum teste existente é editado; `HabitattioApplicationTests` fica intacto. | ✅ | ✅ |
| III. SDD | spec → clarify → plan concluídos. Tasks e analyze vêm a seguir. | ✅ | ✅ |
| IV. YAGNI / acordo | Dependência nova e `validate`: **acordo explícito** registrado (spec FR-014, Clarifications). Nenhuma abstração nova: sem porta, sem repositório extra, sem profile `dev` (R2). O mapeamento continua manual. | ✅ (com acordo) | ✅ |
| V. Convenções | Mensagem nova em português, `IllegalArgumentException` no construtor, `BigDecimal` intacto, nomes de tabela e coluna em português. | ✅ | ✅ |
| Stack | Flyway passa a fazer parte do stack. **Ação**: atualizar a seção "Stack Tecnológico" da constitution (PATCH, via `/speckit-constitution`) e o AGENTS.md §1/§6/§9 na mesma mudança (Governance). | ⚠️ follow-up | ⚠️ follow-up, vira task |

**Resultado**: o gate passa. O único item pendente é uma atualização documental obrigatória
(stack na constitution e no AGENTS.md), que entra como task, sem violação a justificar.

### Ordem TDD (de dentro para fora, constitution §Fluxo)

1. **Domínio**: escrever `deveRejeitarEnderecoComMaisDe255Caracteres`,
   `deveAceitarEnderecoCom255Caracteres` e `deveContarCaracteresENaoUnidadesUtf16NoLimiteDoEndereco`
   em `ImovelTest`. Ver falhar (o primeiro falha; os outros documentam a fronteira e podem
   passar de primeira, e isso deve ser dito). Implementar o `if` em `Imovel`.
2. **Infra de teste**: fixar as imagens e tornar `TestcontainersConfiguration` pública. É
   configuração, não teste.
3. **Constraints no PG**: escrever `ImoveisPostgresConstraintsTest` (G1 a G10). Ver falhar
   (sem profile `prod` e sem migração, o banco é o do Hibernate, sem `CHECK` nem
   `numeric(19,4)`). Criar `application-prod.properties`, `V1__cria_tabela_imoveis.sql` e
   ajustar `ImovelJpaEntity` até ficar verde.
4. **Subida**: escrever `EstruturaBancoProducaoTest` (S1 a S4). Ver falhar pelo motivo
   certo. Completar a configuração até ficar verde.
5. **Modo rápido**: `spring.flyway.enabled=false` na base. Garantir `HabitattioApplicationTests`
   e o restante da suíte verdes sem alteração.
6. **Docs**: `docs/persistencia.md`, AGENTS.md e constitution (PATCH).

## Project Structure

### Documentation (this feature)

```text
specs/001-better-db-schema/
├── plan.md               # este arquivo
├── research.md           # Phase 0 (R1–R9)
├── data-model.md         # Phase 1
├── quickstart.md         # Phase 1
├── contracts/
│   └── imoveis-schema.md # Phase 1: garantias G1–G10 e S1–S4
├── checklists/
│   └── requirements.md
└── tasks.md              # Phase 2 (/speckit-tasks; não criado aqui)
```

### Source Code (repository root)

```text
pom.xml                                                    # + starter-flyway, + flyway-database-postgresql
docs/
└── persistencia.md                                        # NOVO: padrão para os próximos agregados (FR-012)
AGENTS.md                                                  # §1, §6, §9 apontam para o padrão (FR-013)
.specify/memory/constitution.md                            # Stack: + Flyway (PATCH)

src/main/java/br/com/marden/habitattio/
├── domain/model/Imovel.java                               # + regra de 255 caracteres (code points)
└── adapters/outbound/persistence/entity/ImovelJpaEntity.java  # length = 255; precision = 19, scale = 4

src/main/resources/
├── application.properties                                 # + spring.flyway.enabled=false (só isso)
├── application-prod.properties                            # NOVO: flyway on, validate, PostgreSQLDialect, driver PG
└── db/migration/
    └── V1__cria_tabela_imoveis.sql                        # NOVO: tabela, pk e ck_* nomeados

src/test/java/br/com/marden/habitattio/
├── TestcontainersConfiguration.java                       # public; postgres:18.6, redis:8.10.3 como constantes
├── EstruturaBancoProducaoTest.java                        # NOVO: S1–S4 (subida real com profile prod)
├── HabitattioApplicationTests.java                        # INALTERADO
├── domain/model/ImovelTest.java                           # + testes de 255/256 (só adição)
└── adapters/outbound/persistence/
    └── ImoveisPostgresConstraintsTest.java                # NOVO: G1–G10 via SQL direto
```

**Structure Decision**: o projeto Maven único e o layout hexagonal existentes são mantidos.
Nada entra em `ports/` ou `application/`, porque não há caso de uso novo. Os testes seguem a
tabela do AGENTS.md §3: os de persistência ficam em `adapters/outbound/` e os de
contexto/subida ficam na raiz, ao lado de `HabitattioApplicationTests`.

## Riscos e mitigação

| Risco | Mitigação |
|---|---|
| O `validate` acusar diferença de tipo entre o mapeamento e a V1 (ex.: `numeric` vs. precisão) | O mapeamento declara `precision`/`scale`/`length` explícitos (data-model). O teste S1 pega isso na primeira execução. |
| `@DataJpaTest` substituir o datasource ou não rodar o Flyway | `@AutoConfigureTestDatabase(replace = NONE)` explícito. O teste do `CHECK` só passa se a V1 rodou (R6). |
| A base `H2Dialect` vazar para o profile `prod` | O `application-prod.properties` sobrescreve `spring.jpa.database-platform`. Isso é provado pelo S1. |
| A tag `postgres:18.6` ser removida do Docker Hub | Tags de patch não são removidas na prática. Se for, basta trocar a constante (um único lugar). |
| Esbarrar no defeito de reidratação | Nenhum teste lê `ativo` nem usa o retorno do adapter (R6). |

## Complexity Tracking

Sem violações a justificar. A dependência nova tem acordo explícito (FR-014). Não há
abstração, porta ou camada nova.
