# Implementation Plan: Descrição da API derivada do código

**Branch**: `004-auto-docs-api` (trabalho no worktree `worktree-openapi-docs`) | **Date**: 2026-11-02 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `specs/004-auto-docs-api/spec.md`

## Summary

A aplicação passa a produzir a própria descrição OpenAPI 3.1 com o **springdoc-openapi 3.2.0**
(compilado contra o Spring Boot 4.1.2 do projeto). As 5 operações de controller saem do código,
com o texto em português em anotações nos controllers e records; as 3 operações que não são
controllers (`POST /sessao`, `DELETE /sessao`, `GET /actuator/health`), o guia de sessão/CSRF,
os esquemas de segurança e as respostas compartilhadas são montados num
`DocumentacaoApiConfiguration` em `adapters/config/`. Um teste da suíte normal, no modo rápido
e sem Docker, verifica o conteúdo mínimo e só então grava `docs/api/openapi.yaml`, que vira
artefato gerado e a referência do contrato HTTP. A Swagger UI e `/v3/api-docs` ficam públicos no
modo rápido e **desligados (404) em `prod`**. Detalhes e alternativas: [research.md](research.md).

## Technical Context

**Language/Version**: Java 25

**Primary Dependencies**: Spring Boot 4.1.2 (Web MVC, Security, Actuator, Data JPA); **nova**:
`org.springdoc:springdoc-openapi-starter-webmvc-ui:3.2.0` (traz swagger-core 2.2.55 e Swagger UI
5.34.0) — dependência acordada pelo dono para esta feature (exceção ao AGENTS.md §9)

**Storage**: N/A (nenhuma tabela, migração ou entidade nova)

**Testing**: JUnit 5 + `@SpringBootTest`/`@AutoConfigureMockMvc` no modo rápido (H2, sem Docker)
para a descrição e a UI; `@SpringBootTest` com profile `prod` + Testcontainers para o
fechamento em produção (exige Docker)

**Target Platform**: servidor Linux / container (imagem existente, sem mudança no `Dockerfile`)

**Project Type**: web-service (projeto único, Maven)

**Performance Goals**: N/A — a geração roda uma vez por execução da suíte; meta é não adicionar
mais que alguns segundos ao `./mvnw test` (um contexto Spring a mais no modo rápido)

**Constraints**: geração determinística (FR-009); arquivo gravado só se válido (FR-008);
nenhuma superfície nova em `prod` (FR-010); nada em `domain/`, `application/` ou `ports/` (FR-013);
nenhuma rota ou regra de segurança da API alterada (FR-011)

**Scale/Scope**: 8 operações, 4 tags, ~8 componentes compartilhados; 3 controllers anotados,
1 classe de configuração nova, 1 linha de regra nova na cadeia de segurança, 3 testes novos

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Princípio | Avaliação | Status |
|---|---|---|
| **I. Arquitetura Hexagonal** | Anotações do swagger só em `adapters/inbound/rest/`; configuração em `adapters/config/`. `domain/`, `application/`, `ports/` sem nenhum import novo. Nenhum `*JpaEntity`/DTO cruza camada. Controllers continuam instanciáveis por construtor (anotações não exigem contexto). | ✅ |
| **II. TDD** | Os três testes novos (`DescricaoApiTest`, `DocumentacaoApiTest`, `DocumentacaoApiProducaoTest`) são escritos e vistos falhar antes da dependência/configuração (hoje `/v3/api-docs` responde 401 sem login). Nenhum teste existente é alterado; se algum quebrar (ex.: `@WebMvcTest` com a regra de segurança nova), parar e reportar. | ✅ |
| **III. SDD** | Spec com clarificações resolvidas; este plan; tasks na sequência. | ✅ |
| **IV. Simplicidade e YAGNI** | Dependência nova — **exceção acordada** (intake, Clarifications; decisão da avaliação). Nenhuma abstração "para o futuro": sem modo "check" separado, sem grupos de API, sem UI customizada. | ⚠️ justificado (Complexity Tracking) |
| **V. Convenções de nomenclatura** | `DocumentacaoApiConfiguration`, `DescricaoApiTest`, `DocumentacaoApiTest`; textos e nomes de teste em português (`deveConterAsOitoOperacoes`, `deveResponder404NoModoProducao`). | ✅ |
| **Stack Tecnológico e Restrições** | Precisa de emenda PATCH (1.0.2 → 1.0.3) listando o springdoc e a documentação só no modo rápido, e sincronia do AGENTS.md §1/§9 na mesma mudança (research R10). Actuator continua só com `health`. | ✅ com emenda planejada |
| **Definition of Done** | `./mvnw test` completo (com Docker) ao final; relatar se os de integração não rodarem. | ✅ |

**Gate (pré-pesquisa)**: aprovado — a única tensão (dependência nova) é exceção explícita do dono.

**Re-check pós-design**: aprovado. O design não acrescentou porta, caso de uso, entidade nem
tabela; a regra nova da cadeia de segurança é só `permitAll` de rotas `GET` de documentação, e o
fechamento em `prod` é por propriedade, incluindo os arquivos estáticos da Swagger UI
(`/webjars/**`): em `prod` nada da documentação fica online (research R7).

## Project Structure

### Documentation (this feature)

