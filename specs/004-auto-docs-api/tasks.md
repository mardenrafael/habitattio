---

description: "Tasks da feature 004 — descrição da API derivada do código"
---

# Tasks: Descrição da API derivada do código

**Input**: `specs/004-auto-docs-api/` — [spec.md](./spec.md), [plan.md](./plan.md),
[research.md](./research.md), [data-model.md](./data-model.md),
[contracts/descricao-api.md](./contracts/descricao-api.md),
[contracts/documentacao-http.md](./contracts/documentacao-http.md), [quickstart.md](./quickstart.md)

**Tests**: obrigatórios. TDD é inegociável (constitution §II, AGENTS §3): toda tarefa de teste vem
antes da implementação que ela justifica e MUST ser vista falhando **pelo motivo esperado** antes
da tarefa de implementação. Teste visto falhar é read-only.

**Testes de guarda (vermelho por mutação)**: com a dependência no classpath (T001), alguns
comportamentos já existem antes de qualquer código desta feature (exclusões D3, ausência do
parâmetro do proprietário, estabilidade). Para cumprir a constitution §II, cada um desses testes
MUST ser visto vermelho por **mutação controlada**, como na feature 002 (T018): aplicar a mutação
indicada na tarefa, rodar, ver falhar pelo motivo esperado, **desfazer** a mutação e registrar no
relatório. Nenhum teste é declarado verde sem ter sido visto vermelho.

**Testes existentes**: nenhum pode ser alterado. Se algum quebrar (ex.: `@WebMvcTest` dos
controllers com as anotações ou a regra nova de segurança), **parar e reportar** ao dono.

**Fonte dos textos**: a T019 regrava `docs/api/openapi.yaml` com o arquivo gerado. Toda tarefa
que copia texto, exemplo ou descrição do arquivo manual usa a versão original e fixa:
`git show a0901d0:docs/api/openapi.yaml`.

**Organização**: por história de usuário (spec.md). US4 (prova automatizada, P2) depende do
exportador criado na US1; US3 (página no modo rápido e fechamento em `prod`) é independente das
outras. `DescricaoApiTest` obtém a descrição **autenticado** (`with(user("documentacao"))`), para
não depender da regra pública da US3; o conteúdo é o mesmo para anônimo.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: pode rodar em paralelo (arquivos diferentes, sem dependência de tarefa incompleta)
- **[Story]**: US1, US2, US3, US4 (spec.md)

## Path Conventions

- `M/` = `src/main/java/br/com/marden/habitattio/`
- `T/` = `src/test/java/br/com/marden/habitattio/`
- `R/` = `src/main/resources/`

Os caminhos nas tarefas são completos; os prefixos acima só abreviam esta legenda.

---

## Phase 1: Setup

**Purpose**: dependência no build e validação de compatibilidade (bloqueante; research R1, R11).

- [X] T001 Acrescentar a propriedade `<springdoc.version>3.2.0</springdoc.version>` e a dependência `org.springdoc:springdoc-openapi-starter-webmvc-ui` com `<version>${springdoc.version}</version>` (escopo padrão) em `pom.xml` — exceção acordada ao AGENTS §9 (plan, Complexity Tracking)
- [X] T002 Rodar `./mvnw test` e confirmar que a suíte existente continua verde com o springdoc no classpath em Java 25 (contexto sobe; nenhum teste existente quebra). Se algo quebrar por incompatibilidade, **parar e voltar ao dono** (fallback: Option A da avaliação). Registrar o resultado (e se os testes de integração rodaram, com Docker) no relatório da tarefa
- [X] T003 Validar a geração com a app rodando: `./mvnw spring-boot:run`; com `curl` e cookie jar, `GET /sessao` (pega `XSRF-TOKEN`) → `POST /proprietarios` → `POST /sessao` (formulário `email`/`senha`, cabeçalho `X-XSRF-TOKEN`) → `GET /v3/api-docs.yaml` com o cookie de sessão; confirmar 200 e que `openapi:` começa com `3.1` e as 5 operações de controller aparecem. Anotar se `@AuthenticationPrincipal` aparece como parâmetro (research R9). Nenhum arquivo é alterado nesta tarefa

**Checkpoint**: springdoc compatível; geração funcionando por trás do login.

---

## Phase 2: Foundational — a forma

**Purpose**: assinaturas sem lógica, para os testes compilarem (AGENTS §3, passo 1).

**⚠️ CRITICAL**: nenhuma história começa antes desta fase.

