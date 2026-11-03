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
  - Dono do projeto — único stakeholder humano (não há outros colaboradores); decide o escopo e
    quem pode mergear.

## Goals

- Todo PR com alvo `main` tem um resultado objetivo e visível de "testes passam" e "o projeto
  empacota" calculado sobre o estado exato do PR.
- A suíte completa roda nessa validação, incluindo os testes de integração que dependem de Docker
  (Testcontainers: PostgreSQL, Redis, e a construção de imagem do `ImagemContainerTest`) — não uma
  versão reduzida.
- O resultado da validação é reproduzível: o mesmo PR, validado de novo, dá o mesmo veredito.
- O veredito é **único** (testes + empacotamento) e **bloqueia o merge** quando falha.
- Em caso de falha, o resultado traz os detalhes (relatórios de teste), não só passou/falhou.

## Non-Goals

- Construir, publicar ou versionar a imagem da aplicação para o ambiente de produção.
- Deploy, release ou qualquer etapa pós-merge.
- Validar pushes diretos em `main` — já bloqueados por proteção de branch no GitHub; impor essa
  proteção não faz parte deste problema.
- Limite de tempo para o veredito (sem meta por ora).
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
- Tempo até o veredito de um PR — apenas observado, sem meta por ora (baseline: desconhecido).
- PRs com falha cujo resultado traz os relatórios de teste — meta 100% (baseline: 0%).

## Cost of Inaction

`main` continua dependendo de verificação manual e autodeclarada. Um PR pode entrar com testes
quebrados — especialmente os de integração, que só rodam onde há Docker — e o problema só aparece
depois, no próximo desenvolvimento ou na subida em produção. Com agentes de código abrindo PRs, a
garantia de "suíte verde" do Definition of Done fica sem conferência independente, e o custo de
descobrir regressões tarde cresce com o número de features.

## Clarifications (2026-11-02, dono do projeto)

- Push direto em `main`: **já imposto** por proteção de branch no GitHub.
- A validação **bloqueia** o merge (verificação obrigatória).
- Testes e empacotamento: **um só** veredito.
- Limite de tempo até o veredito: **nenhum, por ora**.
- Detalhes de falha (relatórios de teste): **sim**, o resultado deve trazê-los.
- Outros colaboradores além do dono: **não**.

## Open Questions

- [NEEDS CLARIFICATION: confirmar que análise estática, cobertura mínima e scan de segurança
  ficam fora desta primeira validação (ver Non-Goals).]
