# Decision: `imoveis` como tabela-modelo com estrutura versionada e regras no banco

- **Slug**: better-db-schema
- **Decided**: 2026-10-29
- **Verdict**: go
- **Artifacts reviewed**: intake.md | research.md | problem.md | concept.md

## Scorecard

| Criterion | Rating | Justification |
|-----------|--------|---------------|
| Problem validity | adequate | Problema real e verificável no código: só 2 de 5 regras impostas pelo banco, estrutura alterada silenciosamente por `ddl-auto=update`, zero testes de persistência, imagem `postgres:latest` (research › Prior Art › Interno). Não é `strong` porque não há dor sentida: o projeto não está em produção e não há incidente (research › Users & Demand). |
| Evidence strength | adequate | A evidência interna é de alta confiança (código citado com linha). A externa converge em várias fontes (migração versionada, testar em PostgreSQL real, `numeric` para dinheiro), mas veio só de snippets de busca, sem leitura das páginas nem da doc atual do Spring Boot 4 (research › Sources, Gaps). As clarificações do dono substituíram as suposições centrais. |
| Value vs. inaction | adequate | O custo de não fazer é quase zero hoje e cresce ao persistir mais quatro agregados ou ao ter dados reais (problem › Cost of Inaction). O valor está em fixar o modelo (G4) no momento mais barato: uma tabela e nenhum dado. Ganho preventivo, não corretivo. |
| Feasibility / appetite | strong | A opção C tem appetite `small` (alguns dias): uma tabela, ~14 registros, stack já tem PostgreSQL + Testcontainers (concept › Option C; research, clarificação 10). |
| Strategic fit | strong | Atende a constitution: TDD com testes de integração em PostgreSQL (AGENTS.md §3), regras de negócio protegidas (§7), dono pediu explicitamente `imoveis` como modelo (research, clarificação 2). A única tensão, YAGNI (§IV), é mitigada pelo escopo limitado a uma tabela. |
| Risk posture | adequate | Riscos identificados com mitigação: duas fontes de estrutura (H2 automático vs. scripts no PostgreSQL), aceito por decisão do dono e mitigado com provas no PostgreSQL; esbarrar no defeito de reidratação, mitigado mantendo os testes independentes da leitura de estado; escolha da ferramenta, com prazo curto no plan. Fica pendente a confirmação formal da dependência nova (constitution §IV), carregada para a spec. |

## Verdict & Rationale

**Go.** O problema é real e verificável no código, a evidência é adequada (interna forte,
externa convergente), e existe uma opção recomendada (C) com appetite pequeno que leva todas as
métricas quantitativas (M1–M5) ao alvo. Os critérios de `go` são atendidos: validade do problema
`adequate`+, evidência `adequate` e conceito recomendado. O valor é preventivo, não corretivo,
por isso validade e valor ficam em `adequate`, e não em `strong`. O motivo de fazer agora é o
custo mínimo (uma tabela, sem dados) e a decisão explícita do dono de usar `imoveis` como modelo
antes dos próximos agregados. Nenhum critério ficou `unknown` ou `weak`.

## If needs-clarification

Não se aplica.

## If go — Handoff to `/speckit-specify`

- **Problem**: os dados de imóveis no PostgreSQL de produção não têm garantia de respeitar as
  regras de negócio, e a estrutura do banco não é reproduzível nem auditável. Como `imoveis`
  será o modelo para as próximas tabelas, um padrão frágil seria copiado quatro vezes.
- **Chosen approach**: Option C. A estrutura de produção (PostgreSQL) passa a ser definida por
  scripts versionados no repositório. A aplicação, contra o PostgreSQL, só confere a estrutura e
  se recusa a subir quando diverge. O H2 continua com estrutura gerada automaticamente, só para
  uso rápido. Testes de persistência contra PostgreSQL de versão fixa provam cada regra. O
  resultado é documentado como padrão para os próximos agregados.
- **In scope**:
  - tabela `imoveis` com: endereço obrigatório e não vazio; valor sugerido obrigatório e > 0;
    precisão/escala explícitas para o valor;
  - registro versionado da estrutura do PostgreSQL;
  - conferência da estrutura na subida contra PostgreSQL;
  - testes de persistência contra PostgreSQL com versão fixa;
  - documentação do padrão (nomes, tipo de dinheiro, chave, onde cada regra mora).
- **Out of scope**:
  - persistir `Inquilino`, `Contrato`, `Fatura`, `Usuario`;
  - criptografia do CPF (regra futura, quando `Inquilino` for persistido);
  - defeito de reidratação (correção à parte via `/speckit-bug-assess`);
  - H2 seguir os scripts;
  - unicidade de endereço (revogada) e campo próprio de numeração da unidade;
  - regras que dependem de outros objetos;
  - novos casos de uso de imóvel;
  - desempenho;
  - infraestrutura de produção.
- **Success metrics**:
  - M1: regras de `imoveis` impostas pelo PostgreSQL, de 2/5 para 5/5;
  - M2: um teste de rejeição no PostgreSQL por regra, de 0 para ≥ 4;
  - M3: mudanças de estrutura versionadas, de 0% para 100%;
  - M4: divergência detectada na subida, de não para sim;
  - M5: versão do PostgreSQL de testes fixa, de não para sim;
  - M6 (qualitativa): o próximo agregado não redecide o padrão.
- **Carried-forward open questions**:
  - [NEEDS CLARIFICATION: confirmar formalmente a adição da dependência da ferramenta de migração e a troca para o modo "só conferir" contra PostgreSQL. Constitution §IV exige acordo explícito para cada uma; a clarificação 3 da research deu acordo para avaliar.]
  - [NEEDS CLARIFICATION: Flyway ou Liquibase. Decisão do plan, não da spec.]
  - [NEEDS CLARIFICATION: fixar a versão do Redis junto com a do PostgreSQL? A clarificação 3 cobriu só o PostgreSQL.]
  - Suposição: não existe PostgreSQL com dados reais de `imoveis`; o primeiro script cria a tabela do zero.
  - Suposição: a numeração da unidade continua dentro do texto do endereço.
  - Suposição: Docker disponível onde a suíte roda (testes com Testcontainers).
  - Lembrete: o defeito de reidratação deve ser aberto à parte (`/speckit-bug-assess`), e os
    testes desta feature não devem depender da leitura do estado `ativo`.