```text
specs/004-auto-docs-api/
├── spec.md
├── plan.md                    # este arquivo
├── research.md                # R1–R11
├── data-model.md              # artefatos de documentação (sem domínio novo)
├── quickstart.md              # 6 cenários de validação
├── contracts/
│   ├── descricao-api.md       # conteúdo mínimo verificado pelo teste (D1–D5)
│   └── documentacao-http.md   # rotas de documentação por modo
├── checklists/requirements.md
└── tasks.md                   # /speckit-tasks (ainda não criado)
```

### Source Code (repository root)

```text
pom.xml                                            # + springdoc-openapi-starter-webmvc-ui (propriedade springdoc.version=3.2.0)

src/main/java/br/com/marden/habitattio/
└── adapters/
    ├── config/
    │   ├── DocumentacaoApiConfiguration.java      # NOVO: bean OpenAPI (info+guia, servers, securitySchemes,
    │   │                                          #   respostas/esquemas compartilhados) + OpenApiCustomizer
    │   │                                          #   (POST/DELETE /sessao, GET /actuator/health)
    │   └── SegurancaConfiguration.java            # + permitAll GET /v3/api-docs, /v3/api-docs/**, /swagger-ui.html, /swagger-ui/**
    └── inbound/rest/
        ├── ProprietarioController.java            # + @Tag/@Operation/@ApiResponse/@SecurityRequirement; @Schema nos records
        ├── SessaoController.java                  # idem
        └── ImovelController.java                  # idem

src/main/resources/
├── application.properties                         # + springdoc.* (ordem por chave, pretty print, OpenAPI 3.1,
│                                                  #   override-with-generic-response=false, swagger-ui.csrf.enabled=true)
└── application-prod.properties                    # + springdoc.api-docs.enabled=false, springdoc.swagger-ui.enabled=false,
                                                   #   spring.web.resources.add-mappings=false (sem /webjars/** em prod)

src/test/java/br/com/marden/habitattio/
├── DescricaoApiTest.java                          # NOVO: D1–D4 + exportação para docs/api/openapi.yaml (modo rápido)
├── ExportadorDescricaoApi.java                    # NOVO (apoio de teste): verifica o conteúdo mínimo e grava de forma atômica
├── ExportadorDescricaoApiTest.java                # NOVO: JUnit puro — falta de operação/guia falha e não grava (FR-008)
├── DocumentacaoApiTest.java                       # NOVO: UI e /v3/api-docs públicos no modo rápido
└── DocumentacaoApiProducaoTest.java               # NOVO: 404 em prod (rotas de documentação e /webjars/swagger-ui/**),
                                                   #   anônimo e autenticado (Testcontainers)

docs/api/openapi.yaml                              # passa a ser GERADO (substituído na primeira execução)
specs/003-proprietario-login-isolamento/contracts/api-http.md   # + aviso: histórico; referência é o arquivo gerado
README.md                                          # §API: arquivo gerado, como regenerar, Swagger UI no modo rápido
AGENTS.md                                          # §1 (regenerar = ./mvnw test; URLs da UI) e §9 (springdoc acordado; só modo rápido)
.specify/memory/constitution.md                    # 1.0.3 PATCH: stack + Princípio IV
```

**Structure Decision**: projeto único existente. Todo o código novo fica em `adapters/`
(`config/` e `inbound/rest/`), seguindo a regra de que só a configuração encontra o Spring;
nenhum pacote novo. Os testes novos ficam no pacote raiz de teste, ao lado de `ModoRapidoTest` e
`SaudeAplicacaoTest`, porque testam o contexto da aplicação inteira, não um controller isolado.

### Ordem de implementação (insumo para `/speckit-tasks`)

1. **Validação (bloqueante)**: adicionar a dependência e confirmar que o contexto sobe em Java 25 e
   que `/v3/api-docs` responde no modo rápido (R1, R11). Falhou → parar e voltar ao dono.
2. **Testes vermelhos**: `DocumentacaoApiTest` e `DocumentacaoApiProducaoTest` (contrato
   documentacao-http); `DescricaoApiTest` com D1–D5. Ver falhar pelo motivo certo (401; operações
   ausentes; guia ausente).
3. **Exposição**: regra `permitAll` + propriedades por modo → `DocumentacaoApiTest`/`ProducaoTest` verdes.
4. **Configuração global e operações manuais**: `DocumentacaoApiConfiguration` → D1 (#3, #4, #8), D2, D4.
5. **Anotações nos controllers e records** → D1 (#1, #2, #5–#7), exemplos e respostas.
6. **Exportação**: gravação atômica; rodar e commitar o `docs/api/openapi.yaml` gerado.
7. **Governança e docs**: aviso em `api-http.md`, README, AGENTS.md, emenda da constitution.
8. **Quickstart**: cenários 1–6; `./mvnw test` completo.

## Complexity Tracking

| Violation | Why Needed | Simpler Alternative Rejected Because |
|---|---|---|
| Dependência nova (`springdoc-openapi-starter-webmvc-ui`) — Princípio IV / AGENTS.md §9 | Gerar a descrição a partir do código e servir a Swagger UI, ambos pedidos pelo dono; exceção acordada na avaliação | Option A (arquivo manual + teste de rotas) não regenera nem substitui o arquivo e não cobre campos/status; escrever um gerador próprio seria mais código para o mesmo fim |
| Rotas de documentação públicas no modo rápido (superfície além de `/actuator/health`) | Swagger UI navegável com a app rodando (US3); fechadas em `prod` | Exigir login na UI no modo rápido complicaria o uso local sem ganho, já que o modo rápido não é exposto |
