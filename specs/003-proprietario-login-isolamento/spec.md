# Feature Specification: Proprietário, login e isolamento de dados (módulo 2)

**Feature Branch**: `003-proprietario-login-isolamento`

**Created**: 2026-10-30

**Status**: Draft

**Input**: User description: "Módulo 2 — Proprietário, login e isolamento de dados. Base: docs/requisitos-e-der.md na main (RF-01 a RF-04, RF-07, RF-08, RNF-12, RNF-15; decisões Q7, Q14, Q15, Q19, Q20, Q25). Escopo: (1) cadastro de proprietário (Usuario) com nome, e-mail válido e único, senha guardada como hash BCrypt; (2) login com Spring Security (dependência já aprovada, Q19); (3) imoveis passa a pertencer a um proprietário (usuario_id NOT NULL) e ganha identificador (obrigatório, não em branco, até 50 caracteres, único por proprietário), via migração Flyway V2 — não há dados de produção; (4) isolamento: o proprietário autenticado só cadastra, vê e altera os próprios imóveis, inclusive contra id adivinhado; POST /imoveis passa a exigir autenticação. Fora do escopo: inquilinos, contratos, faturas, pagamentos, edição/listagem de imóvel além do necessário para provar o isolamento, frontend. Pendente conhecido no módulo 1: erro de validação responde 500 em vez de 400 (D3) — decidir na clarify se entra aqui ou vai via /speckit-bug-assess."

**Origem**: `docs/requisitos-e-der.md` (§1 RF-01–RF-04, RF-07, RF-08; §2 RNF-12, RNF-15; §5
decisões Q7, Q14, Q15, Q19, Q20, Q25; §8 passo 4).

## Contexto

O sistema passou a ser **multi-proprietário** (Q14): vários donos de imóveis usam a mesma
instalação, e nenhum pode enxergar ou mexer nos dados de outro. Hoje não existe proprietário
fora do modelo de domínio, não existe login, e qualquer pessoa que alcance o sistema consegue
cadastrar imóveis, que não pertencem a ninguém e não têm um nome amigável ("Apto 101") além do
endereço.

Este módulo vem antes de inquilinos, contratos e faturas porque todo agregado seguinte depende
de um dono: sem ele, o isolamento teria de ser retrofitado em cada tabela depois.

Algumas restrições já foram decididas pelo dono e são citadas pelo nome porque não são escolha
desta spec: senha guardada como **hash BCrypt** (Q25), autenticação com **Spring Security**
(Q19) e estrutura do banco alterada por **migração versionada** (módulo 1). Mecanismos
concretos (sessão ou token, formato das rotas, nomes de colunas) ficam para o `plan.md`.

## Clarifications

### Session 2026-10-30

- Q: O defeito D3 (dado inválido no cadastro de imóvel responde 500 em vez de 400) é corrigido neste módulo ou em fluxo de bug separado? → A: Neste módulo. Um tratamento de erro comum vale para todas as operações do módulo, incluindo o cadastro de imóvel; D3 fica resolvido por esta spec, sem `/speckit-bug-assess`.
- Q: O dono autoriza adaptar os testes existentes que quebram com dono e identificador obrigatórios (`ImovelTest`, `CadastrarImovelUseCaseTest`, `ImovelRepositoryAdapterTest`, `ImoveisPostgresConstraintsTest`)? → A: Sim, só o mínimo: acrescentar dono e identificador onde os testes constroem ou gravam um imóvel, sem mudar o que cada teste afirma. Qualquer outra mudança nesses testes volta para o dono.
- Q: Quem pode criar conta de proprietário? → A: Qualquer pessoa, sem convite nem aprovação (auto-cadastro aberto). Não há papel de administrador.
- Q: Quais operações sobre imóvel, além do cadastro, este módulo entrega para provar o isolamento? → A: Consultar um imóvel pelo id e inativar um imóvel. Edição (RF-09) e listagem (RF-10) ficam fora.
- Q: A unicidade do identificador por proprietário ignora maiúsculas/minúsculas ("Apto 101" = "APTO 101")? → A: Sim; ignora maiúsculas/minúsculas e espaços nas pontas, mas não acentos. O identificador é exibido como foi digitado, sem os espaços das pontas.
- Q (do plan, P1): O fake privado de `CadastrarImovelUseCaseTest` pode implementar os métodos novos da porta de imóveis, o que vai além de "só dados"? → A: Sim (opção A, 2026-10-30). O fake ganha os métodos novos da porta; asserções e nomes de teste continuam intocados. Mantém uma porta por agregado.
- Q (do analyze, C1): O tratamento global de exceções genéricas (resposta 500 para erro não previsto) entra neste módulo? → A: Não. Vira feature à parte: a API ainda não tem mecanismo global que capture e traduza exceções genéricas. Neste módulo, erros de infraestrutura e de aplicação conhecidos MUST ser tratados explicitamente onde ocorrem, sem depender de captura global.
- Q (do analyze, D1): O acordo Q19 (Spring Security) cobre também o starter de teste `spring-boot-starter-security-test`? → A: Sim.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Proprietário cria a própria conta (Priority: P1)

