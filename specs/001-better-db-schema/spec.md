# Feature Specification: `imoveis` como tabela-modelo com estrutura versionada e regras no banco

**Feature Branch**: `001-better-db-schema`

**Created**: 2026-10-29

**Status**: Draft

**Input**: User description: "usando o handoff do @.specify/assessments/better-db-schema/decision.md de better-db-schema"

**Origem**: assessment `better-db-schema` (veredito **go**, opção C). Artefatos:
`.specify/assessments/better-db-schema/{intake,research,problem,concept,decision}.md`.

## Contexto

Hoje as regras de negócio de um imóvel (endereço obrigatório e não vazio, valor sugerido
obrigatório e maior que zero) vivem só no domínio. O banco de produção (PostgreSQL) impõe
apenas 2 das 5 garantias decididas. A estrutura do banco é alterada automaticamente a partir
do código, sem registro do que mudou, e nenhum teste exercita a persistência contra o banco
real. `imoveis` é hoje a única tabela e vai servir de **modelo** para as tabelas de
inquilinos, contratos, faturas e usuários. Esta feature acerta `imoveis` e registra o padrão
antes que ele seja copiado quatro vezes.

O PostgreSQL é citado pelo nome porque é uma restrição já decidida pelo dono (banco de
produção), não uma escolha de implementação desta spec. Ferramentas e mecanismos ficam para
o `plan.md`.

## Clarifications

### Session 2026-10-29

- Q: Quantas casas decimais o valor sugerido guarda, e o que acontece com um valor que tenha mais casas? → A: 4 casas decimais (folga para cálculos como juros e rateio). A exibição em centavos é assunto de quem apresenta o valor, não do armazenamento.
- Q: O dono confirma formalmente a nova dependência (ferramenta de versionamento de estrutura) e o modo "só conferir" contra o PostgreSQL? → A: Sim, as duas (acordo da constitution §IV dado). A escolha da ferramenta fica no plan.
- Q: A versão do Redis usada nos testes também deve ser fixada? → A: Sim, fixar junto com a do PostgreSQL.
- Q: O domínio deve arredondar o valor sugerido para 4 casas, para que memória e banco coincidam? → A: Não. O domínio não muda; só o banco arredonda. A diferença entre o valor em memória e o gravado (abaixo de 4 casas) e a rejeição de valores que arredondam para zero pelo banco são aceitas.
- Q: O endereço deve ter tamanho máximo, imposto pelo banco e pelo domínio? → A: Sim, 255 caracteres, validado no domínio (mensagem em português) e no banco.
- Q: Ao subir a aplicação sem escolher nada, qual modo roda? → A: O rápido (banco em memória), como hoje. O modo produção (PostgreSQL, registros versionados, só conferir) é ativado explicitamente.

## User Scenarios & Testing *(mandatory)*

O "usuário" desta feature é o **desenvolvedor do projeto** (e os agentes de código que seguem
o AGENTS.md). O cliente operador do sistema é afetado só indiretamente: dado inconsistente de
imóvel contaminaria contratos e cobranças.

### User Story 1 - O banco de produção rejeita imóvel inválido (Priority: P1)

Como desenvolvedor, quero que o banco de produção recuse por conta própria um imóvel com
endereço ausente ou em branco, ou com valor sugerido ausente, zero ou negativo. Assim, uma
escrita que contorne o domínio (script, correção manual, bug em adapter) não grava dado que
depois alimente contratos e cobranças.

**Why this priority**: é o núcleo do problema (objetivo G1) e o que protege dinheiro. Sozinha,
esta história já entrega valor: o banco passa a ser uma segunda barreira para as regras simples.

**Independent Test**: contra um banco de produção de versão fixa, tentar gravar diretamente,
sem passar pelo domínio, cada combinação inválida e verificar que a gravação é rejeitada. Em
seguida, gravar um imóvel válido e verificar que é aceito.

**Acceptance Scenarios**:

1. **Given** um banco de produção com a estrutura de `imoveis` aplicada, **When** se grava
   diretamente um imóvel sem endereço, **Then** a gravação é rejeitada e nenhum registro é
   criado.
