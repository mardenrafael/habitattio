# Feature Specification: Validação automática de PRs com alvo `main`

**Feature Branch**: `004-pr-validation-ci`

**Created**: 2026-11-02

**Status**: Draft

**Input**: User description: ".specify/assessments/validation-ci/decision.md"

**Origem**: assessment `validation-ci` (veredito **go**, Option A). Artefatos:
`.specify/assessments/validation-ci/{intake,problem,concept,decision}.md` (research pulado: o
problema é interno ao repositório).

## Contexto

`main` só recebe mudanças por pull request, e a proteção de branch já bloqueia push direto. Mas o
PR é aceito sem nenhuma evidência independente de que a suíte de testes passa e de que o projeto
empacota. Hoje isso depende de quem abriu o PR (o dono ou um agente de código): essa pessoa precisa
lembrar de rodar a suíte localmente, com Docker disponível, e relatar o resultado com honestidade.
Sete PRs já entraram em `main` assim. A constitution proíbe declarar a suíte verde sem executá-la,
mas ninguém de fora confere essa declaração.

Esta feature cria uma **validação automática e obrigatória** para todo PR com alvo `main`. Ela
executa a suíte completa, inclusive os testes de integração que dependem de containers, e o
empacotamento do projeto. O resultado é um **veredito único**, visível no próprio PR, que bloqueia
o merge quando falha e dá acesso aos relatórios de teste para diagnóstico.

Algumas tecnologias aparecem pelo nome porque o dono já as decidiu como restrições, e não são
escolhas desta spec: o GitHub, com sua plataforma de automação (GitHub Actions) e máquinas Linux
hospedadas por ele; o mesmo comando de build/teste que o AGENTS.md manda rodar localmente; e o
repositório público. O mecanismo concreto fica para o `plan.md`.

## User Scenarios & Testing *(mandatory)*

Os "usuários" desta feature são o **dono do projeto**, que revisa e mergeia PRs, e quem **abre
PRs**: o próprio dono e os agentes de código que seguem o AGENTS.md. Os proprietários que usam o
sistema são afetados indiretamente, porque menos regressões chegam a `main` e à produção.

### User Story 1 - Veredito automático da suíte completa em todo PR para `main` (Priority: P1)

Como dono, quero que todo PR com alvo `main` seja validado automaticamente, sem ninguém disparar
nada, rodando a suíte completa (incluindo os testes de integração com containers) e o
empacotamento sobre o estado exato que entraria em `main`. Assim, quero ver no PR um único
resultado, aprovado ou reprovado, em vez de confiar numa declaração de quem o abriu.

**Why this priority**: é o núcleo do problema. Sem o veredito não há o que exigir nem o que
diagnosticar. Sozinha, esta história já tira o projeto do baseline de 0% de PRs validados.

**Independent Test**: abrir um PR com alvo `main` sem mudança funcional e verificar que a
validação começa sozinha, roda todos os testes (nenhum pulado por falta de Docker) e termina com
veredito aprovado. Depois, abrir um PR com um teste propositalmente quebrado e verificar o veredito
reprovado.

**Acceptance Scenarios**:

1. **Given** um PR aberto com alvo `main`, **When** o PR é criado, **Then** a validação começa
   sem ação manual e, ao final, o PR mostra um único resultado de validação.
2. **Given** um PR com alvo `main` já validado, **When** um novo commit é enviado ao branch do PR,
   **Then** uma nova validação roda sobre o estado atualizado, e o resultado exibido passa a ser o
   dela.
3. **Given** um PR cuja suíte completa passa e cujo projeto empacota, **When** a validação
   termina, **Then** o veredito é aprovado.
4. **Given** um PR em que pelo menos um teste falha (de domínio, de aplicação ou de integração),
   **When** a validação termina, **Then** o veredito é reprovado.
5. **Given** um PR em que todos os testes passam, mas o empacotamento falha, **When** a validação
   termina, **Then** o veredito é reprovado. Testes e empacotamento formam um único veredito.
6. **Given** um PR que inclui os testes de integração com banco, cache e construção da imagem da
   aplicação, **When** a validação roda, **Then** esses testes são executados de fato. Nenhum é
   pulado ou ignorado por falta de ambiente de containers.
7. **Given** um PR aberto com alvo diferente de `main`, **When** ele é criado ou atualizado,
   **Then** essa validação não roda.

---

### User Story 2 - Merge bloqueado enquanto a validação não aprova (Priority: P2)

Como dono, quero que o resultado da validação possa ser exigido pela proteção de branch de `main`.
Assim, nenhum PR entra com a validação reprovada, em andamento ou ausente.

**Why this priority**: transforma o veredito em controle. Depende da US1 e de um passo manual do
dono na configuração do GitHub, que fica fora do repositório. Por isso vem depois.

**Independent Test**: depois de marcar a validação como obrigatória na proteção de `main`, abrir
um PR com um teste quebrado e confirmar que o merge fica indisponível. Corrigir o teste e
confirmar que o merge é liberado quando o veredito passa a aprovado.

**Acceptance Scenarios**:

1. **Given** a validação marcada como obrigatória na proteção de `main` e um PR com veredito
   reprovado, **When** o dono tenta mergear, **Then** o merge é bloqueado.
2. **Given** a validação obrigatória e um PR com validação ainda em andamento, **When** o dono
   tenta mergear, **Then** o merge é bloqueado até haver veredito aprovado.
3. **Given** a validação obrigatória e um PR com veredito aprovado, **When** o dono mergeia,
   **Then** o merge é permitido (salvo outras regras de proteção já existentes).
4. **Given** várias execuções da validação em PRs diferentes ao longo do tempo, **When** o dono
   escolhe na proteção de branch qual verificação exigir, **Then** a validação aparece sempre com
   o mesmo nome, para ser selecionada uma única vez e continuar valendo para os PRs seguintes.

---

### User Story 3 - Diagnóstico da falha a partir do PR (Priority: P3)

Como quem abriu o PR (dono ou agente), quero acessar, a partir do próprio PR, os relatórios dos
testes de uma validação reprovada. Assim, descubro qual teste falhou e por quê sem precisar
reproduzir a falha localmente.

**Why this priority**: melhora o ciclo de correção, mas o veredito e o bloqueio já entregam o
controle sem ela.

**Independent Test**: abrir um PR com um teste propositalmente quebrado, esperar o veredito
reprovado e, a partir do PR, chegar aos relatórios de teste que identificam o teste que falhou e a
mensagem de falha.

**Acceptance Scenarios**:

1. **Given** uma validação reprovada por falha de teste, **When** o usuário segue, a partir do PR,
   para os detalhes da validação, **Then** encontra os relatórios de teste com o nome de cada
   teste que falhou e a mensagem ou rastro da falha.
2. **Given** uma validação reprovada antes de qualquer teste rodar (por exemplo, erro de
   compilação), **When** o usuário segue para os detalhes da validação, **Then** encontra o
   registro da execução mostrando em que etapa e por que ela falhou.

---

### Edge Cases

- **PR desatualizado em relação a `main`**: a validação considera o resultado da combinação do PR
  com o `main` atual (o que de fato entraria), não só o branch isolado. Se `main` avançar depois
  da validação, a política de exigir o branch atualizado continua sendo da proteção de branch (já
  existente) e não desta feature.
- **PR com conflito de merge com `main`**: não existe estado combinado para validar. O PR não
  recebe veredito aprovado, e o merge continua impossível até o conflito ser resolvido.
- **PR aberto a partir de fork** (o repositório é público): a validação roda igual, sem acesso a
  segredos e sem permissão de escrita no repositório. Uma mudança maliciosa no PR não consegue
  extrair credenciais nem alterar o repositório.
- **Commits enviados em sequência rápida**: o resultado que vale é o da validação do commit mais
  recente. Um veredito de commit anterior não pode aprovar o estado novo.
- **Falha de infraestrutura** (por exemplo, indisponibilidade ao baixar imagens de containers ou
  dependências): a validação reprova. Ela nunca aprova sem ter executado a suíte completa. O dono
  pode pedir uma nova execução sem novo commit.
- **Teste instável** (passa e falha sem mudança de código): a validação não faz retentativa
  silenciosa de teste. Um teste que falha reprova o veredito, e a nova execução é pedida
  manualmente.
- **Execução muito longa ou travada**: há um teto de duração. Ao atingi-lo, a validação é
  encerrada como reprovada, em vez de ficar indefinidamente em andamento.
- **PR em rascunho (draft)**: a validação roda normalmente, como em qualquer PR com alvo `main`.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: A validação MUST iniciar automaticamente quando um PR com alvo `main` é aberto,
  reaberto ou recebe novos commits, sem ação manual.
- **FR-002**: A validação MUST NOT rodar para PRs com alvo diferente de `main` nem para pushes em
  qualquer branch.
- **FR-003**: A validação MUST operar sobre o estado combinado do PR com o `main` atual no momento
  da execução.
- **FR-004**: A validação MUST executar a suíte de testes completa do projeto pelo mesmo comando
  de build/empacotamento que o AGENTS.md prescreve localmente. Nenhum teste pode ser excluído,
  filtrado ou pulado em relação a uma execução local completa.
- **FR-005**: A validação MUST executar os testes de integração que dependem de containers
  (banco, cache e a construção da imagem da aplicação feita dentro da suíte) num ambiente com
  runtime de containers disponível. Um teste de integração pulado por falta desse ambiente
  configura falha de requisito.
- **FR-006**: A validação MUST verificar que o projeto empacota com sucesso.
- **FR-007**: A validação MUST produzir um **único** veredito (aprovado/reprovado) que combina
  testes e empacotamento. Aprovado exige que todos os testes passem e que o empacotamento conclua.
- **FR-008**: O veredito MUST aparecer no próprio PR, associado ao commit validado.
- **FR-009**: A validação MUST ser identificada por um nome estável entre execuções e PRs, para
  ser exigida uma única vez pela proteção de branch de `main`.
- **FR-010**: Quando reprovada, a validação MUST deixar acessíveis, a partir do PR, os relatórios
  dos testes executados (teste que falhou e mensagem/rastro) e o registro da execução.