- [X] T004 [P] Criar `src/main/java/br/com/marden/habitattio/adapters/config/DocumentacaoApiConfiguration.java`: `@Configuration`, sem nenhum bean ainda, com Javadoc de uma linha dizendo que monta a descrição da API (research R2/R3)
- [X] T005 [P] Criar a classe de apoio de teste `src/test/java/br/com/marden/habitattio/ExportadorDescricaoApi.java` (final, sem Spring) com as assinaturas `static void verificar(io.swagger.v3.oas.models.OpenAPI descricao)` e `static void exportar(String yaml, java.nio.file.Path destino)`, ambas com corpo `throw new UnsupportedOperationException()`, e a constante `static final java.nio.file.Path DESTINO = java.nio.file.Path.of("docs", "api", "openapi.yaml")`

**Checkpoint**: tudo compila; nenhum comportamento novo.

---

## Phase 3: User Story 1 — Descrição sempre igual à API implementada (Priority: P1) 🎯 MVP

**Goal**: a suíte regrava `docs/api/openapi.yaml` com as 5 operações de controller, com segurança,
respostas e exemplos, servidor local fixo e geração estável.

**Independent Test**: `./mvnw test -Dtest=DescricaoApiTest` regrava o arquivo; acrescentar um campo
a `ImovelResponse` e rodar de novo faz o campo aparecer (quickstart, cenários 1 e 2).

### Testes para US1 (escrever primeiro, ver falhar)

Todos em `src/test/java/br/com/marden/habitattio/DescricaoApiTest.java`: `@SpringBootTest` +
`@AutoConfigureMockMvc`, sem profile (modo rápido, sem Docker), com um helper privado
`String obterYaml()` que faz `GET /v3/api-docs.yaml` com `with(user("documentacao"))` e exige 200,
e `OpenAPI obterDescricao()` que converte com `io.swagger.v3.core.util.Yaml.mapper().readValue(..., OpenAPI.class)`.
Arrange/Act/Assert separados por linha em branco; mensagens de falha citam a operação (`"POST /imoveis"`).

- [X] T006 [US1] Escrever `deveDescreverSoOServidorLocal` (D4: `servers` com exatamente um item, `url` = `http://localhost:8080`) e `deveUsarOpenApi31` (D4: `openapi` começa com `3.1`) em `src/test/java/br/com/marden/habitattio/DescricaoApiTest.java`. Esperado vermelho: servidor gerado da requisição (`http://localhost`)
- [X] T007 [US1] Escrever `deveMarcarSegurancaDasOperacoesDeController` em `src/test/java/br/com/marden/habitattio/DescricaoApiTest.java`: para cada linha #1, #2, #5, #6, #7 da tabela D1 de `contracts/descricao-api.md`, a operação existe e o conjunto de nomes em `security` é exatamente o da coluna "Segurança" (`POST /proprietarios`→{csrf}; `GET /sessao`→{sessao}; `POST /imoveis`→{sessao,csrf}; `GET /imoveis/{id}`→{sessao}; `POST /imoveis/{id}/inativacao`→{sessao,csrf}). Esperado vermelho: nenhuma marcação
- [X] T008 [US1] Escrever `deveDeclararAsRespostasDasOperacoesDeController` em `src/test/java/br/com/marden/habitattio/DescricaoApiTest.java`: códigos exatamente iguais à coluna "Respostas mínimas" de D1 para #1, #2, #5, #6, #7; 401 de `POST /imoveis`, `GET /imoveis/{id}` e inativação com `$ref` `#/components/responses/NaoAutenticado`; todo 403 com `$ref` `CsrfInvalido`; todo 404 com `$ref` `ImovelNaoEncontrado`; 400 de `{id}` com `$ref` `IdInvalido`; toda resposta de erro não referenciada com conteúdo `application/problem+json`. Esperado vermelho: respostas genéricas/ausentes
- [X] T009 [US1] Escrever `deveTerResumoTagEExemploNasOperacoesDeController` em `src/test/java/br/com/marden/habitattio/DescricaoApiTest.java`: `summary` e `tags` não vazios; `requestBody` de #1 e #5 com `example` não nulo; resposta 2xx de #1, #2, #5, #7 com `example` não nulo. Esperado vermelho: sem summary/exemplos
- [X] T010 [US1] Escrever os testes de guarda `naoDeveListarRotasInternas` (D3: nenhum caminho começa com `/v3/api-docs` ou `/swagger-ui`; único caminho sob `/actuator` é `/actuator/health` se existir; nenhum `/error`), `naoDeveExporOProprietarioComoParametro` (R9: nenhum parâmetro de nenhuma operação se chama `proprietario`) e `deveGerarDescricaoEstavel` (D4: duas chamadas a `obterYaml()` produzem texto idêntico) em `src/test/java/br/com/marden/habitattio/DescricaoApiTest.java`. Vermelho por mutação, uma de cada vez, desfeita logo depois: `naoDeveListarRotasInternas` — em `src/main/resources/application.properties`, `springdoc.show-actuator=true` e `management.endpoints.web.exposure.include=health,info` (aparece `/actuator/info`); `naoDeveExporOProprietarioComoParametro` — em `SessaoController.consultar`, trocar `@AuthenticationPrincipal` por `@RequestParam` (aparece o parâmetro `proprietario`); `deveGerarDescricaoEstavel` — `springdoc.cache.disabled=true` e, em `DocumentacaoApiConfiguration`, um `OpenApiCustomizer` temporário que grava `System.nanoTime()` em `info.description` (nome da propriedade de cache confirmado na T003)
- [X] T011 [US1] Escrever `deveExportarDescricaoParaDocsApi` em `src/test/java/br/com/marden/habitattio/DescricaoApiTest.java`: `yaml = obterYaml()`; `ExportadorDescricaoApi.verificar(Yaml.mapper().readValue(yaml, OpenAPI.class))`; `ExportadorDescricaoApi.exportar(yaml, ExportadorDescricaoApi.DESTINO)`; assert: `Files.readString(DESTINO)` igual a `yaml` (D5). Esperado vermelho: `UnsupportedOperationException`
- [X] T012 [US1] Rodar `./mvnw test -Dtest=DescricaoApiTest` e confirmar que T006–T009 e T011 falham **pelos motivos esperados** acima, e que os testes de T010 falham sob as mutações descritas em T010; desfazer as mutações e confirmar `git diff` vazio fora de `src/test/java/br/com/marden/habitattio/DescricaoApiTest.java`

