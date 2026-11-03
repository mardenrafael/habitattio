# Problem Definition: Validação automática de PRs com alvo `main`

- **Slug**: validation-ci
- **Created**: 2026-11-02
- **Inputs used**: intake.md (com esclarecimentos do dono); research.md ausente (etapa pulada)

## Problem Statement

Hoje, um pull request com alvo `main` pode ser mergeado sem nenhuma evidência independente de
que a suíte de testes passa e de que o projeto empacota: a verificação depende de alguém lembrar
de rodar `./mvnw test`/`package` localmente, com Docker disponível, e de relatar o resultado com
honestidade. Como `main` só recebe mudanças via PR, esse é o único ponto de controle — e ele não
tem verificação objetiva.

## Affected Users & Stakeholders

- **Users**:
  - Dono/mantenedor do projeto — revisa e mergeia PRs sem prova reproduzível de que a suíte está
    verde no estado exato do PR.
  - Agentes de código que abrem PRs — o AGENTS.md (§1, §8) exige rodar `./mvnw test` e declarar o
    que não rodou (ex.: integração sem Docker); hoje essa declaração não é conferida por terceiro.
- **Stakeholders**:
  - Dono do projeto — decide o escopo, quem pode mergear e se a validação bloqueia o merge.
  - [NEEDS CLARIFICATION: há outros colaboradores humanos ou revisores além do dono?]

## Goals

- Todo PR com alvo `main` tem um resultado objetivo e visível de "testes passam" e "o projeto
  empacota" calculado sobre o estado exato do PR.
- A suíte completa roda nessa validação, incluindo os testes de integração que dependem de Docker
  (Testcontainers: PostgreSQL, Redis, e a construção de imagem do `ImagemContainerTest`) — não uma
  versão reduzida.
- O resultado da validação é reproduzível: o mesmo PR, validado de novo, dá o mesmo veredito.

## Non-Goals

- Construir, publicar ou versionar a imagem da aplicação para o ambiente de produção.
- Deploy, release ou qualquer etapa pós-merge.
- Validar pushes diretos em `main` (não são permitidos pelo fluxo do projeto).
- Validar PRs com alvo diferente de `main`.
- Análise estática, cobertura mínima, scan de segurança ou outras verificações além de testes e
  empacotamento [NEEDS CLARIFICATION: confirmar que ficam para depois].

## Success Metrics

- Percentual de PRs com alvo `main` que têm resultado de validação antes do merge — meta 100%
  (baseline: 0%; não há CI no repositório, e 7 PRs já foram mergeados em `main` sem ela).
- PRs mergeados com a validação falhando — meta 0 (baseline: não mensurável hoje).
- Testes de integração efetivamente executados na validação (não pulados por falta de Docker) —
  meta: todos os da suíte (baseline: depende da máquina de quem rodou; 34 classes de teste no
  repositório hoje).
- Tempo até o veredito de um PR — [NEEDS CLARIFICATION: há limite aceitável?] (baseline:
  desconhecido).

## Cost of Inaction

`main` continua dependendo de verificação manual e autodeclarada. Um PR pode entrar com testes
quebrados — especialmente os de integração, que só rodam onde há Docker — e o problema só aparece
depois, no próximo desenvolvimento ou na subida em produção. Com agentes de código abrindo PRs, a
garantia de "suíte verde" do Definition of Done fica sem conferência independente, e o custo de
descobrir regressões tarde cresce com o número de features.

## Open Questions

- [NEEDS CLARIFICATION: "push direto em `main` não é permitido" já é imposto por branch
  protection no GitHub, ou isso faz parte do problema a resolver?]
- [NEEDS CLARIFICATION: a validação deve **bloquear** o merge (verificação obrigatória) ou apenas
  informar?]
- [NEEDS CLARIFICATION: testes e empacotamento são um só veredito ou vereditos separados?]
- [NEEDS CLARIFICATION: há limite aceitável de tempo até o veredito?]
- [NEEDS CLARIFICATION: o resultado precisa trazer detalhes de falha (relatórios de teste) além
  de passou/falhou?]
- [NEEDS CLARIFICATION: há outros colaboradores além do dono?]
