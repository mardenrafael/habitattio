# Concept: Containerizar a aplicação com Dockerfile

- **Slug**: dockerize-app
- **Created**: 2026-10-30
- **Recommended option**: Option B — Dockerfile agora, scan em fatia própria

## Entradas novas do dono (2026-10-30)

- **Registry:** Docker Hub, com a imagem em **repositório privado**.
- **Scan de vulnerabilidades:** continua obrigatório (problem.md, decisão 1), mas a ferramenta
  ainda não foi escolhida; o dono vai estudar as opções do mercado antes.

As decisões anteriores (problem.md) delimitam bastante o espaço de soluções. As opções abaixo
diferem principalmente em **como tratar o scan ainda indefinido**, não em como fazer a imagem.

## Options

### Option A — Pacote completo de uma vez (Dockerfile + healthcheck + scan)

- **Sketch**: Um único ciclo de spec e implementação entrega tudo o que o problem.md lista como
  obrigatório:
  - a imagem multi-stage, não-root, em camadas, com base fixada por digest e `.dockerignore`;
  - o healthcheck via Actuator, só `health` e ignorando o Redis;
  - os dois modos (H2 sem profile, `prod` por variável);
  - o teste automatizado que constrói a imagem e verifica a subida;
  - o scan de vulnerabilidades como passo obrigatório antes do push manual para o Docker Hub.

  O ciclo não pode começar enquanto o dono não terminar o estudo das ferramentas de scan.
- **Appetite**: small (dias), mais o tempo do estudo do scan, que hoje não tem prazo.
- **Trade-offs**:
  - Ganha: atende todos os critérios do problem.md numa entrega só, sem item pendente.
  - Sacrifica: a primeira instalação em produção, que é a motivação principal, fica bloqueada
    por uma decisão de ferramenta que não muda o Dockerfile.
  - Risco: a escolha do scan virar o caminho crítico.
- **Rabbit holes**: comparar ferramentas de scan dentro do ciclo de implementação; definir o
  limiar (falhar ou só reportar) sem ter visto os achados reais da imagem.

### Option B — Dockerfile agora, scan em fatia própria (recomendada)

- **Sketch**: A primeira fatia entrega a imagem com todos os critérios de construção do
  problem.md:
  - multi-stage com só o JRE na imagem final;
  - usuário não-root;
  - camadas e `.dockerignore`;
  - base fixada por digest;
  - healthcheck via Actuator;
  - os dois modos (H2 e `prod`);
  - o teste automatizado com Testcontainers.

  O dono já consegue construir, fazer push para o Docker Hub privado e subir na VPS. O scan vira
  uma segunda fatia, curta, aberta assim que o estudo das ferramentas terminar. Ela adiciona a
  verificação ao fluxo de build e push e define o limiar. A primeira fatia não fecha nenhuma
  porta para o scan: qualquer ferramenta de mercado analisa uma imagem já construída.
- **Appetite**: small (dias) para a primeira fatia; small para a fatia do scan.
- **Trade-offs**:
  - Ganha: destrava a primeira instalação sem esperar o estudo, e o estudo é feito com a imagem
    real em mãos (achados concretos ajudam a definir o limiar).
  - Sacrifica: a imagem pode ir para produção por um período sem scan, enquanto a segunda fatia
    não sai. Isso **adia um critério que o dono marcou como obrigatório** e precisa de
    confirmação explícita no `/speckit-assess-decide`.
  - Risco: a segunda fatia ser esquecida.
- **Rabbit holes**: os mesmos da primeira fatia de A, sem os do scan (ver lista comum abaixo).

### Option C — Não fazer o Dockerfile à mão (Buildpacks ou nada)

- **Sketch**: Usar o `./mvnw spring-boot:build-image`, já disponível no plugin do projeto
  (research.md), ou continuar instalando a aplicação direto no host da VPS.
- **Appetite**: small.
- **Trade-offs**:
  - Ganha: menos artefato para manter, sem dependência nova.
  - Sacrifica: contraria a decisão explícita do dono (Dockerfile escrito à mão, para ter
    controle). Sem container, nenhum objetivo do problem.md é atingido.
  - Registrada só para comparação: **descartada pelo dono** em 2026-10-30.
