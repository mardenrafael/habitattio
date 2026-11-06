# Feature Specification: Autenticação sem estado no servidor (JWT em cookie protegido)

**Feature Branch**: `worktree-006-jwt-sessao-cookie`

**Created**: 2026-11-06

**Status**: Draft

**Input**: User description: "este projeto foi criado um endpoint de sessão, acredito q isso quebra a regra de API RESTful de ser statelles e efemera, ainda mais quando esse projeto esta caminhando para ser 100% nativo  docker, revise a gestão de sessão e vamos adotar o jwt para sessão em cookie secure, http only e afins."

**Origem**: revisão da feature 003 (`specs/003-proprietario-login-isolamento`: FR-008 a FR-012,
`research.md` R1 e R5, `contracts/api-http.md`) e de RF-03 e RNF-12 em `docs/requisitos-e-der.md`.

## Contexto

Hoje, depois do login, quem lembra que o proprietário está autenticado é **a memória do próprio
container**. Isso foi aceito na feature 003 para uma instância única e volume baixo, mas colide
com o rumo do projeto, nativo em Docker:

- **Reiniciar ou trocar o container derruba todo mundo**: cada deploy desconecta todos os
  proprietários.
- **Não dá para ter uma segunda instância** sem amarrar cada cliente a um container específico.
- **O container deixa de ser descartável**: ele guarda algo que não pode ser perdido.

O problema está no estado guardado no servidor, não no nome da rota `/sessao`. Por isso esta
feature troca o mecanismo e mantém o contrato HTTP (ver Assumptions).

Algumas restrições já foram decididas pelo dono e são citadas pelo nome porque não são escolha
desta spec: a autenticação passa a ser **JWT**, transportado em **cookie** `Secure`, `HttpOnly`
e com os demais atributos de proteção. Biblioteca, algoritmo de assinatura e nomes de
configuração ficam para o `plan.md`.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - O acesso sobrevive à troca do container (Priority: P1)

A Ana entrou no sistema e está cadastrando imóveis. Nesse meio tempo o container é reiniciado ou
substituído por uma versão nova, ou a requisição dela cai em outra instância. Ela continua
usando o sistema sem perceber nada e sem precisar entrar de novo.

**Why this priority**: é o motivo da feature. Sem isso, o sistema continua preso a um container
específico.

**Independent Test**: entrar, reiniciar a aplicação com a mesma configuração e repetir uma
operação protegida com a credencial recebida; repetir o teste com duas instâncias, entrando por
uma e operando pela outra.

**Acceptance Scenarios**:

1. **Given** a Ana autenticada, **When** a aplicação é reiniciada com a mesma configuração e ela
   consulta um imóvel próprio, **Then** a consulta é atendida, sem novo login.
2. **Given** duas instâncias com a mesma configuração, **When** a Ana entra pela primeira e opera
   pela segunda, **Then** a operação é atendida, sem nenhuma afinidade entre cliente e instância.
3. **Given** qualquer jornada (cadastro, login, operações, saída), **When** se inspecionam as
   respostas, **Then** nenhuma traz identificador de sessão do servidor.
4. **Given** um cliente anônimo, **When** ele faz qualquer requisição, **Then** o servidor não
   guarda nada em memória sobre esse cliente.

---

### User Story 2 - A credencial fica protegida no navegador (Priority: P1)

Ao entrar, a Ana recebe uma credencial que o navegador guarda e envia sozinho. Um script na
página não consegue lê-la, ela não trafega sem criptografia em produção e não serve de atalho
para um site de terceiros agir em nome dela. Quem tentar forjar ou alterar a credencial é
recusado.

**Why this priority**: a credencial passa a carregar a prova de identidade por conta própria.
Se puder ser lida, forjada ou reaproveitada por outro site, o isolamento entre proprietários
(RNF-15) cai junto.

**Independent Test**: entrar e inspecionar os atributos do cookie recebido; repetir operações
protegidas com credencial alterada, assinada com outra chave, sem assinatura e vencida.

**Acceptance Scenarios**:

1. **Given** credenciais corretas, **When** a Ana entra em produção, **Then** a credencial chega
   num cookie inacessível a scripts, enviado só por conexão segura e não enviado em requisições
   iniciadas por outros sites, exceto navegação de primeiro nível.
