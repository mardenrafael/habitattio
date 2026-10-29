# Idea Research: Revisão e evolução do esquema do banco de dados

- **Slug**: better-db-schema
- **Created**: 2026-10-29
- **Evidence confidence (overall)**: medium

> Convenção de caminhos: `main/…` = `src/main/java/br/com/marden/habitattio/…`;
> `test/…` = `src/test/java/br/com/marden/habitattio/…`.
> Evidência interna (código) tem confiança alta por ser verificável no repositório. Evidência
> externa vem de **snippets de busca web** (nenhuma página foi aberta, ver Sources); por isso a
> confiança geral é `medium`.

## Users & Demand

- O único solicitante identificado é o dono do projeto (pedido direto no intake). Não há tickets,
  incidentes nem usuários externos registrados — [source: `.specify/assessments/better-db-schema/intake.md`; `git log` com 4 commits] (confidence: high)
- O sistema ainda não tem usuários finais: só `Imovel` tem caminho de entrada (cadastro) e
  saída (tabela). Não há leitura (`buscar`/`listar`) na porta `ImovelRepositoryPort` — [source: `main/adapters/outbound/persistence/ImovelRepositoryAdapter.java`, `main/ports/outbound/`] (confidence: high)
- A demanda é **preventiva**: a necessidade observável é ter um esquema correto antes de
  persistir `Inquilino`, `Contrato`, `Fatura` e `Usuario` (AGENTS.md §6, "Dívida restante").
  Não existe dor relatada em produção — [source: AGENTS.md §6 | ASSUMPTION quanto à ausência de produção] (confidence: medium)

## Prior Art

### Interno (estado atual do esquema)

- **Uma única tabela mapeada**: `imoveis` (`id` BIGINT IDENTITY, `endereco`, `valorSugerido`,
  `ativo`), sem precisão/escala explícita no `BigDecimal`, sem nome de coluna explícito, sem
  índices nem constraints além de `NOT NULL` — [source: `main/adapters/outbound/persistence/entity/ImovelJpaEntity.java`] (confidence: high)
- **Esquema gerado pelo Hibernate** com `spring.jpa.hibernate.ddl-auto=update`, em H2 em
  memória no modo PostgreSQL — [source: `src/main/resources/application.properties:4,11`] (confidence: high)
- **Não há ferramenta de migração** (Flyway/Liquibase) no `pom.xml` nem diretório
  `db/migration` — [source: `pom.xml`, `src/main/resources/`] (confidence: high)
- **Não existe nenhum teste de persistência**: não há `test/adapters/`. A tabela de onde cada
  teste vive (AGENTS.md §3) prevê `@DataJpaTest` + Testcontainers, mas nada foi escrito ainda —
  [source: listagem de `src/test/java`] (confidence: high)
- **Testcontainers usa imagens sem versão fixa** (`postgres:latest`, `redis:latest`): o banco
  dos testes de integração muda sozinho entre execuções — [source: `test/TestcontainersConfiguration.java:16,22`] (confidence: high)
- **Defeito de reidratação, ligado ao esquema**: as entidades de domínio só têm construtor de
  criação, que força o estado inicial (`Imovel.ativo = true`, `Contrato.ativo = true`,
  `Fatura.status = PENDENTE`). Ao ler do banco, não há como reconstruir um imóvel inativo, um
  contrato encerrado ou uma fatura `PAGA`/`ATRASADA` com `valorPago`/`dataPagamento`. Hoje isso
  já aparece em `ImovelRepositoryAdapter.salvar`, que devolve `new Imovel(...)` e portanto
  sempre `ativo = true`, mesmo salvando um imóvel inativo — [source: `main/domain/model/Imovel.java:17`, `Contrato.java:29`, `Fatura.java:27`, `main/adapters/outbound/persistence/ImovelRepositoryAdapter.java:26`] (confidence: high)
