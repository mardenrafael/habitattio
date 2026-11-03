# Feature Specification: Descrição da API derivada do código

**Feature Branch**: `004-auto-docs-api`

**Created**: 2026-11-02

**Status**: Draft

**Input**: User description: "@.specify/assessments/auto-docs-api/decision.md — Option B: a aplicação produz a própria descrição OpenAPI a partir do código, enriquecida com o texto explicativo atual (guia CSRF/sessão, descrições em português, exemplos) e com as rotas que não são controllers (`POST /sessao`, `DELETE /sessao`, `GET /actuator/health`). Swagger UI navegável com a app rodando. Um teste da suíte normal exporta a descrição para `docs/api/openapi.yaml`, que passa a ser artefato gerado."

**Origem**: `.specify/assessments/auto-docs-api/` (`intake.md`, `research.md`, `problem.md`,
`concept.md`, `decision.md` — verdict **go**, Option B, 2026-11-02).

## Contexto

A descrição da API que o dono importa nos API clients locais (Bruno, Insomnia, Postman) é o
arquivo `docs/api/openapi.yaml`, escrito e conferido à mão, separado do código. Nada avisa
quando uma rota, um campo ou uma regra de autenticação muda, então o arquivo pode divergir em
silêncio da API real. Hoje o custo é baixo (8 operações, um mantenedor), mas cresce a cada
agregado exposto (`Inquilino`, `Contrato`, `Fatura` ainda não têm rotas).

Esta feature faz a aplicação produzir a própria descrição a partir do que está implementado, e
transforma o arquivo em **artefato gerado** por um comando que já existe (a suíte de testes).

Algumas restrições já foram decididas pelo dono e são citadas pelo nome porque não são escolha
desta spec: a descrição segue o formato **OpenAPI** (o mesmo do arquivo atual); uma interface
navegável (**Swagger UI**) fica disponível com a aplicação rodando; o arquivo manual é
**substituído**, não mantido ao lado; e a nova dependência e o endpoint de documentação são
exceções acordadas ao §9 do AGENTS.md, a registrar na constitution e no AGENTS.md. O gerador
concreto, onde fica o texto explicativo e como o teste sobe a aplicação ficam para o `plan.md`.

## Clarifications

### Session 2026-11-02

- Q: A página de documentação e a descrição bruta ficam disponíveis no modo produção? → A: Não. Ficam fechadas em `prod` para qualquer pessoa, autenticada ou não; só o modo rápido as expõe (FR-010).
- Q (do plan, R7): Os arquivos estáticos da Swagger UI podem continuar alcançáveis em `prod` por quem está autenticado (resíduo do comportamento padrão)? → A: Não. Em `prod` nenhum arquivo da Swagger UI fica online, para ninguém; a descrição existe só no repositório (`docs/api/openapi.yaml`) (FR-010).
- Q: Depois desta feature, qual é a referência do contrato HTTP? → A: A descrição gerada (`docs/api/openapi.yaml`). `specs/003-.../contracts/api-http.md` vira histórico da feature 003 (FR-012).

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Descrição sempre igual à API implementada (Priority: P1)

O dono altera um controller (adiciona uma rota, um campo na resposta, um status de erro), roda a
suíte de testes e o arquivo `docs/api/openapi.yaml` passa a refletir a mudança, sem editar nenhum
arquivo de documentação. Ao revisar o commit, a diferença no arquivo mostra exatamente o que
mudou no contrato; se esqueceu de rodar a suíte, a revisão ou um CI futuro vê o arquivo
desatualizado.

**Why this priority**: é o problema que motivou a feature: a divergência silenciosa entre a
descrição e a API. Sem esta história, nada mais tem valor.

**Independent Test**: rodar a suíte de testes, conferir que o arquivo é regravado e contém todas
as operações dos controllers; acrescentar um campo numa resposta, rodar de novo e ver o campo
aparecer no arquivo sem outra edição.

**Acceptance Scenarios**:

1. **Given** a aplicação no estado atual, **When** o dono roda a suíte de testes, **Then**
   `docs/api/openapi.yaml` é regravado com as operações expostas pelos controllers
   (`POST /proprietarios`, `GET /sessao`, `POST /imoveis`, `GET /imoveis/{id}`,
   `POST /imoveis/{id}/inativacao`), com campos, status e regras de autenticação de cada uma.