### Implementação para US1

- [X] T013 [US1] Em `src/main/resources/application.properties`, acrescentar bloco comentado "Documentação da API (specs/004-auto-docs-api, research R4/R5)": `springdoc.api-docs.version=openapi_3_1`, `springdoc.writer-with-order-by-keys=true`, `springdoc.writer-with-default-pretty-printer=true`, `springdoc.override-with-generic-response=false`
- [X] T014 [US1] Em `src/main/java/br/com/marden/habitattio/adapters/config/DocumentacaoApiConfiguration.java`, criar o bean `OpenAPI descricaoApi()` com: `info.title` = `Habitattio API`, `info.version` = `0.0.1`; `servers` = um item `http://localhost:8080`, descrição `Local (modo rápido, H2)`; `components.securitySchemes` `sessao` (apiKey, cookie, `JSESSIONID`, descrição "Cookie de sessão emitido por `POST /sessao`.") e `csrf` (apiKey, header, `X-XSRF-TOKEN`, descrição "Valor do cookie `XSRF-TOKEN` (obtido em `GET /sessao`). Exigido em `POST`/`DELETE`."); `components.schemas` `ProblemDetail` (RFC 9457, required `status`,`title`,`detail`; `detail` "Mensagem em português."; propriedade `instance`); `components.responses` `NaoAutenticado` (401, "Autenticação necessária."), `CsrfInvalido` (403, "Token CSRF ausente ou inválido."), `ImovelNaoEncontrado` (404, "Imóvel não encontrado.", descrição "O imóvel não existe ou é de outro proprietário (indistinguíveis)."), `IdInvalido` (400, "Identificador de imóvel inválido."), todos `application/problem+json` com `$ref` para `ProblemDetail` e exemplo — textos e exemplos copiados de `git show a0901d0:docs/api/openapi.yaml` (data-model, tabela de componentes)
- [X] T015 [P] [US1] Anotar `src/main/java/br/com/marden/habitattio/adapters/inbound/rest/ProprietarioController.java`: `@Tag(name = "Proprietários", description = "Cadastro de quem é dono dos imóveis (públicas).")`; `@Operation(operationId = "cadastrarProprietario", summary = "Cadastrar proprietário", description = <texto de `git show a0901d0:docs/api/openapi.yaml`>, security = @SecurityRequirement(name = "csrf"))`; `@ApiResponse` 201 (sem `Location`), 400 e 409 com `application/problem+json` e exemplos de `git show a0901d0:docs/api/openapi.yaml`, 403 com `ref = "CsrfInvalido"`; `@Schema` nos componentes dos records: `CadastrarProprietarioRequest` (`nome` minLength 1 maxLength 100; `email` format email maxLength 254, "Normalizado para minúsculas e sem espaços nas pontas."; `senha` format password minLength 8, "No mínimo 8 caracteres e no máximo 72 bytes em UTF-8."; exemplo `Ana Souza`/`ana@exemplo.com`/`segredo123`) e `ProprietarioResponse` (exemplo id 1). Só anotações: nenhuma linha de lógica muda
- [X] T016 [P] [US1] Anotar `src/main/java/br/com/marden/habitattio/adapters/inbound/rest/SessaoController.java`: `@Tag(name = "Sessão", description = "Login, logout e obtenção do token CSRF.")`; `@Operation(operationId = "consultarSessao", summary = "Quem está autenticado", description = <texto de `git show a0901d0:docs/api/openapi.yaml`: também obtém o cookie XSRF-TOKEN mesmo com 401>, security = @SecurityRequirement(name = "sessao"))`; `@ApiResponse` 200 (exemplo id 1, Ana Souza) e 401 próprio ("Sem sessão. Traz o cookie `XSRF-TOKEN`.", `application/problem+json`, detail "Autenticação necessária."); `@Schema` no record `SessaoResponse`. Se T003 mostrou o principal como parâmetro, acrescentar `@Parameter(hidden = true)`
- [X] T017 [P] [US1] Anotar `src/main/java/br/com/marden/habitattio/adapters/inbound/rest/ImovelController.java`: `@Tag(name = "Imóveis", description = "Imóveis do proprietário autenticado.")`; `cadastrar` (`cadastrarImovel`, sessao+csrf, 201 com cabeçalho `Location` `/imoveis/{id}` e exemplo id 7 `Apto 101`/`Rua A, 10`/1500.00/ativo true, 400 e 409 com exemplos de `git show a0901d0:docs/api/openapi.yaml`, 401 `NaoAutenticado`, 403 `CsrfInvalido`); `consultar` (`consultarImovel`, sessao, 200, 400 `IdInvalido`, 401, 404 `ImovelNaoEncontrado`); `inativar` (`inativarImovel`, sessao+csrf, "Sem corpo. Idempotente — inativar um imóvel já inativo responde 200.", 200 exemplo `valorSugerido` 1500.0000 ativo false, 400, 401, 403, 404); `@Schema` em `CadastrarImovelRequest` (`identificador` minLength 1 maxLength 50 "Único por proprietário, sem distinguir maiúsculas de minúsculas."; `endereco` minLength 1 maxLength 255; `valorSugerido` exclusiveMinimum 0 "Valor monetário, estritamente positivo.") e `ImovelResponse` (`valorSugerido`: "No cadastro (201) volta com a escala enviada; na consulta e na inativação, com 4 casas decimais (por exemplo 1500.0000)."). Só anotações
- [X] T018 [US1] Implementar `ExportadorDescricaoApi.exportar` em `src/test/java/br/com/marden/habitattio/ExportadorDescricaoApi.java`: grava num arquivo temporário no **mesmo diretório** do destino (`Files.createTempFile(destino.getParent(), "openapi", ".yaml")`) e move com `Files.move(tmp, destino, ATOMIC_MOVE, REPLACE_EXISTING)` (D5, research R6). `verificar` fica com **corpo vazio**: as checagens são da US4, guiadas pelos testes vermelhos de T035–T037
- [X] T019 [US1] Rodar `./mvnw test -Dtest=DescricaoApiTest` até ficar verde; conferir `git diff docs/api/openapi.yaml` (o arquivo manual é substituído pelo gerado) e rodar uma segunda vez para confirmar diff vazio (SC-005)
- [X] T020 [US1] Rodar os `@WebMvcTest` existentes (`./mvnw test -Dtest='ImovelControllerTest,ProprietarioControllerTest,SessaoControllerTest'`) e confirmar verdes sem alteração; se quebrarem, parar e reportar