- **Regras do AGENTS.md §7 que hoje só vivem (ou nem vivem) no domínio**:
  - "Não emitir duas faturas para o mesmo contrato e competência": nenhuma constraint, e
    `Fatura` nem tem tabela — [source: AGENTS.md §7; `Fatura.java`] (confidence: high)
  - `mesReferencia` é `String` sem validação de formato — [source: `Fatura.java:14,21`] (confidence: high)
  - "Valores monetários estritamente positivos": validado em `Imovel` e em `Fatura.pagar`,
    mas **não** no construtor de `Contrato` (`valorAluguel`) nem no de `Fatura` (`valor`) —
    [source: `Contrato.java:16`, `Fatura.java:21`] (confidence: high)
  - `Contrato` não registra data de encerramento; `encerrar()` só troca o booleano —
    [source: `Contrato.java`] (confidence: high)
- **Restrições de governança já ratificadas**: trocar `ddl-auto`, trocar o banco ou adicionar
  dependência no `pom.xml` exige acordo explícito do usuário — [source: `.specify/memory/constitution.md` §IV; AGENTS.md §9] (confidence: high)
- Não há assessment, spec ou decisão anterior sobre o esquema em `.specify/` — [source: `.specify/`] (confidence: high)

### Externo (práticas de mercado)

- **`ddl-auto=update` não é recomendado em produção.** Renomear um campo pode criar uma coluna
  nova em vez de renomear a antiga, e remover uma entidade pode apagar dados. O padrão citado é
  migração versionada (Flyway/Liquibase) com `ddl-auto=validate` (falha rápido se o esquema
  divergir) ou `none` — [source: codingshuttle.com, codemia.io, docs.spring.io (versões antigas 1.4–2.0)] (confidence: medium — blogs e docs antigas; conferir na doc atual do Spring Boot 4)
- **H2 no modo PostgreSQL não equivale ao PostgreSQL.** Exemplos: `INSERT … ON CONFLICT`
  falha no H2 e `ALTER COLUMN … USING` não funciona. Migrações escritas para PostgreSQL
  precisam de adaptação para rodar no H2. O mercado recomenda testar contra o banco real via
  Testcontainers — [source: docs.docker.com, testcontainers.com, reflectoring.io, encircle360.com] (confidence: high — várias fontes concordam, incluindo Docker e Testcontainers)
- **Dinheiro em PostgreSQL: `numeric`, não `money` nem float.** A doc do PostgreSQL recomenda
  `numeric` para valores monetários. O tipo `money` depende do locale do banco. Precisão
  sugerida por uma fonte: `numeric(19,4)`, com a moeda em coluna separada quando houver mais de
  uma; `bigint` em centavos só para ledgers de alto volume — [source: crunchydata.com, techearl.com, postgrespro.com, medium.com/xendit-engineering] (confidence: high quanto a `numeric`; medium quanto a `(19,4)`)
- **Hibernate 6+ gera `numeric(38,2)` por padrão para `BigDecimal`** quando não há
  `precision`/`scale` — [ASSUMPTION: conhecimento prévio, não confirmado nesta busca] (confidence: medium)
- **Chaves primárias**: no PostgreSQL, colunas `identity` são preferíveis a `serial`. Para um
  banco único, um `bigint` gerado por sequência é suficiente; UUIDv7 só se justifica para IDs
  gerados fora do banco ou expostos publicamente (e embute o momento da criação). No Hibernate,
  `GenerationType.SEQUENCE` com otimizador pooled é preferido a `IDENTITY`, porque o id já é
  conhecido antes do INSERT e o batch funciona — [source: cybertec-postgresql.com, vladmihalcea.com, brandur.org, hn.nuxt.dev] (confidence: medium — opiniões de especialistas, não norma)
- **Enums**: `@Enumerated(EnumType.STRING)` em `VARCHAR` com `CHECK`, ou um tipo enum nativo
  do PostgreSQL. `ORDINAL` é frágil, porque reordenar o enum muda o significado dos dados. A
  `CHECK` precisa ser mantida por migração quando o enum muda — [source: vladmihalcea.com, thorben-janssen.com, forum.hibernate.org, velog.io] (confidence: high)
