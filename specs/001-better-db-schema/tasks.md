---

description: "Tasks de better-db-schema: imoveis como tabela-modelo com estrutura versionada e regras no banco"
---

# Tasks: `imoveis` como tabela-modelo com estrutura versionada e regras no banco

**Input**: Design documents from `specs/001-better-db-schema/`

**Prerequisites**: [plan.md](./plan.md), [spec.md](./spec.md), [research.md](./research.md), [data-model.md](./data-model.md), [contracts/imoveis-schema.md](./contracts/imoveis-schema.md), [quickstart.md](./quickstart.md)

**Tests**: **obrigatórios**. A constitution (Princípio II) e o AGENTS.md §3 exigem TDD: todo
teste é escrito antes da implementação e visto falhar pelo motivo certo. **Um teste visto
falhar fica read-only**: não pode ser editado, apagado nem marcado `@Disabled` para o build
passar. Testes existentes (`HabitattioApplicationTests`, os testes atuais de `ImovelTest` etc.)
**não podem ser alterados**; só se adicionam métodos novos.

**Organization**: tasks agrupadas por user story da spec (US1 = P1, US2 = P2, US3 = P3).

## Format: `[ID] [P?] [Story] Description`

- **[P]**: pode rodar em paralelo (arquivo diferente, sem dependência pendente)
- **[Story]**: a user story atendida (US1, US2, US3)
- Caminhos relativos à raiz do repositório. Pacote base: `br.com.marden.habitattio`
  (`src/main/java/br/com/marden/habitattio/` e `src/test/java/br/com/marden/habitattio/`).

## Decisão de sequenciamento (TDD entre histórias)

O modo "só conferir" (`ddl-auto=validate`) é a implementação da **US2**. Se ele entrasse já
na US1, os testes da US2 passariam de primeira e não provariam nada. Por isso:

- **US1** cria o profile `prod` com Flyway ligado e `spring.jpa.hibernate.ddl-auto=none`. A
  estrutura vem só das migrações e ainda não há conferência.
- **US2** escreve os testes de subida, vê o S3 (divergência) falhar com `none` e só então
  troca para `validate`.

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: dependência acordada (FR-014), versões fixas (FR-015) e modo rápido preservado
(FR-010), sem mudar comportamento.

- [ ] T001 Adicionar ao `pom.xml`, dentro de `<dependencies>` e **sem `<version>`** (gerenciadas pelo BOM do Boot 4.1.2, Flyway 12.4.0): `org.springframework.boot:spring-boot-starter-flyway` e `org.flywaydb:flyway-database-postgresql`. Rodar `./mvnw compile` e confirmar sucesso. Não adicionar nenhuma outra dependência (constitution §IV).
- [ ] T002 Em `src/main/resources/application.properties`, adicionar **apenas** a linha `spring.flyway.enabled=false`, com o comentário `# Modo rápido (padrão): estrutura gerada pelo Hibernate; migrações só no profile prod`. **Não** alterar URL do H2, driver, `ddl-auto=update` nem `H2Dialect` (AGENTS.md §9).
- [ ] T003 [P] Em `src/test/java/br/com/marden/habitattio/TestcontainersConfiguration.java`: tornar a classe `public`; criar `public static final String POSTGRES_IMAGE = "postgres:18.6";` e `public static final String REDIS_IMAGE = "redis:8.10.3";`; usar as constantes em `DockerImageName.parse(...)` no lugar de `postgres:latest` e `redis:latest`. Não mudar os nomes dos métodos `@Bean` nem `@ServiceConnection` (R8).

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: linha de base verde antes de qualquer teste novo.

- [ ] T004 Rodar `./mvnw test` e registrar o resultado: a suíte deve continuar verde depois de T001–T003, com `HabitattioApplicationTests` **inalterado** (`git diff --stat src/test/java/br/com/marden/habitattio/HabitattioApplicationTests.java` vazio). Registrar se o Docker está disponível. Sem Docker, rodar só `./mvnw test -Dtest='ImovelTest,ContratoTest,FaturaTest,InquilinoTest,UsuarioTest,CadastrarImovelUseCaseTest'` e anotar que os testes de integração não rodaram. Nesse caso US1 e US2 ficam bloqueadas e o relatório deve dizer isso.