2. **Given** o mesmo banco, **When** se grava diretamente um imóvel com endereço vazio ou
   composto só de espaços, **Then** a gravação é rejeitada.
3. **Given** o mesmo banco, **When** se grava diretamente um imóvel com endereço de 256
   caracteres, **Then** a gravação é rejeitada; com 255 caracteres, é aceita.
4. **Given** o mesmo banco, **When** se grava diretamente um imóvel sem valor sugerido,
   **Then** a gravação é rejeitada.
5. **Given** o mesmo banco, **When** se grava diretamente um imóvel com valor sugerido igual a
   zero ou negativo, **Then** a gravação é rejeitada.
6. **Given** o mesmo banco, **When** se grava um imóvel com endereço preenchido e valor
   sugerido positivo com até 4 casas decimais, **Then** a gravação é aceita e o valor lido
   de volta é exatamente o valor gravado.
7. **Given** o mesmo banco, **When** se grava um imóvel com valor sugerido positivo com mais
   de 4 casas decimais (ex.: 1500.12345), **Then** a gravação é aceita e o valor é guardado
   arredondado para 4 casas (1500.1235).
8. **Given** um imóvel já gravado com o endereço "Rua A, 100, apto 101", **When** se grava
   outro imóvel com o mesmo endereço, **Then** a gravação é aceita: endereço não é único.

---

### User Story 2 - A estrutura do banco de produção é versionada e conferida na subida (Priority: P2)

Como desenvolvedor, quero que a estrutura do banco de produção seja definida por registros
versionados no repositório e aplicada em ordem. Quero também que a aplicação, ao subir contra
esse banco, apenas confira se o seu mapeamento bate com a estrutura e se recuse a iniciar
quando houver divergência, em vez de alterar o banco silenciosamente.

**Why this priority**: é o que impede perda silenciosa de dados quando houver produção com
dados reais (objetivo G2). Depende da definição das regras da história 1 para ter o que
versionar, mas pode ser testada à parte.

**Independent Test**: subir a aplicação contra um banco de produção vazio e verificar que a
estrutura é criada a partir dos registros versionados. Depois, subir contra um banco cuja
estrutura difere do mapeamento e verificar que a aplicação não inicia e que o banco não foi
alterado.

**Acceptance Scenarios**:

1. **Given** um banco de produção vazio, **When** a aplicação sobe apontando para ele,
   **Then** a estrutura de `imoveis` é criada a partir dos registros versionados, com todas
   as regras da história 1, e a aplicação inicia normalmente.
2. **Given** um banco de produção em que todos os registros versionados já foram aplicados,
   **When** a aplicação sobe de novo, **Then** nenhum registro é reaplicado e a aplicação
   inicia normalmente.
3. **Given** um banco de produção cuja estrutura de `imoveis` diverge do que a aplicação
   espera (por exemplo, uma coluna esperada ausente ou com tipo diferente), **When** a
   aplicação sobe, **Then** ela se recusa a iniciar, informa a divergência e não altera a
   estrutura do banco.
4. **Given** um registro versionado já aplicado ao banco, **When** o conteúdo desse registro é
   modificado no repositório e a aplicação sobe, **Then** a modificação é detectada e a
   aplicação se recusa a iniciar.
5. **Given** nenhuma escolha de modo, **When** a aplicação sobe, **Then** ela roda no modo de
   uso rápido (banco em memória), como hoje, com a estrutura gerada automaticamente, sem
   exigir os registros versionados nem um PostgreSQL disponível.
6. **Given** o modo produção ativado explicitamente, **When** a aplicação sobe, **Then** ela
   usa o PostgreSQL, aplica os registros versionados pendentes e só confere a estrutura.

---

### User Story 3 - O padrão fica documentado para os próximos agregados (Priority: P3)

Como desenvolvedor (ou agente de código), ao persistir o próximo agregado (`Inquilino`,
`Contrato`, `Fatura` ou `Usuario`), quero encontrar escrito o padrão decidido aqui: convenção
de nomes, tipo e precisão de valores monetários, tipo de chave, como evoluir a estrutura e onde
mora cada regra (domínio, banco ou ambos). Assim não preciso redecidir essas questões.

