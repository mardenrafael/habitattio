# Bug Fix: `Imovel` volta como `ativo = true` após salvar, mesmo inativado

- **Slug**: imovel-ativo-perdido
- **Fixed**: 2026-10-29
- **Assessment**: ./assessment.md
- **Status**: applied

## Summary

`Imovel` ganhou um construtor público de 4 argumentos que recebe `ativo`, e o construtor de 3
argumentos passou a delegar a ele com `true`. `ImovelRepositoryAdapter.salvar` agora reconstrói o
domínio com `salvo.isAtivo()`, então o imóvel devolvido reflete o estado persistido.

## Changes

| File | Change | Notes |
|------|--------|-------|
| `src/main/java/br/com/marden/habitattio/domain/model/Imovel.java` | modified | Novo construtor `(id, endereco, valorSugerido, ativo)` com a mesma validação; o de 3 argumentos delega com `true` |
| `src/main/java/br/com/marden/habitattio/adapters/outbound/persistence/ImovelRepositoryAdapter.java` | modified | Volta entity → domínio passa `salvo.isAtivo()` |
| `src/test/java/br/com/marden/habitattio/domain/model/ImovelTest.java` | added tests | 4 testes novos; os 7 existentes não foram tocados |
| `src/test/java/br/com/marden/habitattio/adapters/outbound/persistence/ImovelRepositoryAdapterTest.java` | added (new file) | Primeiro teste de adapter do projeto: `@DataJpaTest` + Testcontainers PostgreSQL |
| `src/test/java/br/com/marden/habitattio/application/usecase/CadastrarImovelUseCaseTest.java` | modified (fake) | `ImovelRepositoryFake` passa a preservar `imovel.isAtivo()`. Ajuste autorizado pelo usuário; nenhuma asserção mudou |

## Diff Highlights

```java
// Imovel.java
public Imovel(Long id, String endereco, BigDecimal valorSugerido) {
    this(id, endereco, valorSugerido, true);
}

public Imovel(Long id, String endereco, BigDecimal valorSugerido, boolean ativo) {
    validar(endereco, valorSugerido);
    ...
    this.ativo = ativo;
}

// ImovelRepositoryAdapter.salvar
return new Imovel(salvo.getId(), salvo.getEndereco(), salvo.getValorSugerido(), salvo.isAtivo());
```

## Tests Added or Updated

- `ImovelTest::deveCriarImovelInativoQuandoInformado`: o construtor completo respeita `ativo = false` e os demais campos.
- `ImovelTest::deveCriarImovelAtivoQuandoInformado`: o construtor completo respeita `ativo = true`.
- `ImovelTest::deveRejeitarEnderecoVazioNoConstrutorCompleto`: o construtor completo valida o endereço, com a mesma mensagem.
- `ImovelTest::deveRejeitarValorSugeridoInvalidoNoConstrutorCompleto`: o construtor completo valida o valor, com a mesma mensagem.
- `ImovelRepositoryAdapterTest::deveManterImovelInativoAoSalvar`: **reproduz o bug.** Salvar um imóvel inativado deve devolver `isAtivo() == false` e gravar `ativo = false`. O banco é relido após `flush()`/`clear()`, então o teste lê o PostgreSQL e não o cache da sessão.
- `ImovelRepositoryAdapterTest::deveManterImovelAtivoAoSalvar`: regressão do caminho feliz.

## Local Verification

Ciclo TDD seguido (AGENTS.md §3):

1. Linha de base, antes de qualquer mudança: `./mvnw test` → 36 testes, BUILD SUCCESS.
2. Stub do construtor de 4 argumentos (`throw new UnsupportedOperationException()`) e depois os testes.
3. `./mvnw test -Dtest='ImovelTest,ImovelRepositoryAdapterTest'` → **falhou pelo motivo esperado**:
   - os 4 testes de domínio novos falharam com `UnsupportedOperationException` (stub);
   - `ImovelRepositoryAdapterTest.deveManterImovelInativoAoSalvar:57 expected: <false> but was: <true>`,
     o bug reproduzido contra um PostgreSQL real;
   - `deveManterImovelAtivoAoSalvar` passou (regressão, esperado).
4. Depois da implementação: `./mvnw test` → **42 testes, 0 falhas, 0 erros, BUILD SUCCESS**. Os
   testes de integração rodaram (Docker 29.9.1 disponível): `ImovelRepositoryAdapterTest` e
   `HabitattioApplicationTests`.
5. `grep` por `import org.springframework|jakarta` em `domain/` e `application/` → nenhum.

## Deviations from Assessment

Nenhuma. A correção seguiu a remediação preferida, já com as decisões do usuário (construtor de
4 argumentos, ajuste do fake autorizado, `Contrato`/`Fatura` fora do escopo).

Detalhe de implementação: o teste de adapter declara seu próprio `PostgreSQLContainer` com
`@Container @ServiceConnection`, em vez de importar `TestcontainersConfiguration`, porque essa
classe é package-private no pacote raiz e sobe também um Redis, que o slice JPA não usa.

## Follow-ups

- Quando `Contrato` e `Fatura` ganharem persistência, aplicar o mesmo padrão: um construtor
  completo que receba `ativo` / `status` / `valorPago` / `dataPagamento` (decisão do usuário:
  corrigir quando esses agregados forem tocados).
- Os containers usam `postgres:latest`, igual ao `TestcontainersConfiguration`. Fixar a versão
  deixaria os testes reprodutíveis.