**Checkpoint**: dependências e versões fixadas, modo rápido idêntico ao de antes.

---

## Phase 3: User Story 1 - O banco de produção rejeita imóvel inválido (Priority: P1) 🎯 MVP

**Goal**: o PostgreSQL de produção impõe as 6 garantias de `imoveis` por conta própria, e o
domínio ganha o limite de 255 caracteres (FR-001 a FR-006, FR-002a, FR-011).

**Independent Test**: `./mvnw test -Dtest='ImovelTest,ImoveisPostgresConstraintsTest'`. Os
testes de domínio e os 10 casos G1–G10 de [contracts/imoveis-schema.md](./contracts/imoveis-schema.md)
ficam verdes contra `postgres:18.6`.

### Tests for User Story 1 (escrever primeiro, ver falhar) ⚠️

- [ ] T005 [P] [US1] Adicionar a `src/test/java/br/com/marden/habitattio/domain/model/ImovelTest.java` três métodos novos (JUnit puro, Arrange/Act/Assert separados por linha em branco), **sem tocar nos existentes**:
  - `deveRejeitarEnderecoComMaisDe255Caracteres`: endereço `"a".repeat(256)` lança `IllegalArgumentException` com mensagem exata `"O endereço do imóvel deve ter no máximo 255 caracteres."`.
  - `deveAceitarEnderecoCom255Caracteres`: `"a".repeat(255)` cria o imóvel e `getEndereco()` devolve o mesmo texto.
  - `deveContarCaracteresENaoUnidadesUtf16NoLimiteDoEndereco`: um endereço de 255 code points em que um deles é `"🏠"` (portanto `length() == 256`) é **aceito**.

  Rodar `./mvnw test -Dtest=ImovelTest`. Esperado: **só** `deveRejeitarEnderecoComMaisDe255Caracteres` falha (ainda não há limite). Os outros dois passam de primeira, porque fixam a fronteira e a contagem por code point contra uma implementação ingênua com `length()`. Registrar isso no relatório.
- [ ] T006 [P] [US1] Criar `src/test/java/br/com/marden/habitattio/adapters/outbound/persistence/ImoveisPostgresConstraintsTest.java` com `@DataJpaTest`, `@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)`, `@ActiveProfiles("prod")` e `@Import(TestcontainersConfiguration.class)`. Injetar `JdbcClient` por construtor do teste (`@Autowired` no construtor é permitido em teste) ou por parâmetro. Toda escrita é **SQL direto** (`INSERT INTO imoveis (endereco, valor_sugerido, ativo) VALUES (:e, :v, true)`), sem `Imovel` nem `ImovelJpaEntity`. Toda leitura de volta também é por SQL e **nunca lê `ativo`** (FR-011, defeito de reidratação). Um teste por garantia do contrato:
  - G1 `deveRejeitarEnderecoNulo`: `assertThrows(DataIntegrityViolationException.class, ...)`.
  - G2 `deveRejeitarEnderecoVazio`, `deveRejeitarEnderecoSoComEspacos` (`"   "`) e `deveRejeitarEnderecoSoComTabEQuebraDeLinha` (`"\t\n"`): `DataIntegrityViolationException` cuja mensagem contém `ck_imoveis_endereco_nao_vazio`.
  - G3 `deveRejeitarEnderecoCom256Caracteres`: `DataIntegrityViolationException`.
  - G4 `deveAceitarEnderecoCom255Caracteres`: o `COUNT(*)` passa a 1.
  - G5 `deveRejeitarValorSugeridoNulo`: `DataIntegrityViolationException`.
  - G6 `deveRejeitarValorSugeridoZero` e `deveRejeitarValorSugeridoNegativo`: a mensagem contém `ck_imoveis_valor_sugerido_positivo`.
  - G7 `deveRejeitarValorSugeridoQueArredondaParaZero` (`0.00001`): a mensagem contém `ck_imoveis_valor_sugerido_positivo`.
  - G8 `deveArredondarValorSugeridoParaQuatroCasas`: grava `1500.12345` e lê `valor_sugerido` como `BigDecimal`, com `compareTo(new BigDecimal("1500.1235")) == 0` e `scale() == 4`.
  - G9 `deveGuardarValorSugeridoComQuatroCasasExatamente`: `1500.1234` volta igual (`compareTo == 0`).
  - G10 `deveAceitarDoisImoveisComMesmoEndereco`: dois inserts de `"Rua A, 100, apto 101"`, e `COUNT(*) WHERE endereco = ...` é 2.

  Rodar `./mvnw test -Dtest=ImoveisPostgresConstraintsTest`. Esperado: **falhar pelo motivo certo**. Sem `application-prod.properties` e sem migração, a tabela é a gerada pelo Hibernate, sem `CHECK`s e com `numeric(38,2)`. Devem falhar pelo menos G2, G6, G7, G8 e G9; G1, G3, G5 e G10 podem passar de primeira, e o relatório deve dizer isso. Se falhar por erro de compilação ou de contexto, o teste está mal escrito: corrigir **antes** de considerá-lo visto falhar.