Uma pessoa que administra imóveis de aluguel se cadastra informando nome, e-mail e senha. A
partir daí ela é um proprietário do sistema. A senha nunca fica guardada de forma legível ou
recuperável, e nenhuma resposta do sistema a devolve.

**Why this priority**: sem proprietário não há a quem atribuir imóveis nem quem autenticar;
todas as outras histórias dependem desta.

**Independent Test**: cadastrar um proprietário e conferir que a conta existe, que o e-mail
repetido é recusado e que a senha guardada não é a senha digitada nem pode ser revertida.

**Acceptance Scenarios**:

1. **Given** nenhum proprietário com o e-mail `ana@exemplo.com`, **When** alguém se cadastra
   com nome "Ana Souza", esse e-mail e uma senha válida, **Then** a conta é criada e a resposta
   traz o identificador, o nome e o e-mail, sem a senha nem o hash.
2. **Given** um proprietário já cadastrado com `ana@exemplo.com`, **When** outra pessoa tenta se
   cadastrar com `ANA@exemplo.com ` (maiúsculas e espaço no fim), **Then** o cadastro é
   recusado com mensagem em português dizendo que o e-mail já está em uso.
3. **Given** o formulário de cadastro, **When** o e-mail é inválido (`ana@`, vazio) ou o nome
   está em branco, **Then** o cadastro é recusado com mensagem em português indicando o campo.
4. **Given** o formulário de cadastro, **When** a senha tem menos de 8 caracteres ou é longa
   demais para ser guardada por inteiro, **Then** o cadastro é recusado com mensagem em
   português; a senha nunca é truncada em silêncio.
5. **Given** uma conta criada com a senha `segredo123`, **When** se inspeciona o que ficou
   guardado, **Then** o valor guardado não contém `segredo123` e dois proprietários com a mesma
   senha têm valores guardados diferentes.

---

### User Story 2 - Proprietário entra no sistema (Priority: P1)

O proprietário informa e-mail e senha e passa a ser reconhecido nas operações seguintes, até
sair ou a sessão expirar. Credenciais erradas não revelam se o e-mail existe.

**Why this priority**: é o que permite saber **quem** está pedindo cada operação — pré-requisito
do isolamento (US3) e da regra "cadastrar imóvel exige autenticação".

**Independent Test**: com uma conta criada, entrar com a senha certa e ser reconhecido; tentar
com senha errada e com e-mail inexistente e receber a mesma recusa.

**Acceptance Scenarios**:

1. **Given** a conta de `ana@exemplo.com` com senha `segredo123`, **When** ela entra com esses
   dados (o e-mail em qualquer combinação de maiúsculas/minúsculas), **Then** o acesso é
   concedido e as operações seguintes são atribuídas à Ana.
2. **Given** a mesma conta, **When** alguém tenta entrar com a senha errada, **Then** o acesso é
   negado com a mensagem "E-mail ou senha inválidos.".
3. **Given** nenhum proprietário com `bruno@exemplo.com`, **When** alguém tenta entrar com esse
   e-mail, **Then** a recusa é idêntica à do cenário 2 (mesma mensagem, mesmo tipo de resposta).
