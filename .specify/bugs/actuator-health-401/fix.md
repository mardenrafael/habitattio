# Bug Fix: `/actuator/health` responde 401 depois da feature 003

- **Slug**: actuator-health-401
- **Fixed**: 2026-11-05
- **Assessment**: ./assessment.md
- **Status**: applied

## Summary

A cadeia de filtros passa a liberar `GET /actuator/**` sem login. Com isso, `health` volta a responder
`200`, os demais caminhos voltam ao `404` do contrato da feature 002 e o `HEALTHCHECK` do container
volta a passar.

## Changes

| File | Change | Notes |
|------|--------|-------|
| `src/main/java/br/com/marden/habitattio/adapters/config/SegurancaConfiguration.java` | modified | uma linha, antes de `anyRequest()` |

## Diff Highlights

```java
.requestMatchers(HttpMethod.POST, "/sessao").permitAll()
.requestMatchers(HttpMethod.GET, "/actuator/**").permitAll()
.anyRequest().authenticated())
```

## Tests Added or Updated

Nenhum. Os testes existentes já cobriam o defeito e não foram editados:

- `SaudeAplicacaoTest.deveResponderSaudavelSemRedis`: `200` sem login.
- `SaudeAplicacaoTest.naoDeveExporOutrosEndpointsOperacionais`: `404` sem login nos demais caminhos.
- `ImagemContainerTest` (4 cenários de saúde): o container fica `healthy`.

## Local Verification

- Antes da correção: `./mvnw -B test -Dtest=SaudeAplicacaoTest` → 3 testes, 2 falhas, ambas
  `but was:<401>` (vermelho pelo motivo certo).
- Depois da correção: `./mvnw -B test` com Docker → **199 testes, 0 falhas, 0 erros, 0 pulados**,
  `BUILD SUCCESS`, 8:41 min. `ImagemContainerTest`: 11 de 11, 341,7 s.
- A duração de 8:41 min também descarta um travamento do teste: a do baseline (23:49 h) não se
  repetiu.

## Deviations from Assessment

Nenhuma.

## Follow-ups

- **Revisão automática de segurança (média)**: apontou `GET /actuator/**` como amplo e sugeriu
  `hasRole("ADMIN")` no resto. Não foi aplicada: quebraria o contrato (`404` sem login), o projeto
  não tem esse papel, e hoje só o `health` está exposto. O risco residual é o da avaliação: um
  endpoint exposto no futuro ficaria público. A cobertura é parcial
  (`naoDeveExporOutrosEndpointsOperacionais` lista seis caminhos), e o AGENTS.md §9 exige acordo para
  expor outros.
- Retomar a feature 004 no T001 depois do merge, trazendo `main` para `004-pr-validation-ci`.
