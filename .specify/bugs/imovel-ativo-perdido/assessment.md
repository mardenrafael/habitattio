# Bug Assessment: `Imovel` volta como `ativo = true` após salvar, mesmo inativado

- **Slug**: imovel-ativo-perdido
- **Created**: 2026-10-29
- **Source**: pasted text
- **Verdict**: valid
- **Severity**: medium

## Report (verbatim or summarized)

> "Atualmente temos um provlema de perca de estado do banco quando salvamos uma entidade, veja a
> entidade Imovel.java. vera q ela tem um construtor qq faz set de ativo com true, porem se savar
> algo como false, ainda retorna true"

## Symptom

Ao salvar um `Imovel` com `ativo = false` (após `inativar()`) via `ImovelRepositoryPort.salvar`, o
objeto retornado tem `isAtivo() == true`. O esperado é que o imóvel devolvido reflita o estado
persistido (`ativo = false`).

## Reproduction

1. `Imovel imovel = new Imovel(null, "Rua das Flores, 123", new BigDecimal("1500.00"));`
2. `imovel.inativar();` → `imovel.isAtivo() == false`
3. `Imovel salvo = imovelRepositoryAdapter.salvar(imovel);`
4. Observado: `salvo.isAtivo() == true`. Esperado: `false`.

A coluna `imoveis.ativo` **é gravada corretamente como `false`**: o adapter passa
`imovel.isAtivo()` para a `ImovelJpaEntity`. O estado se perde só na volta (entity → domínio).

Não existe hoje fluxo exposto (caso de uso/endpoint) que inative e salve um imóvel. O bug é
reproduzível chamando o adapter diretamente (teste de integração), não pela API atual.

## Suspected Code Paths

- `src/main/java/br/com/marden/habitattio/adapters/outbound/persistence/ImovelRepositoryAdapter.java:26-30`:
  reconstrói o domínio com `new Imovel(salvo.getId(), salvo.getEndereco(), salvo.getValorSugerido())`,
  ignorando `salvo.isAtivo()`.
- `src/main/java/br/com/marden/habitattio/domain/model/Imovel.java:11-18`: o único construtor
  é de **criação**. Ele fixa `this.ativo = true` e não aceita o estado como parâmetro, então não
  existe maneira de **reconstituir** um imóvel inativo a partir do banco. Essa é a causa
  estrutural.
- `src/test/java/br/com/marden/habitattio/application/usecase/CadastrarImovelUseCaseTest.java:26`:
  o `ImovelRepositoryFake` repete o mesmo padrão (`new Imovel(proximoId++, ...)`) e também
  descarta `ativo`. Não quebra nenhum teste atual, mas o fake não é fiel à porta.
- Mesmo padrão latente (ainda sem adapter, então não é bug hoje):
  `Contrato.java:16-29` (`ativo = true` fixo) e `Fatura.java:21-27` (`status = PENDENTE` fixo,
  sem `valorPago`/`dataPagamento`). Quando esses agregados ganharem persistência, vão perder
  `encerrado`/`PAGA`/`ATRASADA` do mesmo jeito.

## Root Cause Hypothesis

O modelo de domínio não separa **criação** (imóvel novo, sempre ativo) de **reconstituição**
(imóvel existente, com o estado que tiver no banco). O adapter só tem o construtor de criação à
disposição e por isso força `ativo = true` em todo objeto lido de volta. Confiança: **alta**: a
leitura do código mostra isso diretamente, sem depender de execução.

## Proposed Remediation

**Preferred** (decidido pelo usuário em 2026-10-29): adicionar a `Imovel` um **construtor
público de 4 argumentos** `Imovel(Long id, String endereco, BigDecimal valorSugerido, boolean ativo)`,
que passa pela mesma validação de invariantes e atribui `ativo` recebido. O construtor atual de 3
argumentos passa a delegar a ele com `true` (`this(id, endereco, valorSugerido, true)`), mantendo a
semântica de criação e os testes existentes válidos. Em `ImovelRepositoryAdapter.salvar`, trocar
o `new Imovel(...)` da linha 26 por
`new Imovel(salvo.getId(), salvo.getEndereco(), salvo.getValorSugerido(), salvo.isAtivo())`.
Java puro no domínio, sem import de framework, conforme §2 do AGENTS.md.

