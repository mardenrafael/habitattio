# Data Model: Proprietário, login e isolamento de dados (módulo 2)

**Feature**: [spec.md](./spec.md) · **Research**: [research.md](./research.md) · **Data**: 2026-10-30

Estrutura no banco de produção: [contracts/schema-v2.md](./contracts/schema-v2.md).

---

## Domínio (`domain/model`, Java puro)

### `Usuario` — proprietário (existente, ganha regras)

| Campo | Tipo | Regra | Mensagem |
|---|---|---|---|
| `id` | `Long` | nulo antes de salvar | — |
| `nome` | `String` | obrigatório, não em branco, ≤ 100 code points | "O nome do usuário não pode ser vazio." (existente) · "O nome do usuário deve ter no máximo 100 caracteres." |
| `email` | `String` | normalizado no construtor (`strip()` + `toLowerCase(Locale.ROOT)`) **antes** de validar; obrigatório; formato (`PADRAO_EMAIL` atual); ≤ 254 code points | existentes · "O e-mail do usuário deve ter no máximo 254 caracteres." |
| `senhaHash` | `String` | obrigatório, não em branco (existente) | existente |

- Nenhuma transição de estado neste módulo (sem edição, remoção ou desativação).
- `UsuarioTest` existente continua válido sem mudança: o e-mail usado já está normalizado.

### `Senha` — novo objeto de valor (senha em texto, só em trânsito)

| Campo | Tipo | Regra | Mensagem |
|---|---|---|---|
| `valor` | `String` | obrigatório; ≥ 8 code points; ≤ 72 bytes em UTF-8 | "A senha não pode ser vazia." · "A senha deve ter no mínimo 8 caracteres." · "A senha é longa demais: use no máximo 72 bytes (cerca de 72 letras sem acento)." |

- Existe só durante o cadastro, entre o command e o `CodificadorSenhaPort`; nunca é persistida,
  nunca vai para resposta. `toString()` não expõe o valor (`"Senha[******]"`), para não vazar em
  log ou mensagem de erro (SC-004).

### `Imovel` (existente, ganha dono e identificador)

| Campo | Tipo | Regra | Mensagem |
|---|---|---|---|
| `id` | `Long` | nulo antes de salvar | — |
| `usuarioId` | `Long` | **novo**; obrigatório | "O imóvel deve pertencer a um proprietário." |
| `identificador` | `String` | **novo**; `strip()` no construtor; obrigatório, não em branco, ≤ 50 code points | "O identificador do imóvel não pode ser vazio." · "O identificador do imóvel deve ter no máximo 50 caracteres." |
| `endereco` | `String` | sem mudança (módulo 1) | sem mudança |
| `valorSugerido` | `BigDecimal` | sem mudança (> 0) | sem mudança |
| `ativo` | `boolean` | sem mudança; `true` ao criar | — |

Construtores (substituem os atuais; os testes existentes são adaptados só nos dados, Clarifications Q2):

```text
Imovel(Long id, Long usuarioId, String identificador, String endereco, BigDecimal valorSugerido)              // ativo = true
Imovel(Long id, Long usuarioId, String identificador, String endereco, BigDecimal valorSugerido, boolean ativo)
```

Transição de estado (existente): `ativo --inativar()--> inativo`; `inativar()` em imóvel inativo
não muda nada (idempotente, FR-019). Não há reativação neste módulo.

Comparação de identificador: `mesmoIdentificador(String outro)` — compara após `strip()` com
`equalsIgnoreCase` em `Locale.ROOT`-equivalente; acentos distinguem ("Térreo" ≠ "Terreo").
Usado pelos fakes e por quem precisar da regra fora do banco.

### Exceções de negócio (`domain/exception`, novo pacote)

| Exceção | Lançada por | Mensagem |
|---|---|---|
| `EmailJaCadastradoException` | `CadastrarProprietarioUseCase`; `UsuarioRepositoryAdapter` (corrida) | "Já existe um proprietário com este e-mail." |
| `IdentificadorImovelDuplicadoException` | `CadastrarImovelUseCase`; `ImovelRepositoryAdapter` (corrida) | "Você já tem um imóvel com o identificador '<id>'." |
| `ImovelNaoEncontradoException` | `ConsultarImovelUseCase`, `InativarImovelUseCase` | "Imóvel não encontrado." (sem o id, sem dizer de quem é) |
| `CredenciaisInvalidasException` | `AutenticarProprietarioUseCase` | "E-mail ou senha inválidos." |

---

## Portas

### Inbound (`ports/inbound`, records)

| Command | Campos | Caso de uso |
|---|---|---|
| `CadastrarProprietarioCommand` | `nome`, `email`, `senha` | `CadastrarProprietarioUseCase` → `Usuario` |
| `AutenticarProprietarioCommand` | `email`, `senha` | `AutenticarProprietarioUseCase` → `Usuario` |
| `CadastrarImovelCommand` (**muda**) | `usuarioId`, `identificador`, `endereco`, `valorSugerido` | `CadastrarImovelUseCase` → `Imovel` |
| `ConsultarImovelCommand` | `usuarioId`, `imovelId` | `ConsultarImovelUseCase` → `Imovel` |
| `InativarImovelCommand` | `usuarioId`, `imovelId` | `InativarImovelUseCase` → `Imovel` |

