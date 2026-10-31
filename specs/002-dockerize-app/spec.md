# Feature Specification: Imagem de container do habitattio (primeira fatia)

**Feature Branch**: `002-dockerize-app`

**Created**: 2026-10-30

**Status**: Draft

**Input**: User description: "Dockerfile para o habitattio conforme .specify/assessments/dockerize-app/decision.md (Option B, primeira fatia)"

**Origem**: assessment `dockerize-app` (veredito **go**, Option B, primeira fatia). Artefatos:
`.specify/assessments/dockerize-app/{intake,research,problem,concept,decision}.md`.

## Contexto

O dono do projeto vai fazer a primeira instalação de produção do habitattio numa VPS. Hoje não
existe uma forma empacotada e repetível de levar a aplicação até lá: é preciso preparar o host à
mão (JDK 25, build, variáveis do modo produção). Cada instalação nova repete esse trabalho, pode
divergir do ambiente testado e não há como o orquestrador saber se a aplicação está saudável.

Esta fatia entrega uma **imagem de container** da aplicação, construída a partir de uma receita
escrita à mão e versionada no repositório, com verificação de saúde e um teste automatizado que
prova a subida. O scan de vulnerabilidades da imagem fica para uma **segunda fatia**, depois do
estudo de ferramentas do dono. O dono aceitou explicitamente que a imagem exista sem scan até lá.

Algumas tecnologias são citadas pelo nome porque são restrições já decididas pelo dono, e não
escolhas desta spec: container Docker com receita escrita à mão (Dockerfile; Buildpacks
descartados), Docker Hub como registry privado, Actuator como fonte da verificação de saúde
(dependência nova, com acordo explícito), PostgreSQL em produção e a arquitetura `linux/amd64`.
Os mecanismos concretos ficam para o `plan.md`.

## User Scenarios & Testing *(mandatory)*

Os "usuários" desta feature são o **dono do projeto como operador** da VPS e o **desenvolvedor**
(hoje o mesmo dono, mais os agentes de código que seguem o AGENTS.md). Os proprietários que usam
o sistema são afetados indiretamente, pela disponibilidade da instalação de produção.

### User Story 1 - Subir a aplicação em produção a partir da imagem (Priority: P1)

Como operador, quero construir a imagem na minha máquina e rodá-la num host que só tenha o
runtime de containers, no modo produção e ligada ao PostgreSQL, passando a configuração e os
segredos por variáveis de ambiente. Assim, a primeira instalação na VPS não exige JDK, Maven nem
build no servidor.

**Why this priority**: é a motivação principal do assessment (primeira instalação de produção).
Sozinha, esta história já entrega o valor central.

**Independent Test**: construir a imagem a partir do repositório, rodá-la com o modo produção
ativado e as variáveis de conexão apontando para um PostgreSQL acessível, e verificar que a
aplicação responde na porta configurada. O host não tem JDK.

**Acceptance Scenarios**:

1. **Given** a imagem construída e um PostgreSQL acessível, **When** o container é iniciado com
   o modo produção ativado por variável de ambiente e as credenciais do banco por variáveis de
   ambiente, **Then** a aplicação sobe e responde na porta configurada.
2. **Given** a imagem construída e um banco cuja estrutura diverge do mapeamento da aplicação,
   **When** o container é iniciado no modo produção, **Then** a aplicação se recusa a subir,
   com o mesmo comportamento de hoje fora do container.
3. **Given** a imagem construída, **When** se inspeciona o processo da aplicação em execução,
   **Then** ele não roda como superusuário (root).
4. **Given** a imagem construída, **When** se inspeciona o conteúdo da imagem final, **Then**
   ela contém só o runtime Java 25 necessário para executar, sem o kit de compilação, sem o
   código-fonte e sem as ferramentas de build.

---

### User Story 2 - O orquestrador sabe se a aplicação está saudável (Priority: P1)