- **LGPD**: o `Inquilino.cpf` e o `Usuario.email` são dados pessoais. A LGPD (art. 46) exige
  medidas técnicas de segurança. As fontes encontradas falam de criptografia, minimização e
  inventário de dados de forma genérica, sem orientação específica para colunas de CPF —
  [source: cdp.com, blog.dponet.com.br, dio.me] (confidence: low — fontes genéricas; a referência
  primária seria o Guia de Segurança da ANPD para agentes de pequeno porte, não consultado)

## Market & Context

- **Alternativa de hoje**: deixar o Hibernate derivar o esquema das entities a cada subida. Com
  H2 em memória, o banco é recriado em toda inicialização, então o custo atual é quase zero —
  [source: `application.properties:4`] (confidence: high)
- **Custo de não fazer nada (cresce com o tempo)**:
  - Persistir os 4 agregados restantes sem convenção definida tende a repetir as lacunas de
    `imoveis` (sem constraints, precisão padrão, sem teste de persistência) — [ASSUMPTION] (confidence: medium)
  - Ao migrar para PostgreSQL persistente com `ddl-auto=update`, renomeações e remoções viram
    risco de perda de dados — [source: práticas externas acima] (confidence: medium)
  - O defeito de reidratação impede qualquer caso de uso de leitura correto (listar imóveis
    inativos, consultar fatura paga) — [source: evidência interna acima] (confidence: high)
- **Custo de mudar agora é o menor possível**: não há dados a migrar e há só uma tabela —
  [source: estado do repositório | ASSUMPTION quanto à inexistência de banco persistente fora do repo] (confidence: medium)

## Data & Constraints

- Volume: desconhecido; produto pré-produção. Gestão de aluguéis de um proprietário ou pequena
  carteira sugere volume baixo (centenas a milhares de faturas/ano), o que torna irrelevantes os
  argumentos de performance (`bigint` em centavos, `smallint` para enum, UUID vs bigint) —
  [ASSUMPTION] (confidence: low)
- Stack: Spring Boot 4.1.2, Data JPA (Hibernate), PostgreSQL driver + H2, Testcontainers
  PostgreSQL — [source: `pom.xml`] (confidence: high)
- Governança: adotar Flyway/Liquibase = nova dependência; trocar `ddl-auto` ou o banco padrão
  = mudança restrita. As três coisas exigem acordo explícito — [source: constitution §IV, AGENTS.md §9] (confidence: high)
- Compliance: CPF e e-mail são dados pessoais sob a LGPD — [source: Lei 13.709/2018, via snippets acima] (confidence: medium)
- Regra de negócio com reflexo direto no esquema: unicidade `(contrato, mesReferencia)`,
  valores > 0, `diaVencimento` ∈ [1,31], status `PAGA` terminal, exclusão apenas lógica —
  [source: AGENTS.md §7] (confidence: high)

## Evidence Against the Idea

- **YAGNI / momento**: com uma tabela, sem dados, sem produção e H2 recriado a cada subida, uma
  "revisão de esquema" isolada pode virar abstração antecipada, algo que a constitution §IV
  desencoraja. O esquema dos outros agregados pode nascer certo dentro de cada feature de
  persistência (via SDD), em vez de uma iniciativa separada — [source: constitution §IV] (confidence: medium)
- **Constraints duplicam o domínio**: a arquitetura põe invariantes no construtor das entidades.
  Repetir a regra em `CHECK`/`UNIQUE` cria duas fontes de verdade a manter sincronizadas. Por
  outro lado, unicidade sob concorrência só o banco garante de forma confiável —
  [source: AGENTS.md §2/§5 | ASSUMPTION quanto à concorrência] (confidence: medium)
- **Custo de ferramenta**: Flyway/Liquibase adiciona dependência e passos de manutenção e, se H2
  continuar como banco padrão, obriga escrever migrações que rodem nos dois bancos (ver
  incompatibilidades acima) — [source: encircle360.com, reflectoring.io] (confidence: medium)