2. **Given** uma credencial válida com o conteúdo alterado, **When** ela é usada para se passar
   pelo Bruno, **Then** a operação é recusada como não autenticada, sem efeito.
3. **Given** uma credencial sem assinatura ou assinada com outra chave, **When** ela é usada,
   **Then** a operação é recusada como não autenticada, sem efeito.
4. **Given** a Ana autenticada, **When** uma escrita chega sem o token de proteção contra
   requisição forjada, **Then** ela é recusada como proibida, mesmo com a credencial válida.
5. **Given** uma credencial emitida, **When** seu conteúdo é decodificado, **Then** não há senha,
   hash de senha nem qualquer dado pessoal além do necessário para identificar o proprietário.
6. **Given** a Ana autenticada, **When** ela consulta o imóvel do Bruno pelo id, **Then** recebe
   "não encontrado", como hoje.

---

### User Story 3 - O acesso expira, mas não atrapalha quem está usando (Priority: P1)

Enquanto a Ana usa o sistema, o acesso se mantém. Se ela se afastar por 30 minutos, o acesso
expira, como hoje. Mesmo em uso contínuo, depois de um tempo máximo ela precisa entrar de novo.

**Why this priority**: mantém o comportamento combinado na feature 003 (FR-010) e limita por
quanto tempo uma credencial copiada pode ser usada.

**Independent Test**: com um relógio controlado, simular intervalos de uso abaixo e acima de 30
minutos, e uso contínuo até o limite absoluto.

**Acceptance Scenarios**:

1. **Given** a Ana autenticada, **When** ela opera a cada 20 minutos, **Then** o acesso continua
   válido, com renovação feita pelo servidor, sem ação dela.
2. **Given** a Ana autenticada, **When** ela fica mais de 30 minutos sem operar, **Then** a
   operação seguinte é recusada como não autenticada e nada é gravado.
3. **Given** a Ana em uso contínuo, **When** se passam 8 horas desde o login, **Then** o acesso
   não é mais renovado e a operação seguinte depois do vencimento é recusada.
4. **Given** uma requisição recusada, **When** a resposta chega, **Then** ela não renova nada.

---

### User Story 4 - Sair encerra o acesso (Priority: P2)

Ao terminar, a Ana sai do sistema, inclusive em um computador compartilhado, e o navegador deixa
de estar autenticado.

**Why this priority**: o login já é útil sem isso, mas sair é uma garantia que a feature 003 deu
(FR-011) e que muda de natureza sem estado no servidor.

**Independent Test**: entrar, sair e repetir uma operação protegida pelo mesmo navegador e, à
parte, com uma cópia da credencial feita antes de sair.

**Acceptance Scenarios**:

1. **Given** a Ana autenticada, **When** ela sai, **Then** recebe confirmação, o navegador
   descarta a credencial e a operação seguinte desse navegador é recusada como não autenticada.
2. **Given** um cliente sem credencial válida, **When** ele tenta sair, **Then** recebe "não
   autenticado", e uma credencial vencida que ele tenha é descartada do navegador.
3. **Given** uma cópia da credencial feita antes de a Ana sair, **When** ela é usada depois da
   saída, **Then** o resultado segue FR-011.

---

### User Story 5 - Quem opera o container controla o segredo (Priority: P2)

Quem sobe a aplicação informa o segredo de assinatura pelo ambiente, como já faz com a conexão
do banco. Sem esse segredo, a produção se recusa a subir, em vez de funcionar sem proteção. O
modo rápido continua subindo sem nenhuma configuração.

**Why this priority**: o segredo é o que sustenta toda a confiança na credencial. É P2 só por
depender das histórias anteriores.

**Independent Test**: subir a imagem em modo produção sem o segredo, com um segredo fraco e com
um adequado; subir em modo rápido sem configurar nada.

**Acceptance Scenarios**:

1. **Given** o modo produção sem segredo configurado, **When** a aplicação sobe, **Then** ela
   falha na subida, dizendo o que falta, sem aceitar requisições.
2. **Given** o modo produção com um segredo curto demais, **When** a aplicação sobe, **Then** ela
   falha na subida, sem repetir o valor do segredo na mensagem.
3. **Given** o modo rápido sem nenhuma configuração, **When** a aplicação sobe e reinicia,
   **Then** ela funciona, e credenciais emitidas antes do reinício deixam de valer.