**Checkpoint**: US1 entregue — o arquivo é gerado pela suíte, com as operações de controller completas.

---

## Phase 4: User Story 2 — Fluxo de sessão e CSRF utilizável só com a descrição (Priority: P1)

**Goal**: as 3 operações fora de controller e o guia de sessão/CSRF entram na descrição.

**Independent Test**: importar `docs/api/openapi.yaml` num API client vazio e seguir o fluxo
cadastro → CSRF → entrar → imóvel → sair só com a descrição (quickstart, cenário 4, passo 3).

### Testes para US2 (escrever primeiro, ver falhar)

- [X] T021 [US2] Escrever `deveDescreverEntrarESairComoNoContrato` em `src/test/java/br/com/marden/habitattio/DescricaoApiTest.java`: `POST /sessao` com security {csrf}, `requestBody` com conteúdo `application/x-www-form-urlencoded` cujo schema (direto ou `$ref` `EntrarForm`) exige `email` e `senha`, respostas exatamente 204, 401, 403 (403 com `$ref` `CsrfInvalido`; 401 próprio com detail "E-mail ou senha inválidos."); `DELETE /sessao` com security {sessao,csrf}, respostas 204, 401 (`NaoAutenticado`), 403 (`CsrfInvalido`); ambas com tag `Sessão`; o conteúdo `application/x-www-form-urlencoded` de `POST /sessao` tem `example` não nulo. Esperado vermelho: operações ausentes
- [X] T022 [US2] Escrever `deveDescreverSaudePublica` em `src/test/java/br/com/marden/habitattio/DescricaoApiTest.java`: `GET /actuator/health` com `security` vazia (`[]`), tag `Saúde`, respostas 200 e 503 com conteúdo `application/vnd.spring-boot.actuator.v3+json` e schema `Saude` (`status` enum `UP`, `DOWN`, `OUT_OF_SERVICE`); as respostas 200 e 503 têm `example` não nulo. Esperado vermelho: operação ausente
- [X] T023 [US2] Escrever `deveTrazerOGuiaDeSessaoECsrf` em `src/test/java/br/com/marden/habitattio/DescricaoApiTest.java`: `info.description` contém `GET /sessao`, `XSRF-TOKEN`, `X-XSRF-TOKEN`, `novo` + `XSRF-TOKEN` (aviso do login), `30 min`, `application/problem+json`, `404`, `./mvnw test` e a indicação de que o arquivo é gerado e é a referência do contrato (D2). Esperado vermelho: `info.description` nula
- [X] T024 [US2] Rodar `./mvnw test -Dtest=DescricaoApiTest` e confirmar T021–T023 vermelhos pelos motivos esperados