Os `toString()` de `CadastrarProprietarioCommand` e `AutenticarProprietarioCommand` mascaram a
senha (records geram `toString` com todos os campos por padrão).

### Outbound (`ports/outbound`, interfaces)

```text
UsuarioRepositoryPort
  Usuario salvar(Usuario)                          // lança EmailJaCadastradoException em corrida
  Optional<Usuario> buscarPorEmail(String emailNormalizado)
  boolean existePorEmail(String emailNormalizado)

ImovelRepositoryPort (existente, ganha métodos)
  Imovel salvar(Imovel)                            // lança IdentificadorImovelDuplicadoException em corrida
  boolean existeIdentificador(Long usuarioId, String identificador)   // sem distinguir caixa
  Optional<Imovel> buscarDoProprietario(Long imovelId, Long usuarioId) // vazio se não existe OU é de outro

CodificadorSenhaPort
  String codificar(String senha)
  boolean confere(String senha, String hash)       // false (nunca exceção) para senha fora do limite
```

Não existe busca de imóvel sem dono na porta (R10).

---

## Casos de uso (`application/usecase`)

| Caso de uso | Portas | Fluxo | Erros |
|---|---|---|---|
| `CadastrarProprietarioUseCase` | `UsuarioRepositoryPort`, `CodificadorSenhaPort` | `new Senha(...)` → `Usuario.normalizarEmail` + `existePorEmail` → `codificar` → `new Usuario(...)` → `salvar` (ordem detalhada abaixo) | `IllegalArgumentException`, `EmailJaCadastradoException` |
| `AutenticarProprietarioUseCase` | `UsuarioRepositoryPort`, `CodificadorSenhaPort` | normaliza e-mail → `buscarPorEmail` → `confere`; se não achou, `confere` contra hash fictício (R3) | `CredenciaisInvalidasException` |
| `CadastrarImovelUseCase` (**muda**) | `ImovelRepositoryPort` | `new Imovel(null, usuarioId, identificador, ...)` → `existeIdentificador` → `salvar` | `IllegalArgumentException`, `IdentificadorImovelDuplicadoException` |
| `ConsultarImovelUseCase` | `ImovelRepositoryPort` | `buscarDoProprietario` | `ImovelNaoEncontradoException` |
| `InativarImovelUseCase` | `ImovelRepositoryPort` | `buscarDoProprietario` → `inativar()` → `salvar` | `ImovelNaoEncontradoException` |

Normalização do e-mail antes da consulta: o caso de uso usa a mesma regra do `Usuario`
(método estático `Usuario.normalizarEmail(String)`), para que cadastro, unicidade e login
comparem o mesmo valor.

Ordem em `CadastrarProprietarioUseCase`: (1) `new Senha(...)` — senha inválida falha antes de
tudo; (2) `Usuario.normalizarEmail` e `existePorEmail` — e-mail repetido falha antes de gastar o
hash; (3) `codificar`; (4) `new Usuario(null, nome, email, hash)` — nome/e-mail inválidos;
(5) `salvar`.

---

## Persistência (`adapters/outbound/persistence`)

| Entity | Tabela | Campos | Observação |
|---|---|---|---|
| `UsuarioJpaEntity` (nova) | `usuarios` | `id`, `nome` (100), `email` (254), `senhaHash` (60) | `@UniqueConstraint(name = "uq_usuarios_email")` para o modo rápido |
| `ImovelJpaEntity` (muda) | `imoveis` | + `usuarioId` (`Long`, `nullable = false`), + `identificador` (50, `nullable = false`) | coluna simples, sem `@ManyToOne` (R9); `@UniqueConstraint(name = "uq_imoveis_usuario_identificador", columnNames = {"usuario_id", "identificador"})` só vale no modo rápido (R8) |

Spring Data:

```text
SpringDataUsuarioRepository : JpaRepository<UsuarioJpaEntity, Long>
  Optional<UsuarioJpaEntity> findByEmail(String)
  boolean existsByEmail(String)

SpringDataImovelRepository (existente)
  Optional<ImovelJpaEntity> findByIdAndUsuarioId(Long id, Long usuarioId)
  boolean existsByUsuarioIdAndIdentificadorIgnoreCase(Long usuarioId, String identificador)
```

Os adapters traduzem `*JpaEntity ↔` domínio nas duas direções, à mão, e convertem
`DataIntegrityViolationException` de unicidade em exceção de negócio (R8).

---

## Autenticação (`adapters/inbound/security`)

`ProprietarioAutenticado(Long id, String nome, String email)` — record `Serializable`, principal
guardado na sessão. Nunca contém senha ou hash. Não é entidade de negócio nem é persistido.

---

## Relacionamentos

```text
Usuario (1) ──< (0..N) Imovel        imoveis.usuario_id → usuarios.id, ON DELETE RESTRICT
                                     unicidade: (usuario_id, lower(identificador))
```
