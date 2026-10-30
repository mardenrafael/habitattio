# Implementation Plan: Proprietário, login e isolamento de dados (módulo 2)

**Branch**: `worktree-spec-proprietario-isolamento` (feature `003-proprietario-login-isolamento`) | **Date**: 2026-10-30 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `specs/003-proprietario-login-isolamento/spec.md`

## Summary

Transformar o `Usuario` do domínio em **proprietário** que se cadastra (senha em hash BCrypt) e
entra no sistema por **sessão no servidor** com Spring Security (Q19); dar a todo `Imovel` um
**dono** e um **identificador** único por dono (sem distinguir caixa), com migração Flyway **V2**;
e garantir o **isolamento**: o dono vem só da sessão, a porta de imóveis só oferece buscas
filtradas pelo dono, e imóvel alheio responde exatamente como inexistente (404). Um tratamento de
erro único (`ProblemDetail`) passa a responder 400/401/403/404/409 em português, o que resolve D3.

Abordagem técnica (detalhe em [research.md](./research.md)): `formLogin` em `POST /sessao`
delegando a um `AuthenticationProvider` adapter que chama `AutenticarProprietarioUseCase` (R2);
CSRF `spa()` (R5); `CodificadorSenhaPort` com adapter BCrypt (R4); exceções de negócio em
`domain/exception` (R7); unicidade verificada no caso de uso e garantida no banco, com a corrida
traduzida para a mesma exceção (R8).

## Technical Context

**Language/Version**: Java 25

**Primary Dependencies**: Spring Boot 4.1.2 (Web MVC, Data JPA, Validation, Redis), Flyway;
**novos**: `spring-boot-starter-security` e `spring-boot-starter-security-test` (Spring Security
7.1.2 pelo BOM), cobertos pelo acordo Q19 (R12)

**Storage**: PostgreSQL em `prod` (estrutura só por Flyway, `ddl-auto=validate`); H2 em memória no
modo rápido (padrão, `ddl-auto=update`). Sessões HTTP na memória do container (R1)

**Testing**: JUnit 5 puro (domínio, casos de uso com fakes escritos à mão); `@DataJpaTest` +
Testcontainers PostgreSQL (persistência, profile `prod`); `@WebMvcTest` + MockMvc +
`spring-security-test` (controllers); `@SpringBootTest` + MockMvc no `prod` (isolamento ponta a
ponta). Sem framework de mocking (AGENTS §9)

**Target Platform**: serviço web Linux (JVM), consumido por frontend Next.js em repositório
separado, pela mesma origem (proxy) (Q23, R1)

**Project Type**: web-service (backend único, arquitetura hexagonal)

**Performance Goals**: não pesam (RNF-21, ~14 imóveis). Único custo deliberado: BCrypt custo 12
em `prod` (~0,25 s por login/cadastro)

**Constraints**: domínio e aplicação sem framework; isolamento sem exceção (FR-020/021); senha
nunca legível (SC-004); nenhuma resposta 500 nos cenários da spec (SC-007)

**Scale/Scope**: poucos proprietários, instância única; 6 rotas HTTP (3 novas públicas/de sessão,
3 de imóvel), 5 casos de uso, 1 migração

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

Constitution v1.0.1.