### Implementação para US2

- [X] T025 [US2] Em `src/main/java/br/com/marden/habitattio/adapters/config/DocumentacaoApiConfiguration.java`, colocar em `info.description` (text block) o guia de `info.description` em `git show a0901d0:docs/api/openapi.yaml` (passos 1–4, cookie jar, `Secure` em `prod`, `problem+json`, 404 de outro proprietário), trocando a primeira frase por: arquivo **gerado** pela suíte (`./mvnw test`), não editar à mão, e é a referência do contrato HTTP (FR-012); acrescentar as tags `Saúde` ("Endpoint operacional do Actuator.") e os schemas `EntrarForm` (required `email` format email, `senha` format password) e `Saude`
- [X] T026 [US2] Em `src/main/java/br/com/marden/habitattio/adapters/config/DocumentacaoApiConfiguration.java`, criar o bean `OpenApiCustomizer` que acrescenta à descrição: `POST /sessao` (`entrar`, "Entrar (login)", texto de `git show a0901d0:docs/api/openapi.yaml`: formulário, e-mail em qualquer caixa, troca do id da sessão e novo `XSRF-TOKEN`; corpo do formulário com exemplo `email: ana@exemplo.com`, `senha: segredo123`; 204 com `Set-Cookie`, 401 "E-mail inexistente ou senha errada (indistinguíveis).", 403 `CsrfInvalido`); `DELETE /sessao` (`sair`, "Sair (logout)", "Invalida a sessão; requisições seguintes com o mesmo cookie recebem 401."); `GET /actuator/health` (`verificarSaude`, "Saúde da aplicação", "Pública. Só o estado geral; o Redis não entra no cálculo.", `security` vazia, 200 exemplo `status: UP`, 503 "Banco inacessível ou fora de serviço." exemplo `status: DOWN`) — sem alterar `SegurancaConfiguration` (research R2)
- [X] T027 [US2] Rodar `./mvnw test -Dtest=DescricaoApiTest` até verde; conferir no `git diff docs/api/openapi.yaml` as 3 operações novas e o guia

**Checkpoint**: US1 + US2 — a descrição gerada cobre as 8 operações e o fluxo completo.

---

## Phase 5: User Story 3 — Navegar pela documentação com a app rodando (Priority: P2)

