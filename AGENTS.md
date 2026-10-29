# AGENTS.md — habitattio

Gestão de pagamentos de aluguéis de inquilinos. Java 25 + Spring Boot 4.1.2, arquitetura
hexagonal, TDD e SDD (Spec-Driven Development via Spec Kit).

Este arquivo é a instrução operacional para agentes de código. Leia por completo antes da
primeira alteração.

---

## 1. Comandos

Sempre use o wrapper (`./mvnw`), nunca um `mvn` global.

```bash
./mvnw compile                       # compila
./mvnw test                          # roda toda a suíte
./mvnw test -Dtest=FaturaTest        # uma classe de teste
./mvnw test -Dtest=FaturaTest#deveMarcarComoPagaQuandoValorIntegral   # um método
./mvnw spring-boot:run               # sobe a app (H2 em memória, porta 8080)
./mvnw verify                        # build completo
```

Os testes de integração usam **Testcontainers** (PostgreSQL e Redis) — exigem Docker rodando.
Se o Docker não estiver disponível, rode apenas os testes de domínio/aplicação por `-Dtest=...`
e diga explicitamente no relatório que os de integração não foram executados. Nunca declare a
suíte verde sem tê-la executado.

Para subir a app com os containers em vez do H2, use `TestHabitattioApplication`
(`src/test/java/br/com/marden/habitattio/TestHabitattioApplication.java`).

---

## 2. Arquitetura hexagonal — a regra que não se quebra

**As dependências apontam para dentro.** O domínio não conhece ninguém; a aplicação conhece o
domínio e as portas; os adapters conhecem as portas. Nunca o contrário.

```
src/main/java/br/com/marden/habitattio/
├── domain/model/                     # entidades e regras de negócio — Java puro
├── ports/
│   ├── inbound/                      # commands/queries: o que o mundo pede à aplicação
│   └── outbound/                     # interfaces que a aplicação exige do mundo
├── application/usecase/              # orquestração: um caso de uso por classe
└── adapters/
    ├── inbound/rest/                 # controllers, requests/responses HTTP
    └── outbound/persistence/         # adapter + repositório Spring Data + entity JPA
```

Regras concretas, verificáveis em revisão:

- **`domain/` e `application/` não importam `org.springframework`, `jakarta.persistence` nem
  nada de infraestrutura.** Nem `@Component`, nem `@Service`, nem `@Entity`, nem `@Transactional`.
  Se você sentiu necessidade de uma anotação ali, o desenho está errado — a dependência deve
  virar uma porta.
- **`domain/` não importa de `application/`, `ports/` ou `adapters/`.**
- **Um caso de uso depende apenas de interfaces de `ports/outbound/`**, recebidas por construtor.
  Sem injeção por campo, sem `@Autowired`.
- **O método público de um caso de uso se chama `executar(...)`** e recebe um command de
  `ports/inbound/`. Um caso de uso = uma operação de negócio.
- **A entity JPA nunca vaza da camada de persistência.** O adapter traduz
  `ImovelJpaEntity ↔ Imovel` nas duas direções. Nenhuma classe fora de
  `adapters/outbound/persistence` enxerga `*JpaEntity`.
- **O DTO HTTP nunca entra no caso de uso.** O controller converte `*Request → *Command` e
  `domínio → *Response`. `*Request`/`*Response` são records aninhados no próprio controller
  (ver `ImovelController`).
- **Toda classe de `application/` e `adapters/` é instanciável por construtor em um teste**,
  sem contexto Spring. Isso é o teste prático da arquitetura: se precisa do Spring para testar,
  está acoplado demais.

### Fiação do Spring

Como o domínio e os casos de uso não têm anotações, os beans são declarados **explicitamente**
numa classe de configuração em `adapters/` (ou num pacote `config/`), com `@Configuration` e
métodos `@Bean`. Este é o único lugar onde o Spring e o domínio se encontram.

> ⚠️ **Hoje essa configuração não existe e a suíte está vermelha por causa disso.**
> `CadastrarImovelUseCase` e `ImovelRepositoryAdapter` não são beans nem estão declarados em
> nenhum `@Configuration`, então o contexto não sobe. `./mvnw test` falha com:
>
> ```
> Parameter 0 of constructor in ...adapters.inbound.rest.ImovelController required a bean
> of type '...application.usecase.CadastrarImovelUseCase' that could not be found.
> ```
>
> A primeira tarefa de quem mexer nessa área é criar essa configuração, declarando
> `ImovelRepositoryAdapter` (recebendo `SpringDataImovelRepository`) e `CadastrarImovelUseCase`
> (recebendo a porta) como `@Bean`.

### Ao adicionar uma feature, a ordem é de dentro para fora

1. Regra no `domain/model/` (com teste unitário primeiro).
2. Porta inbound (command) e porta outbound (interface), se necessárias.
3. Caso de uso em `application/usecase/` (teste com fake/stub da porta).
4. Adapter de persistência + entity JPA (teste de integração).
5. Controller (teste de web layer).
6. Registro do bean na configuração.