| Princípio | Verificação | Pré-research | Pós-design |
|---|---|---|---|
| I. Hexagonal | `domain/` e `application/` sem Spring: hash atrás de `CodificadorSenhaPort`; regra de credencial no `AutenticarProprietarioUseCase`, Spring só no adapter `ProprietarioAuthenticationProvider`; exceções em `domain/exception`; entity e `*Request/*Response` não cruzam fronteira; principal (`ProprietarioAutenticado`) fica em `adapters/inbound/security` e o controller passa só o `Long usuarioId` ao command | ✅ | ✅ |
| I. Instanciável sem Spring | casos de uso, adapters de persistência, `BCryptCodificadorSenhaAdapter`, `ProprietarioAuthenticationProvider` e controllers recebem tudo por construtor | ✅ | ✅ |
| II. TDD | ordem de dentro para fora; testes antes; testes existentes adaptados **só** dentro do limite de Clarifications Q2 — ver **P1** | ✅ | ⚠️ P1 |
| III. SDD | spec clarificada; plan registra decisões técnicas; mudança de escopo volta para a spec | ✅ | ✅ |
| IV. YAGNI / dependências | uma dependência nova (Spring Security), acordada (Q19); sem JWT, sem Spring Session, sem Lombok/MapStruct/Mockito; portas só para casos de uso reais; mapeamento manual | ✅ | ✅ |
| V. Convenções | domínio e mensagens em português; sufixos técnicos em inglês; `BigDecimal` intocado; invariantes no construtor com `IllegalArgumentException`; records para commands/DTOs | ✅ | ✅ |
| Stack | Spring Security entra na lista de stack da constitution e no AGENTS §9 (emenda PATCH 1.0.2, como Flyway em 1.0.1) | ✅ planejado | ✅ planejado |

### P1 — pendência que bloqueia a implementação (não o plan)

`CadastrarImovelUseCaseTest` tem um fake **privado** (`ImovelRepositoryFake implements
ImovelRepositoryPort`). O desenho acrescenta dois métodos à porta (`existeIdentificador`,
`buscarDoProprietario`, data-model.md), então esse fake **deixa de compilar** até implementar os
dois métodos. Acrescentar métodos ao fake vai além do limite autorizado em Clarifications Q2
("só acrescentar dono e identificador nos dados"). Opções:

- **(A, recomendada)** O dono amplia a autorização: o fake privado pode ganhar os métodos novos da
  porta, sem tocar asserções nem nomes de teste. Mantém **uma porta por agregado** (AGENTS §6).
- (B) Dividir a porta (`ImovelRepositoryPort` só com `salvar`; `ImovelConsultaPort` com as
  buscas), para não tocar o fake. Cria porta extra só para preservar um teste — contra o espírito
  de YAGNI e de "cada agregado por sua própria porta".

Até a decisão, `/speckit-tasks` pode ser gerado com a opção A marcada como dependente de
confirmação; nenhuma tarefa de implementação começa antes.

Os outros três testes existentes cabem no limite autorizado:
`ImovelTest` (só argumentos de construtor), `ImovelRepositoryAdapterTest` (argumentos; dono é
`Long` simples, sem FK no schema gerado pelo Hibernate) e `ImoveisPostgresConstraintsTest`
(o `INSERT` ganha `usuario_id` e `identificador` e um `@BeforeEach` cria o proprietário dono —
criar o dono é parte de "acrescentar dono"; o identificador de cada linha é gerado único para
não colidir nos testes que repetem endereço).

## Project Structure

### Documentation (this feature)

```text
specs/003-proprietario-login-isolamento/
├── spec.md
├── plan.md              # este arquivo
├── research.md          # R1–R12
├── data-model.md        # domínio, portas, casos de uso, entities
├── quickstart.md        # roteiro de validação
├── contracts/
│   ├── api-http.md      # rotas, status, mensagens
│   └── schema-v2.md     # tabela usuarios, alterações em imoveis, garantias G-*
├── checklists/requirements.md
└── tasks.md             # /speckit-tasks (ainda não criado)
```

### Source Code (repository root)

`N` = novo · `M` = modificado.

