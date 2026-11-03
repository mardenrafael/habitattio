# Research: Descrição da API derivada do código

**Feature**: `004-auto-docs-api` | **Date**: 2026-11-02 | **Plan**: [plan.md](plan.md)

Resolve as incertezas técnicas herdadas da avaliação (`.specify/assessments/auto-docs-api/research.md`,
itens UNVERIFIED) e as escolhas de desenho do plan. Fontes conferidas nesta etapa: Maven Central
(`maven-metadata.xml` e POM pai do springdoc 3.2.0) e o código-fonte da tag `v3.2.0` no GitHub
(`SpringDocConfigProperties`, `SwaggerUiConfigProperties`, `Constants`).

---

## R1 — Gerador e versão

- **Decision**: `org.springdoc:springdoc-openapi-starter-webmvc-ui`, versão **3.2.0**, fixada numa
  propriedade do `pom.xml` (`springdoc.version`). Um único artefato traz o gerador e a Swagger UI.
- **Rationale**: 3.2.0 é a `<release>` do Maven Central (atualizada em 2026-10-24) e o POM pai
  dela herda de `spring-boot-starter-parent` **4.1.2**, exatamente a versão do projeto; o risco
  de compatibilidade da avaliação cai de "não verificado" para "mesmo Boot". Java 25 continua sem
  verificação direta: a task de validação (Fase 1 das tasks) sobe o contexto com a dependência
  antes de qualquer outra coisa.
- **Alternatives considered**: `springdoc-openapi-starter-webmvc-api` (sem UI) + UI à parte —
  rejeitado, a UI foi pedida e seriam duas peças; escrever o gerador à mão — rejeitado, é a
  Option A disfarçada; `springdoc-openapi-maven-plugin` — rejeitado na avaliação (Option C).

## R2 — Operações que não são controllers (`POST /sessao`, `DELETE /sessao`, `GET /actuator/health`)

- **Decision**: descrever as três **à mão**, num `OpenApiCustomizer` em
  `adapters/config/DocumentacaoApiConfiguration`, com o mesmo conteúdo do contrato da feature 003
  (corpo `application/x-www-form-urlencoded` com `email`/`senha`, respostas 204/401/403;
  logout 204/401/403; saúde 200/503 com `application/vnd.spring-boot.actuator.v3+json`).
- **Rationale**: são três operações estáveis, ditadas pela configuração de segurança e pelo
  Actuator, e o teste de exportação (R6) falha se alguma sumir. Montar à mão dá controle total
  do texto em português e dos status reais.
- **Alternatives considered**:
  - `springdoc.show-login-endpoint=true` — gera uma operação de login genérica, pensada para
    `username`/`password`; não descreve CSRF, o 204, nem o logout. Rejeitado.
  - `springdoc.show-actuator=true` — publica o Actuator com o esquema genérico dele e abre
    caminho para outros endpoints aparecerem; o projeto expõe só `health` sem detalhes. Rejeitado.
  - Criar controllers falsos só para documentação — viola "não mudar rotas" (FR-011). Rejeitado.

## R3 — Onde fica o texto explicativo

- **Decision**:
  - **Por operação** (resumo, descrição, respostas e exemplos das 5 operações de controller):
    anotações `io.swagger.v3.oas.annotations` (`@Tag`, `@Operation`, `@ApiResponse`,
    `@SecurityRequirement`) nos controllers e `@Schema` nos componentes dos records
    `*Request`/`*Response` aninhados.
  - **Global** (título, guia de sessão/CSRF em `info.description`, servidor, esquemas de
    segurança `sessao`/`csrf`, respostas compartilhadas `NaoAutenticado`, `CsrfInvalido`,
    `ImovelNaoEncontrado`, `IdInvalido`, esquemas `ProblemDetail`/`EntrarForm`/`Saude`) e as
    operações de R2: bean `OpenAPI` + `OpenApiCustomizer` em
    `adapters/config/DocumentacaoApiConfiguration`, com o guia num text block Java.
- **Rationale**: o texto de cada operação fica ao lado do código que ele descreve, então mudar o
  controller e o texto acontece no mesmo diff. Tudo está em `adapters/`; `domain/`,
  `application/` e `ports/` não ganham nenhum import (FR-013, Princípio I).
- **Alternatives considered**: um arquivo YAML "base" mesclado à geração — seria de novo um
  arquivo de documentação editado à mão (SC-002). Rejeitado. Recurso de texto em
  `src/main/resources` para o guia — possível, mas um text block mantém tudo num só lugar; fica
  como refatoração se o guia crescer.

## R4 — Respostas de erro por operação

