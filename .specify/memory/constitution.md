<!--
Sync Impact Report
Version change: 1.0.1 → 1.0.2 (PATCH)
Rationale: amendment from feature 003-proprietario-login-isolamento (task T066). Spring Security
was added to the stack with explicit owner agreement (Q19 of the feature 003 clarifications).
The "Stack Tecnológico e Restrições" section and the Principle IV list of the stack that covers
the current scope are updated to include it; no principle, MUST rule or governance procedure
changes, so PATCH.
Modified principles: none (only the stack list inside IV. Simplicidade e YAGNI is updated)
Modified sections: Stack Tecnológico e Restrições (Spring Security added); IV (stack list)
Added sections: none
Removed sections: none
Templates requiring follow-up: none (.specify/templates/* do not reference the stack list)
Related sync: AGENTS.md §9 (stack) updated in this same change set (T065); AGENTS.md §2/§6/§7
also updated for the feature 003 model and rules.
Deferred items: none

Previous report — 1.0.0 → 1.0.1 (PATCH): feature 001-better-db-schema (T019) added Flyway to
the stack with explicit owner agreement (spec FR-014); Testcontainers versions fixed.
-->

# Habitattio Constitution

## Core Principles

### I. Arquitetura Hexagonal (NON-NEGOTIABLE)
As dependências apontam para dentro: `domain/` não conhece nenhuma outra camada; `application/`
conhece `domain/` e `ports/`; `adapters/` conhecem `ports/`. Nunca o inverso.

- `domain/` e `application/` MUST NOT importar `org.springframework`, `jakarta.persistence` ou
  qualquer classe de infraestrutura — nem `@Component`, `@Service`, `@Entity`, `@Transactional`.
  Se uma anotação parece necessária ali, o desenho está errado: a dependência deve virar uma
  porta. `domain/` MUST NOT importar de `application/`, `ports/` ou `adapters/`.
- Um caso de uso depende apenas de interfaces de `ports/outbound/`, recebidas por construtor
  (sem injeção por campo, sem `@Autowired`). O método público se chama `executar(...)` e recebe
  um command de `ports/inbound/`; um caso de uso corresponde a uma operação de negócio.
- A entity JPA MUST NOT vazar da camada de persistência: o adapter traduz
  `*JpaEntity ↔ <Entidade de domínio>` nas duas direções. O DTO HTTP MUST NOT entrar no caso de
  uso: o controller converte `*Request → *Command` e `domínio → *Response`.
- Toda classe de `application/` e `adapters/` MUST ser instanciável por construtor em um teste,
  sem contexto Spring — esse é o teste prático de que a arquitetura não está acoplada.

Rationale: mantém o domínio testável e substituível independentemente do framework, e torna a
violação de camada detectável em revisão de código em vez de depender de disciplina informal.

### II. TDD (NON-NEGOTIABLE)
Red → Green → Refactor, sempre. Nenhum código de produção é escrito sem um teste que falhe pelo
motivo certo primeiro — mesmo quando a spec não detalha os testes.

- Ordem estrita por feature: (1) desenhar a porta/assinatura/command, só a forma, sem lógica;
  (2) escrever os testes cobrindo happy path e todos os casos de erro esperados, antes de
  qualquer implementação real; (3) rodar e ver falhar pelo motivo esperado — um teste que passa
  de primeira não provou nada; (4) só então implementar até a suíte ficar verde, refatorando com
  a suíte verde.
- Guardrail: a partir do momento em que um teste é escrito e visto falhar pelo motivo certo, ele
  é read-only — MUST NOT ser alterado, apagado, comentado ou marcado `@Disabled` para fazer o
  build passar. Se a implementação parece exigir mudar o teste, o problema é a implementação. Se
  um teste pré-existente quebra com uma mudança, pare: a alteração do teste exige decisão
  explícita do usuário, nunca uma edição silenciosa do agente.
- Um comportamento por teste, nome descrevendo o comportamento em português
  (`deveRejeitarValorNegativo`). Estrutura Arrange/Act/Assert separadas por linha em branco.
  Prefira fakes escritos à mão implementando a porta a mocks; mock apenas para verificar
  interação que é, de fato, o comportamento sob teste.

Rationale: garante que os testes provem comportamento real em vez de apenas acompanhar a
implementação, e impede a prática de "ajustar o teste até passar", que mascara regressões.

### III. Spec-Driven Development (SDD)
Toda feature não-trivial começa por uma especificação escrita (Spec Kit), não por código:
`/speckit.specify` → `/speckit.clarify` → `/speckit.plan` → `/speckit.tasks` →
`/speckit.analyze` → `/speckit.implement`.

- `spec.md` descreve comportamento observável e regras de negócio, sem decisão técnica; decisão
  de tecnologia é assunto do `plan.md`.
- A spec é a fonte da verdade do requisito, não o código. Se o escopo mudar no meio do trabalho,
  spec e plan MUST ser atualizados antes ou junto da mudança de código.
- Exceção: correção de bug trivial, renomeação e ajuste de formatação podem ser feitos direto,
  sem passar pelo fluxo de spec.

Rationale: decisões de negócio e técnicas ficam registradas e revisáveis antes da implementação,
evitando deriva de requisito e retrabalho por entendimento divergente.

### IV. Simplicidade e YAGNI
Nenhuma abstração, porta ou camada é criada "para o futuro"; uma porta só existe quando há um
caso de uso real que a exige.

- Dependência nova no `pom.xml`, troca de banco ou de `ddl-auto`, ou introdução de Lombok,
  MapStruct ou framework de mocking MUST ter acordo explícito do usuário antes de ser
  adicionada — o stack atual (Web MVC, Data JPA, Redis, Validation, Security,
  PostgreSQL/H2, Flyway, Testcontainers) cobre o escopo vigente.
- Mapeamento entre camadas (`*JpaEntity ↔` entidade de domínio, `*Request/*Response ↔` domínio)
  é manual e explícito, não automático, para manter a fronteira de arquitetura visível em
  revisão.

Rationale: complexidade acidental custa mais do que a decisão deliberada de não antecipá-la; o
mapeamento manual torna a fronteira entre camadas auditável a olho, sem depender de geração
automática opaca.

### V. Convenções de Domínio e Nomenclatura
O vocabulário de domínio é em português, alinhado ao negócio (`Imovel`, `Inquilino`,
`Contrato`, `Fatura`, `Usuario`, `valorAluguel`, `diaVencimento`, `mesReferencia`; verbos como
`pagar()`, `encerrar()`, `inativar()`, `executar()`). Sufixos técnicos são em inglês, pela
convenção do framework (`...Port`, `...Adapter`, `...UseCase`, `...Command`, `...Request`,
`...Response`, `...JpaEntity`, `...Controller`).

- Dinheiro MUST ser `BigDecimal`, nunca `double`/`float`, comparado com `compareTo`, nunca
  `equals`/`==`. Datas MUST usar `java.time` (`LocalDate` para vencimento e competência).
- Validação de invariante acontece dentro do construtor da entidade de domínio — um objeto de
  domínio nunca existe em estado inválido — lançando `IllegalArgumentException` com mensagem em
  português descrevendo o que está errado. `@Valid`/Bean Validation ficam no DTO de entrada do
  controller, na borda, não no domínio.
- Records para commands e DTOs imutáveis, sem lógica; classes para entidades de domínio, que têm
  comportamento e invariantes. Campos `private final` sempre que possível; dependências
  recebidas por construtor. Imports explícitos, sem wildcard.

Rationale: consistência de nomenclatura e de tipos evita erros de cálculo financeiro (a razão de
negócio do sistema) e mantém o domínio legível para quem conhece o vocabulário em português.

## Stack Tecnológico e Restrições

Java 25, Spring Boot 4.1.2, Spring Web MVC, Spring Data JPA, Spring Data Redis, Bean Validation,
Spring Security (login por sessão e isolamento por proprietário, acordado na feature 003),
PostgreSQL (runtime), H2 (dev/test em memória), Flyway (migrações versionadas, só no PostgreSQL:
profile `prod` com `ddl-auto=validate`; o modo rápido com H2 usa `ddl-auto=update`, sem
migrações), Testcontainers (PostgreSQL + Redis, versões fixas) para testes de integração.

- Sempre use o wrapper `./mvnw`, nunca um `mvn` global.
- Testes de integração exigem Docker rodando. Na ausência de Docker, execute apenas os testes de
  domínio/aplicação (`-Dtest=...`) e declare explicitamente no relatório que os testes de
  integração não foram executados. Nunca declarar a suíte verde sem tê-la executado.
- Como `domain/` e `application/` não têm anotações, os beans são declarados explicitamente em
  uma classe de configuração (`@Configuration` + métodos `@Bean`) — único ponto de contato entre
  Spring e o núcleo da aplicação. Todo novo adapter/caso de uso ganha seu `@Bean` ali.
- Alteração de `.specify/scripts/` ou `.specify/templates/` está fora do escopo de qualquer
  tarefa de feature.

## Fluxo de Desenvolvimento e Definition of Done

Ordem para toda feature nova, de dentro para fora: (1) regra em `domain/model/` com teste
unitário primeiro; (2) porta inbound (command) e porta outbound (interface), se necessárias;
(3) caso de uso em `application/usecase/` (teste com fake da porta); (4) adapter de persistência
+ entity JPA (teste de integração); (5) controller (teste de web layer); (6) registro do bean na
configuração.

Antes de reportar uma tarefa como concluída, MUST verificar:

- Existe teste escrito antes do código, que falhou e agora passa.
- `./mvnw test` foi executado; se os testes de integração não rodaram (sem Docker), isso está
  dito explicitamente no relatório.
- `domain/` e `application/` sem nenhum import de framework.
- Nenhum `*JpaEntity` nem DTO HTTP cruzando fronteira de camada.
- Beans novos registrados na configuração.
- Spec e tasks atualizados, se o escopo mudou.
- Nomes em português no domínio, mensagens de erro em português.

## Governance

Esta constitution tem precedência sobre qualquer prática, convenção pessoal ou atalho pontual.
Em caso de conflito com `AGENTS.md` ou qualquer outro guia operacional, esta constitution
prevalece, e o documento conflitante MUST ser atualizado na mesma mudança para restaurar a
consistência.

- **Emendas**: exigem (a) proposta explícita do usuário ou identificação de uma inconsistência
  real entre prática e princípio, (b) atualização deste arquivo via `/speckit.constitution`,
  (c) incremento de versão conforme as regras abaixo, e (d) sincronização da seção
  correspondente em `AGENTS.md` no mesmo conjunto de mudanças.
- **Versionamento semântico**: MAJOR para remoção ou redefinição incompatível de um princípio;
  MINOR para adição de um princípio novo ou expansão material de orientação existente; PATCH
  para clarificação, correção de redação ou ajuste não-semântico.
- **Revisão de conformidade**: toda revisão de código e toda execução de `/speckit.analyze`
  MUST verificar aderência aos princípios acima antes de aprovar a implementação. Complexidade
  que viole o Princípio IV (Simplicidade e YAGNI) MUST ser justificada explicitamente no
  `plan.md` da feature ou rejeitada em revisão.
- Use `AGENTS.md` para orientação operacional detalhada (comandos, modelo de domínio atual,
  dívida técnica conhecida, regras de negócio). Este documento define os princípios
  não-negociáveis; `AGENTS.md` documenta como aplicá-los no dia a dia — os dois MUST permanecer
  consistentes.

**Version**: 1.0.2 | **Ratified**: 2026-10-29 | **Last Amended**: 2026-10-30
