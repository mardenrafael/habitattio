# Bug Assessment: `/actuator/health` responde 401 depois da feature 003

- **Slug**: actuator-health-401
- **Created**: 2026-11-05
- **Source**: pasted text (achado no T001 da feature 004, baseline de `./mvnw test` em `main`)
- **Verdict**: valid
- **Severity**: high

## Report (verbatim or summarized)

> GET /actuator/health responde 401 depois da feature 003 (Spring Security): SegurancaConfiguration
> exige autenticação em anyRequest, o que quebra SaudeAplicacaoTest (2 falhas) e ImagemContainerTest
> (4 erros: container nunca fica healthy). main está vermelho. Baseline: 199 testes, 2 falhas,
> 4 erros, 0 pulados.

## Symptom

Sem login, `GET /actuator/health` responde `401`, e qualquer outro caminho sob `/actuator` também.
O esperado (`specs/002-dockerize-app/contracts/saude-http.md`) é `200 {"status":"UP"}` no endpoint
de saúde e `404` nos demais caminhos. Com isso, o `HEALTHCHECK` do container nunca passa.

## Reproduction

1. Em `main` (`6bcd95a`), com Docker no ar, rodar `./mvnw -B test`.
2. `SaudeAplicacaoTest.deveResponderSaudavelSemRedis:29` falha com `Status expected:<200> but was:<401>`.
3. `SaudeAplicacaoTest.naoDeveExporOutrosEndpointsOperacionais:49` falha com
   `Status expected:<404> but was:<401>`.
4. Quatro testes de `ImagemContainerTest` falham com
   `Timed out waiting for container to become healthy`:
   `deveSubirSaudavelNoModoRapidoSemBancoExterno`,
   `deveFicarSaudavelEmProducaoComPostgresSemRedis`,
   `deveFicarNaoSaudavelQuandoPostgresFicaInacessivel` e
   `deveResponderSoComEstadoGeralQuandoPostgresFicaInacessivel`.

Reprodução rápida, sem Docker: `./mvnw test -Dtest=SaudeAplicacaoTest`.

## Suspected Code Paths

- `src/main/java/br/com/marden/habitattio/adapters/config/SegurancaConfiguration.java:30-32`:
  só `POST /proprietarios` e `POST /sessao` são públicos; `anyRequest().authenticated()` alcança
  `/actuator/**`.
- `docker/VerificarSaude.java`: o `HEALTHCHECK` chama `/actuator/health` sem credenciais e exige
  `200`. Está correto; só sofre o efeito.
- `src/main/resources/application.properties:23-29`: expõe só `health`, sem detalhes e sem
  descoberta. Está correto: é o que garante o `404` nos demais caminhos.

## Root Cause Hypothesis

Confiança: **alta**. A feature 002 criou o contrato de saúde sem login, quando ainda não havia
Spring Security. A feature 003 adicionou a cadeia de filtros com `anyRequest().authenticated()` sem
abrir exceção para o Actuator, e entrou em `main` pelo PR #130 (2026-10-31). Nenhuma das duas
falhou no merge porque não há CI, e os testes que acusam o problema já existiam. Os seis testes
falham pelo mesmo motivo: o filtro responde 401 antes de o Actuator ou o MVC serem alcançados.

## Proposed Remediation

**Preferred**: em `SegurancaConfiguration`, adicionar antes de `anyRequest()`:
`.requestMatchers(HttpMethod.GET, "/actuator/**").permitAll()`.

O padrão precisa ser `/actuator/**`, e não só `/actuator/health`. O contrato exige `404` sem login em
todo caminho sob `/actuator`. Com a exceção só no `health`, `/actuator/env` e os demais continuariam
em `401`, e `naoDeveExporOutrosEndpointsOperacionais` continuaria vermelho. O que limita a exposição
é o `management.endpoints.web.exposure.include=health`, que já existe.

**Alternatives**:
- `EndpointRequest.to(HealthEndpoint.class)`: mais explícito, mas cobre só o `health`. Os demais
  caminhos ficariam em 401 e violariam o contrato.
- `EndpointRequest.toAnyEndpoint()`: casa só endpoints expostos, com o mesmo problema.
- Mudar o contrato para aceitar `401` nos demais caminhos: exigiria editar um teste existente e
  decisão do dono. Também revelaria a diferença entre "existe" e "não existe" só depois do login,
  sem ganho real.

**Files likely to change**:
- `src/main/java/br/com/marden/habitattio/adapters/config/SegurancaConfiguration.java`

**Tests to add or update**:
- Nenhum teste novo nem alterado. `SaudeAplicacaoTest` e `ImagemContainerTest` já falham pelo motivo
  certo e são o vermelho do TDD. Testes existentes não são editados.

## Risks & Considerations

- **Segurança**: a regra libera `GET` em todo `/actuator/**`. Um endpoint exposto no futuro ficaria
  público sem nova decisão na cadeia de filtros. A proteção é `naoDeveExporOutrosEndpointsOperacionais`,
  que reprova se `env`, `metrics`, `info`, `beans`, `configprops` ou a descoberta deixarem de dar `404`.
  Endpoints fora dessa lista não têm essa cobertura. O AGENTS.md §9 já proíbe expor outros sem acordo.
- **Exposição do `health`**: público por contrato, só com o estado geral (`show-details=never`,
  `show-components=never`). Sem sessão criada e sem dado de proprietário.
- **Métodos**: só `GET`. Outros métodos sob `/actuator` continuam exigindo autenticação e CSRF.
- **Isolamento por proprietário**: não muda. A regra é anterior a `anyRequest()` e só casa `/actuator/**`.
- **Duração do baseline**: `deveFicarSaudavelEmProducaoComPostgresSemRedis` levou 85117 s com timeout
  de 2 min, o que aponta para máquina suspensa, e não para travamento do teste. Não confirmado.

## Open Questions

Nenhuma. O dono escolheu corrigir em bug fix separado (2026-11-05), antes de retomar a feature 004.