### Implementation for User Story 1

- [ ] T007 [US1] Em `src/main/java/br/com/marden/habitattio/domain/model/Imovel.java`, dentro de `validar(...)` e **depois** da checagem de vazio (que permanece igual), adicionar: se `endereco.codePointCount(0, endereco.length()) > 255`, lançar `new IllegalArgumentException("O endereço do imóvel deve ter no máximo 255 caracteres.")`. Sem imports novos, sem framework (constitution §I). Rodar `./mvnw test -Dtest=ImovelTest` até ficar verde.
- [ ] T008 [P] [US1] Criar `src/main/resources/db/migration/V1__cria_tabela_imoveis.sql` (data-model, "Tabela imoveis"):
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
- [ ] T009 [P] [US1] Criar `src/main/resources/application-prod.properties` (R2) com exatamente:
  `spring.flyway.enabled=true`, `spring.jpa.hibernate.ddl-auto=none` (passa a `validate` em T013, na US2), `spring.jpa.database-platform=org.hibernate.dialect.PostgreSQLDialect`, `spring.datasource.driver-class-name=org.postgresql.Driver`, e um comentário dizendo que URL, usuário e senha vêm de `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME` e `SPRING_DATASOURCE_PASSWORD`. **Nenhuma credencial ou URL em arquivo.**
- [ ] T010 [P] [US1] Em `src/main/java/br/com/marden/habitattio/adapters/outbound/persistence/entity/ImovelJpaEntity.java`: `endereco` → `@Column(nullable = false, length = 255)`; `valorSugerido` → `@Column(nullable = false, precision = 19, scale = 4)`. Sem `name=` explícito (a convenção é a estratégia padrão: `valorSugerido` → `valor_sugerido`). O adapter `ImovelRepositoryAdapter` não muda.
- [ ] T011 [US1] Rodar `./mvnw test -Dtest='ImovelTest,ImoveisPostgresConstraintsTest'` até ficar verde, **sem editar nenhum teste visto falhar**. Depois rodar `./mvnw test` completo: `HabitattioApplicationTests` (modo padrão, Flyway desligado) continua verde e inalterado.

**Checkpoint**: SC-001 (6 de 6 garantias no banco) e SC-002 (≥ 5 testes de rejeição) atendidos. A US1 é o MVP.

---

## Phase 4: User Story 2 - Estrutura versionada e conferida na subida (Priority: P2)

**Goal**: o banco de produção só é criado por migrações. A aplicação no profile `prod` só
confere a estrutura e se recusa a subir quando há divergência ou script adulterado. O modo
rápido continua sendo o padrão (FR-007 a FR-010).

**Independent Test**: `./mvnw test -Dtest=EstruturaBancoProducaoTest`. Os cenários S1–S4 de
[contracts/imoveis-schema.md](./contracts/imoveis-schema.md) e o modo rápido padrão ficam verdes.

### Tests for User Story 2 (escrever primeiro, ver falhar) ⚠️