4. **Given** a Ana autenticada, **When** ela sai do sistema, **Then** as operações seguintes
   feitas com o mesmo acesso são recusadas como não autenticadas.
5. **Given** nenhuma autenticação, **When** alguém tenta qualquer operação sobre imóveis,
   **Then** a operação é recusada como não autenticada, sem executar nada.

---

### User Story 3 - Cada proprietário só enxerga e altera os próprios imóveis (Priority: P1)

O proprietário autenticado cadastra imóveis com um identificador amigável ("Apto 101") além do
endereço. O imóvel pertence a quem o cadastrou — o dono vem sempre da autenticação, nunca de um
dado enviado na requisição. Ele consegue consultar e inativar os próprios imóveis; os de outro
proprietário se comportam como se não existissem, mesmo quando o identificador interno é
adivinhado.

**Why this priority**: é o requisito de privacidade central do sistema multi-proprietário
(RF-04, RNF-15); vazar o imóvel de um dono para outro é o pior defeito possível deste módulo.

**Independent Test**: com dois proprietários (Ana e Bruno), cada um cadastra um imóvel; cada um
tenta consultar e inativar o imóvel do outro pelo identificador interno e recebe "não
encontrado", enquanto as mesmas operações sobre o próprio imóvel funcionam.

**Acceptance Scenarios**:

1. **Given** a Ana autenticada, **When** ela cadastra um imóvel com identificador "Apto 101",
   endereço "Rua A, 10" e valor sugerido 1500,00, **Then** o imóvel é criado, ativo, pertence à
   Ana, e a resposta traz identificador, endereço e valor sugerido.
2. **Given** a Ana autenticada, **When** ela envia, junto do cadastro, a indicação de que o dono
   é o Bruno, **Then** essa indicação é ignorada e o imóvel pertence à Ana.
3. **Given** a Ana com um imóvel "Apto 101", **When** ela cadastra outro com identificador
   "Apto 101" (ou " apto 101 "), **Then** o cadastro é recusado com mensagem em português dizendo
   que ela já tem um imóvel com esse identificador.
4. **Given** a Ana com um imóvel "Apto 101", **When** o Bruno cadastra um imóvel "Apto 101",
   **Then** o cadastro é aceito: a unicidade é por proprietário.
5. **Given** a Ana autenticada, **When** ela cadastra um imóvel com identificador em branco ou
   com mais de 50 caracteres, **Then** o cadastro é recusado com mensagem em português.
6. **Given** a Ana com o imóvel de id 7, **When** ela consulta o imóvel 7, **Then** recebe os
   dados dele, incluindo se está ativo.
7. **Given** o imóvel 7 da Ana, **When** o Bruno consulta o imóvel 7, **Then** recebe "não
   encontrado" — a mesma resposta de consultar um id que não existe.
8. **Given** o imóvel 7 da Ana, ativo, **When** o Bruno tenta inativá-lo, **Then** recebe "não
   encontrado" e o imóvel continua ativo.
9. **Given** o imóvel 7 da Ana, ativo, **When** a Ana o inativa, **Then** ele passa a constar
   como inativo numa consulta posterior; inativar de novo não é erro e o deixa inativo.

---

### User Story 4 - O banco de produção também garante dono e identificador (Priority: P2)

As regras novas de imóvel (todo imóvel tem dono; identificador obrigatório, não em branco, até
50 caracteres, único por proprietário) e as de proprietário (e-mail único) valem também no banco
de produção, como já valem as de endereço e valor sugerido desde o módulo 1. A mudança de
estrutura é registrada como uma nova versão, aplicada sobre a versão do módulo 1.

**Why this priority**: o padrão do projeto (RNF-05, RNF-06) é que invariantes simples também
fiquem no banco; sem isso, uma gravação que contorne o domínio poderia criar imóvel sem dono —
uma falha de isolamento silenciosa. Fica em P2 porque o domínio já barra o caso comum.