4. **Given** o segredo de produção trocado, **When** uma credencial antiga é usada, **Then** ela
   é recusada como não autenticada.

---

### Edge Cases

- **Cookie vencido ou inválido em operação pública**: login, cadastro de proprietário e
  verificação de saúde funcionam normalmente; o cookie ruim não pode impedir ninguém de entrar.
- **Login com credencial já presente**, inclusive de outro proprietário ou plantada por um
  atacante: a credencial nova sempre substitui a anterior.
- **Requisições simultâneas durante a renovação**: as respostas podem trazer credenciais
  renovadas diferentes; qualquer uma vale e nenhuma derruba o acesso.
- **Relógios diferentes entre instâncias**: uma diferença de poucos segundos não deve recusar uma
  credencial recém-emitida; a tolerância é decidida no plan.
- **Modo rápido e banco zerado**: como o banco em memória se perde a cada reinício, uma credencial
  antiga poderia apontar para outro proprietário com o mesmo id; o segredo gerado a cada subida
  (FR-015) impede isso.
- **Banco de produção recriado com o mesmo segredo**: mesmo risco; o procedimento documentado
  MUST mandar trocar o segredo junto.
- **Proprietário cuja conta não existe mais**: não acontece (não há remoção de conta), como na
  feature 003.
- **Cookie grande demais**: a credencial carrega só o mínimo (FR-006), bem abaixo do limite de
  tamanho de cookie dos navegadores.

## Requirements *(mandatory)*

### Functional Requirements

**Sem estado no servidor**

- **FR-001**: O sistema MUST reconhecer o proprietário autenticado a partir da credencial enviada
  na própria requisição, sem depender de nada guardado na memória da instância que a atende.
- **FR-002**: O sistema MUST NOT criar sessão de servidor em nenhuma requisição, autenticada ou
  anônima, nem emitir cookie de sessão do servidor.
- **FR-003**: Uma credencial válida MUST ser aceita por qualquer instância com a mesma
  configuração e continuar válida depois de reinício ou substituição do container.

**Emissão e proteção da credencial**

- **FR-004**: Um login bem-sucedido MUST emitir uma credencial assinada (JWT) em cookie e renovar
  o token de proteção contra requisição forjada; a nova credencial MUST substituir qualquer outra
  presente na requisição. Login recusado MUST NOT emitir credencial e MUST manter a resposta
  única para e-mail inexistente e senha errada (FR-009 da feature 003).
- **FR-005**: O cookie da credencial MUST ser inacessível a scripts (`HttpOnly`), MUST valer só
  para o host da aplicação (sem domínio amplo), para todos os caminhos da API, e MUST NOT ser
  enviado em requisições iniciadas por outros sites, exceto navegação de primeiro nível
  (`SameSite=Lax` ou mais restrito). No modo produção MUST ser enviado só por conexão segura
  (`Secure`). O cookie MUST ter validade alinhada à da credencial.
- **FR-006**: A credencial MUST conter apenas o necessário para identificar o proprietário e
  controlar a validade. MUST NOT conter senha, hash de senha nem outro dado pessoal
  desnecessário, porque o conteúdo é legível por quem a possuir.
- **FR-007**: Credencial ausente, vencida, alterada, sem assinatura, assinada com outra chave ou
  com algoritmo diferente do esperado MUST ser recusada como não autenticada, com a resposta
  atual ("Autenticação necessária."), sem efeito colateral e sem revelar o motivo da recusa.
- **FR-008**: O dono de toda operação MUST continuar vindo só da credencial validada, nunca de
  dado enviado na requisição; o isolamento entre proprietários (RNF-15 e os requisitos de
  isolamento da feature 003) MUST permanecer inalterado.

**Expiração**

- **FR-009**: O acesso MUST expirar depois de 30 minutos sem requisição autenticada aceita. O
  servidor MUST renovar a credencial durante o uso, sem ação do cliente. Requisição recusada MUST
  NOT renovar.
- **FR-010**: O acesso MUST expirar no máximo 8 horas depois do login, mesmo em uso contínuo; a
  renovação MUST NOT ultrapassar esse limite.

**Saída**