2. **Given** um campo novo numa resposta de controller, **When** o dono roda a suíte de testes,
   **Then** o campo aparece no arquivo gerado sem que nenhum arquivo de documentação tenha sido
   editado.
3. **Given** a suíte rodada duas vezes seguidas sem mudança no código, **When** o dono compara o
   arquivo, **Then** o conteúdo é idêntico (a geração é estável e não produz diferença espúria).
4. **Given** uma operação que deixou de existir, **When** a suíte roda, **Then** ela desaparece do
   arquivo gerado.

---

### User Story 2 - Fluxo de sessão e CSRF utilizável só com a descrição gerada (Priority: P1)

O dono importa o arquivo gerado num API client e consegue percorrer o fluxo completo — cadastrar
proprietário, obter o token CSRF, entrar, usar as rotas de imóveis, sair — sem consultar outro
documento. Para isso, a descrição gerada inclui as operações que não nascem de controllers
(entrar, sair e saúde) e o texto explicativo que hoje só existe no arquivo manual: guia de
CSRF/sessão, descrições em português e exemplos.

**Why this priority**: as rotas de entrar e sair são as que o gerador não enxerga sozinho e as
mais difíceis de usar; se sumirem, a descrição gerada é pior que a manual e a substituição é um
retrocesso.

**Independent Test**: importar o arquivo gerado num API client vazio e executar o fluxo
cadastro → CSRF → entrar → cadastrar imóvel → consultar → sair seguindo apenas a descrição.

**Acceptance Scenarios**:

1. **Given** o arquivo gerado, **When** o dono procura as operações de entrar, sair e saúde,
   **Then** encontra `POST /sessao`, `DELETE /sessao` e `GET /actuator/health`, com entrada,
   respostas e status equivalentes ao contrato da feature 003.
2. **Given** o arquivo gerado, **When** o dono lê a introdução da API, **Then** encontra o guia
   do fluxo de sessão e CSRF (como obter o token, em que cabeçalho enviá-lo, quais operações o
   exigem) em português.
3. **Given** uma operação qualquer no arquivo gerado, **When** o dono a abre, **Then** o resumo,
   a descrição e as mensagens de erro estão em português, e existe ao menos um exemplo de
   corpo para cada operação que recebe ou devolve corpo.
4. **Given** o arquivo gerado importado num API client, **When** o dono segue o fluxo
   cadastro → CSRF → entrar → rotas protegidas → sair, **Then** todas as chamadas funcionam sem
   consultar outro documento.

---

### User Story 3 - Navegar pela documentação com a aplicação rodando (Priority: P2)

Com a aplicação rodando no modo rápido, o dono abre no navegador uma página com a documentação
navegável da API (Swagger UI) e a descrição bruta correspondente, sem precisar do arquivo
exportado.

**Why this priority**: pedido explícito do dono, mas a descrição exportada (US1/US2) já entrega o
valor principal; a página é conveniência.

**Independent Test**: subir a aplicação no modo rápido, abrir a página de documentação sem estar
autenticado e ver todas as operações listadas.

**Acceptance Scenarios**:

1. **Given** a aplicação rodando no modo rápido, **When** o dono abre a página de documentação
   sem estar autenticado, **Then** vê todas as operações da API, com o mesmo conteúdo do arquivo
   exportado.
2. **Given** a aplicação rodando no modo rápido, **When** o dono abre a página, **Then** pode ler
   todas as operações; executar pela página as operações que exigem CSRF é desejável, mas não
   obrigatório (ver Assumptions).
3. **Given** a aplicação rodando no modo produção, **When** alguém tenta abrir a página de
   documentação ou a descrição bruta, **Then** o acesso segue a política definida em FR-010.

---

### User Story 4 - Prova automatizada de que a descrição está completa (Priority: P2)

Um teste da suíte normal é o que exporta o arquivo e, além de gravá-lo, falha se a descrição
gerada perder algo essencial: alguma das operações expostas, o guia de sessão/CSRF ou a marcação
de quais operações exigem autenticação.

**Why this priority**: é a exigência de TDD da constitution (o teste é a verificação) e o que
impede que as operações montadas à mão (entrar, sair, saúde) sumam numa atualização.

**Independent Test**: remover da configuração a descrição de `DELETE /sessao` e rodar a suíte:
o teste falha apontando a operação ausente.

**Acceptance Scenarios**:

1. **Given** a descrição completa, **When** a suíte roda, **Then** o teste passa e o arquivo é
   gravado.
2. **Given** uma das 8 operações ausente da descrição gerada, **When** a suíte roda, **Then** o
   teste falha indicando qual operação falta.
3. **Given** uma operação protegida descrita como pública (ou o contrário), **When** a suíte
   roda, **Then** o teste falha.

---

### Edge Cases

- **Execução sem Docker**: a exportação precisa rodar no modo rápido, sem PostgreSQL nem Redis
  em container, para funcionar em qualquer máquina ou CI; se a suíte for rodada só com
  `-Dtest=...` de outras classes, o arquivo simplesmente não é regravado.
- **Ordem instável**: rodar a geração duas vezes sem mudança no código não pode produzir
  diferença no arquivo (ordem de operações, de campos e de esquemas estável).
- **Endereço do servidor**: a descrição não pode embutir uma porta aleatória de teste; o
  servidor descrito é o de uso local (o mesmo do arquivo manual atual).
- **Teste que falha**: se a verificação de conteúdo falhar, o arquivo não pode ser gravado
  parcial ou corrompido.
- **Rotas internas**: a página de documentação e a descrição bruta não aparecem como
  operações da própria API no arquivo gerado; nenhum endpoint do Actuator além de `health`
  aparece.
- **Coleção Bruno**: continua manual e pode divergir do arquivo gerado; não é verificada.
- **Fonte de verdade**: se o arquivo gerado e `specs/003-proprietario-login-isolamento/contracts/api-http.md`
  discordarem, vale o arquivo gerado (FR-012); `api-http.md` é histórico e não é atualizado.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: A aplicação MUST produzir uma descrição OpenAPI da própria API derivada do código
  implementado, de modo que mudanças de rota, campo, status ou regra de autenticação nos
  controllers apareçam na descrição sem editar arquivo de documentação.
- **FR-002**: A descrição MUST incluir todas as 8 operações expostas hoje: `POST /proprietarios`,
  `GET /sessao`, `POST /sessao`, `DELETE /sessao`, `POST /imoveis`, `GET /imoveis/{id}`,
  `POST /imoveis/{id}/inativacao` e `GET /actuator/health`.
- **FR-003**: As operações que não nascem de controllers (`POST /sessao`, `DELETE /sessao`,
  `GET /actuator/health`) MUST ser descritas com entrada, respostas e status equivalentes ao
  contrato da feature 003 e ao comportamento real da aplicação.
- **FR-004**: A descrição MUST indicar, para cada operação, se exige sessão autenticada e se
  exige o token CSRF, coerente com as regras de segurança em vigor.
- **FR-005**: A descrição MUST conter, em português, o guia do fluxo de sessão e CSRF que hoje
  está no arquivo manual, e resumo/descrição em português para cada operação.
- **FR-006**: A descrição MUST conter ao menos um exemplo de corpo para cada operação que recebe
  ou devolve corpo, e descrever as respostas de erro conhecidas (validação, não autenticado,
  não encontrado, conflito) de cada operação.
- **FR-007**: Um teste da suíte normal MUST obter a descrição da aplicação rodando no modo rápido
  e gravá-la em `docs/api/openapi.yaml`, substituindo o arquivo manual, sem passo manual além de
  rodar a suíte.
- **FR-008**: O mesmo teste MUST falhar, sem gravar arquivo parcial, quando a descrição não
  contiver alguma das operações de FR-002, a marcação de autenticação de FR-004 ou o guia de
  FR-005.
- **FR-009**: Duas gerações seguidas sem mudança de código MUST produzir arquivos idênticos, e o
  arquivo MUST descrever o servidor de uso local, não a porta efêmera do teste.
- **FR-010**: No modo rápido, a página de documentação navegável e a descrição bruta MUST estar
  acessíveis sem autenticação. No modo produção, ambas MUST ficar indisponíveis (respondem como
  inexistentes), para qualquer pessoa, autenticada ou não — incluindo qualquer arquivo da página
  de documentação (scripts, estilos, imagens). Em produção a descrição existe só no repositório,
  em `docs/api/openapi.yaml`.
- **FR-011**: A feature MUST NOT alterar rotas, comportamento, regras de autenticação nem a
  exposição do Actuator (continua só `health`); a única superfície nova permitida é a de
  documentação descrita em FR-010.
