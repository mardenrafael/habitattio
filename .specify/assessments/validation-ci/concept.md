# Concept: Validação automática de PRs com alvo `main`

- **Slug**: validation-ci
- **Created**: 2026-11-02
- **Recommended option**: A — Uma validação única em runner hospedado do GitHub

Contexto observado no repositório (fato, não desenho): o `pom.xml` não declara o
`maven-failsafe-plugin` e não há classes `*IT`; toda a suíte roda pelo surefire, logo
`./mvnw package` já executa todos os testes — inclusive os 14 arquivos de teste que usam
Testcontainers e o `ImagemContainerTest`. O wrapper fixa Maven 3.10.0; o projeto exige Java 25.

## Options

### Option A — Uma validação única em runner hospedado do GitHub

- **Sketch**: Ao abrir ou atualizar um PR com alvo `main`, o GitHub Actions roda, numa máquina
  hospedada pelo GitHub (Linux, Docker já disponível), o mesmo `./mvnw package` que o
  desenvolvedor rodaria localmente. O PR mostra um único check, verde ou vermelho; quando
  vermelho, os relatórios de teste ficam acessíveis a partir do próprio PR. Esse check passa a ser
  exigido pela proteção de branch de `main`, então o merge fica bloqueado enquanto ele falhar.
- **Appetite**: small (dias).
- **Trade-offs**: ganha simplicidade máxima e paridade com o comando local (um veredito, um
  comando, sem divergência entre "o que o CI roda" e "o que o AGENTS.md manda rodar"); Docker vem
  de graça no runner. Sacrifica velocidade de feedback — o veredito só sai depois da suíte inteira,
  incluindo pull de imagens e a construção de imagem do `ImagemContainerTest` — e não distingue
  "falhou no domínio" de "falhou na integração" além do que o relatório mostra. Risco: tempo de
  execução desconhecido (aceitável, sem meta de tempo por ora).
- **Rabbit holes**:
  - `ImagemContainerTest` constrói a imagem dentro da suíte: depende de baixar as imagens-base do
    `Dockerfile` e pode ser o passo mais lento/instável. Não é o "build de imagem de produção" que
    ficou fora do escopo — é teste — mas pesa no tempo.
  - Pulls de imagem (PostgreSQL, Redis, imagens-base) sujeitos a limite de taxa de registries
    públicos.
  - Disponibilidade de Java 25 nas ações de setup do runner.
  - Exibição dos relatórios de falha: anexar arquivos é trivial; anotar falhas inline no PR pode
    puxar ações de terceiros e permissões extras — fácil de inflar.
  - Tornar o check obrigatório é configuração da proteção de branch (feita pelo dono no GitHub,
    fora do repositório) e depende de o nome do check ser estável.

### Option B — Validação em etapas paralelas com veredito agregado

- **Sketch**: O PR dispara várias etapas em paralelo — testes rápidos de domínio/aplicação, testes
  de integração com containers e empacotamento — e um passo final agrega tudo num único veredito
  obrigatório. O dono vê rapidamente se as regras de negócio quebraram, antes de a integração
  terminar.
- **Appetite**: medium (semanas, considerando a separação da suíte).
- **Trade-offs**: ganha feedback mais rápido e diagnóstico mais claro por categoria. Sacrifica a
  paridade com o comando local: hoje a suíte não é separada (sem failsafe, sem convenção `*IT`),
  então separar exige mudar a forma como os testes são organizados ou filtrados — mexendo em
  build/teste por causa do CI. Mais peças, mais pontos de falha, e o empacotamento acaba rodando os
  testes de novo ou sendo configurado para não rodar.
- **Rabbit holes**: reorganizar a suíte (failsafe, tags, convenção de nomes); garantir que nenhum
  teste caia "entre" as categorias e deixe de rodar — o que violaria a meta de rodar a suíte
  completa; agregação correta do veredito único.

### Option C — Não fazer nada (manter a verificação manual)

- **Sketch**: Continuar como hoje: o autor do PR (humano ou agente) roda `./mvnw test`/`package`
  localmente e declara o resultado; o dono confia na declaração ao mergear.
- **Appetite**: nenhum.
- **Trade-offs**: custo zero de construção. Mantém todas as metas no baseline (0% de PRs
  validados, falhas de integração dependentes da máquina de quem rodou, sem relatório de falha
  verificável).
- **Rabbit holes**: nenhum na construção; o risco está no custo da inação descrito no
  `problem.md`.

## Recommendation

**Option A.** Cobre todas as metas do `problem.md` com o menor apetite: veredito único e
obrigatório sobre o estado do PR, suíte completa com Docker (o runner já o tem, e `package` já
roda tudo pelo surefire), relatórios de teste disponíveis na falha, e o mesmo comando que o
AGENTS.md prescreve localmente — o que mantém CI e desenvolvimento local medindo a mesma coisa.
A ausência de meta de tempo tira o principal argumento da Option B; a separação da suíte que ela
exige seria uma mudança de build motivada só pelo CI. A Option C não move nenhuma métrica.

## Out of Scope (for the recommended option)

- Construir, publicar ou versionar a imagem da aplicação para produção (o build de imagem dentro
  do `ImagemContainerTest` é teste e continua rodando).
- Deploy, release ou qualquer etapa pós-merge; gatilho em push para `main`.
- PRs com alvo diferente de `main`.
- Análise estática, cobertura mínima, scan de segurança.
- Separar ou reorganizar a suíte de testes (failsafe, tags, paralelização).
- Meta de tempo de execução e otimizações de velocidade além do trivial (ex.: cache de
  dependências Maven é opcional, não requisito).
- Anotação inline de falhas no PR por ações de terceiros — a meta é o relatório acessível, não a
  anotação.
- Configurar a proteção de branch em si — já existe; apenas incluir o novo check como obrigatório
  é passo manual do dono.

## Assumptions to Validate

- O runner Linux hospedado do GitHub executa os testes com Testcontainers e o
  `ImagemContainerTest` (construção de imagem pelo builder clássico do daemon) sem ajuste de
  ambiente.
- Java 25 está disponível para o runner por uma ação de setup padrão.
- O tempo total de `./mvnw package` no runner é tolerável para o dono, mesmo sem meta formal.
- Os limites de taxa de pull de imagens públicas não causam falhas recorrentes no volume de PRs do
  projeto (um único colaborador).
- O repositório tem minutos de GitHub Actions suficientes para o volume de PRs
  [NEEDS CLARIFICATION: repositório público ou privado? Afeta a cota gratuita].
- Validar o commit de merge do PR com `main` (e não só a cabeça do branch) é o que "estado exato
  do PR" significa para o dono.
- Todos os PRs vêm de branches do próprio repositório (sem forks), já que não há outros
  colaboradores — dispensando cuidados de permissão para PRs de fork.