**Alternatives** (descartadas):
- *Fábrica estática `Imovel.reconstituir(...)`*: deixaria explícita a diferença entre criar e
  reconstituir, mas o usuário preferiu o construtor de 4 argumentos.
- *Devolver o próprio `imovel` recebido, só com o id gerado*: exigiria um setter de `id`
  (mutabilidade) e não resolve a leitura futura (`buscarPorId`).

**Files likely to change**:
- `src/main/java/br/com/marden/habitattio/domain/model/Imovel.java`
- `src/main/java/br/com/marden/habitattio/adapters/outbound/persistence/ImovelRepositoryAdapter.java`
- `src/test/java/br/com/marden/habitattio/domain/model/ImovelTest.java` (apenas **adicionar** testes)
- `src/test/java/br/com/marden/habitattio/application/usecase/CadastrarImovelUseCaseTest.java` (ajuste do `ImovelRepositoryFake`, autorizado)
- `src/test/java/br/com/marden/habitattio/adapters/outbound/persistence/ImovelRepositoryAdapterTest.java` (novo)

**Tests to add or update** (TDD: escrever primeiro e ver falhar):
- `ImovelTest.deveCriarImovelInativoQuandoInformado`: `new Imovel(1L, ..., false)` → `isAtivo() == false`.
- `ImovelTest.deveCriarImovelAtivoQuandoInformado`: `new Imovel(1L, ..., true)` → `isAtivo() == true`.
- `ImovelTest.deveRejeitarEnderecoVazioNoConstrutorCompleto` / valor inválido: o construtor de 4
  argumentos também valida as invariantes.
- `ImovelRepositoryAdapterTest.deveManterImovelInativoAoSalvar` (`@DataJpaTest` + Testcontainers
  PostgreSQL): salvar um imóvel inativado → retorno com `isAtivo() == false` e linha no banco com
  `ativo = false`. **É o teste que expõe o bug**: tem que falhar antes da correção. Exige Docker.
- `ImovelRepositoryAdapterTest.deveManterImovelAtivoAoSalvar`: regressão do caminho feliz.

## Risks & Considerations

- **Corrupção futura de dados**: hoje o banco guarda o valor certo. Quando existir leitura
  (`buscarPorId`) ou um caso de uso que carregue, altere e salve de novo, o mesmo mapeamento
  reativa o imóvel e grava `ativo = true` no banco. Isso viola a regra de negócio "Imóvel inativo
  não recebe novo contrato" (AGENTS.md §7). A severidade sobe para **high** assim que houver um
  fluxo de inativação ou consulta.
- **Guardrail de testes**: o `ImovelRepositoryFake` em `CadastrarImovelUseCaseTest` também
  descarta `ativo`. Ajustá-lo é mexer em teste existente, o que o AGENTS.md §3 só permite com
  decisão explícita do usuário. **Autorizado pelo usuário em 2026-10-29**: o fake deve passar a
  usar o construtor de 4 argumentos, preservando `imovel.isAtivo()`. A mudança é só na fidelidade
  do fake; nenhuma asserção existente é alterada.
- Não há migração de schema: a coluna `ativo` já existe e já é gravada.
- Sem impacto de API: `ImovelResponse` nem expõe `ativo` hoje.
- Ainda não há nenhum teste de adapter (`src/test/java/.../adapters/` não existe). O primeiro
  teste `@DataJpaTest` com Testcontainers vai precisar de Docker no ambiente de quem rodar.

## Open Questions

Todas respondidas pelo usuário em 2026-10-29:

1. **Fábrica estática vs. construtor**: construtor público de 4 argumentos.
2. **`Contrato` e `Fatura`**: fora do escopo deste bug. Serão corrigidos quando esses agregados
   ganharem persistência (feature própria).
3. **Ajuste do `ImovelRepositoryFake`**: autorizado.