- **FR-012**: A descrição gerada (`docs/api/openapi.yaml`) MUST passar a ser a referência única
  do contrato HTTP. `specs/003-proprietario-login-isolamento/contracts/api-http.md` MUST ser
  marcado como histórico da feature 003, apontando para o arquivo gerado, e o README e o
  AGENTS.md MUST apontar para o arquivo gerado como referência.
- **FR-013**: O texto explicativo e a configuração da documentação MUST ficar na camada de
  adapters; `domain/` e `application/` MUST continuar sem nenhuma dependência de documentação.
- **FR-014**: A constitution (seção "Stack Tecnológico e Restrições" e lista do Princípio IV) e o
  AGENTS.md (§1 comandos e §9 limites) MUST registrar a nova dependência, a página de
  documentação e o fato de que `docs/api/openapi.yaml` é gerado pela suíte.

### Key Entities

- **Descrição da API**: documento OpenAPI que lista operações, entradas, respostas, regras de
  autenticação, guia de uso e exemplos. Produzido pela aplicação; exportado para
  `docs/api/openapi.yaml`.
- **Operação**: par método + caminho exposto pela aplicação, com resumo, descrição, entrada,
  respostas e exigência de sessão/CSRF. São 8 hoje (FR-002).
- **Página de documentação**: interface navegável que apresenta a descrição com a aplicação
  rodando.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: 8 de 8 operações expostas aparecem na descrição gerada, incluindo entrar, sair e
  saúde.
- **SC-002**: 0 arquivos de documentação editados à mão para que uma mudança de rota ou campo
  num controller apareça na descrição (baseline: toda mudança exige edição manual).
- **SC-003**: obter a descrição atualizada exige 1 comando já existente (rodar a suíte de
  testes), sem passo manual (baseline: edição e conferência manual contra a aplicação rodando).
- **SC-004**: remover qualquer uma das 8 operações da descrição faz a suíte falhar em 100% dos
  casos.
- **SC-005**: duas gerações consecutivas sem mudança de código produzem 0 linhas de diferença no
  arquivo.
- **SC-006**: o dono completa o fluxo cadastro → CSRF → entrar → cadastrar e consultar imóvel →
  sair num API client usando só o arquivo gerado, sem consultar outro documento.

## Assumptions

- **Consumidores**: a descrição é consumida só pelos API clients locais do dono e pela página de
  documentação; não há terceiros, geração de SDK nem publicação externa (research não encontrou
  nenhum consumidor além desses).
- **Coleção Bruno** (`docs/api/habitattion-api/`): continua manual e fora do escopo; não é
  removida nem derivada da descrição.
- **Contagem de operações**: a decisão fala em "7/7"; o arquivo manual e o código têm 8
  operações (5 de controllers + `POST /sessao`, `DELETE /sessao`, `GET /actuator/health`). Esta
  spec usa 8.
- **Forma do teste**: o teste exporta o arquivo **e** verifica conteúdo mínimo (FR-008); só
  exportar não satisfaria a regra de TDD, porque não teria como falhar.
- **"Try it out"**: executar pela página operações que exigem CSRF é desejável, não obrigatório;
  se custar além do apetite (alguns dias), a página fica só de leitura e o fluxo continua
  coberto pelo API client (US2).
- **Fidelidade ao arquivo atual**: o arquivo gerado não precisa reproduzir literalmente a
  estrutura, a ordem ou o texto do YAML manual; precisa preservar o conteúdo explicativo (guia,
  português, exemplos) na medida do possível.
- **Ambiente da exportação**: a exportação roda no modo rápido (banco em memória) sem Docker nem
  Redis; a compatibilidade do gerador com Spring Boot 4.1.2 / Java 25 e a subida da aplicação
  nesse teste são validadas na primeira task, antes do resto.
- **CI**: nenhum pipeline é criado; o comando fica pronto para um CI futuro, que detectaria
  arquivo desatualizado por diferença após rodar a suíte.
- **Agregados sem rota** (`Inquilino`, `Contrato`, `Fatura`): fora do escopo; passam a aparecer
  na descrição automaticamente quando ganharem controllers.
- **Exceções ao §9**: a nova dependência e o endpoint de documentação foram acordados pelo dono
  na avaliação (`intake.md`, Clarifications); nenhuma outra dependência ou endpoint entra.
