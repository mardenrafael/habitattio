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

Respostas à segunda rodada de perguntas (2026-10-30):

1. **Critérios obrigatórios de boas práticas:**
   - build multi-stage, com só o JRE 25 na imagem final;
   - usuário não-root;
   - jar extraído em camadas, mais `.dockerignore`;
   - imagem base fixada por digest;
   - scan de vulnerabilidades da imagem.
2. **Entrega na VPS:** build local, push manual para um registry e pull na VPS. Sem CI neste
   escopo.
3. **Testes no build da imagem:** o `docker build` não roda testes; a suíte completa roda antes,
   no host.
4. **Uso em desenvolvimento:** a mesma imagem serve aos dois modos. Sem profile, sobe no modo
   rápido (H2). Produção é ativada pelo profile `prod` via variável de ambiente.
5. **Redis:** o healthcheck ignora Redis (só o indicador de saúde do Redis é desligado); o
   starter permanece no projeto.
6. **Actuator:** expõe só `/actuator/health`, sem detalhes por componente.
7. **Segredos:** por variáveis de ambiente (ex.: `.env` do compose, fora do git), o mesmo modelo
   que o profile `prod` já usa.
8. **Aceite em TDD:** teste automatizado que constrói a imagem a partir do Dockerfile e verifica
   a subida e o healthcheck (Testcontainers).

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
- CI/CD: o push para o registry é manual (decisão 2).
- Testes dentro do `docker build` (decisão 3).
- Endpoints do Actuator além de `health` (decisão 6).
- Docker secrets em arquivo (decisão 7).

## Success Metrics

- Primeira instalação de produção na VPS concluída usando a imagem, com a aplicação respondendo
  na porta configurada. (baseline: nenhuma instalação de produção existe)
- Passos manuais de preparação do host para rodar a aplicação: só o runtime de containers, sem
  JDK nem Maven no host. (baseline: JDK 25 + build com `./mvnw` + variáveis exportadas à mão)
- O healthcheck reporta "saudável" com o PostgreSQL acessível e "não saudável" quando ele não
  está. (baseline: não há endpoint de saúde)
- O container não roda como root. (baseline: não se aplica, não há container)
- Quando só o código mudou, reconstruir a imagem não baixa de novo as dependências Maven e reusa
  a camada de dependências. (baseline: não se aplica)
- O scan de vulnerabilidades da imagem final não acusa achados críticos sem triagem.
  (baseline: não se aplica; o limiar exato fica para o plano)
- O healthcheck continua "saudável" sem Redis acessível. (baseline: não se aplica)
- Desenvolvimento: sem profile, a imagem sobe no modo rápido (H2) sem JDK 25 no host.
  (baseline: exige JDK 25)
- Um teste automatizado constrói a imagem do Dockerfile e prova a subida e o healthcheck.
  (baseline: não existe)

## Cost of Inaction

A primeira instalação de produção teria de ser feita preparando a VPS à mão: instalar JDK 25,
copiar ou construir o jar, configurar as variáveis `SPRING_DATASOURCE_*` e garantir que o
processo reinicie sozinho. Cada reinstalação ou servidor novo repetiria esses passos, com risco
de divergência entre ambientes e sem verificação automática de saúde. Fora do servidor, o custo
é baixo enquanto houver um único desenvolvedor com JDK 25 instalado (research.md, "Market &
Context").

## Open Questions

As oito perguntas da etapa anterior foram respondidas pelo dono (ver "Decisões já
registradas"). Restam detalhes menores, que podem ser resolvidos no shape ou no plano:

- [NEEDS CLARIFICATION: Qual registry recebe o push manual (GHCR, Docker Hub, outro) e se a
  imagem é privada?]
- [NEEDS CLARIFICATION: Ferramenta e limiar do scan de vulnerabilidades (ex.: falhar com achado
  crítico, ou só reportar)?]