- **Decision**: `springdoc.override-with-generic-response=false`, com respostas declaradas
  explicitamente em cada operação (via `@ApiResponse` com `ref` para as respostas
  compartilhadas de R3).
- **Rationale**: com o padrão (`null` → ligado), o springdoc anexa a **todas** as operações cada
  `@ExceptionHandler` do `TratamentoErrosHandler` (409, 404, 403…), o que contradiz o contrato
  (ex.: `GET /sessao` nunca devolve 409).
- **Alternatives considered**: manter o padrão e limpar no customizer — mais código para o mesmo
  efeito. Rejeitado.

## R5 — Geração estável e servidor fixo (FR-009)

- **Decision**: `springdoc.writer-with-order-by-keys=true`,
  `springdoc.writer-with-default-pretty-printer=true`, versão OpenAPI 3.1 (padrão do 3.2.0,
  `OpenApiVersion.OPENAPI_3_1`, declarada mesmo assim para não depender do padrão) e `servers`
  declarado no bean `OpenAPI` como `http://localhost:8080` — "Local (modo rápido, H2)".
- **Rationale**: ordenar chaves elimina variação de ordem entre execuções; com `servers`
  declarado, o springdoc não usa a URL da requisição (no teste seria `http://localhost` ou uma
  porta efêmera).
- **Alternatives considered**: pós-processar o YAML no teste para ordenar e trocar o servidor —
  esconderia o problema do que a aplicação serve em `/v3/api-docs`. Rejeitado.

## R6 — Como o teste exporta e verifica (FR-007, FR-008)

- **Decision**: `DescricaoApiTest` (pacote raiz de teste, ao lado de `ModoRapidoTest`),
  `@SpringBootTest` + `@AutoConfigureMockMvc` **sem profile** (modo rápido, H2, sem Docker nem
  Redis):
  1. obtém `GET /v3/api-docs.yaml` autenticado (`with(user("documentacao"))`), para não depender
     da regra pública da US3 — o conteúdo é o mesmo que o anônimo recebe no modo rápido;
  2. converte com `io.swagger.v3.core.util.Yaml.mapper()` para `io.swagger.v3.oas.models.OpenAPI`
     (vem do swagger-core, que o springdoc já traz — nenhuma dependência extra);
  3. testes separados verificam o conteúdo mínimo do [contrato da descrição](contracts/descricao-api.md):
     as 8 operações, a marcação `sessao`/`csrf` de cada uma, o guia, as exclusões, o servidor e a
     estabilidade (duas leituras iguais);
  4. a verificação e a gravação ficam na classe de apoio de teste `ExportadorDescricaoApi`
     (testável sem Spring em `ExportadorDescricaoApiTest`): o teste de exportação chama
     `verificar` antes de `exportar`, que grava em `docs/api/openapi.yaml` por arquivo temporário +
     `Files.move(ATOMIC_MOVE, REPLACE_EXISTING)`. Se a verificação falha, nada é gravado, seja qual
     for a ordem dos métodos.
- **Rationale**: a suíte normal (`./mvnw test`) passa a ser o comando de regeneração; o diretório
  de trabalho do Surefire é a raiz do projeto, então o caminho relativo é estável. O modo rápido
  sobe sem Redis acessível — `ModoRapidoTest` já faz isso hoje.
- **Operações novas**: a verificação usa "contém as 8" e não "exatamente 8": um controller novo
  aparece no arquivo sem tocar no teste (SC-002); a lista só é atualizada quando se quer
  **garantir** a operação nova.
- **Alternatives considered**: comparar com o arquivo commitado e falhar se diferir (modo
  "check") — bom para CI, mas obrigaria dois modos; o `git diff` pós-suíte já cumpre o papel
  (fora do escopo, sem CI). Rejeitado por ora.

## R7 — Exposição por modo (FR-010)

- **Decision**:
  - Cadeia de segurança: `permitAll` para `GET /v3/api-docs`, `/v3/api-docs/**`,
    `/swagger-ui.html` e `/swagger-ui/**` em `SegurancaConfiguration`, valendo nos dois modos.
  - `application-prod.properties`: `springdoc.api-docs.enabled=false`,
    `springdoc.swagger-ui.enabled=false` e **`spring.web.resources.add-mappings=false`**. Sem os
    beans do springdoc, as rotas de documentação não existem; sem o mapeamento de recursos
    estáticos, `/webjars/**` (onde o Spring Boot serviria os arquivos da Swagger UI do classpath)
    também não. Tudo responde **404** pelo tratamento padrão (`/error` já é público), para
    anônimo e autenticado.
