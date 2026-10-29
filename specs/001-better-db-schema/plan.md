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
2 arquivos de configuração, 4 classes de teste novas e 1 documento de padrão.

Nenhum `NEEDS CLARIFICATION` restante: todas as incógnitas foram resolvidas em
[research.md](./research.md) (R1 a R9).

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Princípio | Verificação | Pré-design | Pós-design |
|---|---|---|---|
| I. Hexagonal | Flyway, profiles e anotações JPA ficam em `resources/` e `adapters/outbound/persistence`. `domain/` ganha só um `if` em Java puro. Nenhuma porta nova, `*JpaEntity` não sai do adapter. | ✅ | ✅ |
| II. TDD | Toda mudança em `src/main/` (incluindo `.properties` e mapeamento JPA) tem ao menos um teste visto falhar pelo motivo certo **antes** dela (ver "Ordem TDD"). Testes que passam de primeira são de **caracterização**, não justificam código e são declarados no relatório. Nenhum teste existente é editado; `HabitattioApplicationTests` fica intacto. | ✅ | ✅ (corrigido após `/speckit-analyze` C1/C2) |
| III. SDD | spec → clarify → plan concluídos. Tasks e analyze vêm a seguir. | ✅ | ✅ |
| IV. YAGNI / acordo | Dependência nova e `validate`: **acordo explícito** registrado (spec FR-014, Clarifications). Nenhuma abstração nova: sem porta, sem repositório extra, sem profile `dev` (R2). O mapeamento continua manual. | ✅ (com acordo) | ✅ |
| V. Convenções | Mensagem nova em português, `IllegalArgumentException` no construtor, `BigDecimal` intacto, nomes de tabela e coluna em português. | ✅ | ✅ |
| Stack | Flyway passa a fazer parte do stack. **Ação**: atualizar a seção "Stack Tecnológico" da constitution (PATCH, via `/speckit-constitution`) e o AGENTS.md §1/§6/§9 na mesma mudança (Governance). | ⚠️ follow-up | ⚠️ follow-up, vira task |

**Resultado**: o gate passa. O único item pendente é uma atualização documental obrigatória
(stack na constitution e no AGENTS.md), que entra como task, sem violação a justificar.

### Ordem TDD (de dentro para fora, constitution §Fluxo)

Cada passo diz qual teste fica vermelho e qual mudança de produção ele justifica. A numeração
das tasks está em [tasks.md](./tasks.md).

1. **Setup**: dependência do Flyway no `pom.xml` e imagens fixas em
   `TestcontainersConfiguration`. Não há mudança de comportamento em `src/main/`.
2. **Modo rápido** (analyze C1): `ModoRapidoTest` (sem profile → H2 e sem bean `Flyway`) fica
   **vermelho** com o Flyway no classpath. Isso justifica `spring.flyway.enabled=false` na base.
3. **Domínio**: `ImovelTest` ganha 255 e 256 caracteres e o caso de code point. O de 256 fica
   **vermelho**, e isso justifica o `if` em `Imovel`. Os outros dois são caracterização.
4. **Constraints no PG**: `ImoveisPostgresConstraintsTest` (G1–G11) fica **vermelho**, por
   falha de contexto (sem profile `prod`) ou de asserção (sem `CHECK`s/`numeric(19,4)`); os
   dois casos são aceitos (analyze I2). Isso justifica a `V1__cria_tabela_imoveis.sql` e o
   `application-prod.properties` com Flyway ligado e **`ddl-auto=none`**.
5. **Mapeamento** (analyze C2): `ImovelJpaEntityMapeamentoH2Test` fica **vermelho** em
   `numeric(19,4)`, porque o padrão implícito é `numeric(38,2)`. Isso justifica
   `precision = 19, scale = 4`. O `length = 255` é refatoração protegida pela caracterização
   do mesmo teste.
6. **Subida**: `EstruturaBancoProducaoTest` fica **vermelho** em S3 (divergência), porque com
   `none` a aplicação sobe sem a coluna. Isso justifica trocar para **`ddl-auto=validate`**.
   S1, S2 e S4 são caracterização do que os passos 4 e 5 e o Flyway já entregam.
7. **Docs**: `docs/persistencia.md`, AGENTS.md e constitution (PATCH). Não há código.

## Project Structure

### Documentation (this feature)

```text
specs/001-better-db-schema/
├── plan.md               # este arquivo
├── research.md           # Phase 0 (R1–R9)
├── data-model.md         # Phase 1
├── quickstart.md         # Phase 1
├── contracts/
│   └── imoveis-schema.md # Phase 1: garantias G1–G11 e S1–S4
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
├── ModoRapidoTest.java                                    # NOVO: sem profile → H2, sem Flyway (FR-010)
├── EstruturaBancoProducaoTest.java                        # NOVO: S1–S4 (subida real com profile prod)
├── HabitattioApplicationTests.java                        # INALTERADO
├── domain/model/ImovelTest.java                           # + testes de 255/256 (só adição)
└── adapters/outbound/persistence/
    ├── ImoveisPostgresConstraintsTest.java                # NOVO: G1–G11 via SQL direto
    └── ImovelJpaEntityMapeamentoH2Test.java               # NOVO: numeric(19,4)/varchar(255) no H2
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
| Exceções 22001/22003 não virarem `DataIntegrityViolationException` | Verificado na primeira execução; o tipo esperado é ajustado ainda na fase de escrita do teste, antes do vermelho "oficial" (tasks T007). |
| A base `H2Dialect` vazar para o profile `prod` | O `application-prod.properties` sobrescreve `spring.jpa.database-platform`. Isso é provado pelo S1. |
| A tag `postgres:18.6` ser removida do Docker Hub | Tags de patch não são removidas na prática. Se for, basta trocar a constante (um único lugar). |
| Esbarrar no defeito de reidratação | Nenhum teste lê `ativo` nem usa o retorno do adapter (R6). |

## Complexity Tracking

Sem violações a justificar. A dependência nova tem acordo explícito (FR-014). Não há
abstração, porta ou camada nova.
