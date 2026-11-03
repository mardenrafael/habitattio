# Contrato: endpoint de saúde HTTP

Única superfície operacional exposta pela aplicação (FR-013–FR-016). Vale nos dois modos.

## `GET /actuator/health`

| Situação | Status HTTP | Corpo (exato) |
|---|---|---|
| Aplicação e banco OK (Redis indiferente) | `200` | `{"status":"UP"}` |
| Banco inacessível (ou outro indicador ativo `DOWN`) | `503` | `{"status":"DOWN"}` |
| Fora de serviço | `503` | `{"status":"OUT_OF_SERVICE"}` |

- O corpo **não** contém `components`, `details` nem `groups`.
- O tipo de conteúdo é o padrão do Actuator (`application/vnd.spring-boot.actuator.v3+json` ou
  `application/json`). Quem consome olha só o status HTTP.
- Não há autenticação: o endpoint não revela nada além do estado geral.
- Indicadores considerados: os padrões do Boot (`db`, `diskSpace`, `ping`, ...), **exceto
  `redis`**.

Verificado por `SaudeAplicacaoTest` (`deveResponderSaudavelSemRedis` e
`deveResponderSoComEstadoGeral`), no modo rápido, e por `ImagemContainerTest`, no modo produção
e no `503` sem banco.

## Qualquer outro caminho sob `/actuator`

`/actuator`, `/actuator/env`, `/actuator/metrics`, `/actuator/info`, `/actuator/beans`,
`/actuator/configprops` e os demais → `404`.

Verificado por `SaudeAplicacaoTest.naoDeveExporOutrosEndpointsOperacionais`.