- **FR-011**: A validação MUST funcionar sem nenhum segredo configurado e sem permissão de
  escrita no repositório. Isso inclui PRs abertos a partir de forks.
- **FR-012**: A validação MUST reprovar, nunca aprovar, quando não conseguir concluir a suíte
  completa por qualquer motivo (falha de infraestrutura, tempo esgotado, erro de ambiente).
- **FR-013**: A validação MUST ter um teto de duração. Ao ultrapassá-lo, é encerrada como
  reprovada.
- **FR-014**: Quando um novo commit chega a um PR, o veredito exibido MUST refletir a validação
  desse commit. Execuções ainda em andamento de commits anteriores do mesmo PR podem ser
  canceladas.
- **FR-015**: O dono MUST conseguir disparar uma nova execução da validação para o mesmo commit,
  sem precisar de novo commit.
- **FR-016**: A feature MUST NOT adicionar dependências ao build do projeto, reorganizar a suíte
  de testes nem alterar a forma de rodá-la localmente.
- **FR-017**: A documentação operacional do projeto MUST registrar a existência da validação, o
  que ela roda e o passo manual de marcá-la como obrigatória na proteção de `main`.

### Key Entities

- **Validação de PR**: uma execução automática associada a um PR com alvo `main` e a um commit
  específico. Atributos: commit validado (estado combinado com `main`), nome estável, estado (em
  andamento, aprovada, reprovada), duração, relatórios de teste e registro da execução.
- **Veredito**: o resultado único de uma validação (aprovado/reprovado) exibido no PR e
  consultável pela proteção de branch.
- **Relatórios de teste**: o detalhamento por teste de uma validação (nome, resultado,
  mensagem/rastro de falha), acessível a partir do PR.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: 100% dos PRs com alvo `main` abertos após a entrega têm veredito da validação antes
  do merge (baseline: 0%).
- **SC-002**: 0 PRs mergeados em `main` com a validação reprovada ou ausente após a validação ser
  marcada como obrigatória.
- **SC-003**: Em toda validação concluída, 100% dos testes de integração da suíte são executados.
  Nenhum aparece como pulado por falta de ambiente de containers.
- **SC-004**: 100% das validações reprovadas por falha de teste dão acesso, a partir do PR, ao
  nome do teste que falhou e à mensagem de falha.
- **SC-005**: O mesmo commit, validado duas vezes sem mudança, chega ao mesmo veredito (descontada
  falha de infraestrutura, que sempre reprova).
- **SC-006**: Um PR propositalmente quebrado (teste falhando) é reprovado, e um PR sem regressão é
  aprovado, na primeira execução após a entrega.
- **SC-007**: O tempo até o veredito é registrado em cada execução e observado pelo dono, sem meta
  nesta fatia.

## Assumptions

- O dono marca a validação como obrigatória na proteção de branch de `main` depois da entrega.
  Esse passo é manual, feito no GitHub e fora do repositório. A feature só garante um nome estável
  que torne isso possível.
- O repositório é público (verificado em 2026-11-02). As máquinas hospedadas padrão do GitHub são
  gratuitas nesse caso, e não há preocupação com cota de minutos.
- As máquinas Linux hospedadas do GitHub já têm runtime de containers e executam os testes de
  integração (inclusive a construção de imagem dentro da suíte) sem ajuste especial de ambiente.
  A primeira execução vai confirmar ou refutar essa suposição.
- A versão de Java exigida pelo projeto (25) está disponível para essas máquinas por meio de uma
  ação de setup padrão.
- "Estado exato do PR" significa o resultado da combinação do PR com o `main` atual, e não só a
  cabeça do branch do PR.
- O tempo total de execução é tolerável para o dono mesmo sem meta formal. Cache de dependências
  é otimização opcional, não requisito.
- Limites de taxa de download de imagens públicas não causam falhas recorrentes no volume de PRs
  do projeto (um único colaborador). Se causarem, a validação reprova (FR-012) e o dono pede uma
  nova execução.
- O teto de duração da FR-013 é escolhido no `plan.md`, com folga sobre o tempo observado de uma
  execução completa.
- **Fora do escopo**: construir ou publicar a imagem de produção, deploy e release; validar push em
  `main`; PRs com outro alvo; análise estática, cobertura mínima e scan de segurança; reorganizar
  a suíte (separar categorias, paralelizar); meta de tempo; anotar falhas linha a linha no PR com
  ferramentas de terceiros; configurar a proteção de branch em si.
- A feature é de infraestrutura de entrega, sem código de domínio. O `plan.md` decide como a regra
  de TDD do AGENTS.md se aplica à definição da validação. As opções são um teste automatizado
  sobre a definição (gatilho, comando, ausência de segredos e permissões) ou a verificação manual
  pelos cenários de aceitação com PRs reais, aprovado e propositalmente reprovado. Ficar fora do
  TDD exige acordo explícito do dono, como já ocorreu com o `.dockerignore`. Esta spec não decide
  isso. Em qualquer caso, os cenários de aceitação com PRs reais são a prova final.
