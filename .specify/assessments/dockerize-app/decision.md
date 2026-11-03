# Decision: Containerizar a aplicação com Dockerfile

- **Slug**: dockerize-app
- **Decided**: 2026-10-30
- **Verdict**: go
- **Artifacts reviewed**: intake.md | research.md | problem.md | concept.md

## Scorecard

| Criterion | Rating | Justification |
|-----------|--------|---------------|
| Problem validity | adequate | Necessidade concreta e próxima: a primeira instalação de produção numa VPS. Não há outra forma empacotada e repetível de levar a app até lá (problem.md). Ainda não é dor observada em operação, porque produção não existe. |
| Evidence strength | adequate | A evidência interna é alta e citada: profile `prod` já lê env vars, Docker já é pré-requisito, Redis sem uso, sem Actuator (research.md). As decisões do dono fecham as lacunas de destino, escopo, entrega e segredos. As práticas externas continuam como ASSUMPTION, sem fonte verificada. |
| Value vs. inaction | adequate | Sem a imagem, a primeira instalação e cada reinstalação exigem preparar o host à mão, sem verificação de saúde (problem.md, Cost of Inaction). Em desenvolvimento o ganho é menor, porque há um único desenvolvedor com JDK 25. |
| Feasibility / appetite | strong | Appetite small (dias). O stack já tem o necessário; a única dependência nova (Actuator) tem acordo explícito do dono. |
| Strategic fit | adequate | Reabre um ponto do YAGNI (constitution §IV), mas o dono justificou com a necessidade imediata de produção, não com os microsserviços futuros. Não toca `domain/` nem `application/`. TDD é atendido pelo teste automatizado da imagem. |
| Risk posture | adequate | Os riscos estão identificados (concept.md, rabbit holes comuns). O dono escolheu tratá-los à medida que aparecerem, em vez de mitigá-los antes; isso é aceitável pelo appetite pequeno, mas deixa riscos sem mitigação prévia. O adiamento do scan foi aceito explicitamente. |

## Verdict & Rationale

**Go**, com a Option B do concept.md. O problema é real e imediato (instalação de produção), a
evidência interna é suficiente e o dono fechou todas as decisões de escopo. A opção cabe num
appetite pequeno, e o critério obrigatório que ficou pendente (o scan de vulnerabilidades) foi
adiado com aceite explícito do dono: "aceito que a imagem agora exista sem scan". Nenhum
critério ficou `weak` ou `unknown`.

Duas ressalvas ficam registradas:

- Risk posture é `adequate`, e não `strong`, porque o dono preferiu avaliar correções à medida
  que os riscos aparecerem. A spec e o plan devem pelo menos tornar esses riscos visíveis, por
  exemplo nos critérios de aceite.
- O scan não foi cancelado, só adiado para uma fatia própria. Ela deve ser aberta quando o
  estudo de ferramentas terminar.

## If needs-clarification

- n/a (verdict: go)

## If go — Handoff to `/speckit-specify`

- **Problem**: A primeira instalação de produção do habitattio numa VPS, e cada novo ambiente de
  execução, exige preparar o host à mão (JDK 25, build, variáveis do profile `prod`). Isso não é
  repetível, pode divergir do ambiente testado e não verifica a saúde da aplicação.
- **Chosen approach**: Option B. Primeira fatia: Dockerfile escrito à mão, com healthcheck via
  Actuator e teste automatizado. Segunda fatia, futura: scan de vulnerabilidades, depois do
  estudo de ferramentas do dono.
- **In scope**:
  - Dockerfile multi-stage, com só o JRE 25 na imagem final.
  - Usuário não-root.
  - Jar extraído em camadas, mais `.dockerignore`.
  - Imagem base fixada por digest.
  - A mesma imagem nos dois modos: sem profile = modo rápido H2 (desenvolvimento);
    `SPRING_PROFILES_ACTIVE=prod` = PostgreSQL (produção).
  - Configuração e segredos por variáveis de ambiente.
  - Actuator (dependência nova, com acordo do dono) expondo só `/actuator/health`, sem detalhes
    por componente, ignorando o Redis e mantendo o starter no projeto.
  - Healthcheck do container.
  - O `docker build` não roda testes; a suíte completa roda antes, no host.
  - Teste automatizado (Testcontainers) que constrói a imagem a partir do Dockerfile e prova a
    subida e o healthcheck.
  - Entrega por push manual para um repositório **privado no Docker Hub**, com pull na VPS.
- **Out of scope**:
  - Scan de vulnerabilidades (segunda fatia).
  - `docker-compose` e definição do PostgreSQL ou de outros serviços.
  - Provisionamento da VPS (SO, Docker, firewall, TLS, domínio, proxy).
  - CI/CD.
  - Testes dentro do `docker build`.
  - Endpoints do Actuator além de `health`.
  - Docker secrets em arquivo.
  - Microsserviços, imagem nativa e Buildpacks.
  - Backup e operação do banco.
  - Mudança no `./mvnw spring-boot:run` e no modo rápido.
- **Success metrics** (problem.md):
  - Primeira instalação na VPS concluída usando a imagem.
  - O host precisa só do runtime de containers.
  - Healthcheck "saudável" com o PostgreSQL acessível e "não saudável" sem ele, mesmo sem Redis.
  - Processo não-root.
  - Rebuild só de código reusa a camada de dependências.
  - A imagem sobe em H2 sem profile, sem JDK no host.
  - O teste automatizado constrói a imagem e prova a subida e o healthcheck.
- **Carried-forward open questions**:
  - ~~Arquitetura de CPU da VPS~~ Resolvido em 2026-10-30: **amd64** (`linux/amd64`). A máquina
    de build local também é x86_64, então não há cross-build.
  - ~~Proteção contra produção subir em H2 se `SPRING_PROFILES_ACTIVE=prod` for esquecido~~
    Resolvido em 2026-10-30: **risco aceito**. Nesta fatia não haverá detecção nem proteção; o
    dono reavalia se o problema aparecer. A spec não deve exigir esse mecanismo.
  - **Garantir que a VPS roda a imagem publicada** (pergunta do dono, 2026-10-30): a spec deve
    tratar isso como requisito observável:
    - a imagem é identificada por uma tag de versão imutável, não `latest`;
    - a VPS faz pull autenticado do repositório privado;
    - o digest da imagem em execução na VPS confere com o digest gerado no push.

    O mecanismo concreto fica para o plan.
  - Riscos a observar durante o plan, sem mitigação prévia por decisão do dono:
    - disponibilidade de imagem base JRE 25 mínima e rotina de atualização do digest;
    - comando de healthcheck em imagem sem `curl`/`wget`;
    - tempo do teste que constrói a imagem dentro do `./mvnw test`;
    - confirmação de que só o indicador de Redis é desligado e o do banco continua valendo;
    - limites do plano do Docker Hub para repositório privado e credenciais de pull na VPS.
  - Segunda fatia (scan): ferramenta e limiar, depois do estudo do dono. Confirmado em
    2026-10-30: o dono fará o estudo e decidirá a ferramenta.
