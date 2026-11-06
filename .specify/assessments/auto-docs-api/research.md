# Idea Research: Geração automática da especificação OpenAPI verificada no CI

- **Slug**: auto-docs-api
- **Created**: 2026-11-02
- **Evidence confidence (overall)**: medium — o estado do repositório foi verificado diretamente
  (alta); o comportamento do gerador vem só dos READMEs no GitHub, sem a documentação detalhada
  em springdoc.org (não buscada) e sem teste local.

## Users & Demand

- O único consumidor declarado da especificação são os **API clients locais** (Bruno, Insomnia,
  Postman, Swagger UI) — [source: `README.md` §API, linha 127] (confidence: high, cited)
- A demanda é **declarada** pelo dono do projeto (intake), não observada: não há incidente
  registrado de especificação desatualizada; o arquivo manual tem horas de idade (commit
  `a0901d0`, 2026-11-02) — [source: `git show --stat HEAD`; `intake.md`] (confidence: high, cited)
- O risco de divergência é real em princípio: a API vai crescer, já que `Inquilino`, `Contrato` e
  `Fatura` ainda não têm controller (AGENTS.md §6, "Dívida restante"), e cada feature nova
  exigiria editar o YAML à mão — [source: `AGENTS.md` §6] (confidence: high, cited)
- Hoje o projeto tem um só mantenedor, então não há outros consumidores sofrendo com a
  divergência — ASSUMPTION (confidence: medium)

## Prior Art

- **Interno — especificação manual**: `docs/api/openapi.yaml` (OpenAPI 3.1, 6 caminhos, 45
  campos `description:`, todo em português, com guia passo a passo de CSRF/sessão no
  `info.description`). Foi "conferido contra a app rodando" à mão — [source: `docs/api/openapi.yaml`;
  mensagem do commit `a0901d0`] (confidence: high, cited)
- **Interno — contrato de referência**: `specs/003-proprietario-login-isolamento/contracts/api-http.md`
  é declarado a fonte da verdade; o YAML diz "em caso de divergência, a spec prevalece" — [source:
  `docs/api/openapi.yaml` linhas 5-8; `README.md` linha 128] (confidence: high, cited)
- **Interno — coleção Bruno**: `docs/api/habitattion-api/` (`opencollection.yml`, pasta Health,
  ambiente `local`) é outro artefato manual que descreve a API e não seria coberto por um gerador
  de OpenAPI — [source: árvore `docs/api/habitattion-api/`] (confidence: high, cited)
- **Interno — nenhuma tentativa anterior**: não há menção a OpenAPI/Swagger em `specs/` nem na
  constitution; as assessments anteriores (`better-db-schema`, `dockerize-app`) não tratam disso —
  [source: grep em `specs/`, `.specify/memory/`] (confidence: high, cited)
- **Externo — springdoc-openapi**: a linha **3.x** suporta Spring Boot 4.x; a versão exibida no
  README é `3.2.0`; o artefato para Web MVC + UI é
  `org.springdoc:springdoc-openapi-starter-webmvc-ui`. Serve `/v3/api-docs`,
  `/v3/api-docs.yaml` e `/swagger-ui.html` — [source: github.com/springdoc/springdoc-openapi]
  (confidence: medium, cited — versão não conferida no Maven Central)
- **Externo — springdoc-openapi-maven-plugin**: gera JSON/YAML **com a aplicação rodando**, na
  fase de integration-test (`mvn verify`), junto com os goals `start`/`stop` do
  `spring-boot-maven-plugin`; o padrão é `target/openapi.json` e `failOnError` vale `false` — [source:
  github.com/springdoc/springdoc-openapi-maven-plugin] (confidence: medium, cited)

## Market & Context

- O que se usa hoje: escrever e conferir o YAML à mão, o que custa uma edição manual por mudança
  de rota — [source: commit `a0901d0`] (confidence: high, cited)
- Custo de não fazer nada: com 7 rotas (README §API), o YAML ainda é pequeno; o custo cresce a
  cada agregado novo exposto (Inquilino, Contrato, Fatura) — [source: `README.md` §API;
  `AGENTS.md` §6] (confidence: medium, cited + ASSUMPTION sobre o ritmo de crescimento)
- Code-first com springdoc é a abordagem dominante no ecossistema Spring — ASSUMPTION
  (confidence: medium; não verificado nesta pesquisa)

## Data & Constraints

- **Stack**: Spring Boot 4.1.2, `spring-boot-starter-webmvc`, Java 25 — [source: `pom.xml`]
  (confidence: high, cited). A compatibilidade do springdoc 3.x com Java 25 / Boot 4.1 não foi
  verificada — ASSUMPTION.
- **Endpoints que não são controllers**: `POST /sessao` (login) e `DELETE /sessao` (logout) são
  tratados por filtros do Spring Security (`formLogin`, `logout`) em `SegurancaConfiguration`,
  não por `@RestController`; só `GET /sessao` está em `SessaoController`. Um gerador code-first
  que lê controllers **não os enxerga** sem configuração extra — [source:
  `adapters/config/SegurancaConfiguration.java` linhas 32-44; `SessaoController.java`]
  (confidence: high quanto ao código; medium quanto ao comportamento do gerador)
- **`/actuator/health`** também não é controller da aplicação — [source: `application.properties`,
  bloco Actuator] (confidence: high, cited)