- **Parte do problema não é de esquema**: o defeito de reidratação é do modelo de domínio e do
  adapter, não do DDL; resolvê-lo não depende de mudar o banco. Misturar os dois temas pode
  inflar o escopo — [source: evidência interna acima] (confidence: high)
- **Fontes externas fracas em parte**: várias são blogs, e as docs oficiais do Spring encontradas
  são de versões antigas — [source: lista de Sources] (confidence: high)

## Clarificações do usuário (2026-10-29)

Respostas do dono do projeto às perguntas abertas desta pesquisa. Prevalecem sobre as
suposições acima.

1. **Banco de produção: PostgreSQL.** H2 serve só para uso rápido (testes e afins). Portanto
   PostgreSQL é o alvo de esquema e de migrações; o H2 não define o desenho.
2. **Escopo: só a tabela `imoveis` por agora**, acertada junto com suas regras para servir de
   **modelo (padrão de referência)** às tabelas de `Inquilino`, `Contrato`, `Fatura` e
   `Usuario`, que ficam fora deste escopo.
3. **Acordo para avaliar migração versionada (Flyway/Liquibase) com `ddl-auto=validate`.**
   Isso atende à exigência de acordo explícito da constitution §IV para avaliar a opção. A
   escolha da ferramenta e a dependência concreta continuam para as etapas de shape/plan.
4. **Invariantes simples vão também para o banco** (`NOT NULL`, `CHECK` de valor positivo,
   `UNIQUE` e similares). **Regras complexas ficam preferencialmente na aplicação.** O
   domínio continua validando tudo; o banco é uma segunda barreira para o que é simples.
5. **O defeito de reidratação fica fora deste escopo** e vira correção à parte (candidato a
   `/speckit-bug-assess`).
6. **O CPF será criptografado no banco.** É regra para quando `Inquilino` for persistido,
   portanto fora do escopo imediato (item 2), mas o padrão definido aqui não deve impedi-la.

### Segunda rodada (2026-10-29)

7. **Critério de "invariante simples"**: é a regra sobre um membro da própria classe que, no
   código, vira um `if` na entidade (ex.: `NOT NULL`, não vazio, maior que zero). Essas vão ao
   banco. Regras que dependem de mais de um objeto ou de estado externo ficam na aplicação.
8. **H2 usa o esquema gerado pelo Hibernate.** As migrações versionadas miram apenas o
   PostgreSQL. Isso elimina o custo de escrever migrações compatíveis com os dois bancos,
   apontado em "Evidence Against the Idea".
9. **Regras de `imoveis` que vão ao banco: todas as candidatas citadas**:
   - `endereco` não nulo e não vazio (`NOT NULL` + `CHECK`);
   - `valor_sugerido` não nulo e `> 0` (`NOT NULL` + `CHECK`);
   - precisão/escala explícitas para `valor_sugerido`;
   - `endereco` único (`UNIQUE`).

   Observação para a etapa de definição: a unicidade de `endereco` **não existe hoje no
   domínio** (não há `if` em `Imovel` para isso, nem dá para haver, porque depende de outros
   imóveis). Pelo critério do item 7, ela seria regra de aplicação; foi incluída por decisão
   explícita. Falta definir como o domínio/caso de uso reage à violação (mensagem em
   português, tipo de erro) e como tratar endereços com grafias diferentes.
10. **Volume esperado: cerca de 14 imóveis e 14 faturas.** É um projeto de estudo com um único
    cliente, por enquanto. Performance é irrelevante para as decisões de esquema: `bigint`
    vs. UUID, `numeric` vs. centavos e `STRING` vs. `ORDINAL` se decidem por clareza e
    correção, não por custo.

## Gaps & Open Questions

- Nenhuma pergunta bloqueante restante para a etapa de definição.
- Pendente de detalhe (pode ir para `/speckit-assess-define`): normalização de `endereco` para a
  unicidade (maiúsculas/minúsculas, espaços) e o comportamento esperado quando ela é violada.
