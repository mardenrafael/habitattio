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

**Preferred**: adicionar a `Imovel` um caminho explícito de reconstituição que recebe o estado
completo e passa pela mesma validação de invariantes, por exemplo uma fábrica estática
`public static Imovel reconstituir(Long id, String endereco, BigDecimal valorSugerido, boolean ativo)`
(ou um construtor de 4 argumentos ao qual o de 3 argumentos delega com `true`). O construtor
atual continua sendo o de criação, com a mesma semântica (`ativo = true`), e os testes existentes
seguem válidos. Em `ImovelRepositoryAdapter.salvar`, trocar o `new Imovel(...)` da linha 26 por
`Imovel.reconstituir(salvo.getId(), salvo.getEndereco(), salvo.getValorSugerido(), salvo.isAtivo())`.
Isso é Java puro no domínio, sem import de framework, e respeita a §2 do AGENTS.md.

**Alternatives**:
- *Construtor público de 4 argumentos `(id, endereco, valorSugerido, ativo)`*: mais simples, mas
  deixa ambíguo para quem chama qual construtor usar ao *criar* um imóvel. Com dois construtores
  públicos, alguém pode criar um imóvel "novo" já inativo. A fábrica nomeada `reconstituir`
  deixa a intenção explícita.
- *Devolver o próprio `imovel` recebido, só com o id gerado*: exigiria um setter de `id`
  (mutabilidade) e não resolve a leitura futura (`buscarPorId`), que vai precisar reconstituir de
  qualquer forma.

**Files likely to change**:
- `src/main/java/br/com/marden/habitattio/domain/model/Imovel.java`
- `src/main/java/br/com/marden/habitattio/adapters/outbound/persistence/ImovelRepositoryAdapter.java`
- `src/test/java/br/com/marden/habitattio/domain/model/ImovelTest.java` (apenas **adicionar** testes)
- `src/test/java/br/com/marden/habitattio/adapters/outbound/persistence/ImovelRepositoryAdapterTest.java` (novo)

**Tests to add or update** (TDD: escrever primeiro e ver falhar):
- `ImovelTest.deveReconstituirImovelInativo`: `reconstituir(..., false)` → `isAtivo() == false`.
- `ImovelTest.deveReconstituirImovelAtivo`: `reconstituir(..., true)` → `isAtivo() == true`.
- `ImovelTest.deveRejeitarEnderecoVazioAoReconstituir` / valor inválido: a reconstituição também
  valida as invariantes.
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
  decisão explícita do usuário. Ele não bloqueia a correção, já que nenhum teste atual depende
  disso, então a recomendação é não tocar agora e registrar a pendência.
- Não há migração de schema: a coluna `ativo` já existe e já é gravada.
- Sem impacto de API: `ImovelResponse` nem expõe `ativo` hoje.
- Ainda não há nenhum teste de adapter (`src/test/java/.../adapters/` não existe). O primeiro
  teste `@DataJpaTest` com Testcontainers vai precisar de Docker no ambiente de quem rodar.

## Open Questions

- [NEEDS CLARIFICATION: Preferência entre fábrica estática `Imovel.reconstituir(...)`
  (recomendada) e construtor público de 4 argumentos?]
- [NEEDS CLARIFICATION: Aplicar o mesmo padrão de reconstituição em `Contrato` e `Fatura` agora,
  de forma preventiva, ou só quando ganharem adapter? O AGENTS.md (§6, §9 YAGNI) sugere esperar
  pela feature.]
- [NEEDS CLARIFICATION: Autoriza ajustar o `ImovelRepositoryFake` de
  `CadastrarImovelUseCaseTest` para preservar `ativo` (alteração em teste existente)?]
