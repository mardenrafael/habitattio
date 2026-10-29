---

description: "Tasks de better-db-schema: imoveis como tabela-modelo com estrutura versionada e regras no banco"
---

# Tasks: `imoveis` como tabela-modelo com estrutura versionada e regras no banco

**Input**: Design documents from `specs/001-better-db-schema/`

**Prerequisites**: [plan.md](./plan.md), [spec.md](./spec.md), [research.md](./research.md), [data-model.md](./data-model.md), [contracts/imoveis-schema.md](./contracts/imoveis-schema.md), [quickstart.md](./quickstart.md)

**Tests**: **obrigatórios**. A constitution (Princípio II) e o AGENTS.md §3 exigem TDD:
**nenhum código de produção, nem configuração em `src/main/`, sem um teste que falhe pelo
motivo certo antes**. Um teste visto falhar fica **read-only**: não pode ser editado, apagado nem
marcado `@Disabled` para o build passar. Testes existentes (`HabitattioApplicationTests`, os
atuais de `ImovelTest` etc.) **não podem ser alterados**; só se adicionam métodos novos.

**Organization**: tasks agrupadas por user story da spec (US1 = P1, US2 = P2, US3 = P3).

## Format: `[ID] [P?] [Story] Description`

- **[P]**: pode rodar em paralelo (arquivo diferente, sem dependência pendente)
- **[Story]**: a user story atendida (US1, US2, US3)
- Caminhos relativos à raiz do repositório. Pacote base: `br.com.marden.habitattio`
  (`src/main/java/br/com/marden/habitattio/` e `src/test/java/br/com/marden/habitattio/`).

## Decisões de sequenciamento (TDD)

- **Modo rápido antes da propriedade** (analyze C1): com o Flyway no classpath (T001), o modo
  padrão passa a criar um bean `Flyway` contra o H2. O teste `ModoRapidoTest` (T003) é escrito e
  visto falhar **antes** de `spring.flyway.enabled=false` (T004).
- **`none` antes de `validate`** (plan, Ordem TDD): a US1 cria o profile `prod` com
  `ddl-auto=none`. A US2 vê o S3 (divergência) falhar e só então troca para `validate`.
- **Testes de caracterização**: alguns testes fixam comportamento que já existe e passam de
  primeira. Eles **não justificam código de produção**; cada mudança em `src/main/` tem ao
  menos um teste vermelho próprio. Os casos estão listados nas tasks e **devem ser declarados no
  relatório**.

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: dependência acordada (FR-014) e versões fixas (FR-015). Nenhuma mudança de
comportamento em `src/main/`.

