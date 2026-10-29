# habitattio

Gestão de pagamentos de aluguéis: imóveis, inquilinos, contratos e as faturas mensais que eles
geram — quem pagou, quem está pendente, quem atrasou.

Feito em **Java 25** e **Spring Boot 4.1.2**, com arquitetura hexagonal, TDD e
Spec-Driven Development (via [Spec Kit](https://github.com/github/spec-kit)).

> **Status:** em desenvolvimento inicial. O modelo de domínio está completo e testado, mas por
> enquanto só o cadastro de imóveis está exposto pela API. Veja o [roadmap](#roadmap).

---

## Sumário

- [Domínio](#domínio)
- [Arquitetura](#arquitetura)
- [Como rodar](#como-rodar)
- [API](#api)
- [Testes](#testes)
- [Como o projeto é desenvolvido](#como-o-projeto-é-desenvolvido)
- [Roadmap](#roadmap)
- [Contribuindo](#contribuindo)

---

## Domínio

O vocabulário do código é o do negócio, em português.

| Entidade | O que representa |
|---|---|
| `Imovel` | Unidade locável, com endereço e valor sugerido de aluguel. Pode ser inativada. |
| `Inquilino` | Locatário: nome, CPF e telefone. |
| `Contrato` | Vínculo entre inquilino e imóvel: valor do aluguel, dia de vencimento e data de início. Pode ser encerrado. |
| `Fatura` | Cobrança mensal de um contrato, com status `PENDENTE`, `PAGA` ou `ATRASADA`. |
| `Usuario` | Operador do sistema. |

As regras de negócio que o sistema protege:

- Uma fatura pertence a um único contrato e a um único mês de referência — nunca duas faturas
  para o mesmo contrato na mesma competência.
- O dia de vencimento vai de 1 a 31; em meses mais curtos, o vencimento cai no último dia do mês.
- Valores monetários são estritamente positivos (e sempre `BigDecimal`).
- Contrato encerrado não gera novas faturas; imóvel inativo não recebe novo contrato.
- Fatura `PAGA` é terminal: não volta para `PENDENTE` nem para `ATRASADA`.
- Nada é apagado de fato — inativar e encerrar são desativações lógicas.

Toda entidade valida suas invariantes no construtor: um objeto de domínio nunca existe em estado
inválido.

## Arquitetura

Hexagonal (ports & adapters). **As dependências apontam para dentro**: o domínio não conhece
ninguém, a aplicação conhece o domínio e as portas, os adapters conhecem as portas.

```
src/main/java/br/com/marden/habitattio/
├── domain/model/                  # entidades e regras de negócio — Java puro
├── ports/
│   ├── inbound/                   # commands: o que o mundo pede à aplicação
│   └── outbound/                  # interfaces que a aplicação exige do mundo
├── application/usecase/           # um caso de uso por classe, método executar(...)
└── adapters/
    ├── config/                    # BeanConfiguration: único ponto de encontro Spring ↔ domínio
    ├── inbound/rest/              # controllers e DTOs HTTP
    └── outbound/persistence/      # adapter, repositório Spring Data e entity JPA
```

Na prática:

- `domain/` e `application/` não importam nada de Spring nem de JPA. Os beans são declarados
  explicitamente em `adapters/config/BeanConfiguration.java`.
- Entities JPA não saem da camada de persistência; o adapter traduz `ImovelJpaEntity ↔ Imovel`
  à mão, sem mapeador automático.
- DTOs HTTP não entram no caso de uso; o controller converte `Request → Command` e
  `domínio → Response`.
- Toda classe de aplicação e de adapter é instanciável por construtor num teste, sem contexto
  Spring.

## Como rodar

### Pré-requisitos

- **JDK 25**
- **Docker** — só para os testes de integração e para rodar com PostgreSQL/Redis reais

Não é preciso instalar Maven: use o wrapper `./mvnw` incluído no repositório.

### Modo rápido (H2 em memória)

```bash
git clone https://github.com/mardenrafael/habitattio.git
cd habitattio
./mvnw spring-boot:run
```

A aplicação sobe em `http://localhost:8080` com um banco H2 em memória (modo de compatibilidade
PostgreSQL). Os dados se perdem ao parar a aplicação.

### Com PostgreSQL e Redis (Testcontainers)

Para rodar contra containers reais em vez do H2, execute a classe
`TestHabitattioApplication` (em `src/test/java/br/com/marden/habitattio/`) pela sua IDE, ou:

```bash
./mvnw spring-boot:test-run
```

O Testcontainers sobe PostgreSQL e Redis automaticamente — basta o Docker estar rodando.

## API

### `POST /imoveis` — cadastrar imóvel

```bash
curl -X POST http://localhost:8080/imoveis \
  -H 'Content-Type: application/json' \
  -d '{"endereco": "Rua das Flores, 123 - apto 42", "valorSugerido": 1800.00}'
```

Resposta `201 Created`:

```json
{
  "id": 1,
  "endereco": "Rua das Flores, 123 - apto 42",
  "valorSugerido": 1800.00
}
```

Os demais agregados ainda não têm endpoints — veja o [roadmap](#roadmap).

## Testes

```bash
./mvnw test                                   # suíte completa (exige Docker)
./mvnw test -Dtest=FaturaTest                 # uma classe
./mvnw test -Dtest='*Test,!ImovelRepositoryAdapterTest'   # só domínio e casos de uso, sem Docker
./mvnw verify                                 # build completo
```

| Camada | Onde | Como |
|---|---|---|
| Domínio | `src/test/java/.../domain/model/` | JUnit 5 puro |
| Casos de uso | `src/test/java/.../application/usecase/` | JUnit 5 + fakes escritos à mão |
| Persistência | `src/test/java/.../adapters/outbound/` | `@DataJpaTest` + Testcontainers (PostgreSQL) |
| Contexto Spring | `HabitattioApplicationTests` | `@SpringBootTest` |

O projeto prefere fakes (implementações em memória das portas) a mocks, e não usa framework de
mocking.

## Como o projeto é desenvolvido

**TDD, sem exceção.** Red → Green → Refactor: primeiro a assinatura (porta, command, caso de
uso), depois os testes cobrindo o caminho feliz e cada caso de erro, depois a implementação. Um
teste que já foi visto falhando pelo motivo certo não é alterado para fazer o build passar.

**Spec-Driven Development.** Toda feature começa por uma especificação, não por código, usando
o [Spec Kit](https://github.com/github/spec-kit):

```
specify → clarify → plan → tasks → analyze → implement
```

A `spec.md` descreve o quê e o porquê; o `plan.md`, as decisões técnicas. Avaliações de ideias
e investigações de bugs ficam registradas em `.specify/assessments/` e `.specify/bugs/`.

**Feature nova, de dentro para fora:** regra no domínio → portas → caso de uso → adapter de
persistência → controller → registro do bean.

As regras completas para quem (ou o quê) escreve código aqui estão em [`AGENTS.md`](AGENTS.md).

## Roadmap

- [x] Modelo de domínio completo, com testes unitários
- [x] Cadastro de imóveis (domínio → caso de uso → persistência → API)
- [ ] Esquema de banco versionado e regras impostas no PostgreSQL
      (avaliação aprovada em `.specify/assessments/better-db-schema/`)
- [ ] Portas, casos de uso e API para `Inquilino`, `Contrato`, `Fatura` e `Usuario`
- [ ] Emissão mensal de faturas e marcação de atraso
- [ ] Registro de pagamentos

## Contribuindo

Issues e pull requests são bem-vindos. Antes de abrir um PR:

1. Leia o [`AGENTS.md`](AGENTS.md) — ele vale para pessoas também.
2. Feature nova começa por uma spec; correção pontual pode ir direto.
3. Escreva o teste antes do código e rode `./mvnw test` antes de enviar.
4. Mantenha `domain/` e `application/` livres de imports de framework.