Como operador, quero que o container informe se a aplicação está saudável, considerando o
acesso ao banco e não só se o processo está de pé. Assim, o orquestrador da VPS consegue reagir
a uma aplicação que subiu mas não está funcional.

**Why this priority**: é métrica de sucesso explícita do assessment e parte do "pronto para
produção". Também é o que o teste automatizado verifica.

**Independent Test**: com o container em execução, consultar o estado de saúde reportado pelo
runtime de containers e pelo endpoint de saúde, com e sem o PostgreSQL acessível, e sem Redis
em nenhum dos casos.

**Acceptance Scenarios**:

1. **Given** o container em modo produção com o PostgreSQL acessível e **sem** Redis
   acessível, **When** a verificação de saúde roda, **Then** o estado é "saudável".
2. **Given** o container em modo produção, já saudável, **When** o PostgreSQL deixa de estar
   acessível, **Then** o estado passa a "não saudável".
3. **Given** o container em execução, **When** alguém consulta o endpoint de saúde pela rede,
   **Then** a resposta traz só o estado geral, sem detalhes por componente.
4. **Given** o container em execução, **When** alguém tenta acessar qualquer outro endpoint
   operacional (métricas, ambiente, configuração etc.), **Then** ele não está exposto.

---

### User Story 3 - Garantir que a VPS roda exatamente a imagem publicada (Priority: P2)

Como operador, quero publicar a imagem num repositório privado com uma versão que não muda e
conferir que o que roda na VPS é exatamente o que publiquei. Assim, não há divergência entre o
artefato testado e o que está em produção.

**Why this priority**: é a pergunta do dono que o decision.md trouxe como requisito. Depende da
US1 (precisa da imagem), mas é verificável à parte.

**Independent Test**: publicar a imagem com uma tag de versão, baixá-la na VPS com
autenticação e comparar o identificador de conteúdo (digest) da imagem em execução com o que o
push devolveu.

**Acceptance Scenarios**:

1. **Given** uma imagem construída, **When** o operador a publica, **Then** ela é identificada
   por uma tag de versão imutável, e não por `latest`.
2. **Given** a imagem publicada no repositório privado, **When** a VPS tenta baixá-la sem
   credenciais, **Then** o download é negado; **When** baixa com credenciais, **Then** consegue.
3. **Given** o container em execução na VPS, **When** o operador compara o digest da imagem em
   execução com o digest registrado no push, **Then** os dois coincidem, e o procedimento para
   fazer essa comparação está documentado.

---

### User Story 4 - Mesma imagem no modo rápido para desenvolvimento (Priority: P3)

Como desenvolvedor, quero rodar a mesma imagem sem ativar nenhum modo e ter a aplicação no modo
rápido (banco em memória), sem JDK 25 no host. Assim, montar um ambiente de execução fica mais
fácil, e o artefato é o mesmo de produção.

**Why this priority**: ganho real, porém menor (há um único desenvolvedor, que já tem JDK 25).

**Independent Test**: rodar a imagem sem variável de modo e sem banco externo e verificar que a
aplicação sobe e fica saudável.

**Acceptance Scenarios**:

1. **Given** a imagem construída, **When** o container é iniciado sem ativar o modo produção e
   sem banco externo, **Then** a aplicação sobe no modo rápido e o estado de saúde é "saudável".
2. **Given** o projeto com esta feature, **When** o desenvolvedor usa `./mvnw spring-boot:run`
   fora do container, **Then** o comportamento é igual ao de antes da feature.

---

### User Story 5 - Rebuild rápido quando só o código mudou (Priority: P3)

Como desenvolvedor, quero que reconstruir a imagem depois de mudar só o código da aplicação
reaproveite as dependências já baixadas e a camada de dependências da imagem. Assim, cada
rebuild e cada push ficam mais curtos.

**Why this priority**: eficiência, sem impacto funcional.