```text
src/main/java/br/com/marden/habitattio/
├── domain/
│   ├── model/
│   │   ├── Usuario.java                         M  normaliza e-mail; limites 100/254; normalizarEmail()
│   │   ├── Senha.java                           N  8 code points ≤ senha ≤ 72 bytes UTF-8
│   │   └── Imovel.java                          M  usuarioId, identificador; mesmoIdentificador()
│   └── exception/                               N
│       ├── EmailJaCadastradoException.java
│       ├── IdentificadorImovelDuplicadoException.java
│       ├── ImovelNaoEncontradoException.java
│       └── CredenciaisInvalidasException.java
├── ports/
│   ├── inbound/
│   │   ├── CadastrarProprietarioCommand.java    N
│   │   ├── AutenticarProprietarioCommand.java   N
│   │   ├── CadastrarImovelCommand.java          M  + usuarioId, identificador
│   │   ├── ConsultarImovelCommand.java          N
│   │   └── InativarImovelCommand.java           N
│   └── outbound/
│       ├── UsuarioRepositoryPort.java           N
│       ├── ImovelRepositoryPort.java            M  + existeIdentificador, buscarDoProprietario
│       └── CodificadorSenhaPort.java            N
├── application/usecase/
│   ├── CadastrarProprietarioUseCase.java        N
│   ├── AutenticarProprietarioUseCase.java       N
│   ├── CadastrarImovelUseCase.java              M  dono + checagem de identificador
│   ├── ConsultarImovelUseCase.java              N
│   └── InativarImovelUseCase.java               N
└── adapters/
    ├── config/
    │   ├── BeanConfiguration.java               M  novos adapters e casos de uso
    │   └── SegurancaConfiguration.java          N  SecurityFilterChain, formLogin, logout, csrf.spa(), entry points
    ├── inbound/
    │   ├── rest/
    │   │   ├── ImovelController.java            M  principal → command; GET /{id}; POST /{id}/inativacao
    │   │   ├── ProprietarioController.java      N  POST /proprietarios
    │   │   ├── SessaoController.java            N  GET /sessao
    │   │   └── TratamentoErrosHandler.java      N  @RestControllerAdvice → ProblemDetail
    │   └── security/                            N
    │       ├── ProprietarioAutenticado.java       record principal (Serializable)
    │       ├── ProprietarioAuthenticationProvider.java
    │       └── RespostasSeguranca.java            handlers 204/401/403 delegando ao HandlerExceptionResolver
    └── outbound/
        ├── persistence/
        │   ├── ImovelRepositoryAdapter.java     M  novos métodos; tradução de corrida
        │   ├── SpringDataImovelRepository.java  M  findByIdAndUsuarioId, existsBy...IgnoreCase
        │   ├── UsuarioRepositoryAdapter.java    N
        │   ├── SpringDataUsuarioRepository.java N
        │   └── entity/
        │       ├── ImovelJpaEntity.java         M  usuarioId, identificador, @UniqueConstraint
        │       └── UsuarioJpaEntity.java        N
        └── seguranca/
            └── BCryptCodificadorSenhaAdapter.java N

src/main/resources/
├── application.properties                       M  bcrypt-custo=10; sessão 30m; cookie SameSite=Lax
├── application-prod.properties                  M  bcrypt-custo=12; cookie Secure
└── db/migration/V2__cria_usuarios_e_dono_de_imoveis.sql   N

src/test/java/br/com/marden/habitattio/
├── fakes/                                       N  UsuarioRepositoryFake, ImovelRepositoryFake, CodificadorSenhaFake
├── domain/model/
│   ├── UsuarioTest.java                         M  só testes novos acrescentados
│   ├── SenhaTest.java                           N
│   └── ImovelTest.java                          M  dados adaptados (Q2) + testes novos
├── application/usecase/
│   ├── CadastrarProprietarioUseCaseTest.java    N
│   ├── AutenticarProprietarioUseCaseTest.java   N
│   ├── CadastrarImovelUseCaseTest.java          M  dados adaptados (Q2, P1); testes novos de dono/duplicidade em classe nova
│   ├── CadastrarImovelComDonoUseCaseTest.java   N
│   ├── ConsultarImovelUseCaseTest.java          N
│   └── InativarImovelUseCaseTest.java           N
├── adapters/outbound/
│   ├── persistence/
│   │   ├── ImovelRepositoryAdapterTest.java     M  dados adaptados (Q2)
│   │   ├── ImoveisPostgresConstraintsTest.java  M  dados adaptados (Q2)
│   │   ├── ImovelRepositoryAdapterDonoTest.java N  buscarDoProprietario, existeIdentificador, corrida
│   │   ├── UsuarioRepositoryAdapterTest.java    N
│   │   └── UsuariosEDonoPostgresConstraintsTest.java N  G-U1…G-I7
│   └── seguranca/BCryptCodificadorSenhaAdapterTest.java N  JUnit puro
├── adapters/inbound/rest/
│   ├── ProprietarioControllerTest.java          N  @WebMvcTest
│   ├── ImovelControllerTest.java                N  @WebMvcTest (inclui 400 em vez de 500 — D3)
│   └── SessaoControllerTest.java                N
├── EstruturaBancoProducaoTest.java              —  inalterado; nova prova G-S1 em teste novo
├── SessaoIntegracaoTest.java                    N  login/logout/CSRF/fixação de sessão no prod
└── IsolamentoProprietariosIntegracaoTest.java   N  SC-002, SC-003 com dois donos reais

pom.xml                                          M  spring-boot-starter-security (+ -test)
```