- **FR-011**: Ao sair, o sistema MUST responder com sucesso e instruir o navegador a descartar a
  credencial. Uma cópia dessa credencial feita antes da saída [NEEDS CLARIFICATION: deve ser
  recusada de imediato, o que exige um registro compartilhado de credenciais revogadas, ou pode
  continuar válida até vencer (no máximo 30 minutos sem renovação, ou até o limite de FR-010 se
  continuar sendo renovada), mantendo o servidor 100% sem estado?]
- **FR-012**: Sair sem credencial válida MUST continuar respondendo "não autenticado" e MUST
  instruir o navegador a descartar o cookie, se houver.

**Proteção contra requisição forjada e operações públicas**

- **FR-013**: A proteção contra requisição forjada MUST continuar ativa, sem estado no servidor,
  com o contrato atual: toda escrita, inclusive login, cadastro e saída, exige o token, e a falta
  dele responde "proibido". Trocar de mecanismo não dispensa essa proteção, porque o navegador
  continua enviando o cookie sozinho.
- **FR-014**: Cadastro de proprietário, login e verificação de saúde MUST funcionar mesmo com um
  cookie de credencial vencido ou inválido na requisição. A consulta de quem está autenticado MUST
  continuar entregando o token de proteção mesmo respondendo "não autenticado".

**Segredo de assinatura**

- **FR-015**: No modo produção, o segredo MUST vir do ambiente, nunca de arquivo do repositório
  nem da imagem, e a aplicação MUST se recusar a subir sem ele ou com um segredo abaixo da força
  mínima exigida pelo algoritmo (definido no plan). No modo rápido, sem segredo informado, a
  aplicação MUST gerar um aleatório a cada subida.
- **FR-016**: Trocar o segredo MUST invalidar as credenciais emitidas com o anterior. Rotação
  sem desconectar ninguém está fora do escopo.
- **FR-017**: Credencial e segredo MUST NOT aparecer em logs, mensagens de erro ou respostas,
  além do próprio cookie entregue ao proprietário.

**Contrato e substituição**

- **FR-018**: Rotas, métodos, formatos de requisição, status e corpos das respostas MUST
  permanecer os da feature 003, salvo o nome do cookie e o que FR-011 decidir. O frontend não
  precisa mudar nada, porque não lê a credencial.
- **FR-019**: O mecanismo de sessão no servidor MUST ser removido por inteiro, sem convivência
  entre os dois. Sessões existentes não são migradas: cada proprietário entra de novo uma vez,
  depois do primeiro deploy.
- **FR-020**: A documentação que descreve a autenticação MUST ser atualizada nesta feature:
  contrato HTTP, operação do container (nova variável de ambiente), `docs/requisitos-e-der.md`,
  `AGENTS.md` e a constitution (o stack cita "login por sessão"), que passa por
  `/speckit.constitution`.

### Key Entities *(include if feature involves data)*

- **Credencial de acesso**: prova assinada de que um proprietário entrou. Carrega quem é o
  proprietário, quando o login aconteceu e até quando vale. Não é persistida; existe só no cookie
  do cliente. Substitui a sessão de servidor.
- **Segredo de assinatura**: valor que torna a credencial impossível de forjar. Compartilhado por
  todas as instâncias, fornecido pelo ambiente.
- **Proprietário** (`Usuario`): inalterado.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Depois de reiniciar ou substituir a aplicação com a mesma configuração, 100% dos
  proprietários autenticados continuam operando sem novo login.
- **SC-002**: Com duas instâncias, 100% das operações autenticadas são atendidas, seja qual for a
  instância que receber a requisição.
- **SC-003**: Em uma jornada completa, 0 respostas trazem identificador de sessão de servidor.
- **SC-004**: 100% das credenciais alteradas, forjadas ou vencidas são recusadas, sem nenhuma
  gravação decorrente.
- **SC-005**: Em produção, a credencial não pode ser lida por script da página nem é enviada por
  conexão sem criptografia, em 100% das verificações.
- **SC-006**: Um proprietário que opera a cada 20 minutos não é interrompido antes de 8 horas; um
  que fica 31 minutos parado sempre precisa entrar de novo.
- **SC-007**: A aplicação em modo produção sem segredo adequado nunca chega a aceitar requisições.
- **SC-008**: 0 acessos entre proprietários, percorrendo todos os identificadores de imóveis
  existentes, como na feature 003.
- **SC-009**: Nenhuma alteração é necessária no frontend.

## Assumptions

