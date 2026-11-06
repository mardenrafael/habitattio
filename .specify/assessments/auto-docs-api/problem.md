# Problem Definition: Documentação da API que não acompanha o código

- **Slug**: auto-docs-api
- **Created**: 2026-11-02
- **Inputs used**: intake.md, research.md

## Problem Statement

A descrição da API que os API clients importam é escrita e conferida à mão, separada do código.
Nada avisa quando uma rota, um campo ou uma regra de autenticação muda, então a descrição
pode divergir em silêncio da API real. Esse risco cresce a cada agregado novo exposto
(`Inquilino`, `Contrato`, `Fatura`) e não há como verificá-lo sem uma pessoa conferir tudo de
novo contra a aplicação rodando.

## Affected Users & Stakeholders

- **Users**: o dono/mantenedor do projeto, que testa a API em API clients locais (Bruno,
  Insomnia, Postman, Swagger UI). Hoje ele importa a descrição manual e precisa reeditá-la e
  reconferi-la a cada mudança — [research: Users & Demand; Prior Art]
- **Users (futuros)**: um pipeline de CI que ainda não existe e deveria produzir a descrição
  sem passo manual — [intake: Clarifications]
- **Stakeholders**: o dono do projeto, que decide e já acordou exceções ao AGENTS.md §9
  (dependência nova; endpoint de documentação além de `/actuator/health`) só para esta
  feature — [intake: Clarifications]
- **Stakeholders**: outros consumidores da API (terceiros, geração de SDK) — [NEEDS CLARIFICATION:
  existem? research não encontrou nenhum]

## Goals

- A descrição da API reflete o que está implementado, sem depender de edição ou conferência
  manual a cada mudança de rota.
- Obter a descrição atualizada é um passo repetível, sem intervenção humana, utilizável por um
  CI futuro.
- O fluxo mais difícil de usar no API client (cadastro → obter CSRF → login → rotas protegidas →
  logout) continua documentado; as rotas de autenticação não podem sumir da descrição — [research:
  Data & Constraints; Evidence Against]
- O conteúdo explicativo de hoje (guia de CSRF/sessão, descrições em português, exemplos) é
  preservado na medida do possível — [intake: Clarifications]
- Uma interface navegável da documentação fica disponível com a aplicação rodando — [intake:
  Clarifications]
- A mudança não abre superfície de acesso além do que o dono decidir explicitamente — [research:
  Evidence Against]

## Non-Goals

- Criar ou configurar um pipeline de CI agora (só deixar pronto para um futuro) — [intake]
- Usar a descrição para gerar SDKs/clientes ou publicá-la para terceiros.
- Manter a coleção Bruno (`docs/api/habitattion-api/`) automaticamente — [NEEDS CLARIFICATION:
  confirmar que fica fora do escopo]
- Mudar o comportamento, as rotas ou as regras de autenticação da API.
- Documentar agregados que ainda não têm rota (`Inquilino`, `Contrato`, `Fatura`).

## Success Metrics

- Toda rota exposta pela aplicação aparece na descrição, incluindo `POST /sessao`,
  `DELETE /sessao` e `GET /actuator/health` (baseline: 7/7 hoje, mas conferido à mão).
- Uma mudança de rota/campo num controller aparece na descrição sem editar nenhum arquivo de
  documentação (baseline: 0%, toda mudança exige edição manual).
- Obter a descrição atualizada leva um comando, sem passo manual (baseline: edição + conferência
  manual contra a app rodando).
- Existe um teste automatizado que falha quando a descrição não corresponde ao que está
  implementado, como exige a regra de TDD (baseline: nenhum) — [NEEDS CLARIFICATION: forma do teste]
- Qualitativo: a descrição gerada continua suficiente para o fluxo de CSRF/sessão funcionar num API
  client sem consultar outro documento (baseline: atendido pelo arquivo manual).

## Cost of Inaction

Hoje o custo é baixo: são 7 rotas, um mantenedor e a descrição foi conferida em 2026-11-02. Ele
cresce com cada feature nova que expõe rotas (três agregados ainda sem controller): cada uma
exige editar e reconferir o YAML à mão, e um esquecimento faz o API client mostrar uma API que não
existe mais, sem aviso. Sem uma forma repetível de produzir a descrição, um CI futuro não tem como
garantir a atualização. Ainda assim, nada quebra para usuários finais se nada for feito.
[research: Market & Context; Evidence Against]

## Open Questions

- [NEEDS CLARIFICATION: Quem consome a descrição além dos API clients locais?]
- [NEEDS CLARIFICATION: Qual teste prova que a descrição está atualizada?]
- [NEEDS CLARIFICATION: A interface navegável fica disponível em `prod`, e com ou sem login?]
- [NEEDS CLARIFICATION: `contracts/api-http.md` continua sendo a fonte de verdade, ou a descrição derivada do código passa a ser a referência?]
- [NEEDS CLARIFICATION: A coleção Bruno continua manual, passa a derivar da descrição ou é removida?]
- [NEEDS CLARIFICATION: As rotas tratadas pela segurança (login/logout) e pelo Actuator podem ser descritas de forma confiável junto com as dos controllers? (research: UNVERIFIED)]