- [ ] T012 [US2] Criar `src/test/java/br/com/marden/habitattio/EstruturaBancoProducaoTest.java` (R7). Cada cenário sobe a aplicação com `new SpringApplicationBuilder(HabitattioApplication.class)`, com as propriedades `spring.main.web-application-type=none`, `spring.datasource.url/username/password` do container e `spring.data.redis.host=localhost`, e fecha o contexto em `try-with-resources`. Cada cenário que mexe na estrutura usa um `PostgreSQLContainer` **próprio**, com `TestcontainersConfiguration.POSTGRES_IMAGE` e iniciado e parado no teste. As consultas de verificação usam JDBC puro (`DriverManager`) contra o container. Métodos:
  - `deveSubirNoModoRapidoQuandoNenhumProfileEhEscolhido` (US2 #5): sem `.profiles(...)` e sem datasource; o contexto sobe, `DataSource#getConnection().getMetaData().getURL()` começa com `jdbc:h2:` e não existe o bean `Flyway` (`context.getBeanProvider(Flyway.class).getIfAvailable() == null`).
  - `deveCriarEstruturaDeImoveisEmBancoVazio` (S1, SC-006, US2 #1 e #6): `.profiles("prod")`; após a subida, `imoveis` existe, `pg_constraint` contém `ck_imoveis_endereco_nao_vazio` e `ck_imoveis_valor_sugerido_positivo`, e `flyway_schema_history` tem a versão `1` com `success = true`.
  - `naoDeveReaplicarMigracoesJaAplicadas` (S2, US2 #2): sobe e fecha duas vezes no mesmo container; `SELECT COUNT(*) FROM flyway_schema_history WHERE version = '1'` é 1 e a segunda subida não lança exceção.
  - `deveRecusarSubidaQuandoEstruturaDiverge` (S3, SC-004, US2 #3): sobe uma vez para migrar, fecha, executa `ALTER TABLE imoveis DROP COLUMN endereco`, e a nova subida com `prod` lança exceção (`assertThrows(Exception.class, ...)`). Depois a coluna `endereco` **continua ausente** em `information_schema.columns`, ou seja, o banco não foi alterado.
  - `deveRecusarSubidaQuandoMigracaoAplicadaFoiModificada` (S4, FR-009, US2 #4): sobe uma vez, fecha, executa `UPDATE flyway_schema_history SET checksum = checksum + 1 WHERE version = '1'`, e a nova subida lança exceção cuja cadeia de causas contém uma mensagem com `checksum` (case-insensitive).

  Rodar `./mvnw test -Dtest=EstruturaBancoProducaoTest`. Esperado: **S3 falha**, porque com `ddl-auto=none` a aplicação sobe mesmo sem a coluna, e esse é o vermelho que justifica T013. O modo rápido, S1, S2 e S4 passam de primeira: eles caracterizam o comportamento entregue pela US1 e pelo Flyway, e o relatório deve dizer isso explicitamente.

### Implementation for User Story 2

- [ ] T013 [US2] Em `src/main/resources/application-prod.properties`, trocar `spring.jpa.hibernate.ddl-auto=none` por `spring.jpa.hibernate.ddl-auto=validate` (acordo FR-014). Rodar `./mvnw test -Dtest='EstruturaBancoProducaoTest,ImoveisPostgresConstraintsTest'` até ficar verde, sem editar testes. Se o `validate` acusar diferença de tipo em S1, o ajuste é no mapeamento (T010) ou na migração (T008), **nunca** no teste.
- [ ] T014 [US2] Em `src/test/java/br/com/marden/habitattio/TestHabitattioApplication.java`, ativar o profile `prod`, para que subir "com os containers" seja subir como produção (Flyway + `validate`): usar `.withAdditionalProfiles("prod")` em `SpringApplication.from(...).with(...)` se a API existir no Boot 4.1.2; senão, incluir `--spring.profiles.active=prod` nos `args` repassados. Verificar manualmente com `./mvnw spring-boot:test-run`: o log mostra o Flyway aplicando `V1__cria_tabela_imoveis` e nenhum DDL do Hibernate. Atualizar a nota do `quickstart.md` §6 com a forma escolhida.

**Checkpoint**: SC-003 (100% da estrutura por migração), SC-004 (divergência impede a subida) e SC-006 (banco vazio estruturado só pela subida) atendidos.

---

## Phase 5: User Story 3 - O padrão fica documentado para os próximos agregados (Priority: P3)

**Goal**: quem for persistir `Inquilino`, `Contrato`, `Fatura` ou `Usuario` encontra todas as
decisões prontas (FR-012, FR-013).

**Independent Test**: lendo só `docs/persistencia.md`, um leitor responde aos itens (a)–(f) de
FR-012 para um agregado novo, e o documento não contradiz `V1__cria_tabela_imoveis.sql` nem
`ImovelJpaEntity` (US3 #1 e #2).

- [ ] T015 [P] [US3] Criar `docs/persistencia.md` (em português) com uma seção por item de FR-012, usando `imoveis` como exemplo concreto e citando os arquivos reais:
  - (a) **Nomes**: tabela em português, plural, snake_case; colunas snake_case derivadas do campo Java pela estratégia padrão (sem `@Column(name=...)`); constraints `pk_<tabela>`, `fk_<tabela>_<ref>`, `uq_<tabela>_<colunas>`, `ck_<tabela>_<coluna>_<regra>`.
  - (b) **Dinheiro**: `BigDecimal` ↔ `numeric(19,4)`, `@Column(precision = 19, scale = 4)`; o banco arredonda acima de 4 casas e o domínio não arredonda (divergência aceita, ver spec).
  - (c) **Chave**: `bigint GENERATED BY DEFAULT AS IDENTITY` + `GenerationType.IDENTITY`, com o motivo do `BY DEFAULT`.
  - (d) **Onde mora cada regra**: invariante simples sobre um membro da própria entidade (o `if` no construtor) vai **também** ao banco como `NOT NULL`/`CHECK`/tamanho; regra que depende de outros objetos ou de estado externo fica só na aplicação. O domínio sempre valida primeiro. Quando o tamanho de texto existe no banco, o domínio também o valida, contando code points.
  - (e) **Evolução**: migração nova `V<n>__<descricao_em_portugues>.sql` em `src/main/resources/db/migration/`, nunca editar uma aplicada (checksum); mapeamento JPA e migração mudam no mesmo commit; o `validate` não confere `CHECK`/tamanho/precisão, por isso o teste de constraints é obrigatório; o H2 do modo rápido não segue as migrações.
  - (f) **Teste de rejeição**: o molde de `ImoveisPostgresConstraintsTest` (anotações, SQL direto, um teste por garantia, assert pelo nome da constraint, não ler estado reidratado) e de `EstruturaBancoProducaoTest`.

  Incluir uma seção "Modos de execução" (padrão rápido × `prod`, variáveis `SPRING_DATASOURCE_*`, versões fixas `postgres:18.6`/`redis:8.10.3` em `TestcontainersConfiguration`) e uma seção "Fora deste padrão (ainda)" (criptografia do CPF quando `Inquilino` for persistido; o padrão não a impede).
- [ ] T016 [US3] Atualizar `AGENTS.md` (FR-013), mudando só o necessário:
  - §1: como subir em modo produção (`SPRING_PROFILES_ACTIVE=prod` + `SPRING_DATASOURCE_*`) e que `TestHabitattioApplication` sobe com o profile `prod`.
  - §6: um parágrafo apontando para `docs/persistencia.md` como padrão obrigatório de persistência dos próximos agregados.
  - §9: o stack acordado passa a incluir o Flyway, e a regra "não mude `ddl-auto`" passa a dizer que o modo rápido usa `update` e o `prod` usa `validate`, ambos acordados.

  Não mexer nas outras seções.
- [ ] T017 [US3] Rodar `/speckit-constitution` para uma emenda **PATCH** (1.0.0 → 1.0.1): incluir o Flyway (migrações versionadas, só PostgreSQL) na seção "Stack Tecnológico e Restrições" de `.specify/memory/constitution.md`, com o Sync Impact Report citando esta feature. Nenhum princípio muda (Governance: constitution e AGENTS.md consistentes na mesma mudança).

**Checkpoint**: SC-008 fica preparado para ser verificado na próxima feature de persistência.

---

## Phase 6: Polish & Cross-Cutting Concerns

- [ ] T018 Rodar `./mvnw test` completo e registrar no relatório: total de testes, verdes e falhas, e se os de integração rodaram (Docker). Verificar:
  - `git diff main -- src/test/java/br/com/marden/habitattio/HabitattioApplicationTests.java` vazio, e `git diff main -- src/test/java/br/com/marden/habitattio/domain/model/ImovelTest.java` só com adições.
  - `grep -rn "org.springframework\|jakarta.persistence" src/main/java/br/com/marden/habitattio/domain src/main/java/br/com/marden/habitattio/application` sem resultados.
  - Nenhum `*JpaEntity` fora de `adapters/outbound/persistence` (`grep -rln JpaEntity src/main/java | grep -v adapters/outbound/persistence` vazio).
- [ ] T019 Executar os cenários manuais 5 e 6 de [quickstart.md](./quickstart.md): no modo rápido, `./mvnw spring-boot:run` + `curl POST /imoveis` → `201`; no modo produção, o Flyway no log. Registrar o resultado, ou registrar que não foram executados.
- [ ] T020 Conferir a rastreabilidade: cada FR-001 a FR-015 e SC-001 a SC-007 da [spec.md](./spec.md) tem pelo menos uma task concluída que o atende. Se o escopo mudou durante a implementação, atualizar spec e plan (constitution §III).

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: sem dependências. T001 antes de T002 (o Flyway precisa estar no classpath para a propriedade fazer sentido). T003 é independente.
- **Foundational (Phase 2)**: T004 depende de T001–T003 e bloqueia todas as histórias.
- **US1 (Phase 3)**: depende de T004.
- **US2 (Phase 4)**: depende da US1 (T008, T009 e T010 entregam a migração e o profile `prod` que os cenários S1–S4 sobem). Ela é testável sozinha, mas não implementável antes da US1.
- **US3 (Phase 5)**: T015 pode começar após a US1 (o conteúdo de (a)–(d) já está decidido). T016 e T017 só depois da US2, porque descrevem `validate` e o `TestHabitattioApplication` em `prod`.
- **Polish (Phase 6)**: depois de todas as histórias.

### Within Each User Story

- Teste escrito → rodado → visto falhar pelo motivo certo → implementação → verde. Nunca o inverso.
- Na US1, os dois testes (T005, T006) vêm antes das quatro implementações (T007–T010). T011 fecha a fase.
- Na US2, T012 vem antes de T013. T014 vem depois de T013.

### Parallel Opportunities

- Setup: T003 em paralelo com T001 e T002.
- US1: T005 ∥ T006 (arquivos de teste diferentes). Depois de ver os dois falharem: T007 ∥ T008 ∥ T009 ∥ T010 (quatro arquivos diferentes).
- US3: T015 em paralelo com a US2 inteira.

---

## Parallel Example: User Story 1

```bash
# Testes primeiro, em paralelo (arquivos diferentes):
Task: "T005 testes de 255/256 caracteres em src/test/java/.../domain/model/ImovelTest.java"
Task: "T006 ImoveisPostgresConstraintsTest (G1–G10) em src/test/java/.../adapters/outbound/persistence/"

# Depois de ver os dois falharem, implementações em paralelo:
Task: "T007 limite de 255 code points em src/main/java/.../domain/model/Imovel.java"
Task: "T008 V1__cria_tabela_imoveis.sql em src/main/resources/db/migration/"
Task: "T009 application-prod.properties (flyway on, ddl-auto=none)"
Task: "T010 length/precision/scale em ImovelJpaEntity.java"
```

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Phase 1 (T001–T003) e Phase 2 (T004): dependência, versões fixas, linha de base verde.
2. Phase 3 (T005–T011): o banco de produção passa a impor as 6 garantias.
3. **Parar e validar**: `./mvnw test -Dtest='ImovelTest,ImoveisPostgresConstraintsTest'`.
   Com isso M1, M2 e M5 já estão no alvo.

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
- Testes que passam de primeira (T005: 2 de 3; T006: G1/G3/G5/G10; T012: modo rápido,
  S1/S2/S4) são esperados e **devem ser declarados** no relatório, com o motivo.
- Commitar ao fim de cada task ou grupo lógico, com mensagens em português.
- Fora de escopo, para abrir à parte depois: defeito de reidratação (`/speckit-bug-assess`) e
  `IllegalArgumentException` do domínio virando HTTP 500 no `ImovelController` (research,
  "Pontos observados fora de escopo").