**Goal**: UI e descrição públicas no modo rápido; nada da documentação online em `prod`.

**Independent Test**: `./mvnw test -Dtest='DocumentacaoApiTest,DocumentacaoApiProducaoTest'`
(o segundo exige Docker); quickstart, cenários 4 e 5.

### Testes para US3 (escrever primeiro, ver falhar)

- [X] T028 [P] [US3] Criar `src/test/java/br/com/marden/habitattio/DocumentacaoApiTest.java` (`@SpringBootTest` + `@AutoConfigureMockMvc`, sem profile, no estilo de `SaudeAplicacaoTest`) com, **anônimo**: `deveServirADescricaoSemLogin` (`GET /v3/api-docs` → 200 JSON), `deveServirADescricaoEmYamlSemLogin` (`GET /v3/api-docs.yaml` → 200), `deveRedirecionarParaAPaginaSemLogin` (`GET /swagger-ui.html` → 3xx), `deveServirAPaginaSemLogin` (`GET /swagger-ui/index.html` → 200), `deveEnviarOTokenCsrfNasChamadasDaPagina` (`GET /swagger-ui/swagger-initializer.js` → 200 e o corpo contém `requestInterceptor`, `XSRF-TOKEN` e `X-XSRF-TOKEN` — trecho injetado por `AbstractSwaggerIndexTransformer.addCSRF` no springdoc 3.2.0) — contrato `contracts/documentacao-http.md`, coluna "Modo rápido". Esperado vermelho: 401
- [X] T029 [P] [US3] Criar `src/test/java/br/com/marden/habitattio/DocumentacaoApiProducaoTest.java` (`@SpringBootTest` + `@AutoConfigureMockMvc` + `@ActiveProfiles("prod")` + `@Import(TestcontainersConfiguration.class)`, como `SessaoIntegracaoTest`) com: `deveResponder404NaDocumentacaoParaAnonimo` e `deveResponder404NaDocumentacaoParaAutenticado` (`with(user("ana"))`) para `/v3/api-docs`, `/v3/api-docs.yaml`, `/swagger-ui.html`, `/swagger-ui/index.html`; e `deveTratarArquivosDaPaginaComoRotaInexistente`: para anônimo e autenticado, o status de `GET /webjars/swagger-ui/index.html` é igual ao de `GET /rota-que-nao-existe` (401 anônimo, 404 autenticado). Esperado vermelho: anônimo 401 (antes da T031) e autenticado 200/redirect nas rotas do springdoc; arquivo estático servido (200) ao autenticado
- [X] T030 [US3] Rodar `./mvnw test -Dtest='DocumentacaoApiTest,DocumentacaoApiProducaoTest'` e confirmar vermelhos pelos motivos esperados (sem Docker, registrar que o de produção não rodou); `deveEnviarOTokenCsrfNasChamadasDaPagina` vermelho com 401 antes da T031 e, depois da T031 e antes da T032, sem `requestInterceptor`

### Implementação para US3

- [X] T031 [US3] Em `src/main/java/br/com/marden/habitattio/adapters/config/SegurancaConfiguration.java`, acrescentar ao `authorizeHttpRequests`, antes de `anyRequest()`: `.requestMatchers(HttpMethod.GET, "/v3/api-docs", "/v3/api-docs.yaml", "/v3/api-docs/**", "/swagger-ui.html", "/swagger-ui/**").permitAll()` — nenhuma outra regra muda (FR-011)
- [X] T032 [US3] Em `src/main/resources/application.properties`, acrescentar `springdoc.swagger-ui.csrf.enabled=true` ao bloco da documentação (research R8)
- [X] T033 [US3] Em `src/main/resources/application-prod.properties`, acrescentar bloco comentado "Documentação da API: nada online em produção; a descrição vive só no repositório (specs/004-auto-docs-api, research R7)": `springdoc.api-docs.enabled=false`, `springdoc.swagger-ui.enabled=false`, `spring.web.resources.add-mappings=false`
- [X] T034 [US3] Rodar `./mvnw test -Dtest='DocumentacaoApiTest,DocumentacaoApiProducaoTest,SaudeAplicacaoTest,SessaoIntegracaoTest,IsolamentoProprietariosIntegracaoTest'` até verde (o desligamento de recursos estáticos em `prod` não pode afetar as rotas existentes); a T032 só é considerada feita com `deveEnviarOTokenCsrfNasChamadasDaPagina` verde