- **Rationale**: uma só regra de segurança, e o "fechado em `prod`" fica num único lugar, o arquivo
  do profile, sem `@Profile` em código. Responder 404 está alinhado com a clarificação ("como
  inexistentes"). Desligar o mapeamento estático em `prod` não tem efeito colateral: a aplicação é
  só API REST e não tem `static/`, `public/` nem `templates/` (conferido em `src/main/resources`).
  Decisão do dono (2026-11-02): os arquivos da Swagger UI não ficam online em `prod`; a descrição
  vive só no repositório.
- **Alternatives considered**:
  - Liberar só no modo rápido via `@Profile` na configuração de segurança — duas cadeias para
    manter. Rejeitado.
  - `denyAll` para `/webjars/swagger-ui/**` só em `prod` — exigiria regra condicional por profile
    na cadeia de segurança e responderia 403 (revela que existe), não 404. Rejeitado.
  - Tirar o webjar da Swagger UI do jar empacotado (exclusão no `spring-boot-maven-plugin`) — a
    mesma imagem roda nos dois modos (`docker run` sem profile é o modo rápido, AGENTS.md §1), e a
    UI quebraria ali; os bytes no jar não ficam online. Rejeitado.

## R8 — "Try it out" com CSRF na Swagger UI

- **Decision**: `springdoc.swagger-ui.csrf.enabled=true` no modo rápido. Os padrões do 3.2.0 são
  cookie `XSRF-TOKEN` e cabeçalho `X-XSRF-TOKEN` (`Constants.CSRF_DEFAULT_*`), os mesmos do
  `csrf.spa()` do projeto. O fluxo pela UI é: executar `GET /sessao` (recebe o cookie), depois
  os `POST`/`DELETE`.
- **Rationale**: custo de uma propriedade; dentro do apetite. A configuração é provada por teste
  automatizado (`DocumentacaoApiTest#deveEnviarOTokenCsrfNasChamadasDaPagina`): com a propriedade,
  o springdoc injeta em `/swagger-ui/swagger-initializer.js` um `requestInterceptor` que copia o
  cookie `XSRF-TOKEN` para o cabeçalho `X-XSRF-TOKEN` (`AbstractSwaggerIndexTransformer.addCSRF`,
  conferido no código da tag `v3.2.0`). O funcionamento ponta a ponta no navegador continua
  manual (quickstart, cenário 4); se não funcionar, a UI fica só de leitura, como a spec admite.
- **Alternatives considered**: interceptor JavaScript próprio na UI — fora do apetite. Rejeitado.

## R9 — `@AuthenticationPrincipal` nos parâmetros

- **Decision**: confiar no suporte a Spring Security do springdoc, que ignora parâmetros
  `@AuthenticationPrincipal`; a verificação "nenhuma operação tem parâmetro `proprietario`" entra
  no contrato da descrição. Se a task de validação mostrar o contrário, usar
  `@Parameter(hidden = true)` no parâmetro.
- **Rationale**: sem isso, `ProprietarioAutenticado` vazaria como parâmetro de consulta.

## R10 — Referência do contrato e governança (FR-012, FR-014)

- **Decision**:
  - `specs/003-proprietario-login-isolamento/contracts/api-http.md` ganha um aviso no topo:
    histórico da feature 003; a referência é `docs/api/openapi.yaml`, gerado pela suíte.
  - `README.md` (§API) e `AGENTS.md` (§1 comandos, §9 limites) apontam para o arquivo gerado,
    dizem que ele se regenera com `./mvnw test` e registram a dependência e as rotas de
    documentação (só no modo rápido).
  - Constitution **1.0.2 → 1.0.3 (PATCH)**: "Stack Tecnológico e Restrições" e a lista do
    Princípio IV ganham o springdoc-openapi (acordado na feature 004; documentação só no modo
    rápido). Nenhum princípio ou regra MUST muda, como nas emendas das features 002 e 003.
  - A coleção Bruno (`docs/api/habitattion-api/`) não muda.

## R11 — Riscos que seguem abertos

| Risco | Mitigação |
|---|---|
| Java 25 com springdoc 3.2.0 / swagger-core 2.2.55 | Primeira task: subir o contexto com a dependência e obter `/v3/api-docs` antes de qualquer outra. Falhando, parar e voltar ao dono (fallback: Option A da avaliação). |
| O springdoc resolver `BigDecimal`, records aninhados ou `ResponseEntity<...>` de forma diferente do arquivo manual | Aceito (a spec não exige reproduzir literalmente o YAML); o contrato da descrição fixa só o essencial. |
| Ordem dos métodos de teste | A exportação revalida antes de gravar (R6); a ordem não importa. |