**Independent Test**: contra o banco de produção, tentar gravar diretamente um imóvel sem dono,
com dono inexistente, com identificador vazio ou longo demais, um identificador repetido para o
mesmo dono e um e-mail de proprietário repetido; todas as gravações são recusadas, e um
identificador repetido para donos diferentes é aceito.

**Acceptance Scenarios**:

1. **Given** um banco de produção vazio, **When** a aplicação sobe, **Then** a nova versão de
   estrutura é aplicada depois da do módulo 1 e a conferência de estrutura na subida passa.
2. **Given** o banco de produção, **When** se grava diretamente um imóvel sem dono ou com dono
   inexistente, **Then** a gravação é recusada.
3. **Given** o banco de produção, **When** se grava diretamente um imóvel com identificador
   vazio, só com espaços, com mais de 50 caracteres ou repetido para o mesmo dono, **Then** a
   gravação é recusada.
4. **Given** o banco de produção, **When** se grava diretamente um proprietário com e-mail já
   usado por outro, **Then** a gravação é recusada.
5. **Given** um proprietário com imóveis, **When** se tenta apagar o proprietário diretamente no
   banco, **Then** a remoção é recusada (nada é apagado de fato, RNF-08).

---

### Edge Cases

- **E-mail com espaços nas pontas ou maiúsculas**: é normalizado (sem espaços nas pontas, em
  minúsculas) antes de validar, guardar, comparar e autenticar; `Ana@Exemplo.com` e
  `ana@exemplo.com` são a mesma conta.
- **Identificador com espaços nas pontas**: os espaços nas pontas são removidos antes de validar
  e guardar; a comparação de unicidade ignora maiúsculas/minúsculas ("Apto 101" = "APTO 101").
  O valor exibido mantém a grafia digitada (sem os espaços das pontas).
- **Identificador com 50 caracteres acentuados**: aceito — o limite conta caracteres, não bytes,
  como no endereço (módulo 1).
- **Dois cadastros simultâneos com o mesmo e-mail** (ou o mesmo identificador para o mesmo
  dono): exatamente um é aceito; o outro recebe a mesma recusa de duplicidade, não um erro
  interno.
- **Senha com caracteres acentuados ou emoji**: aceita, desde que caiba inteira no limite;
  nunca é truncada.
- **Id inexistente × id de outro proprietário**: respostas indistinguíveis ("não encontrado"),
  para não revelar que o imóvel existe.
- **Autenticação expirada no meio do uso**: a operação seguinte é recusada como não
  autenticada; nada é gravado.
- **Proprietário autenticado cuja conta não existe mais**: não acontece neste módulo (não há
  remoção de conta); fica registrado em Out of Scope.
- **Login repetido com senha errada**: neste módulo não há bloqueio nem limitação de tentativas
  (ver Assumptions).

## Requirements *(mandatory)*

### Functional Requirements

**Proprietário (RF-01, RF-02, RNF-12)**

- **FR-001**: O sistema MUST permitir que qualquer pessoa sem autenticação se cadastre como
  proprietário informando nome, e-mail e senha, sem convite nem aprovação de terceiros.
- **FR-002**: O nome MUST ser obrigatório, não em branco e ter no máximo 100 caracteres.
- **FR-003**: O e-mail MUST ser obrigatório, ter formato válido, ter no máximo 254 caracteres e
  ser normalizado (sem espaços nas pontas, em minúsculas) antes de qualquer validação, gravação
  ou comparação.
- **FR-004**: O e-mail MUST ser único entre todos os proprietários, comparado já normalizado.
  Cadastro com e-mail em uso MUST ser recusado com mensagem em português.
- **FR-005**: A senha MUST ter no mínimo 8 caracteres e no máximo o limite que o mecanismo de
  hash consegue considerar por inteiro; senha fora disso MUST ser recusada, nunca truncada.
- **FR-006**: A senha MUST ser guardada apenas como **hash BCrypt** (Q25) — com salt próprio por
  senha e custo ajustável sem migração de dados — e nunca em texto puro nem com criptografia
  reversível.
- **FR-007**: Nenhuma resposta do sistema MUST conter a senha ou o hash da senha.

**Login (RF-03, Q7, Q19)**