**Independent Test**: construir a imagem, alterar só um arquivo de código da aplicação,
reconstruir e verificar que as dependências não são baixadas de novo e que a camada de
dependências é reaproveitada (cache).

**Acceptance Scenarios**:

1. **Given** uma imagem já construída, **When** só o código da aplicação muda e a imagem é
   reconstruída, **Then** as dependências não são baixadas de novo e a camada de dependências
   da imagem é reaproveitada.
2. **Given** arquivos irrelevantes para o build no diretório do projeto (saída de build local,
   metadados de IDE e de git, artefatos de spec e de assessment), **When** a imagem é
   construída, **Then** eles não entram no contexto de build.

---

### User Story 6 - Teste automatizado prova a imagem (Priority: P1)

Como desenvolvedor, quero que a suíte de testes do projeto construa a imagem a partir da receita
versionada e verifique a subida e a saúde. Assim, uma mudança que quebre a imagem é detectada
antes do push, e a feature segue o TDD da constitution.

**Why this priority**: é o critério de aceite em TDD acordado com o dono e a única prova
automática de que as US1, US2 e US4 continuam valendo.

**Independent Test**: rodar `./mvnw test` com Docker disponível e verificar que o teste da
imagem a constrói a partir da receita do repositório e afirma a subida e a saúde.

**Acceptance Scenarios**:

1. **Given** Docker disponível, **When** a suíte roda, **Then** um teste constrói a imagem a
   partir da receita do repositório (e não de uma imagem pronta) e verifica que o container sobe
   e fica "saudável" no modo produção com PostgreSQL e sem Redis.
2. **Given** Docker disponível, **When** a suíte roda, **Then** um teste verifica que o estado
   fica "não saudável" quando o PostgreSQL não está acessível.
3. **Given** Docker disponível, **When** a suíte roda, **Then** um teste verifica que o processo
   do container não roda como root.
4. **Given** uma mudança que quebre a receita da imagem, **When** a suíte roda, **Then** o teste
   da imagem falha.

---

### Edge Cases

- **Modo produção esquecido**: se o container for iniciado na VPS sem ativar o modo produção,
  ele sobe no modo rápido, com dados em memória que se perdem. **Risco aceito pelo dono em
  2026-10-30**: esta fatia não detecta nem impede isso. O risco fica documentado no guia de
  operação.
- **Variáveis de conexão ausentes ou erradas no modo produção**: a aplicação não sobe (ou nunca
  fica saudável) e o motivo aparece no log do container. Ela não cai silenciosamente para o banco
  em memória.
- **Redis ausente**: não afeta a subida nem a saúde (o starter de Redis continua no projeto; só
  o indicador de saúde dele é desconsiderado). Desconsiderar o Redis **não** pode desconsiderar
  o indicador do banco.
- **Imagem mínima sem utilitários HTTP**: a verificação de saúde do container precisa funcionar
  mesmo que a imagem final não tenha ferramentas de linha de comando para requisições HTTP.
- **Subida lenta**: a verificação de saúde não pode marcar o container como "não saudável"
  durante o tempo normal de subida da aplicação (inclui as migrações do modo produção).
- **Imagem base envelhecida**: com a base fixada por digest, ela não se atualiza sozinha. A
  atualização do digest é um passo manual e documentado (o scan da segunda fatia vai ajudar a
  saber quando atualizar).
- **Docker indisponível ao rodar a suíte**: o teste da imagem não roda, como os demais testes de
  integração. O relatório deve dizer isso explicitamente (constitution, Stack e Restrições).
- **Limites do plano do Docker Hub** (quantidade de repositórios privados, pulls): risco a
  observar, sem mitigação prévia por decisão do dono.

## Requirements *(mandatory)*

### Functional Requirements

**Construção da imagem**

- **FR-001**: O repositório MUST conter uma receita de imagem escrita à mão e versionada, a
  partir da qual a imagem da aplicação é construída na máquina do desenvolvedor.
