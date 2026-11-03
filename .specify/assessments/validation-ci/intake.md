# Idea Intake: Primeira pipeline de CI — validação de PR para `main`

- **Slug**: validation-ci
- **Created**: 2026-11-02
- **Source**: pasted text
- **Type**: new-capability

## Idea (as captured)

> "lets build our first CI for this project, we need to merges to main, run test, run build to check (no docker build). this first pipeline is for just a validation for PR with main as target"

## Restated

Criar a primeira pipeline de integração contínua do projeto, disparada por pull requests cujo
alvo é `main`, que roda os testes e o build para validar a mudança antes do merge — sem
construir a imagem Docker.

## Origin & Context

- **Raised by**: dono do projeto, via `/speckit-assess-intake`.
- **Trigger**: [NEEDS CLARIFICATION: nenhum incidente citado; aparentemente o próximo passo natural após a containerização (`dockerize-app`)].
- **Estado atual observado no repositório** (fato, não avaliação): não há diretório `.github/`
  nem qualquer configuração de CI; o remoto é `github.com/mardenrafael/habitattio`; o projeto é
  Java 25 + Spring Boot 4.1.2 com `./mvnw`; parte da suíte (`./mvnw test`) usa Testcontainers
  (PostgreSQL, Redis) e o `ImagemContainerTest` constrói a imagem a partir do `Dockerfile`
  dentro do `./mvnw test` (AGENTS.md §1).

## Clarifications (2026-11-02, dono do projeto)

Respostas do dono às primeiras dúvidas, registradas como esclarecimento (não avaliação):

- **Docker**: o runner **deve ter Docker disponível**. "No docker build" significa não
  construir/publicar a imagem da aplicação para o ambiente de produção nesta fase de validação —
  não significa excluir testes que usam Docker. (Original: "provide docker, i mean, no build for
  applications image for production environment in validation phase".)
- **Gatilho**: push direto em `main` não é permitido; a pipeline é só para PRs com alvo `main`.
  (Original: "no direct push to main is allowed".)
- **Build**: o passo de build é `package`. (Original: "package".)
- **Plataforma**: GitHub Actions. (Original: "yes, github actions".)

## First-Glance Unknowns

- [NEEDS CLARIFICATION: "no direct push to main is allowed" — isso já é aplicado hoje por branch protection no GitHub, ou faz parte desta ideia configurar a proteção?]
- [NEEDS CLARIFICATION: test e `package` são passos separados ou um só (`package` já roda os testes)?]
- [NEEDS CLARIFICATION: o check deve ser obrigatório para merge (required status check) ou apenas informativo?]
- [NEEDS CLARIFICATION: há expectativa de tempo máximo de execução, cache de dependências Maven ou publicação de relatórios de teste?]
- [NEEDS CLARIFICATION: o que fica explicitamente para pipelines futuras (ex.: build/publicação de imagem, deploy)?]