- [X] T001 Adicionar ao `pom.xml`, dentro de `<dependencies>` e **sem `<version>`** (gerenciadas pelo BOM do Boot 4.1.2, Flyway 12.4.0): `org.springframework.boot:spring-boot-starter-flyway` e `org.flywaydb:flyway-database-postgresql`. Rodar `./mvnw compile` e confirmar sucesso. Não adicionar nenhuma outra dependência (constitution §IV).
- [X] T002 [P] Em `src/test/java/br/com/marden/habitattio/TestcontainersConfiguration.java`: tornar a classe `public`; criar `public static final String POSTGRES_IMAGE = "postgres:18.6";` e `public static final String REDIS_IMAGE = "redis:8.10.3";`; usar as constantes em `DockerImageName.parse(...)` no lugar de `postgres:latest` e `redis:latest`. Não mudar os nomes dos métodos `@Bean` nem `@ServiceConnection` (R8). É infraestrutura de teste, não produção.

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: manter o modo rápido como padrão (FR-010, US2 #5) em TDD e fixar a linha de base.
Bloqueia todas as histórias: sem T004, a migração da US1 quebraria o H2 do modo padrão.

- [X] T003 Criar `src/test/java/br/com/marden/habitattio/ModoRapidoTest.java` com `@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)`, **sem** `@Import(TestcontainersConfiguration.class)` e **sem** `@ActiveProfiles`. Um método `deveSubirNoModoRapidoQuandoNenhumProfileEhEscolhido` (US2 #5), Arrange/Act/Assert: a URL de `DataSource#getConnection().getMetaData().getURL()` começa com `jdbc:h2:` **e** `applicationContext.getBeanProvider(Flyway.class).getIfAvailable()` é `null`. Rodar `./mvnw test -Dtest=ModoRapidoTest`. Vermelho esperado: **o bean `Flyway` existe** (autoconfigurado depois de T001). Se falhar por outro motivo (compilação, contexto), o teste está mal escrito; corrigir antes de considerá-lo visto falhar.
- [X] T004 Em `src/main/resources/application.properties`, adicionar **apenas** a linha `spring.flyway.enabled=false`, com o comentário `# Modo rápido (padrão): estrutura gerada pelo Hibernate; migrações só no profile prod`. **Não** alterar URL do H2, driver, `ddl-auto=update` nem `H2Dialect` (AGENTS.md §9). Rodar `./mvnw test -Dtest=ModoRapidoTest` até ficar verde.
- [X] T005 Rodar `./mvnw test` e registrar o resultado: suíte verde, com `HabitattioApplicationTests` **inalterado** (`git diff --stat main -- src/test/java/br/com/marden/habitattio/HabitattioApplicationTests.java` vazio). Registrar se o Docker está disponível. Sem Docker, rodar só `./mvnw test -Dtest='ModoRapidoTest,ImovelTest,ContratoTest,FaturaTest,InquilinoTest,UsuarioTest,CadastrarImovelUseCaseTest'` e anotar que os testes de integração não rodaram. Nesse caso US1 e US2 ficam bloqueadas e o relatório deve dizer isso.

**Checkpoint**: dependências e versões fixadas; o modo rápido continua o padrão, provado por teste.

---

## Phase 3: User Story 1 - O banco de produção rejeita imóvel inválido (Priority: P1) 🎯 MVP

**Goal**: o PostgreSQL de produção impõe as 6 garantias de `imoveis` por conta própria, o
domínio ganha o limite de 255 caracteres e o mapeamento declara a precisão explícita (FR-001 a
FR-006, FR-002a, FR-011).

**Independent Test**: `./mvnw test -Dtest='ImovelTest,ImoveisPostgresConstraintsTest,ImovelJpaEntityMapeamentoH2Test'`.
Os testes de domínio, os casos G1–G11 de [contracts/imoveis-schema.md](./contracts/imoveis-schema.md)
contra `postgres:18.6` e o mapeamento no H2 ficam verdes.

### Tests for User Story 1 (escrever primeiro, ver falhar) ⚠️

- [ ] T006 [P] [US1] Adicionar a `src/test/java/br/com/marden/habitattio/domain/model/ImovelTest.java` três métodos novos (JUnit puro, Arrange/Act/Assert separados por linha em branco), **sem tocar nos existentes**:
  - `deveRejeitarEnderecoComMaisDe255Caracteres`: endereço `"a".repeat(256)` lança `IllegalArgumentException` com mensagem exata `"O endereço do imóvel deve ter no máximo 255 caracteres."`.
  - `deveAceitarEnderecoCom255Caracteres`: `"a".repeat(255)` cria o imóvel e `getEndereco()` devolve o mesmo texto.
  - `deveContarCaracteresENaoUnidadesUtf16NoLimiteDoEndereco`: `"a".repeat(254) + "🏠"` (255 code points, `length() == 256`) é **aceito**.

  Rodar `./mvnw test -Dtest=ImovelTest`. Vermelho esperado: `deveRejeitarEnderecoComMaisDe255Caracteres` (ainda não há limite). Os outros dois são **caracterização** e passam de primeira: fixam a fronteira e a contagem por code point contra uma implementação ingênua com `length()`.
- [ ] T007 [P] [US1] Criar `src/test/java/br/com/marden/habitattio/adapters/outbound/persistence/ImoveisPostgresConstraintsTest.java` com `@DataJpaTest`, `@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)`, `@ActiveProfiles("prod")` e `@Import(TestcontainersConfiguration.class)`. Receber `JdbcClient` pelo construtor do teste (`@Autowired` no construtor é permitido em teste). Se o `@DataJpaTest` do Boot 4.1.2 não autoconfigurar `JdbcClient`, receber o `DataSource` e criar com `JdbcClient.create(dataSource)`, ainda na fase de escrita, antes de o teste contar como visto falhar. Toda escrita é **SQL direto**, por exemplo `INSERT INTO imoveis (endereco, valor_sugerido, ativo) VALUES (:e, :v, true)`, sem `Imovel` nem `ImovelJpaEntity`. Para os casos nulos, escrever `NULL` literal no SQL. Toda leitura de volta também é por SQL e **nunca lê `ativo`** (FR-011, defeito de reidratação). **Ao menos** um teste por garantia do contrato:
  - G1 `deveRejeitarEnderecoNulo`: `assertThrows(DataIntegrityViolationException.class, ...)`.
  - G2 `deveRejeitarEnderecoVazio`, `deveRejeitarEnderecoSoComEspacos` (`"   "`), `deveRejeitarEnderecoSoComTabEQuebraDeLinha` (`"\t\n"`) e `deveRejeitarEnderecoSoComRetornoDeCarroFormFeedETabVertical` (`"\r\f\u000B"`): `DataIntegrityViolationException` cuja mensagem contém `ck_imoveis_endereco_nao_vazio`.
  - G3 `deveRejeitarEnderecoCom256Caracteres`: `DataIntegrityViolationException`.
  - G4 `deveAceitarEnderecoCom255Caracteres` (`"a".repeat(255)`): `COUNT(*)` passa a 1.
  - G4b `deveAceitarEnderecoCom255CaracteresMultibyte` (`"ã".repeat(254) + "🏠"`, 255 caracteres e mais de 255 bytes): aceito, provando que o banco conta caracteres e não bytes (spec, Edge Cases).
  - G5 `deveRejeitarValorSugeridoNulo`: `DataIntegrityViolationException`.
  - G6 `deveRejeitarValorSugeridoZero` e `deveRejeitarValorSugeridoNegativo`: a mensagem contém `ck_imoveis_valor_sugerido_positivo`.
  - G7 `deveRejeitarValorSugeridoQueArredondaParaZero` (`0.00001`): a mensagem contém `ck_imoveis_valor_sugerido_positivo`.
  - G8 `deveArredondarValorSugeridoParaQuatroCasas`: grava `1500.12345`, lê `valor_sugerido` como `BigDecimal`, e `compareTo(new BigDecimal("1500.1235")) == 0` e `scale() == 4`.
  - G9 `deveGuardarValorSugeridoComQuatroCasasExatamente`: `1500.1234` volta igual (`compareTo == 0`).
  - G10 `deveAceitarDoisImoveisComMesmoEndereco`: dois inserts de `"Rua A, 100, apto 101"`; `COUNT(*) WHERE endereco = ...` é 2.
  - G11 `deveRejeitarValorSugeridoAcimaDaCapacidade` (`1000000000000000`, 16 dígitos inteiros): `DataIntegrityViolationException`, sem truncamento.

  **Tradução de exceções (U1)**: G3 (SQLState 22001) e G11 (22003) dependem do fallback de SQLState do Spring para virar `DataIntegrityViolationException`. Ao rodar pela primeira vez, se a exceção traduzida for outra subclasse de `DataAccessException`, ajustar o tipo esperado **nessa mesma fase de escrita**, antes de o teste contar como "visto falhar pelo motivo certo", e registrar no relatório.

  Rodar `./mvnw test -Dtest=ImoveisPostgresConstraintsTest`. Vermelho esperado (analyze I2): **qualquer um destes conta como vermelho pelo motivo certo**, que é a ausência da estrutura de produção:
  - (a) **falha de contexto ou de DDL**, porque o profile `prod` ainda não existe e a base herda o `H2Dialect` contra o PostgreSQL;
  - (b) falhas de asserção em G2, G6, G7, G8, G9 e G11, se o contexto subir com a tabela gerada pelo Hibernate (sem `CHECK`s, `numeric(38,2)`).

  No caso (b), G1, G3, G4, G4b, G5 e G10 podem passar de primeira (G4b porque o `varchar(255)` gerado pelo Hibernate no PostgreSQL também conta caracteres), como **caracterização**. Só erro de **compilação** ou de lógica do próprio teste conta como teste mal escrito.
- [ ] T008 [P] [US1] Criar `src/test/java/br/com/marden/habitattio/adapters/outbound/persistence/ImovelJpaEntityMapeamentoH2Test.java` (analyze C2) com `@DataJpaTest` no modo padrão (H2 embutido, estrutura gerada pelo Hibernate a partir de `ImovelJpaEntity`; **sem** Testcontainers e **sem** profile). Consultas via `JdbcClient` (mesmo fallback de T007: `JdbcClient.create(dataSource)` se não houver bean) em `INFORMATION_SCHEMA.COLUMNS` (`TABLE_NAME = 'IMOVEIS'`, nomes em maiúsculas no H2):
  - `deveMapearValorSugeridoComPrecisao19EEscala4`: para a coluna `VALOR_SUGERIDO`, `NUMERIC_PRECISION = 19` e `NUMERIC_SCALE = 4`.
  - `deveMapearEnderecoComTamanho255`: para `ENDERECO`, `CHARACTER_MAXIMUM_LENGTH = 255`.

  Rodar `./mvnw test -Dtest=ImovelJpaEntityMapeamentoH2Test`. Vermelho esperado: `deveMapearValorSugeridoComPrecisao19EEscala4`, porque hoje o Hibernate gera `numeric(38,2)`. `deveMapearEnderecoComTamanho255` passa de primeira (255 já é o padrão implícito) e é **caracterização**: ele protege a refatoração de T012, que torna o tamanho explícito sem mudar comportamento.

### Implementation for User Story 1

- [ ] T009 [US1] Em `src/main/java/br/com/marden/habitattio/domain/model/Imovel.java`, dentro de `validar(...)` e **depois** da checagem de vazio (que fica igual), adicionar: se `endereco.codePointCount(0, endereco.length()) > 255`, lançar `new IllegalArgumentException("O endereço do imóvel deve ter no máximo 255 caracteres.")`. Sem imports novos, sem framework (constitution §I). Rodar `./mvnw test -Dtest=ImovelTest` até ficar verde.
- [ ] T010 [P] [US1] Criar `src/main/resources/db/migration/V1__cria_tabela_imoveis.sql` (data-model, "Tabela imoveis"):
  ```sql
  CREATE TABLE imoveis (
      id             bigint GENERATED BY DEFAULT AS IDENTITY,
      endereco       varchar(255)  NOT NULL,
      valor_sugerido numeric(19,4) NOT NULL,
      ativo          boolean       NOT NULL,
      CONSTRAINT pk_imoveis PRIMARY KEY (id),
      CONSTRAINT ck_imoveis_endereco_nao_vazio CHECK (endereco !~ '^\s*$'),
      CONSTRAINT ck_imoveis_valor_sugerido_positivo CHECK (valor_sugerido > 0)
  );
  ```
  Sem `UNIQUE` em `endereco` (FR-005) e sem `DEFAULT` em `ativo`.
- [ ] T011 [P] [US1] Criar `src/main/resources/application-prod.properties` (R2) com exatamente: `spring.flyway.enabled=true`; `spring.jpa.hibernate.ddl-auto=none` (passa a `validate` em T015, na US2); `spring.jpa.database-platform=org.hibernate.dialect.PostgreSQLDialect`; `spring.datasource.driver-class-name=org.postgresql.Driver`; e um comentário dizendo que URL, usuário e senha vêm de `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME` e `SPRING_DATASOURCE_PASSWORD`. **Nenhuma credencial ou URL em arquivo.**
- [ ] T012 [P] [US1] Em `src/main/java/br/com/marden/habitattio/adapters/outbound/persistence/entity/ImovelJpaEntity.java`: `valorSugerido` → `@Column(nullable = false, precision = 19, scale = 4)` (justificado pelo vermelho de T008); `endereco` → `@Column(nullable = false, length = 255)` (refatoração: torna explícito o padrão atual, protegida pela caracterização de T008). Sem `name=` explícito (convenção: estratégia padrão, `valorSugerido` → `valor_sugerido`). O adapter `ImovelRepositoryAdapter` não muda.
- [ ] T013 [US1] Rodar `./mvnw test -Dtest='ImovelTest,ImoveisPostgresConstraintsTest,ImovelJpaEntityMapeamentoH2Test'` até ficar verde, **sem editar nenhum teste visto falhar**. Depois rodar `./mvnw test` completo: `HabitattioApplicationTests` e `ModoRapidoTest` continuam verdes e inalterados.

**Checkpoint**: SC-001 (6 de 6 garantias no banco) e SC-002 (≥ 5 testes de rejeição) atendidos. A US1 é o MVP.

---

## Phase 4: User Story 2 - Estrutura versionada e conferida na subida (Priority: P2)

**Goal**: o banco de produção só é criado por migrações. A aplicação no profile `prod` só
confere a estrutura e se recusa a subir quando há divergência ou script adulterado (FR-007 a
FR-009). O modo rápido como padrão (FR-010, US2 #5) já foi entregue em T003/T004.

**Independent Test**: `./mvnw test -Dtest='EstruturaBancoProducaoTest,ModoRapidoTest'`. Os
cenários S1–S4 de [contracts/imoveis-schema.md](./contracts/imoveis-schema.md) e o modo rápido
ficam verdes.

### Tests for User Story 2 (escrever primeiro, ver falhar) ⚠️

- [ ] T014 [US2] Criar `src/test/java/br/com/marden/habitattio/EstruturaBancoProducaoTest.java` (R7). Cada cenário sobe a aplicação com `new SpringApplicationBuilder(HabitattioApplication.class).profiles("prod")`, com as propriedades `spring.main.web-application-type=none`, `spring.datasource.url/username/password` do container e `spring.data.redis.host=localhost`, e fecha o contexto em `try-with-resources`. Cada cenário usa um `PostgreSQLContainer` **próprio** (imagem `TestcontainersConfiguration.POSTGRES_IMAGE`), iniciado e parado no teste. As verificações usam JDBC puro (`DriverManager`) contra o container. Métodos:
  - `deveCriarEstruturaDeImoveisEmBancoVazio` (S1, SC-006, US2 #1 e #6): após a subida, `imoveis` existe, `pg_constraint` contém `ck_imoveis_endereco_nao_vazio` e `ck_imoveis_valor_sugerido_positivo`, e `flyway_schema_history` tem a versão `1` com `success = true`.
  - `naoDeveReaplicarMigracoesJaAplicadas` (S2, US2 #2): sobe e fecha duas vezes no mesmo container; `SELECT COUNT(*) FROM flyway_schema_history WHERE version = '1'` é 1, e a segunda subida não lança exceção.
  - `deveRecusarSubidaQuandoEstruturaDiverge` (S3, SC-004, US2 #3): sobe uma vez para migrar, fecha, executa `ALTER TABLE imoveis DROP COLUMN endereco`. A nova subida lança exceção **cuja cadeia de causas contém uma mensagem que menciona `endereco`** (a divergência é informada, analyze A2). Depois disso, a coluna `endereco` **continua ausente** em `information_schema.columns`, ou seja, o banco não foi alterado.
  - `deveRecusarSubidaQuandoMigracaoAplicadaFoiModificada` (S4, FR-009, US2 #4): sobe uma vez, fecha, executa `UPDATE flyway_schema_history SET checksum = checksum + 1 WHERE version = '1'`. A nova subida lança exceção cuja cadeia de causas contém uma mensagem com `checksum` (case-insensitive).

  Rodar `./mvnw test -Dtest=EstruturaBancoProducaoTest`. Vermelho esperado: **S3**, porque com `ddl-auto=none` a aplicação sobe mesmo sem a coluna. É ele que justifica T015. S1, S2 e S4 são **caracterização** do que a US1 e o Flyway já entregam e passam de primeira.

### Implementation for User Story 2

- [ ] T015 [US2] Em `src/main/resources/application-prod.properties`, trocar `spring.jpa.hibernate.ddl-auto=none` por `spring.jpa.hibernate.ddl-auto=validate` (acordo FR-014). Rodar `./mvnw test -Dtest='EstruturaBancoProducaoTest,ImoveisPostgresConstraintsTest'` até ficar verde, sem editar testes. Se o `validate` acusar diferença de tipo em S1, o ajuste é no mapeamento (T012) ou na migração (T010), **nunca** no teste.
- [ ] T016 [US2] Em `src/test/java/br/com/marden/habitattio/TestHabitattioApplication.java` (executável de desenvolvimento, não é teste), ativar o profile `prod`, para que subir "com os containers" seja subir como produção (Flyway + `validate`). Usar `.withAdditionalProfiles("prod")` em `SpringApplication.from(...).with(...)` se a API existir no Boot 4.1.2; senão, incluir `--spring.profiles.active=prod` nos `args` repassados. Verificar manualmente com `./mvnw spring-boot:test-run`: o log mostra o Flyway aplicando `V1__cria_tabela_imoveis` e nenhum DDL do Hibernate. Atualizar a nota de `quickstart.md` §6 com a forma escolhida.

**Checkpoint**: SC-003 (100% da estrutura por migração), SC-004 (divergência impede a subida) e SC-006 (banco vazio estruturado só pela subida) atendidos.

---

## Phase 5: User Story 3 - O padrão fica documentado para os próximos agregados (Priority: P3)

**Goal**: quem for persistir `Inquilino`, `Contrato`, `Fatura` ou `Usuario` encontra todas as
decisões prontas (FR-012, FR-013).

**Independent Test**: lendo só `docs/persistencia.md`, um leitor responde aos itens (a)–(f) de
FR-012 para um agregado novo, e o documento não contradiz `V1__cria_tabela_imoveis.sql` nem
`ImovelJpaEntity` (US3 #1 e #2).

- [ ] T017 [P] [US3] Criar `docs/persistencia.md` (em português) com uma seção por item de FR-012, usando `imoveis` como exemplo concreto e citando os arquivos reais:
  - (a) **Nomes**: tabela em português, plural, snake_case; colunas snake_case derivadas do campo Java pela estratégia padrão (sem `@Column(name=...)`); constraints `pk_<tabela>`, `fk_<tabela>_<ref>`, `uq_<tabela>_<colunas>`, `ck_<tabela>_<coluna>_<regra>`.
  - (b) **Dinheiro**: `BigDecimal` ↔ `numeric(19,4)`, `@Column(precision = 19, scale = 4)`; o banco arredonda acima de 4 casas e rejeita mais de 15 dígitos inteiros; o domínio não arredonda (divergência aceita, ver spec).
  - (c) **Chave**: `bigint GENERATED BY DEFAULT AS IDENTITY` + `GenerationType.IDENTITY`, com o motivo do `BY DEFAULT`.
  - (d) **Onde mora cada regra**: invariante simples sobre um membro da própria entidade (o `if` no construtor) vai **também** ao banco como `NOT NULL`/`CHECK`/tamanho; regra que depende de outros objetos ou de estado externo fica só na aplicação; o domínio sempre valida primeiro e pode ser mais estrito que o banco (ex.: `isBlank` × `\s`); quando o tamanho de texto existe no banco, o domínio também o valida, contando code points.
  - (e) **Evolução**: migração nova `V<n>__<descricao_em_portugues>.sql` em `src/main/resources/db/migration/`; nunca editar uma aplicada (checksum); mapeamento JPA e migração mudam no mesmo commit; o `validate` não confere `CHECK`/tamanho/precisão, por isso os testes de constraints e de mapeamento são obrigatórios; o H2 do modo rápido não segue as migrações.
  - (f) **Testes**: o molde de `ImoveisPostgresConstraintsTest` (anotações, SQL direto, ao menos um teste por garantia, assert pelo nome da constraint, não ler estado reidratado), de `ImovelJpaEntityMapeamentoH2Test` e de `EstruturaBancoProducaoTest`.

  Incluir uma seção "Modos de execução" (padrão rápido × `prod`, variáveis `SPRING_DATASOURCE_*`, versões fixas `postgres:18.6`/`redis:8.10.3` em `TestcontainersConfiguration`) e uma seção "Fora deste padrão (ainda)" (criptografia do CPF quando `Inquilino` for persistido; o padrão não a impede).
- [ ] T018 [US3] Atualizar `AGENTS.md` (FR-013), mudando só o necessário:
  - §1: como subir em modo produção (`SPRING_PROFILES_ACTIVE=prod` + `SPRING_DATASOURCE_*`) e que `TestHabitattioApplication` sobe com o profile `prod`.
  - §6: um parágrafo apontando para `docs/persistencia.md` como padrão obrigatório de persistência dos próximos agregados.
  - §9: o stack acordado passa a incluir o Flyway, e a regra "não mude `ddl-auto`" passa a dizer que o modo rápido usa `update` e o `prod` usa `validate`, ambos acordados.

  Não mexer nas outras seções.
- [ ] T019 [US3] Rodar `/speckit-constitution` para uma emenda **PATCH** (1.0.0 → 1.0.1): incluir o Flyway (migrações versionadas, só PostgreSQL) na seção "Stack Tecnológico e Restrições" de `.specify/memory/constitution.md`, com o Sync Impact Report citando esta feature. Nenhum princípio muda (Governance: constitution e AGENTS.md consistentes na mesma mudança).

**Checkpoint**: SC-008 fica preparado para ser verificado na próxima feature de persistência.

---

## Phase 6: Polish & Cross-Cutting Concerns

- [ ] T020 Rodar `./mvnw test` completo e registrar no relatório: total de testes, verdes e falhas; se os de integração rodaram (Docker); e a lista de testes de caracterização que passaram de primeira (T006, T007, T008, T014). Verificar:
  - `git diff main -- src/test/java/br/com/marden/habitattio/HabitattioApplicationTests.java` vazio, e `git diff main -- src/test/java/br/com/marden/habitattio/domain/model/ImovelTest.java` só com adições.
  - `grep -rn "org.springframework\|jakarta.persistence" src/main/java/br/com/marden/habitattio/domain src/main/java/br/com/marden/habitattio/application` sem resultados.
  - Nenhum `*JpaEntity` fora de `adapters/outbound/persistence` (`grep -rln JpaEntity src/main/java | grep -v adapters/outbound/persistence` vazio).
- [ ] T021 Executar os cenários manuais 5 e 6 de [quickstart.md](./quickstart.md): no modo rápido, `./mvnw spring-boot:run` + `curl POST /imoveis` → `201`; no modo produção, o Flyway aparece no log. Registrar o resultado, ou registrar que não foram executados.
- [ ] T022 Conferir a rastreabilidade: cada FR-001 a FR-015 e SC-001 a SC-007 da [spec.md](./spec.md) tem pelo menos uma task concluída que o atende. Se o escopo mudou durante a implementação, atualizar spec e plan (constitution §III).

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: sem dependências. T002 em paralelo com T001.
- **Foundational (Phase 2)**: T003 depende de T001 (o vermelho exige o Flyway no classpath). T004 depende de ver T003 falhar. T005 depende de T001–T004. Bloqueia todas as histórias.
- **US1 (Phase 3)**: depende de T005.
- **US2 (Phase 4)**: depende da US1 (T010, T011 e T012 entregam a migração, o profile `prod` e o mapeamento que S1–S4 sobem). Testável sozinha, mas não implementável antes da US1.
- **US3 (Phase 5)**: T017 pode começar após a US1. T018 e T019 só depois da US2, porque descrevem `validate` e o `TestHabitattioApplication` em `prod`.
- **Polish (Phase 6)**: depois de todas as histórias.

### Within Each User Story

- Teste escrito → rodado → visto falhar pelo motivo certo → implementação → verde. Nunca o inverso.
- US1: T006, T007 e T008 (testes) antes de T009–T012 (implementação); T013 fecha.
- US2: T014 antes de T015; T016 depois de T015.

### Parallel Opportunities

- Setup: T001 ∥ T002.
- US1: T006 ∥ T007 ∥ T008 (três arquivos de teste). Depois de ver os três falharem: T009 ∥ T010 ∥ T011 ∥ T012 (quatro arquivos de produção diferentes).
- US3: T017 em paralelo com a US2 inteira.

---

## Parallel Example: User Story 1

```bash
# Testes primeiro, em paralelo (arquivos diferentes):
Task: "T006 testes de 255/256 caracteres em src/test/java/.../domain/model/ImovelTest.java"
Task: "T007 ImoveisPostgresConstraintsTest (G1–G11) em src/test/java/.../adapters/outbound/persistence/"
Task: "T008 ImovelJpaEntityMapeamentoH2Test em src/test/java/.../adapters/outbound/persistence/"

# Depois de ver os três falharem, implementações em paralelo:
Task: "T009 limite de 255 code points em src/main/java/.../domain/model/Imovel.java"
Task: "T010 V1__cria_tabela_imoveis.sql em src/main/resources/db/migration/"
Task: "T011 application-prod.properties (flyway on, ddl-auto=none)"
Task: "T012 precision/scale/length em ImovelJpaEntity.java"
```

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Phase 1 (T001–T002) e Phase 2 (T003–T005): dependência, versões fixas, modo rápido provado e linha de base verde.
2. Phase 3 (T006–T013): o banco de produção passa a impor as 6 garantias.
3. **Parar e validar**: `./mvnw test -Dtest='ImovelTest,ImoveisPostgresConstraintsTest,ImovelJpaEntityMapeamentoH2Test'`. Com isso M1, M2 e M5 já estão no alvo.

### Incremental Delivery

1. Setup + Foundational → base pronta.
2. US1 → regras no banco provadas (MVP).
3. US2 → estrutura conferida na subida (M3 e M4 no alvo).
4. US3 → padrão documentado, AGENTS.md e constitution consistentes (M6 preparado).
5. Polish → suíte completa e rastreabilidade.

---

## Notes

- **Read-only após falhar**: um teste visto falhar não é editado para passar. Se a
  implementação parece exigir mudar o teste, o problema é a implementação. Se um teste
  **existente** quebrar, pare e reporte (AGENTS.md §3), sem edição silenciosa.
- **Caracterização** (passam de primeira, devem ser declarados): T006 (255 e code point), T007
  (G1, G3, G4, G4b, G5, G10, se o contexto subir), T008 (tamanho 255), T014 (S1, S2, S4).
- Commitar ao fim de cada task ou grupo lógico, com mensagens em português.
- Fora de escopo, para abrir à parte depois: defeito de reidratação (`/speckit-bug-assess`) e
  `IllegalArgumentException` do domínio virando HTTP 500 no `ImovelController` (research,
  "Pontos observados fora de escopo").
