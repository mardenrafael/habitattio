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

Sem profile, a app sobe no **modo rápido** (H2 em memória, estrutura gerada pelo Hibernate,
Flyway desligado). O **modo produção** é o profile `prod`: PostgreSQL, estrutura criada só
pelas migrações Flyway e `ddl-auto=validate` (a app se recusa a subir se o mapeamento diverge
do banco). A conexão vem de variáveis de ambiente, nunca de arquivo:

```bash
SPRING_PROFILES_ACTIVE=prod \
SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/habitattio \
SPRING_DATASOURCE_USERNAME=... SPRING_DATASOURCE_PASSWORD=... \
./mvnw spring-boot:run
```

Para subir em modo produção com os containers em vez de um PostgreSQL próprio, use
`TestHabitattioApplication` (`./mvnw spring-boot:test-run`), que já ativa o profile `prod`
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
em `adapters/config/BeanConfiguration.java`, com `@Configuration` e métodos `@Bean`. Este é o
único lugar onde o Spring e o domínio se encontram. Todo novo adapter/caso de uso ganha seu
`@Bean` ali (ou numa classe de configuração nova, se o arquivo crescer demais).

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
produção sem um teste falhando que o justifique. Isso vale **mesmo quando a spec não detalha
os testes** — a obrigação de TDD é do processo, não da spec.

### Ordem estrita para toda feature nova

1. **Desenhe a interface primeiro, sem lógica**: a porta (`ports/inbound`/`ports/outbound`), a
   assinatura do caso de uso (`executar(...)`) e os models/commands necessários. Só a forma —
   corpo vazio, `return null` ou `throw new UnsupportedOperationException()` onde precisar
   compilar.
2. **Com base nessas assinaturas, escreva os testes** — cobrindo o happy path e todos os casos
   de erro esperados (validação, estado inválido, regra de negócio violada) — **antes de
   qualquer implementação real**.
3. **Rode e veja falhar** pelo motivo esperado (não por erro de compilação acidental, nem por
   um teste mal escrito). Um teste que passa de primeira não provou nada.
4. **Só então implemente de verdade**, até a suíte ficar verde. Refatore com a suíte verde.

Exemplo — feature "pagar fatura": primeiro a porta/assinatura de `PagarFaturaUseCase.executar(...)`
e o `PagarFaturaCommand`; depois os testes (`devePagarFaturaComValorIntegral`,
`deveRejeitarPagamentoParcial`, `deveRejeitarPagamentoDeFaturaJaPaga`, ...); só depois o corpo
real do caso de uso e da regra em `Fatura`.

### Guardrail: teste criado é read-only

**A partir do momento em que um teste é escrito e visto falhar pelo motivo certo, ele não pode
mais ser alterado, apagado, comentado ou pulado (`@Disabled`) para fazer o build passar.** A
única via de produção é implementar o código até o teste passar como está.

- Se a implementação parece exigir mudar o teste, o problema é a implementação, não o teste —
  volte e implemente a regra correta.
- Se um teste existente (de antes desta mudança) quebra com a sua alteração, pare. Ou a
  mudança está errada, ou o requisito realmente mudou de propósito — e nesse segundo caso a
  alteração do teste exige decisão explícita do usuário, nunca uma edição silenciosa do agente.
  Diga isso no relatório antes de tocar no teste.
- Isso não impede **adicionar** testes novos para casos ainda não cobertos — só proíbe editar
  um teste existente para acomodar código que não o satisfaz.
- Um comportamento por teste. Nome do teste descreve o comportamento em português:
  `deveRejeitarValorNegativo`, `deveMarcarFaturaComoAtrasadaAposVencimento`.
- Estrutura **Arrange / Act / Assert**, separados por linha em branco.

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

> A constitution (`.specify/memory/constitution.md`, v1.0.1) define os princípios
> não-negociáveis e prevalece em caso de conflito; este AGENTS.md documenta como aplicá-los no
> dia a dia. Toda emenda da constitution sincroniza a seção correspondente daqui na mesma
> mudança.

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
| `Fatura` | contratoId, mesReferencia, valor, dataVencimento, status, valorPago, dataPagamento | cobrança mensal; `pagar(valorPago, dataPagamento)`, `verificarAtraso(referencia)` |
| `Usuario` | id, nome, email, senhaHash | operador do sistema |

`Fatura.StatusFatura`: `PENDENTE`, `PAGA`, `ATRASADA`. `PAGA` é terminal — `verificarAtraso` não
o sobrescreve.

As entidades referenciam-se por **id** (`Long contratoId`), não por objeto. Mantenha isso: cada
agregado é carregado e persistido por sua própria porta.

Todas as entidades de domínio têm teste unitário cobrindo construção válida, cada rejeição de
invariante e as transições de estado (`src/test/java/.../domain/model/`).

### Padrão de persistência

`imoveis` é a tabela-modelo. **Ao persistir qualquer agregado novo, siga
[`docs/persistencia.md`](docs/persistencia.md)**: nomes de tabela/coluna/constraint, dinheiro
em `numeric(19,4)`, chave `bigint GENERATED BY DEFAULT AS IDENTITY`, invariantes simples
também no banco (`NOT NULL`/`CHECK`/tamanho), estrutura só por migração Flyway
(`src/main/resources/db/migration/V<n>__<descricao>.sql`, nunca editar uma aplicada) e os três
testes obrigatórios (constraints no PostgreSQL, mapeamento no H2, subida em produção). Não
redecida essas questões dentro da feature.

### Dívida restante

- Fora de `Imovel`, nenhum agregado (`Inquilino`, `Contrato`, `Fatura`, `Usuario`) tem porta,
  caso de uso ou adapter — eles existem só como modelo de domínio, sem forma de entrar ou sair
  do sistema. Construir isso é feature nova: passa pelo fluxo SDD (§4) e pela ordem estrita de
  TDD (§3), não por correção pontual.

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
  Validation, PostgreSQL/H2, Flyway, Testcontainers) cobre o escopo.
- Não troque H2 por outro banco em `application.properties` nem mude `ddl-auto` sem acordo. O
  acordado é: `update` no modo rápido (`application.properties`) e `validate` no profile
  `prod` (`application-prod.properties`). Estrutura de produção só muda por migração nova.
- Não introduza framework de mocking, Lombok, MapStruct ou mapeador automático sem acordo:
  o mapeamento manual nos adapters é deliberado e mantém a fronteira visível.
- Não crie camada de abstração "para o futuro". YAGNI: porta se cria quando há um caso de uso
  que precisa dela.
- Não altere nada em `.specify/scripts/` ou `.specify/templates/`.