---

## 3. TDD — não negociável

**Red → Green → Refactor.** O teste vem antes da implementação, sempre. Não escreva código de
produção sem um teste falhando que o justifique.

- Escreva o teste, **rode e veja falhar** pelo motivo esperado. Um teste que passa de primeira
  não provou nada — ou está errado, ou o código já existia.
- Implemente o mínimo para passar. Só então refatore, com a suíte verde.
- Um comportamento por teste. Nome do teste descreve o comportamento em português:
  `deveRejeitarValorNegativo`, `deveMarcarFaturaComoAtrasadaAposVencimento`.
- Estrutura **Arrange / Act / Assert**, separados por linha em branco.
- Nunca enfraqueça ou comente um teste para fazer o build passar. Se um teste existente quebra,
  ou a mudança está errada, ou a regra mudou de propósito — e nesse caso o teste é atualizado
  deliberadamente, e você diz isso no relatório.

### Onde cada teste vive

| Alvo | Local | Ferramenta | Spring? |
|---|---|---|---|
| Entidades de domínio | `src/test/java/.../domain/model/` | JUnit 5 puro | não |
| Casos de uso | `src/test/java/.../application/usecase/` | JUnit 5 + fake da porta | não |
| Adapter de persistência | `src/test/java/.../adapters/outbound/` | `@DataJpaTest` + Testcontainers | sim |
| Controller | `src/test/java/.../adapters/inbound/rest/` | `@WebMvcTest` + MockMvc | sim |
| Contexto / fiação | `src/test/java/.../HabitattioApplicationTests.java` | `@SpringBootTest` | sim |

A maior parte dos testes deve ser da primeira metade da tabela: domínio e casos de uso são
rápidos, não precisam de container e cobrem as regras que importam. Prefira **fakes escritos à
mão** implementando a porta (ex.: um `ImovelRepositoryFake` com `Map` em memória) a mocks —
são mais legíveis e não acoplam o teste à ordem de chamadas. Use mock só para verificar
interação que realmente é o comportamento sob teste.

> ⚠️ **Hoje não existe nenhum teste de domínio ou de caso de uso** — só o `contextLoads`.
> Código já escrito sem teste (ver §6) deve ganhar cobertura antes de ser alterado.

---

## 4. SDD — Spec-Driven Development (Spec Kit)

Toda feature começa por uma especificação escrita, não por código. O Spec Kit está instalado em
`.specify/` e exposto como skills do Claude Code.

Fluxo para uma feature nova:

1. `/speckit.specify` — descreve a feature em linguagem natural; gera `spec.md` (o **quê** e o
   **por quê**, sem decisões técnicas).
2. `/speckit.clarify` — responde as ambiguidades; as respostas voltam para o `spec.md`.
3. `/speckit.plan` — gera o `plan.md` com o desenho técnico (aqui sim: portas, adapters, schema).
4. `/speckit.tasks` — gera `tasks.md`, ordenado por dependência.
5. `/speckit.analyze` — checa consistência entre spec, plan e tasks antes de codar.
6. `/speckit.implement` — executa as tasks, em TDD.

Regras de uso:

- **Não implemente feature sem spec.** Se o pedido chega direto como código e é maior que um
  ajuste pontual, proponha rodar `/speckit.specify` primeiro.
- A `spec.md` descreve comportamento observável e regras de negócio. Decisão de tecnologia é
  assunto do `plan.md`.
- Mudou o escopo no meio? Atualize a spec e o plan. O código não é a fonte da verdade do
  requisito — a spec é.
- Correção de bug trivial, renomeação, ajuste de formatação: direto, sem spec.

> ⚠️ **A constitution (`.specify/memory/constitution.md`) ainda é o template em branco**, com
> todos os placeholders `[PRINCIPLE_N_NAME]` intactos. Rode `/speckit.constitution` para
> ratificá-la com os princípios deste projeto (hexagonal, TDD, SDD, simplicidade). Até então,
> **este AGENTS.md é a referência vigente**, e os dois precisam ficar consistentes depois.

---

## 5. Convenções de código

- **Domínio em português**, alinhado ao vocabulário do negócio: `Imovel`, `Inquilino`,
  `Contrato`, `Fatura`, `Usuario`, `valorAluguel`, `diaVencimento`, `mesReferencia`.
  Verbos de negócio também: `pagar()`, `encerrar()`, `inativar()`, `executar()`.
- **Sufixos técnicos em inglês**, pela convenção do framework: `...Port`, `...Adapter`,
  `...UseCase`, `...Command`, `...Request`, `...Response`, `...JpaEntity`, `...Controller`.
- **Dinheiro é `BigDecimal`**, nunca `double` ou `float`. Compare com `compareTo`, jamais com
  `equals` ou `==`.
