# Problem Definition: Containerizar a aplicação com Dockerfile

- **Slug**: dockerize-app
- **Created**: 2026-10-30
- **Inputs used**: intake.md, research.md, user input (respostas do dono em 2026-10-30)

## Problem Statement

O dono do projeto precisa fazer a primeira instalação de produção do habitattio numa VPS, e
hoje não existe uma forma empacotada e repetível de levar a aplicação até lá. Rodar a aplicação
depende de preparar o host à mão (JDK 25, build com `./mvnw`, variáveis de ambiente do profile
`prod`), e cada instalação ou ambiente de desenvolvimento novo repete esse trabalho, com risco de
divergir do ambiente onde a aplicação foi testada.

## Decisões já registradas pelo dono (2026-10-30)

Respostas às perguntas da pesquisa. São restrições para as próximas etapas, não para o
enunciado do problema:

- **Por que agora, apesar do YAGNI:** o dono reconhece o princípio, mas a justificativa é
  concreta e imediata: a primeira instalação em produção e a facilidade no desenvolvimento. A
  arquitetura de microsserviços deixa de ser a motivação principal. (Isso responde a
  research.md, Evidence Against: "problema ainda não observado".)
- **Ambiente de destino:** uma VPS, orquestrada inicialmente com docker compose.
- **Forma de construir:** Dockerfile escrito à mão, para ter mais controle. Buildpacks
  (`spring-boot:build-image`) foram considerados e descartados.
- **Configuração em produção:** o container sobe com o profile `prod`, ligado a PostgreSQL.
- **Escopo:** apenas o Dockerfile. O arquivo de compose e a definição dos demais serviços ficam
  fora.
- **Healthcheck:** aprovado, com acordo explícito para adicionar o Actuator ao `pom.xml`
  (constitution §IV / AGENTS.md §9).

## Affected Users & Stakeholders

- **Users**:
  - Dono do projeto, como **operador** da VPS: hoje teria de preparar o host manualmente para a
    primeira instalação. [source: user input]
  - Dono do projeto e futuros colaboradores, como **desenvolvedores**: hoje precisam de JDK 25
    no host; querem um ambiente mais fácil de reproduzir. Não há outros desenvolvedores
    registrados. [source: user input; research.md "Users & Demand"]
- **Stakeholders**:
  - Dono do projeto: decide, financia (custo da VPS) e opera; é o único decisor. [source:
    intake.md]
  - Proprietários que usarão o sistema (multi-proprietário desde 2026-10-29): são afetados de
    forma indireta pela disponibilidade da instalação de produção. [source: memória
    `db-prod-postgres-constraints`]

## Goals

- Fazer a primeira instalação de produção na VPS sem preparar o host além do runtime de
  containers.
- Garantir que o que roda em produção é o mesmo artefato construído e verificado, sem
  configuração divergente feita à mão no servidor.
- Permitir que o orquestrador (docker compose) saiba se a aplicação está saudável, e não
  apenas se o processo está de pé.
- Reduzir o atrito de montar um ambiente de execução da aplicação para desenvolvimento.
- Seguir boas práticas reconhecidas de segurança e eficiência para imagens de container. Os
  critérios ainda vão ser escolhidos (ver Open Questions).

## Non-Goals

- Arquivo `docker-compose` e definição do PostgreSQL ou de outros serviços na VPS (decisão do
  dono: escopo é apenas o Dockerfile).
- Divisão do sistema em microsserviços ou qualquer mudança na arquitetura hexagonal do monólito.
- Provisionamento da VPS (sistema operacional, instalação do Docker, firewall, TLS, domínio,
  proxy reverso).
- Backup, restauração e operação do banco de produção.
- Mudança no modo rápido (H2) ou no comportamento atual do `./mvnw spring-boot:run`.
- Imagem nativa (GraalVM/AOT) e buildpacks.
- CI/CD e registry de imagens, a menos que o dono os inclua (ver Open Questions).

## Success Metrics