**Checkpoint**: US3 entregue — UI no modo rápido; `prod` fechado.

---

## Phase 6: User Story 4 — Prova automatizada de que a descrição está completa (Priority: P2)

**Goal**: o exportador recusa descrição incompleta com mensagem clara e não grava nada (FR-008).

**Independent Test**: `./mvnw test -Dtest=ExportadorDescricaoApiTest` (JUnit puro, sem Spring);
quickstart, cenário 3.

### Testes para US4 (escrever primeiro, ver falhar)

Em `src/test/java/br/com/marden/habitattio/ExportadorDescricaoApiTest.java`, JUnit 5 puro.
A descrição de referência é lida de `docs/api/openapi.yaml` já gerado (`Yaml.mapper()`), e cada
teste remove/altera **uma** coisa numa cópia; a gravação usa `@TempDir`.

- [ ] T035 [US4] Escrever `deveRecusarDescricaoSemUmaOperacao` (`@ParameterizedTest` sobre as 8 operações de D1, no formato `"DELETE /sessao"`: remover a operação da cópia → `AssertionError` cuja mensagem contém a operação) em `src/test/java/br/com/marden/habitattio/ExportadorDescricaoApiTest.java`. Esperado vermelho: `verificar` vazio não lança nada. (A aceitação da descrição real já é provada pela T011, que chama `verificar` sobre ela.)
- [ ] T036 [US4] Escrever `deveRecusarOperacaoProtegidaDescritaComoPublica` (zerar `security` de `POST /imoveis` → falha citando `POST /imoveis`), `deveRecusarOperacaoPublicaDescritaComoProtegida` (pôr {sessao} em `GET /actuator/health` → falha) e `deveRecusarDescricaoSemOGuia` (`info.description` nula → falha citando o guia) em `src/test/java/br/com/marden/habitattio/ExportadorDescricaoApiTest.java`. Esperado vermelho: `verificar` vazio
- [ ] T037 [US4] Escrever `naoDeveGravarQuandoAVerificacaoFalha` em `src/test/java/br/com/marden/habitattio/ExportadorDescricaoApiTest.java`: num `@TempDir`, arquivo `openapi.yaml` com conteúdo `antigo`; chamar o fluxo de `DescricaoApiTest` (`verificar` e depois `exportar`) com uma descrição sem `GET /sessao`; assert: `AssertionError` lançado e o arquivo continua `antigo`, sem temporários sobrando no diretório. Esperado vermelho: `verificar` vazio deixa gravar, e o arquivo vira o YAML incompleto
- [ ] T038 [US4] Rodar `./mvnw test -Dtest=ExportadorDescricaoApiTest` e confirmar vermelhos pelos motivos esperados

### Implementação para US4