**Why this priority**: é o objetivo G4, cujo valor aparece na próxima feature de persistência.
Depende das histórias 1 e 2 para ter o que documentar.

**Independent Test**: um leitor que não participou desta feature consegue, só com a
documentação, responder às perguntas do padrão listadas em FR-012 para um agregado novo.

**Acceptance Scenarios**:

1. **Given** a documentação do padrão publicada no repositório, **When** um desenvolvedor
   planeja persistir `Contrato`, **Then** ele encontra a resposta para cada item de FR-012
   sem precisar consultar os artefatos do assessment.
2. **Given** a documentação, **When** ela é comparada com a estrutura real de `imoveis`,
   **Then** não há contradição entre as duas.

---

### Edge Cases

- **Endereço só com espaços**: o domínio já o rejeita como vazio. O banco também o rejeita,
  não apenas a "string de tamanho zero", cobrindo pelo menos os espaços ASCII (FR-002). Um
  endereço só com espaços Unicode exóticos (ex.: `\u2003`) é barrado pelo domínio, mas não
  pelo banco: divergência aceita.
- **Valor com mais de 4 casas decimais**: é guardado arredondado para 4 casas (ver FR-004).
  O arredondamento ocorre abaixo do centavo, então não altera nenhum valor exibido em reais.
- **Valor positivo que arredonda para zero** (ex.: 0.00001): o banco o rejeita, porque o valor
  guardado deixaria de ser maior que zero, embora o domínio o aceite. Divergência **aceita**
  (ver Clarifications): nenhuma regra nova no domínio para esse caso.
- **Valor em memória × valor gravado**: ao cadastrar 1500.12345, o objeto de domínio mantém
  1500.12345 e o banco guarda 1500.1235. Divergência **aceita**: ela só aparece abaixo da 4ª
  casa decimal.
- **Valor acima da capacidade máxima do campo**: a gravação é rejeitada, não truncada.
- **Endereço repetido**: aceito normalmente, sem erro nem aviso.
- **Endereço no limite**: 255 caracteres é aceito; 256 é rejeitado no domínio (mensagem em
  português) e, se o domínio for contornado, no banco. O limite conta caracteres, não bytes:
  acentos ("São João") contam como um caractere cada.
- **Banco de produção que já tenha `imoveis` criada pela geração automática**: fora do
  previsto. Assume-se que não existe nenhum banco de produção com dados reais (ver
  Assumptions); o primeiro registro versionado cria a tabela do zero.
- **Imóvel inativo lido do banco**: existe um defeito conhecido de reidratação (todo imóvel
  volta como ativo). Ele fica fora do escopo, e os testes desta feature não podem depender da
  leitura do estado `ativo`.
- **Divergência entre o modo de uso rápido e o banco de produção**: o banco em memória pode
  aceitar dado que o de produção rejeita. Isso é aceito conscientemente: a prova das regras
  roda no banco de produção.

## Requirements *(mandatory)*

### Functional Requirements

**Regras de `imoveis` no banco de produção (G1)**

- **FR-001**: O banco de produção MUST rejeitar a gravação de um imóvel sem endereço.
- **FR-002**: O banco de produção MUST rejeitar a gravação de um imóvel cujo endereço seja
  vazio ou composto apenas de espaços em branco, cobrindo **pelo menos** espaço, tab, quebra
  de linha, retorno de carro, form feed e tab vertical. O domínio continua mais estrito
  (também trata espaços Unicode como branco); essa diferença é aceita, porque o
  domínio é a primeira barreira e o banco a segunda.
- **FR-002a**: O endereço MUST ter no máximo **255 caracteres**. O banco de produção MUST
  rejeitar endereço mais longo, e o domínio MUST rejeitá-lo na construção de `Imovel` com
  `IllegalArgumentException` e mensagem em português que diga o limite (regra nova, em TDD).
- **FR-003**: O banco de produção MUST rejeitar a gravação de um imóvel sem valor sugerido ou
  com valor sugerido menor ou igual a zero.