- [NEEDS CLARIFICATION: `mesReferencia` deve virar um tipo de competência (ano-mês) no domínio, com coluna própria no banco?]
- Lacuna de pesquisa: doc atual do Spring Boot 4 / Hibernate 7 sobre `ddl-auto`, `precision`/`scale` padrão e suporte a UUIDv7 não foi consultada diretamente.
- Lacuna de pesquisa: Guia de Segurança da ANPD para agentes de pequeno porte não foi consultado.

## Sources

Todas as fontes externas abaixo foram vistas **apenas como snippets de resultado de busca web**.
Nenhuma URL foi aberta. Pela URL Trust Policy, hosts fora da allowlist precisariam de
confirmação para serem abertos. Política: `not-fetched (search snippet)`.

- https://codingshuttle.com/spring-boot-handbook/database-migration-in-production-using-flyway (host: codingshuttle.com, policy: not-fetched)
- https://codemia.io/knowledge-hub/path/spring_boot_ddl_auto_generator (host: codemia.io, policy: not-fetched)
- https://docs.spring.io/spring-boot/docs/2.0.0.M5/reference/html/howto-database-initialization.html (host: docs.spring.io, policy: not-fetched)
- https://docs.docker.com/guides/testcontainers-java-replace-h2/problem-with-h2 (host: docs.docker.com, policy: not-fetched)
- https://testcontainers.com/guides/replace-h2-with-real-database-for-testing (host: testcontainers.com, policy: not-fetched)
- https://reflectoring.io/spring-boot-testcontainers/ (host: reflectoring.io, policy: not-fetched)
- https://encircle360.com/en/blog/testcontainers-integration-tests (host: encircle360.com, policy: not-fetched)
- https://github.com/thorlauridsen/spring-boot-java-sample/pull/60 (host: github.com, policy: allowlisted, not-fetched)
- https://www.crunchydata.com/blog/working-with-money-in-postgres (host: www.crunchydata.com, policy: not-fetched)
- https://techearl.com/store-money-postgresql (host: techearl.com, policy: not-fetched)
- https://postgrespro.com/list/thread-id/1390594 (host: postgrespro.com, policy: not-fetched)
- https://medium.com/xendit-engineering/benchmarking-pg-numeric-integer-9c593d7af67e (host: medium.com, policy: not-fetched)
- https://www.cybertec-postgresql.com/en/uuid-serial-or-identity-columns-for-postgresql-auto-generated-primary-keys/ (host: www.cybertec-postgresql.com, policy: not-fetched)
- https://vladmihalcea.com/postgresql-serial-column-hibernate-identity/ (host: vladmihalcea.com, policy: not-fetched)
- https://brandur.org/nanoglyphs/026-ids (host: brandur.org, policy: not-fetched)
- https://hn.nuxt.dev/item/40884878 (host: hn.nuxt.dev, policy: not-fetched)
- https://vladmihalcea.com/the-best-way-to-map-an-enum-type-with-jpa-and-hibernate/ (host: vladmihalcea.com, policy: not-fetched)
- https://thorben-janssen.com/blog/page/80/ (host: thorben-janssen.com, policy: not-fetched)
- https://forum.hibernate.org/viewtopic.php?p=2352497 (host: forum.hibernate.org, policy: not-fetched)
- https://cdp.com/pt/articles/customer-data-security-best-practices/ (host: cdp.com, policy: not-fetched)
- https://blog.dponet.com.br/?p=2041 (host: blog.dponet.com.br, policy: not-fetched; query param `p` não sensível)
- https://www.dio.me/en/articles/boas-praticas-e-lgpd-como-garantir-a-protecao-de-dados-na-sua-empresa-78cef83860b9 (host: www.dio.me, policy: not-fetched)
- Repositório local (read-only): `pom.xml`, `src/main/resources/application.properties`, `src/main/java/.../domain/model/*`, `src/main/java/.../adapters/outbound/persistence/*`, `src/test/java/.../TestcontainersConfiguration.java`, `AGENTS.md`, `.specify/memory/constitution.md`