- [ ] T039 [US4] Implementar `ExportadorDescricaoApi.verificar` em `src/test/java/br/com/marden/habitattio/ExportadorDescricaoApi.java` com as 8 operações e a marcação exata de D1 (incluindo #3, #4, #8 e operações públicas sem `security`), o guia de D2 (mesmos marcadores de T023) e os esquemas `sessao`/`csrf`; acumular todas as faltas numa única `AssertionError` (uma linha por falta)
- [ ] T040 [US4] Rodar `./mvnw test -Dtest='ExportadorDescricaoApiTest,DescricaoApiTest'` até verde; executar o quickstart cenário 3 (comentar `DELETE /sessao` no customizer → suíte vermelha e arquivo intacto; desfazer)

**Checkpoint**: todas as histórias entregues.

---

## Phase 7: Polish & Cross-Cutting

- [ ] T041 [P] Acrescentar no topo de `specs/003-proprietario-login-isolamento/contracts/api-http.md` um aviso: histórico da feature 003; a referência do contrato HTTP é `docs/api/openapi.yaml`, gerado pela suíte (`./mvnw test`) a partir da feature 004 (FR-012)
- [ ] T042 [P] Atualizar a seção API de `README.md`: `docs/api/openapi.yaml` é gerado (não editar), é a referência do contrato, regenera com `./mvnw test`; Swagger UI em `http://localhost:8080/swagger-ui.html` só no modo rápido; em `prod` nada da documentação fica online; a coleção Bruno continua manual
- [ ] T043 [P] Atualizar `AGENTS.md`: §1 — regenerar a descrição é rodar `./mvnw test` (`DescricaoApiTest`), URLs da UI no modo rápido; §9 — springdoc-openapi entrou por acordo na feature 004; documentação (`/v3/api-docs`, `/swagger-ui`) só no modo rápido, desligada em `prod` com recursos estáticos desligados
- [ ] T044 Rodar `/speckit-constitution` com a emenda PATCH 1.0.2 → **1.0.3** (Governance (b)), arquivo `.specify/memory/constitution.md`: novo bloco no Sync Impact Report (feature 004, task T044; springdoc acrescentado à stack com acordo do dono; seções modificadas: Stack Tecnológico e Restrições e lista do Princípio IV; AGENTS.md §1/§9 sincronizados em T043); acrescentar à lista da stack "springdoc-openapi (descrição da API e Swagger UI só no modo rápido; desligado em `prod`, acordado na feature 004)" e à lista do Princípio IV; atualizar a linha `**Version**: 1.0.3 | ... | **Last Amended**: 2026-11-02`
- [ ] T045 Conferir a arquitetura: `grep -rn "io.swagger\|springdoc" src/main/java/br/com/marden/habitattio/domain src/main/java/br/com/marden/habitattio/application src/main/java/br/com/marden/habitattio/ports` não retorna nada (FR-013)
- [ ] T046 Executar `specs/004-auto-docs-api/quickstart.md` cenários 1, 2 e 4 (incluindo o fluxo no API client e o "Try it out" opcional, registrando se funcionou ou se a UI fica só de leitura)
- [ ] T047 Rodar `./mvnw test` completo (com Docker) e registrar no relatório: total de testes, se os de integração rodaram, e que nenhum teste existente foi alterado. Commitar `docs/api/openapi.yaml` gerado junto com o código

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (T001–T003)**: sem dependências; T002/T003 bloqueiam tudo (compatibilidade).
- **Foundational (T004–T005)**: depende do Setup; bloqueia as histórias.
- **US1 (T006–T020)**: depende da Foundational.
- **US2 (T021–T027)**: depende da US1 (mesmo `DescricaoApiTest` e mesmo `DocumentacaoApiConfiguration`; usa os componentes compartilhados de T014).
- **US3 (T028–T034)**: depende só da Foundational — pode rodar em paralelo com US1/US2 (arquivos diferentes, exceto `application.properties`: T032 depois de T013 se em paralelo).
- **US4 (T035–T040)**: depende da US2 (a descrição de referência precisa das 8 operações) e de T018.
- **Polish (T041–T047)**: depois de todas as histórias; T041–T043 em paralelo; T044 depois de T043.

### Within Each User Story

- Testes → ver falhar (T012, T024, T030, T038) → implementação → verde.
- Em US1: T013/T014 antes de T015–T017 (as anotações referenciam respostas compartilhadas); T018 antes de T019.

### Parallel Opportunities

- T004 ‖ T005.
- T015 ‖ T016 ‖ T017 (controllers diferentes).
- T028 ‖ T029 (classes de teste diferentes); US3 inteira ‖ US1.
- T041 ‖ T042 ‖ T043.

---

## Parallel Example: User Story 1

```bash
# Depois de T013/T014, anotações em paralelo:
Task: "T015 Anotar ProprietarioController"
Task: "T016 Anotar SessaoController"
Task: "T017 Anotar ImovelController"
```

## Parallel Example: User Story 3 (junto com a US1)

```bash
Task: "T028 DocumentacaoApiTest (modo rápido)"
Task: "T029 DocumentacaoApiProducaoTest (prod, Testcontainers)"
```

---

## Implementation Strategy

### MVP

Setup + Foundational + **US1**: o arquivo passa a ser gerado com as 5 operações de controller.
Ainda **não** substitui o manual com segurança (faltam login/logout/saúde e guia), por isso não
commitar `docs/api/openapi.yaml` gerado antes da US2. Na US1, o exportador ainda não recusa
descrição incompleta (a guarda chega na US4).

### Incremental

1. US1 → US2: descrição completa (8 operações + guia); primeiro commit do arquivo gerado.
2. US3: UI no modo rápido e `prod` fechado (pode vir antes, em paralelo).
3. US4: guarda contra descrição incompleta.
4. Polish: referência do contrato, README, AGENTS, constitution, suíte completa.

## Notes

- Nenhum teste existente é alterado; quebrou → parar e reportar (AGENTS §3).
- Anotações só em `adapters/`; `domain/`, `application/`, `ports/` intocados (T045).
- O arquivo `docs/api/openapi.yaml` nunca é editado à mão a partir de T019.
- "Try it out" com CSRF é desejável, não obrigatório (spec, Assumptions).