- **Datas são `java.time`**: `LocalDate` para vencimento e competência.
- **Records** para commands e DTOs (imutáveis, sem lógica). **Classes** para entidades de
  domínio, que têm comportamento e invariantes.
- **Campos `private final`** sempre que possível; dependências recebidas por construtor.
- **Validação de invariante dentro do construtor da entidade.** Um objeto de domínio nunca
  existe em estado inválido. Lance `IllegalArgumentException` com mensagem em português,
  dizendo o que está errado (ver `Contrato`, que valida `diaVencimento` no construtor).
- `@Valid` / Bean Validation ficam no DTO de entrada do controller — borda, não domínio.
- Sem Lombok no projeto: getters escritos à mão. Não introduza Lombok sem acordo.
- Imports explícitos, sem wildcard.

---

## 6. Modelo de domínio atual

| Classe | Estado | Papel |
|---|---|---|
| `Imovel` | id, endereco, valorSugerido, ativo | unidade locável; `inativar()` |
| `Inquilino` | id, nome, cpf, telefone | locatário |
| `Contrato` | imovelId, inquilinoId, valorAluguel, diaVencimento, dataInicio, ativo | vínculo inquilino↔imóvel; `encerrar()` |
| `Fatura` | contratoId, mesReferencia, valor, dataVencimento, status | cobrança mensal; `pagar(valorPago)` |
| `Usuario` | id, nome, email, hash_senha | operador do sistema |

`Fatura.StatusFatura`: `PENDENTE`, `PAGA`, `ATRASADA`.

As entidades referenciam-se por **id** (`Long contratoId`), não por objeto. Mantenha isso: cada
agregado é carregado e persistido por sua própria porta.

### Dívidas conhecidas no domínio (corrigir com teste primeiro)

- `Imovel.validar(...)` existe, é `private` e **nunca é chamada** — o construtor não valida nada.
- `Fatura.pagar(valorPago)` **ignora `valorPago` na comparação com `valor`**: marca `PAGA` em
  qualquer pagamento positivo, inclusive parcial. Não existe registro do valor pago nem da data.
- Nada transiciona uma fatura para `ATRASADA`; a regra de vencimento não está implementada.
- `Usuario.hash_senha` / `getHash_senha()` fogem do camelCase do resto do código; devem virar
  `senhaHash` / `getSenhaHash()`. Nenhuma entidade de domínio valida CPF, e-mail ou telefone.
- `Inquilino` e `Usuario` não validam nada no construtor — aceitam `null` e string vazia.
- Fora de `Imovel`, nenhum agregado tem porta, caso de uso ou adapter.

Não "conserte" isso de passagem. Escreva o teste que expõe o defeito, veja falhar, corrija.

---

## 7. Regras de negócio a respeitar

- Uma fatura pertence a exatamente um contrato e a um `mesReferencia`; não emita duas faturas
  para o mesmo contrato e mesma competência.
- `diaVencimento` ∈ [1, 31]. Para meses mais curtos que o dia configurado, o vencimento cai no
  último dia do mês.
- Valores monetários são estritamente positivos.
- Contrato encerrado não gera novas faturas.
- Imóvel inativo não recebe novo contrato.
- Fatura `PAGA` é terminal: não volta para `PENDENTE` nem para `ATRASADA`.
- Nada é apagado de fato: `Imovel.inativar()` e `Contrato.encerrar()` são desativação lógica.

Se uma regra necessária não estiver aqui nem na spec, **pergunte** em vez de inventar — regra
de cobrança errada vira dinheiro errado.

---

## 8. Definition of Done

Antes de dizer que terminou:

- [ ] Existe teste escrito **antes** do código, que falhou e agora passa.
- [ ] `./mvnw test` executado. Se os de integração não rodaram (sem Docker), isso está dito
      explicitamente no relatório.
- [ ] `domain/` e `application/` sem nenhum import de framework.
- [ ] Nenhum `*JpaEntity` nem DTO HTTP cruzando fronteira de camada.
- [ ] Beans novos registrados na configuração.
- [ ] Spec e tasks atualizados, se o escopo mudou.
- [ ] Nomes em português no domínio, mensagens de erro em português.

Relate o que foi verificado e o que não foi. Não afirme que algo passa sem ter rodado.

---

## 9. Limites

- Não adicione dependência no `pom.xml` sem pedir — o stack atual (Web MVC, Data JPA, Redis,
  Validation, PostgreSQL/H2, Testcontainers) cobre o escopo.
- Não troque H2 por outro banco em `application.properties` nem mude `ddl-auto` sem acordo.
- Não introduza framework de mocking, Lombok, MapStruct ou mapeador automático sem acordo:
  o mapeamento manual nos adapters é deliberado e mantém a fronteira visível.
- Não crie camada de abstração "para o futuro". YAGNI: porta se cria quando há um caso de uso
  que precisa dela.
- Não altere nada em `.specify/scripts/` ou `.specify/templates/`.
