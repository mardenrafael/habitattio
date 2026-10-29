# Contrato: estrutura de `imoveis` no banco de produção

A feature não muda nenhuma interface HTTP (`POST /imoveis` continua com o mesmo request e
response). O contrato que ela introduz fica entre a **aplicação** e o **banco de produção**:
o que a migração V1 entrega, o que o `validate` exige e o que qualquer escrita (da aplicação
ou não) precisa respeitar.

## Garantias que o banco oferece a qualquer escritor

| # | Escrita | Resultado | Constraint / erro PostgreSQL |
|---|---|---|---|
| G1 | `endereco` nulo | rejeitada | `not_null_violation` (23502) |
| G2 | `endereco` vazio ou só espaços (`''`, `'   '`, `E'\t\n'`) | rejeitada | `ck_imoveis_endereco_nao_vazio` (23514) |
| G3 | `endereco` com 256 caracteres | rejeitada | `string_data_right_truncation` (22001) |
| G4 | `endereco` com 255 caracteres | aceita | — |
| G5 | `valor_sugerido` nulo | rejeitada | `not_null_violation` (23502) |
| G6 | `valor_sugerido` = 0 ou negativo | rejeitada | `ck_imoveis_valor_sugerido_positivo` (23514) |
| G7 | `valor_sugerido` = 0.00001 (arredonda para 0) | rejeitada | `ck_imoveis_valor_sugerido_positivo` (23514) |
| G8 | `valor_sugerido` = 1500.12345 | aceita; lida como `1500.1235` | — |
| G9 | `valor_sugerido` = 1500.1234 | aceita; lida exatamente como `1500.1234` | — |
| G10 | dois imóveis com o mesmo `endereco` | ambos aceitos | — |

Cada linha de G1 a G10 corresponde a um teste de persistência (FR-011, SC-002).

## Garantias que a aplicação oferece na subida (profile `prod`)

| # | Estado do banco | Resultado da subida |
|---|---|---|
| S1 | vazio | aplica V1 e sobe |
| S2 | todas as migrações já aplicadas | não reaplica nada e sobe |
| S3 | `imoveis` sem uma coluna mapeada, ou com tipo incompatível | **não sobe**; o banco não é alterado |
| S4 | checksum de migração aplicada difere do script no repositório | **não sobe** |

## Evolução

- Toda mudança de estrutura é uma migração nova, `V<n+1>__<descricao>.sql`. Uma migração
  aplicada **nunca** é editada (S4 a recusaria).
- O mapeamento JPA e a migração mudam juntos, no mesmo commit. O `validate` (S3) é a rede
  que pega quem esquecer.