- **Rabbit holes**: n/a.

### Rabbit holes comuns a A e B (para o decide pesar)

- **Imagem base Java 25:** a disponibilidade e o tamanho de imagens JRE 25 mínimas não foram
  verificados (research.md). Fixar por digest também exige uma rotina de atualização da base,
  senão a imagem envelhece com vulnerabilidades.
- **Healthcheck em imagem mínima:** imagens JRE enxutas ou distroless podem não ter `curl` nem
  `wget`. O comando do healthcheck do container pode exigir uma solução própria.
- **Teste que constrói a imagem:** construir a imagem dentro de `./mvnw test` pode deixar a suíte
  bem mais lenta. Pode ser preciso separar esse teste do ciclo rápido sem violar a regra de que a
  suíte roda completa.
- **Actuator + Redis:** o efeito real do indicador de Redis na saúde (research.md, ASSUMPTION)
  precisa ser confirmado. Desligar só esse indicador não pode desligar o do banco.
- **Dois modos na mesma imagem:** o default sem profile é H2 (dados voláteis). Esquecer
  `SPRING_PROFILES_ACTIVE=prod` na VPS subiria produção em memória, então é preciso decidir se
  isso é aceitável ou se precisa de alguma proteção.
- **Docker Hub privado:** limites de repositórios privados e de pulls no plano da conta, e
  credenciais de pull na VPS. O plano da conta não foi verificado.

## Recommendation

**Option B.** O objetivo principal do problem.md é a primeira instalação em produção na VPS, e
as métricas de sucesso dela (instalação concluída, host só com runtime de containers,
healthcheck reportando a saúde real, não-root, cache de camadas, teste automatizado) não
dependem da ferramenta de scan. Bloquear tudo por um estudo sem prazo (Option A) atrasa o
objetivo sem melhorar a imagem. A fatia do scan fica pequena e mais bem informada, porque o
dono poderá avaliar as ferramentas contra a imagem real.

**Condição:** B adia um critério que o dono declarou obrigatório. O `/speckit-assess-decide`
precisa registrar a confirmação explícita do dono de que a imagem pode ir para produção antes do
scan, ou que o push para produção espera a segunda fatia. Se o dono não aceitar nenhuma das
duas, a escolha volta a ser a Option A.

## Out of Scope (for the recommended option)

- Ferramenta, execução e limiar do scan de vulnerabilidades (vão para a segunda fatia).
- Arquivo `docker-compose` e definição do PostgreSQL e de outros serviços na VPS.
- Provisionamento da VPS (SO, Docker, firewall, TLS, domínio, proxy reverso).
- CI/CD: build e push para o Docker Hub continuam manuais.
- Testes dentro do `docker build`.
- Endpoints do Actuator além de `health`, e detalhes por componente.
- Docker secrets em arquivo (segredos entram por variáveis de ambiente).
- Microsserviços, imagem nativa (GraalVM/AOT), Buildpacks.
- Backup e operação do banco de produção.
- Mudar o comportamento do `./mvnw spring-boot:run` e do modo rápido.

## Assumptions to Validate

- Existe imagem base com JRE 25 adequada (mínima, com variante multi-arquitetura se a VPS não for
  amd64) que possa ser fixada por digest.
- A arquitetura de CPU da VPS é conhecida e compatível com a imagem construída localmente
  [NEEDS CLARIFICATION: amd64 ou arm64?].
- Com Actuator, o indicador de Redis derruba a saúde se o Redis estiver ausente, e dá para
  desligar só ele (research.md, ASSUMPTION).
- A aplicação sobe no profile `prod` sem Redis acessível (não verificado).
- A conta do Docker Hub permite o repositório privado necessário, e a VPS consegue autenticar
  para fazer o pull.
- O teste que constrói a imagem cabe no tempo aceitável de `./mvnw test`.
- O dono aceita produção sem scan durante o intervalo entre as fatias (ver Recommendation).