- **FR-004**: O valor sugerido MUST ser armazenado com precisão e número de casas decimais
  declarados explicitamente na estrutura, nunca com um padrão implícito, com **4 casas
  decimais**. Um valor com até 4 casas MUST ser guardado e lido exatamente. Um valor com mais
  de 4 casas MUST ser guardado arredondado para 4 casas. A capacidade total (dígitos antes da
  vírgula) MUST comportar com folga qualquer valor de aluguel; o número exato é decidido no
  plan.
- **FR-005**: O banco de produção MUST aceitar vários imóveis com o mesmo endereço.
- **FR-006**: O domínio MUST continuar validando todas as regras de `Imovel` como hoje, mais
  o limite de tamanho do endereço (FR-002a), que é a única regra nova no domínio. O banco é
  uma segunda barreira, não substitui a validação do domínio. As mensagens de erro existentes
  (em português) não mudam.

**Estrutura versionada e conferência (G2)**

- **FR-007**: A estrutura do banco de produção MUST ser definida exclusivamente por registros
  versionados no repositório, aplicados em ordem e uma única vez cada.
- **FR-008**: Ao subir contra o banco de produção, a aplicação MUST NOT criar, alterar ou
  remover estrutura por geração automática. Ela MUST apenas conferir se o mapeamento bate com
  a estrutura existente e MUST se recusar a iniciar quando houver divergência.
- **FR-009**: A modificação de um registro versionado já aplicado MUST ser detectada na subida
  e impedir a aplicação de iniciar.
- **FR-010**: O modo de uso rápido (banco em memória) MUST continuar disponível e funcionando
  como hoje, com estrutura gerada automaticamente e sem depender dos registros versionados.
  Rodar como produção (contra PostgreSQL) e rodar rápido MUST ser modos claramente separados.
  O modo rápido MUST continuar sendo o padrão quando nenhum modo é escolhido; o modo
  produção MUST ser ativado explicitamente.

**Prova contra o banco real (G3)**

- **FR-011**: Cada regra de FR-001 a FR-005 (incluindo FR-002a) MUST ter ao menos um teste automatizado que grava
  diretamente no banco de produção, contornando o domínio, e verifica a rejeição (ou a
  aceitação, no caso de FR-005 e do caso válido). Esses testes MUST rodar sempre contra a
  mesma versão fixa do banco de produção, e MUST NOT depender da leitura do estado `ativo`.

**Padrão documentado (G4)**

- **FR-012**: O repositório MUST conter documentação do padrão de persistência que responda,
  pelo menos: (a) convenção de nomes de tabelas e colunas; (b) tipo, precisão e casas decimais
  para valores monetários; (c) tipo e geração da chave primária; (d) critério para decidir se
  uma regra vai também ao banco (regra simples sobre um membro da própria entidade) ou fica só
  na aplicação (regra que depende de outros objetos ou de estado externo); (e) como criar e
  evoluir a estrutura por registros versionados; (f) como escrever o teste de rejeição contra
  o banco de produção.
- **FR-013**: AGENTS.md MUST passar a apontar para essa documentação e refletir a mudança na
  forma de subir contra o banco de produção, para que os dois não fiquem inconsistentes.

**Governança**

- **FR-014**: A adição de uma ferramenta de versionamento de estrutura como dependência do
  projeto e a troca para o modo "só conferir" contra o banco de produção MUST ter acordo
  explícito do dono, conforme a constitution §IV. **Acordo dado em 2026-10-29** para as duas
  mudanças (ver Clarifications). A escolha entre as ferramentas fica no plan e não exige novo
  acordo, desde que seja uma só.
- **FR-015**: As versões do banco de produção (PostgreSQL) e do Redis usadas nos testes MUST
  ser fixas e declaradas explicitamente (não "a mais recente").

### Key Entities *(include if feature involves data)*

- **Imóvel (tabela `imoveis`)**: unidade locável. Atributos: identificador gerado pelo banco;
  endereço (texto obrigatório, não em branco, até 255 caracteres, não único, inclui a
  numeração da unidade);
  valor sugerido (monetário, obrigatório, maior que zero, precisão explícita); ativo (indica
  desativação lógica; nada é apagado de fato).