- Primeira instalação de produção na VPS concluída usando a imagem, com a aplicação respondendo
  na porta configurada. (baseline: nenhuma instalação de produção existe)
- Passos manuais de preparação do host para rodar a aplicação: só o runtime de containers, sem
  JDK nem Maven no host. (baseline: JDK 25 + build com `./mvnw` + variáveis exportadas à mão)
- O healthcheck reporta "saudável" com o PostgreSQL acessível e "não saudável" quando ele não
  está. (baseline: não há endpoint de saúde)
- O container não roda como root. (baseline: não se aplica, não há container)
- Quando só o código mudou, reconstruir a imagem não baixa de novo as dependências Maven.
  (baseline: não se aplica) — *a medir no plano, critério depende das respostas abaixo*
- Desenvolvimento: um ambiente novo consegue rodar a aplicação sem instalar JDK 25.
  (baseline: exige JDK 25) — *qualitativo; depende de como a imagem será usada em dev*

## Cost of Inaction

A primeira instalação de produção teria de ser feita preparando a VPS à mão: instalar JDK 25,
copiar ou construir o jar, configurar as variáveis `SPRING_DATASOURCE_*` e garantir que o
processo reinicie sozinho. Cada reinstalação ou servidor novo repetiria esses passos, com risco
de divergência entre ambientes e sem verificação automática de saúde. Fora do servidor, o custo
é baixo enquanto houver um único desenvolvedor com JDK 25 instalado (research.md, "Market &
Context").

## Open Questions

Perguntas da etapa anterior que continuam abertas, refeitas para resposta do dono:

1. [NEEDS CLARIFICATION: **Critérios de "melhores práticas"**: quais são aceite obrigatório?
   Candidatos:
   - build multi-stage (JDK no build, só JRE na execução);
   - usuário não-root;
   - imagem base mínima (JRE slim ou distroless);
   - jar extraído em camadas para cache;
   - imagem base fixada por digest;
   - `.dockerignore`;
   - flags de JVM para limites de memória do container;
   - scan de vulnerabilidades.]
2. [NEEDS CLARIFICATION: **Como a imagem chega à VPS**: build direto na VPS a partir do
   repositório, ou push para um registry (ex.: GHCR) com pull na VPS? CI fica dentro ou fora
   do escopo?]
3. [NEEDS CLARIFICATION: **Testes durante o build**: os testes de integração exigem Docker e não
   rodam dentro do `docker build`. O build da imagem pula todos os testes (que rodariam antes,
   no host ou em CI), ou roda só os de domínio e aplicação?]
4. [NEEDS CLARIFICATION: **"Facilitar o desenvolvimento"**: a mesma imagem deve rodar em dev no
   modo rápido (H2, sem profile), ou dev continua com `./mvnw` e a imagem é só para produção?]
5. [NEEDS CLARIFICATION: **Redis**: o starter está no `pom.xml` mas não é usado em `src/main`. Com
   Actuator, o health indicator de Redis provavelmente marca a aplicação como "não saudável" se
   não houver Redis acessível (ASSUMPTION, não verificado). O Redis vai existir na VPS, ou o
   healthcheck deve ignorá-lo (ou o starter sai do projeto)?]
6. [NEEDS CLARIFICATION: **Exposição do Actuator**: só `/actuator/health`, sem detalhes para
   chamadas externas? Ou outros endpoints (info, metrics)?]
7. [NEEDS CLARIFICATION: **Segredos**: credenciais do banco (e, no futuro, a chave de
   criptografia do CPF) entram por variáveis de ambiente (ex.: `.env` do compose) ou por
   arquivos de Docker secrets?]
8. [NEEDS CLARIFICATION: **TDD para infraestrutura**: o aceite inclui um teste automatizado que
   constrói a imagem a partir do Dockerfile e verifica subida e healthcheck (ex.: Testcontainers
   com imagem gerada do Dockerfile), ou basta uma verificação manual documentada?]