- **Segurança bloqueia tudo por padrão**: `anyRequest().authenticated()`. `/v3/api-docs` e
  `/swagger-ui/**` exigiriam liberação explícita na cadeia de filtros, ou ficariam atrás de
  login — [source: `SegurancaConfiguration.java` linha 34] (confidence: high, cited)
- **CSRF modo SPA** (`csrf.spa()`, cabeçalho `X-XSRF-TOKEN`): chamadas "Try it out" de
  POST/DELETE na Swagger UI dão 403 sem o cabeçalho; não se sabe se a UI do springdoc o envia —
  [source: `SegurancaConfiguration.java` linha 26; documentação de suporte a CSRF não buscada]
  (confidence: low)
- **Cookie `Secure` em `prod`**: a sessão só trafega por HTTPS — [source:
  `application-prod.properties`] (confidence: high, cited)
- **Plugin precisa da app rodando**: no modo rápido a app sobe com H2 em memória (sem infra
  externa); se o Redis precisa estar acessível na subida não foi verificado — [source:
  `application.properties`; ASSUMPTION sobre o Redis] (confidence: medium)
- **Governança**: dependência nova e endpoint novo além de `/actuator/health` exigem acordo (§9)
  e foram **acordados** para esta feature (intake, Clarifications) — [source: `AGENTS.md` §9;
  `intake.md`] (confidence: high, cited)
- **TDD**: o processo exige teste antes do código (constitution / AGENTS.md §3); a geração de
  documentação precisa de um teste que a justifique — [source: `AGENTS.md` §3] (confidence: high, cited)

## Evidence Against the Idea

- **A especificação gerada perde o que o arquivo manual tem de mais útil**: o guia de CSRF/sessão,
  as descrições em português e os exemplos não saem do código; reproduzi-los exige anotações nos
  controllers/records ou um customizador de OpenAPI, que viram uma segunda fonte para manter —
  [source: `docs/api/openapi.yaml`] (confidence: medium)
- **As rotas de autenticação (login/logout) ficam de fora** de uma geração ingênua, porque não
  são controllers; justamente o fluxo mais difícil de usar no API client seria o mal documentado —
  [source: `SegurancaConfiguration.java`] (confidence: medium)
- **Conflito com a hierarquia de verdade atual**: hoje a spec (`contracts/api-http.md`) prevalece
  sobre o YAML. Gerar do código troca a âncora para "o que está implementado"; o que
  impede código e contrato de divergirem passa a ser o teste, não a documentação —
  [source: `README.md` linha 128] (confidence: medium)
- **Superfície nova exposta**: Swagger UI e `/v3/api-docs` mostram a API inteira a quem os
  alcançar; o projeto é deliberadamente restritivo (Actuator só com `health`, sem detalhes) —
  [source: `application.properties`; `AGENTS.md` §9] (confidence: medium)
- **Pouca dor medida hoje**: 7 rotas, um mantenedor, especificação recém-conferida, sem CI
  para aproveitar a automação agora ("preparar para um CI futuro") — [source: `README.md`;
  `intake.md`] (confidence: medium)
- **Dependência extra e ciclo de build mais lento**: o plugin sobe a aplicação na fase
  integration-test — [source: README do springdoc-openapi-maven-plugin] (confidence: medium)

## Gaps & Open Questions

- [NEEDS CLARIFICATION: Quem consome a especificação além dos API clients locais? (pendente desde o intake)]
- [NEEDS CLARIFICATION: Qual teste prova que a especificação está atualizada? (pendente desde o intake)]
- [NEEDS CLARIFICATION: A Swagger UI fica disponível em `prod`, e com ou sem login? (pendente desde o intake)]
- [NEEDS CLARIFICATION: O `contracts/api-http.md` continua sendo a fonte de verdade, ou a especificação gerada passa a ser a referência?]
- [NEEDS CLARIFICATION: A coleção Bruno (`docs/api/habitattion-api/`) continua manual, passa a ser derivada da especificação ou é removida?]
- [UNVERIFIED — fetch skipped: host not on safe list: springdoc.org] Nomes das propriedades para ligar/desligar
  endpoints por profile, suporte a login por formulário (`springdoc.show-login-endpoint`) e a CSRF
  na Swagger UI — documentados em springdoc.org/properties.html e springdoc.org/modules.html.
- [UNVERIFIED] Compatibilidade do springdoc 3.x com Java 25 e Spring Boot 4.1.2, e se a app sobe
  sem o Redis acessível (requisito para o plugin rodar num CI).

## Sources

- https://github.com/springdoc/springdoc-openapi (host: github.com, policy: allowlisted)
- https://github.com/springdoc/springdoc-openapi-maven-plugin (host: github.com, policy: allowlisted)
- https://springdoc.org/properties.html (host: springdoc.org, policy: not fetched — host not on safe list)
- https://springdoc.org/modules.html (host: springdoc.org, policy: not fetched — host not on safe list)
- Repositório (somente leitura): `README.md`, `AGENTS.md`, `pom.xml`, `docs/api/openapi.yaml`,
  `docs/api/habitattion-api/`, `src/main/resources/application*.properties`,
  `src/main/java/.../adapters/config/SegurancaConfiguration.java`,
  `src/main/java/.../adapters/inbound/rest/*Controller.java`, `specs/`, commit `a0901d0`