- **Registro versionado de estrutura**: unidade ordenada e imutável que descreve uma mudança
  de estrutura do banco de produção. O conjunto aplicado em ordem reproduz a estrutura em
  qualquer ambiente. O banco guarda quais registros já foram aplicados.
- **Documento do padrão de persistência**: referência escrita para os próximos agregados
  (FR-012).

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001 (M1)**: as 6 garantias de `imoveis` (endereço não nulo, endereço não em branco,
  endereço com até 255 caracteres, valor não nulo, valor > 0, precisão explícita do valor)
  são impostas pelo próprio banco de produção e declaradas explicitamente: de 2 de 5 hoje
  (o limite de 255 existe só como padrão implícito) para **6 de 6**.
- **SC-002 (M2)**: há ao menos **1 teste de rejeição contra o banco de produção por regra**,
  totalizando **≥ 5** (hoje: 0), além do teste do caso válido e do endereço repetido.
- **SC-003 (M3)**: **100%** das mudanças de estrutura do banco de produção têm registro
  versionado no repositório (hoje: 0%).
- **SC-004 (M4)**: subir a aplicação contra um banco de produção com estrutura divergente
  resulta em falha de inicialização **em 100% das tentativas**, sem nenhuma alteração no banco
  (hoje: o banco é alterado silenciosamente).
- **SC-005 (M5)**: os testes rodam **sempre contra as mesmas versões declaradas** do
  PostgreSQL e do Redis (hoje: "a mais recente", variável).
- **SC-006**: um banco de produção vazio chega à estrutura completa de `imoveis` só pela
  subida da aplicação, sem nenhum passo manual.
- **SC-007**: o modo de uso rápido continua subindo e a suíte de testes de domínio e de casos
  de uso continua verde, sem nenhum teste existente alterado.
- **SC-008 (M6, qualitativa)**: na próxima feature de persistência, nenhuma das decisões de
  FR-012 precisa ser rediscutida. Verificado na revisão dessa feature.

## Assumptions

- Não existe hoje nenhum banco de produção com dados reais de `imoveis`. O primeiro registro
  versionado cria a tabela do zero, sem migrar dados.
- A numeração da unidade (ex.: "apto 101") continua dentro do texto do endereço. Não vira
  campo próprio.
- Docker está disponível onde a suíte roda (os testes contra o banco de produção usam
  containers). Se não estiver, o relatório de implementação diz que eles não rodaram.
- Valores com mais de 4 casas decimais são arredondados pelo banco, sem regra nova no domínio
  que os rejeite. Se for preciso rejeitá-los, isso é regra nova de domínio e volta para a
  spec.
- A escolha da ferramenta de versionamento (Flyway ou Liquibase) é decisão do `plan.md`, com
  prazo curto. Para uma tabela e um banco só, qualquer uma atende esta spec.
- A identificação do imóvel continua sendo um número gerado pelo banco. Troca por UUID ou
  outro tipo, por motivo de desempenho, está fora de escopo (volume de ~14 imóveis).
- O banco em memória do modo rápido pode divergir do de produção. Esse risco é aceito, porque
  as regras continuam no domínio e são provadas no banco de produção.

## Out of Scope

- Persistir `Inquilino`, `Contrato`, `Fatura` ou `Usuario` (apenas seguirão o padrão depois).
- Criptografia do CPF. Fica para quando `Inquilino` for persistido; o padrão não deve
  impedi-la.
- Corrigir o defeito de reidratação (imóvel inativo voltando como ativo). Vai à parte via
  `/speckit-bug-assess`.
- Fazer o banco em memória seguir os registros versionados.
- Unicidade de endereço (revogada) e campo próprio para numeração da unidade.
- Levar ao banco regras que dependem de outros objetos ou de estado externo.
- Casos de uso novos de imóvel (listar, buscar, inativar via API).
- Otimização de desempenho.
- Infraestrutura e implantação de produção (provisionar o banco, pipeline de deploy).