- **A rota `/sessao` é mantida** (`POST`, `GET`, `DELETE`). O que quebra a ausência de estado é a
  memória do servidor, não o nome da rota, e o frontend (repositório separado, Q23) continua
  funcionando. Renomear seria mudança de contrato sem ganho funcional; se o dono preferir, entra
  como decisão na `/speckit-clarify`.
- **30 minutos de inatividade** vêm da feature 003. O **limite absoluto de 8 horas** é um padrão
  razoável, proposto aqui (a feature 003 previa "minutos a poucas horas, não dias"); ajustável na
  `/speckit-clarify`.
- **Renovação no servidor, sem segundo tipo de credencial** (refresh token): preserva FR-010 da
  feature 003 sem o estado que um refresh token revogável exigiria.
- **`Secure` só no modo produção**, como hoje: o modo rápido roda em HTTP local. `SameSite=Lax` é
  o valor atual; o plan pode endurecê-lo, desde que a mesma origem via proxy do frontend (003, R1)
  continue funcionando.
- **Não há dados de produção**, então descartar sessões existentes não afeta ninguém.
- **Sem proteção contra força bruta**, como na feature 003: continua candidato a módulo futuro.
- **Dependência nova**: emitir e validar JWT provavelmente exige biblioteca fora do stack atual.
  A escolha é do `plan.md`, que apresenta a dependência para acordo do dono (constitution,
  Princípio IV).

### Mudança deliberada de requisito existente

Esta feature substitui, de propósito, decisões da feature 003:

- **`research.md` R1** (sessão em memória). JWT foi rejeitado ali por exigir dependência nova,
  gestão de chave de assinatura e lista de revogação para o logout; o rumo nativo em Docker muda
  o peso dessa troca. A proteção contra fixação de sessão (`SegurancaConfiguration`) perde objeto;
  FR-004 cobre o risco equivalente.
- **FR-010 e FR-011 da feature 003** (acesso até sair ou expirar; depois de sair, o mesmo acesso é
  recusado): FR-010 é preservado aqui em FR-009; FR-011 depende da decisão em aberto nesta spec.
- **`contracts/api-http.md`**: cookie `JSESSIONID` deixa de existir.

Testes existentes que **vão deixar de compilar ou de passar** (conferido contra `origin/main` em
`673b7ab`):

- `SessaoCookieIntegracaoTest#deveEmitirCookieDeSessaoHttpOnlySameSiteLaxESecureNoProd`: exige o
  cookie `JSESSIONID`.
- `SessaoIntegracaoTest#deveTrocarOIdDaSessaoAoEntrar`: prova a troca de id de uma sessão que
  deixa de existir.
- `SessaoIntegracaoTest#deveConfigurarExpiracaoDaSessaoEmTrintaMinutos`: lê a propriedade de
  expiração da sessão de servidor.
- `SessaoIntegracaoTest#deveRecusarOperacaoProtegidaDepoisDeSair`: reutiliza a sessão depois de
  sair; o que ela prova depende de FR-011.
- `IsolamentoProprietariosIntegracaoTest`: usa `MockHttpSession` para carregar a autenticação; o
  cenário provado não muda, só o transporte.

Pela regra do AGENTS §3 (teste existente é read-only), alterar esses testes exige **decisão
explícita do dono**. [NEEDS CLARIFICATION: o dono autoriza (a) trocar o transporte de
`MockHttpSession` para o cookie da credencial, sem mudar asserções nem cenários, e (b) substituir
os testes que só provam a sessão de servidor por equivalentes do novo mecanismo?]

### Pontos a confirmar na `/speckit-clarify`

1. Decisão sobre cópia da credencial depois de sair (FR-011).
2. Autorização para adaptar os testes existentes (seção acima).
3. **Assumidos sem decisão do dono** (ajustáveis): manter a rota `/sessao`; limite absoluto de 8
   horas; `SameSite=Lax`.

## Out of Scope

- Rotação de segredo sem desconectar proprietários (FR-016).
- "Encerrar todos os meus acessos" e lista de dispositivos conectados.
- Refresh token e acesso por clientes fora do navegador (cabeçalho `Authorization`).
- Bloqueio de tentativas de login e recuperação de senha.
- Compose de produção, orquestração e balanceamento de várias instâncias: esta feature só deixa
  a aplicação pronta para isso.
- Mudanças no frontend.
