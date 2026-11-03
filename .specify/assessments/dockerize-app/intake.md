# Idea Intake: Containerizar a aplicação com Dockerfile

- **Slug**: dockerize-app
- **Created**: 2026-10-30
- **Source**: pasted text (invocação de `/speckit-assess-intake`)
- **Type**: improvement

## Idea (as captured)

> "esse projeto depende de uma instalação baremetal, com o que é planejado para o futuro, uma
> aplicação em modelo de microservicço, standalone dessa forma complica, então é necessarop a
> criação de um dockerfile para esse projeto com as melhores praticas do mercado"

## Restated

Hoje a aplicação só roda instalada direto no host (bare metal). A proposta é criar um
Dockerfile seguindo as boas práticas do mercado, para que a aplicação rode em container. A
motivação é a migração planejada para uma arquitetura de microsserviços.

## Origin & Context

- **Raised by**: dono do projeto (Marden Rafael), via sessão de assessment.
- **Trigger**: a evolução planejada para microsserviços. O ideador considera o modelo atual
  (standalone, bare metal) um obstáculo para isso.
- **Estado atual observado no repositório** (fato, sem avaliação):
  - Não há `Dockerfile`, `.dockerignore` nem `docker-compose*` no repositório.
  - Build com Maven Wrapper (`./mvnw`), Java 25, Spring Boot 4.1.2, artefato
    `habitattio-0.0.1-SNAPSHOT`.
  - `application.properties` usa H2 em memória (modo PostgreSQL), `ddl-auto=update`, porta 8080.
  - PostgreSQL e Redis aparecem hoje só via Testcontainers (testes e
    `TestHabitattioApplication`).
  - A memória do projeto registra o Postgres como banco de produção pretendido e o H2 só como
    atalho rápido.

## First-Glance Unknowns

- [NEEDS CLARIFICATION: O escopo é só o Dockerfile da aplicação, ou inclui também um
  `docker-compose` com PostgreSQL e Redis para rodar o conjunto localmente?]
- [NEEDS CLARIFICATION: Quais "melhores práticas" contam como critério de aceite? Por exemplo
  multi-stage build, imagem base mínima/distroless, usuário não-root, layered jar, healthcheck,
  scan de vulnerabilidades, tamanho da imagem.]
- [NEEDS CLARIFICATION: Onde a imagem vai rodar (Docker local, Kubernetes, um serviço de nuvem
  gerenciado)? O ambiente de destino muda os requisitos.]
- [NEEDS CLARIFICATION: Com qual configuração o container sobe? H2 em memória (o padrão atual)
  ou PostgreSQL/Redis externos via variáveis de ambiente/profile? Hoje não há profile de
  produção.]
- [NEEDS CLARIFICATION: Existe pipeline de CI/registry de imagens planejado (build e push
  automáticos), ou isso fica fora do escopo?]
- [NEEDS CLARIFICATION: A divisão em microsserviços já está definida (quais serviços, quando),
  ou o Dockerfile é só um passo preparatório para o monólito atual?]
- [NEEDS CLARIFICATION: Como os segredos (credenciais do banco, chave de criptografia do CPF)
  entram no container?]
- [NEEDS CLARIFICATION: Isso conflita com o limite do AGENTS.md §9 ("não troque H2… sem
  acordo")? Rodar em container pode exigir mudar a configuração padrão.]