- **FR-002**: A construção MUST ser em estágios: a compilação acontece num estágio separado, e a
  imagem final contém só o runtime Java 25 necessário para executar, sem o kit de compilação,
  sem o código-fonte e sem as ferramentas de build.
- **FR-003**: A construção da imagem MUST NOT rodar testes. A suíte completa roda antes, no
  host.
- **FR-004**: A aplicação MUST ser empacotada na imagem final em camadas separadas, de modo que
  as dependências fiquem numa camada que é reaproveitada quando só o código muda.
- **FR-005**: A construção MUST reaproveitar as dependências já baixadas quando só o código da
  aplicação mudou.
- **FR-006**: O repositório MUST definir o que fica fora do contexto de build (saída de build
  local, metadados de IDE e de git, artefatos de spec e de assessment, arquivos de segredo como
  `.env`).
- **FR-007**: Todas as imagens base usadas MUST ser fixadas por digest, e não só por tag.
- **FR-008**: A imagem MUST ser construída para `linux/amd64` (arquitetura da VPS e da máquina
  de build; sem cross-build).

**Execução**

- **FR-009**: O processo da aplicação no container MUST rodar com um usuário sem privilégios de
  superusuário.
- **FR-010**: A mesma imagem MUST servir aos dois modos: sem ativar nenhum modo, sobe no modo
  rápido (banco em memória); com o modo produção ativado por variável de ambiente, sobe ligada
  ao PostgreSQL com as regras atuais do modo produção (estrutura só por migração, conferência do
  mapeamento na subida).
- **FR-011**: Toda configuração de ambiente e todo segredo (URL, usuário e senha do banco, modo)
  MUST entrar por variáveis de ambiente no momento da execução. A imagem MUST NOT conter
  segredos.
- **FR-012**: A imagem MUST NOT incluir nenhum mecanismo para detectar ou impedir a subida no
  modo rápido em produção (risco aceito; ver Edge Cases).

**Saúde**

- **FR-013**: A aplicação MUST expor um endpoint de saúde que reporta "saudável" ou "não
  saudável" levando em conta o acesso ao banco de dados.
- **FR-014**: O endpoint de saúde MUST responder só com o estado geral, sem detalhes por
  componente.
- **FR-015**: Nenhum outro endpoint operacional, além do de saúde, MUST ser exposto.
- **FR-016**: O estado de saúde MUST desconsiderar a disponibilidade do Redis e MUST continuar
  considerando o banco de dados. O suporte a Redis continua no projeto.
- **FR-017**: A imagem MUST declarar uma verificação de saúde do container baseada no endpoint de
  saúde, que funcione sem depender de utilitários HTTP de linha de comando e que tolere o tempo
  normal de subida da aplicação.

**Publicação e verificação na VPS**

- **FR-018**: A imagem MUST ser publicada manualmente num repositório **privado** do Docker Hub,
  identificada por uma tag de versão imutável; `latest` MUST NOT ser a referência usada para
  instalar na VPS.
- **FR-019**: A VPS MUST baixar a imagem com autenticação no repositório privado.
- **FR-020**: MUST existir um procedimento documentado para confirmar que o digest da imagem em
  execução na VPS coincide com o digest gerado no push.

**Testes e documentação**

- **FR-021**: A suíte de testes MUST incluir um teste automatizado que constrói a imagem a
  partir da receita do repositório e verifica: (a) subida e estado "saudável" no modo produção
  com PostgreSQL e sem Redis; (b) estado "não saudável" sem o PostgreSQL acessível; (c) processo
  sem privilégios de superusuário; (d) subida e estado "saudável" no modo rápido, sem banco
  externo.
- **FR-022**: O teste da imagem MUST seguir as mesmas regras dos testes de integração atuais
  (exige Docker, faz parte de `./mvnw test`, e a ausência de Docker é declarada no relatório).