**Structure Decision**: mantém o layout hexagonal único do AGENTS §2. Pacotes novos:
`domain/exception` (R7), `adapters/inbound/security` (adapter de entrada do Spring Security) e
`adapters/outbound/seguranca` (adapter de hash). A configuração de segurança ganha classe própria
em `adapters/config`, como o AGENTS §2 prevê quando `BeanConfiguration` cresceria demais.

## Ordem de implementação (de dentro para fora, TDD)

1. **Pré-condição**: P1 decidida.
2. `pom.xml`: dependências de segurança (o contexto precisa subir com elas antes de tudo; os
   testes de contexto existentes devem continuar verdes, já que nenhuma rota de imóvel está
   coberta por eles).
3. Domínio: `Senha`, `Usuario` (normalização e limites), `Imovel` (dono, identificador) e as
   exceções — testes primeiro; adaptação dos dados de `ImovelTest` no mesmo passo em que o
   construtor muda.
4. Portas e commands (só forma) → testes dos casos de uso com fakes → implementação. Adaptação
   de `CadastrarImovelUseCaseTest`.
5. Persistência: V2 + entities + adapters; testes `prod` com Testcontainers (garantias G-*,
   corrida). Adaptação de `ImovelRepositoryAdapterTest` e `ImoveisPostgresConstraintsTest`.
6. Adapter BCrypt (teste JUnit puro).
7. Segurança + controllers + tratamento de erro (`@WebMvcTest`).
8. Fiação (`BeanConfiguration`, `SegurancaConfiguration`) e testes ponta a ponta
   (`SessaoIntegracaoTest`, `IsolamentoProprietariosIntegracaoTest`).
9. Documentação (FR-027): AGENTS §2 (pacotes novos), §6 (`Usuario` = proprietário; `Imovel` com
   dono e identificador), §7 (isolamento, identificador único por dono), §9 (Spring Security no
   stack); constitution PATCH 1.0.2 via `/speckit-constitution`; `docs/requisitos-e-der.md`
   (RF-01…04, 07, 08, RNF-11, 12, 15 → ✅; D3, D6, D7 resolvidos; `senha_hash varchar(60)`).
10. `./mvnw test` completo; relatório diz explicitamente se a integração rodou.

## Riscos

| Risco | Mitigação |
|---|---|
| Esquecer o filtro por dono num caso de uso futuro | a porta não oferece busca sem dono (R10); teste ponta a ponta percorre ids vizinhos |
| `IllegalArgumentException` de infraestrutura virar 400 | só o domínio lança de propósito; adapter BCrypt converte a dele (R6) |
| Senha em log | `Senha` e os commands com senha mascaram `toString()`; quickstart confere o console |
| Sessão perdida em reinício | aceito (R1); Spring Session com Redis fica como evolução, com acordo de dependência |
| Banco de dev com linhas da V1 | V2 falha de propósito; quickstart manda recriar |
| Testes de contexto existentes quebrarem com Spring Security no classpath | passo 2 roda a suíte logo após incluir a dependência; se um teste existente quebrar, parar e reportar (AGENTS §3) |

## Complexity Tracking

Nenhuma violação da constitution a justificar. A única tensão (P1) é de processo, não de
desenho, e está registrada acima para decisão do dono.