- **FR-008**: O proprietário MUST conseguir se autenticar com e-mail e senha; a autenticação é
  feita com **Spring Security** (Q19).
- **FR-009**: Falha de autenticação MUST produzir a mesma resposta e a mesma mensagem ("E-mail ou
  senha inválidos.") para e-mail inexistente e para senha errada.
- **FR-010**: Uma autenticação bem-sucedida MUST valer para as operações seguintes do mesmo
  cliente até o proprietário sair ou o acesso expirar por inatividade.
- **FR-011**: O proprietário MUST conseguir sair (encerrar a autenticação); depois disso, o
  mesmo acesso MUST ser recusado.
- **FR-012**: Cadastro de proprietário e login MUST ser as únicas operações disponíveis sem
  autenticação; toda operação sobre imóveis MUST exigir autenticação e ser recusada sem ela,
  sem efeito colateral.

**Imóvel pertence a um proprietário e tem identificador (RF-07, RF-08, Q15, Q20)**

- **FR-013**: Todo imóvel MUST pertencer a exatamente um proprietário; o domínio MUST recusar a
  criação de um imóvel sem proprietário.
- **FR-014**: O proprietário de um imóvel novo MUST ser o proprietário autenticado; qualquer
  indicação de dono enviada na requisição MUST ser ignorada.
- **FR-015**: Todo imóvel MUST ter um **identificador** amigável, além do endereço (Q15):
  obrigatório, não em branco, com no máximo 50 caracteres (contados como caracteres, não
  bytes), sem espaços nas pontas.
- **FR-016**: O identificador MUST ser único **por proprietário**, sem distinguir
  maiúsculas/minúsculas (acentos continuam distinguindo: "Térreo" ≠ "Terreo"); proprietários diferentes MAY usar o mesmo identificador. Cadastro com
  identificador repetido para o mesmo dono MUST ser recusado com mensagem em português.
- **FR-017**: As regras de imóvel do módulo 1 (endereço obrigatório, não em branco, até 255
  caracteres, não único; valor sugerido > 0) MUST continuar valendo sem mudança.

**Isolamento (RF-04, RNF-15, Q14)**

- **FR-018**: O proprietário autenticado MUST conseguir consultar um imóvel próprio pelo
  identificador interno, recebendo identificador, endereço, valor sugerido e situação (ativo ou
  inativo).
- **FR-019**: O proprietário autenticado MUST conseguir inativar um imóvel próprio
  (desativação lógica, RF-11); inativar um imóvel já inativo MUST ser aceito sem mudar nada.
- **FR-020**: Toda consulta e toda alteração de imóvel MUST considerar apenas os imóveis do
  proprietário autenticado. Um imóvel de outro proprietário MUST receber exatamente a mesma
  resposta de um imóvel inexistente ("não encontrado"), e nenhuma alteração MUST ser aplicada.
  A regra vale para qualquer identificador interno informado, inclusive valores adivinhados ou
  sequenciais.
- **FR-021**: *(incorporado ao FR-020; o identificador fica reservado para rastreabilidade.)*

**Banco de produção (RNF-05, RNF-06, RNF-08)**

- **FR-022**: A mudança de estrutura MUST ser entregue como uma nova versão de migração,
  aplicada depois da versão do módulo 1, sem migração de dados (não há dados de produção).
- **FR-023**: O banco de produção MUST garantir, com restrições nomeadas no padrão do projeto:
  e-mail de proprietário obrigatório e único; nome e hash de senha obrigatórios; imóvel com dono
  obrigatório e existente; identificador obrigatório, não em branco, até 50 caracteres e único
  por dono (sem distinguir maiúsculas/minúsculas).
- **FR-024**: A referência de imóvel para proprietário MUST impedir a remoção física de um
  proprietário que tenha imóveis.
- **FR-025**: Uma violação de unicidade detectada só pelo banco (cadastros simultâneos) MUST
  chegar ao usuário como a mesma recusa de duplicidade de FR-004/FR-016, não como erro interno.

**Respostas de erro**

- **FR-026**: As operações novas deste módulo (cadastro de proprietário, login, consulta e
  inativação de imóvel) e o cadastro de imóvel alterado por ele MUST responder a dado inválido
  ou duplicado com uma recusa do cliente (família 4xx) e mensagem em português — nunca com erro
  interno. A distinção entre "inválido", "duplicado", "não autenticado" e "não encontrado" MUST
  ser observável pela resposta. Um único tratamento de erro, comum a todas essas operações,
  MUST produzir essas recusas; isso resolve o defeito D3 de `docs/requisitos-e-der.md`
  (o cadastro de imóvel deixa de responder 500 a dado inválido).
  Cada erro conhecido — de domínio, de aplicação ou de infraestrutura — MUST ser tratado
  explicitamente onde ocorre (por exemplo: violação de unicidade traduzida no adapter de
  persistência, limite do hash de senha tratado no adapter de hash). Captura global de exceções
  genéricas (resposta 500 para erro não previsto) está **fora** deste módulo (Clarifications, C1).

**Documentação**

- **FR-027**: `AGENTS.md` (§6 modelo de domínio e §7 regras) MUST passar a descrever `Usuario`
  como proprietário (não "operador do sistema"), o dono e o identificador do imóvel, e a regra
  de isolamento — corrige D6 de `docs/requisitos-e-der.md`.

### Key Entities *(include if feature involves data)*

- **Proprietário (`Usuario`)**: pessoa que administra imóveis no sistema. Atributos: nome, e-mail
  (normalizado, único) e hash da senha. Dono de 0..N imóveis; nunca é apagado neste módulo.
- **Imóvel**: unidade locável. Passa a ter **proprietário** (referência por id, como as demais
  entidades — AGENTS §6) e **identificador** amigável (único por proprietário), além de
  endereço, valor sugerido e situação ativo/inativo já existentes.
- **Autenticação**: o vínculo, durante o uso, entre o cliente que faz as requisições e o
  proprietário que se autenticou. Não é um dado de negócio persistido por esta spec; a forma
  (sessão, token) é decisão do plan.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Uma pessoa nova consegue criar a conta, entrar e cadastrar o primeiro imóvel em
  menos de 2 minutos, sem ajuda.
- **SC-002**: Em um cenário com dois proprietários, **100%** das tentativas de um consultar ou
  inativar imóveis do outro — percorrendo todos os identificadores internos existentes e
  vizinhos — terminam em "não encontrado", sem nenhuma alteração gravada.
- **SC-003**: **0** operações sobre imóveis são executadas sem autenticação válida.
- **SC-004**: **0** senhas recuperáveis no armazenamento: nenhuma senha cadastrada aparece em
  texto em dados guardados, respostas ou registros de execução, e senhas iguais geram valores
  guardados diferentes.
- **SC-005**: As respostas de login para "e-mail inexistente" e "senha errada" são
  indistinguíveis em conteúdo e tipo em 100% dos casos testados.
- **SC-006**: Todas as tentativas de gravar diretamente no banco de produção um imóvel sem dono,
  com identificador inválido ou duplicado para o mesmo dono, ou um e-mail duplicado são
  recusadas (100% dos casos de FR-023).
- **SC-007**: Nenhum cenário de aceitação deste módulo termina em erro interno: dado inválido,
  duplicado, sem autenticação ou de outro dono sempre recebe uma recusa explicada em português.

## Assumptions

- **Auto-cadastro aberto** (decidido em 2026-10-30, ver Clarifications): qualquer pessoa pode
  criar uma conta de proprietário, sem convite nem aprovação; o isolamento (FR-020) garante que
  uma conta nova não enxerga dado de ninguém.
- **Sem confirmação de e-mail, recuperação ou troca de senha** neste módulo; o e-mail só
  identifica a conta.
- **Limites de tamanho**: nome até 100 caracteres e e-mail até 254 (limite prático de endereço de
  e-mail) são padrões razoáveis, não decisões do dono; senha mínima de 8 caracteres segue a
  recomendação usual para senhas escolhidas pelo usuário. O máximo da senha é fixado no plan a
  partir do limite do BCrypt.
- **Expiração por inatividade**: o tempo exato fica no plan; o padrão esperado é da ordem de
  minutos a poucas horas, não dias.
- **Sem proteção contra força bruta** (bloqueio de conta, limite de tentativas) neste módulo;
  volume baixo (RNF-21). Fica como candidato a módulo futuro.
- **Revelação no cadastro**: recusar um e-mail em uso revela que a conta existe. Isso é aceito
  (é o comportamento esperado de um cadastro); o login, por outro lado, não revela (FR-009).
- **Consulta e inativação de imóvel** (decidido em 2026-10-30, ver Clarifications) são as
  operações mínimas para provar o isolamento em leitura e escrita ("vê e altera"). Edição de dados do imóvel (RF-09) e listagem
  (RF-10) ficam fora.
- **Modo rápido (banco em memória)** continua o padrão para desenvolvimento; nele também é
  preciso criar uma conta e entrar antes de cadastrar imóveis.
- **Frontend em repositório separado** (Q23): o backend precisa permitir que um cliente
  separado se autentique e mantenha o acesso entre requisições; detalhes (origens permitidas,
  formato do acesso) ficam no plan.
- **Sem dados de produção** a migrar: a nova versão de estrutura pode exigir dono e
  identificador em `imoveis` sem preencher linhas antigas.

### Mudança deliberada de requisito existente

O cadastro de imóvel do módulo 1 muda de propósito: passa a exigir autenticação, dono e
identificador. Testes existentes que **vão deixar de compilar ou de passar** (conferido contra a
`main` em `eb5e715`):

- `ImovelTest`, `CadastrarImovelUseCaseTest` e `ImovelRepositoryAdapterTest`: constroem imóveis
  sem dono e sem identificador.
- `ImoveisPostgresConstraintsTest`: grava diretamente em `imoveis` sem dono e sem identificador,
  o que a nova versão de estrutura passa a recusar (FR-023) antes de chegar à restrição que cada
  teste quer provar.

`EstruturaBancoProducaoTest` e `ModoRapidoTest` continuam válidos: só conferem a versão 1 e o
modo rápido, que esta spec não muda.
Pela regra do AGENTS §3 (teste existente é read-only), a adaptação desses testes exige
**decisão explícita do dono** — **dada em 2026-10-30** (ver Clarifications), com este limite:
a adaptação MUST se restringir a acrescentar dono e identificador nos dados de preparação
(construção do imóvel ou linha gravada) e, por decisão posterior do dono (P1 do plan), a
implementar no fake privado de `CadastrarImovelUseCaseTest` os métodos novos da porta de
imóveis; nenhuma asserção, nome de teste ou cenário provado MUST mudar. Qualquer outra alteração nesses quatro testes exige nova decisão do dono.

### Pontos a confirmar na `/speckit-clarify`

1. ~~**D3**~~ — decidido: corrigido neste módulo (ver Clarifications e FR-026).
2. ~~**Adaptação dos testes existentes de imóvel**~~ — decidido: autorizada, só o mínimo (seção
   acima).
3. ~~Identificador sem distinguir maiúsculas/minúsculas~~ — decidido (ver Clarifications).
   **Ainda assumidos sem decisão do dono** (baixo impacto, ajustáveis no plan): e-mail sem
   distinguir maiúsculas/minúsculas (prática usual); limites
   de nome (100) e senha (mínimo 8).

## Out of Scope

- Inquilinos, contratos, faturas, pagamentos e painel (módulos 3–5).
- Edição de imóvel (RF-09) e listagem de imóveis (RF-10).
- Edição, remoção ou desativação de conta de proprietário; troca e recuperação de senha;
  confirmação de e-mail.
- Papéis ou perfis (administrador, operador); todo usuário é proprietário.
- Bloqueio por tentativas, limitação de taxa, autenticação em dois fatores, login social.
- Frontend (repositório separado, Q23).
- Atualização do documento do Drive (§7 de `docs/requisitos-e-der.md`).
- Mecanismo global de captura e tradução de exceções genéricas (resposta 500 padronizada para
  erro não previsto): feature à parte (Clarifications, C1).
