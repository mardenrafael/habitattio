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

## First-Glance Unknowns

- [NEEDS CLARIFICATION: "we need to merges to main" — significa só "PRs que serão mergeados em `main`", ou também rodar a pipeline em pushes/merges diretos para `main`? O texto final diz "just a validation for PR with main as target".]
- [NEEDS CLARIFICATION: "no docker build" exclui só um passo explícito de `docker build`, ou também o `ImagemContainerTest`, que constrói a imagem dentro de `./mvnw test`? E os demais testes de integração com Testcontainers (exigem Docker no runner) devem rodar no CI?]
- [NEEDS CLARIFICATION: "run build to check" — qual comando conta como build: `./mvnw verify`, `./mvnw package`, ou só compilação? Test e build são passos separados ou um só?]
- [NEEDS CLARIFICATION: plataforma de CI — GitHub Actions é presumível pelo remoto no GitHub, mas não foi dito.]
- [NEEDS CLARIFICATION: o check deve ser obrigatório para merge (branch protection / required status check) ou apenas informativo?]
- [NEEDS CLARIFICATION: há expectativa de tempo máximo de execução, cache de dependências Maven ou publicação de relatórios de teste?]
- [NEEDS CLARIFICATION: o que fica explicitamente para pipelines futuras (ex.: build/publicação de imagem, deploy)?]