- **FR-023**: O fluxo de desenvolvimento atual MUST continuar igual: `./mvnw spring-boot:run`, o
  modo rápido e `./mvnw spring-boot:test-run`.
- **FR-024**: O projeto MUST documentar como construir, publicar (com tag de versão), baixar com
  autenticação, rodar nos dois modos (variáveis necessárias), conferir o digest, atualizar o
  digest da imagem base, e o risco aceito do modo produção esquecido.

### Fora de escopo

- Scan de vulnerabilidades da imagem (segunda fatia, depois do estudo de ferramentas do dono).
- Arquivo `docker-compose` e definição do PostgreSQL ou de outros serviços.
- Provisionamento da VPS (SO, Docker, firewall, TLS, domínio, proxy reverso).
- CI/CD: build e push continuam manuais.
- Testes dentro da construção da imagem.
- Endpoints operacionais além do de saúde, e detalhes de saúde por componente.
- Segredos montados como arquivo (Docker secrets).
- Microsserviços, imagem nativa e Buildpacks.
- Backup e operação do banco de produção.
- Proteção contra subir o modo rápido em produção (risco aceito).

### Key Entities

- **Imagem da aplicação**: artefato executável e imutável, identificado por uma tag de versão e
  por um digest de conteúdo. Contém só o runtime e a aplicação em camadas, roda sem privilégios
  e recebe a configuração por ambiente.
- **Receita da imagem**: definição versionada no repositório, a partir da qual a imagem é
  construída, inclusive pelo teste automatizado.
- **Estado de saúde**: "saudável" ou "não saudável", derivado do acesso ao banco e
  desconsiderando o Redis; exposto pelo endpoint de saúde e usado pela verificação do container.
- **Repositório privado**: destino da publicação manual; exige autenticação para baixar.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: A primeira instalação de produção na VPS é concluída usando a imagem, com a
  aplicação respondendo na porta configurada.
- **SC-002**: O host de execução precisa de zero ferramentas além do runtime de containers
  (nenhum JDK, Maven ou build no host).
- **SC-003**: Em 100% das execuções do teste automatizado, o estado é "saudável" com o
  PostgreSQL acessível e sem Redis, e "não saudável" sem o PostgreSQL.
- **SC-004**: O processo da aplicação no container roda com usuário sem privilégios em 100% das
  execuções verificadas.
- **SC-005**: Um rebuild depois de mudar só código não baixa nenhuma dependência de novo e
  reaproveita a camada de dependências.
- **SC-006**: A imagem sobe no modo rápido e fica "saudável" num host sem JDK, sem banco externo.
- **SC-007**: O digest da imagem em execução na VPS coincide com o digest do push na instalação
  de produção.
- **SC-008**: Uma mudança que quebre a receita da imagem é detectada pelo `./mvnw test` antes de
  qualquer push.

## Assumptions

- Existe uma imagem base com runtime Java 25 adequada para `linux/amd64` que pode ser fixada por
  digest (não verificado; risco a observar no plan).
- A aplicação sobe no modo produção sem Redis acessível, e dá para desconsiderar só o indicador
  de saúde do Redis sem afetar o do banco (research.md, ASSUMPTION; a ser confirmado no plan).
- A conta do Docker Hub do dono permite o repositório privado necessário, e a VPS tem
  credenciais próprias para baixar a imagem (não verificado; risco a observar).
- O teste que constrói a imagem cabe num tempo aceitável dentro de `./mvnw test`. Se não couber,
  o plan propõe como separá-lo sem deixar de rodar a suíte completa.
- A porta da aplicação continua a atual (8080), configurável por ambiente.
- A tag de versão segue um esquema simples definido no plan (por exemplo, a versão do projeto
  mais um identificador do commit); o requisito é só ser imutável e diferente de `latest`.
- O compose da VPS, fora deste escopo, vai usar a verificação de saúde declarada na imagem.
- O dono cuida de abrir a segunda fatia (scan) quando terminar o estudo das ferramentas.
