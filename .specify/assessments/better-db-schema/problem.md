# Problem Definition: Esquema de `imoveis` sem garantias nem rastreabilidade

- **Slug**: better-db-schema
- **Created**: 2026-10-29
- **Inputs used**: intake.md, research.md (incluindo as duas rodadas de clarificações do usuário)

## Problem Statement

Hoje o desenvolvedor do habitattio não tem como confiar que os dados de imóveis gravados no
PostgreSQL de produção respeitam as regras de negócio, nem como reproduzir ou auditar a
estrutura do banco: as invariantes vivem só no código, a estrutura muda sozinha a partir das
classes e nada foi verificado contra o banco real. Isso importa agora porque `imoveis` é a única
tabela existente e vai servir de **padrão** para as tabelas de inquilinos, contratos, faturas e
usuários; um padrão frágil seria copiado quatro vezes, e regra de cobrança errada vira dinheiro
errado.

## Affected Users & Stakeholders

- **Users**:
  - **Desenvolvedor do projeto (dono)**: é quem escreve e evolui a persistência. Hoje não tem
    garantia de que o banco rejeita dado inválido, nem histórico de como a estrutura mudou, nem
    teste que prove o comportamento contra PostgreSQL — [research: Prior Art › Interno].
  - **Agentes de código** que seguem o AGENTS.md: replicam o padrão existente ao persistir os
    próximos agregados — [research: Users & Demand; AGENTS.md §6].
- **Stakeholders**:
  - **Dono do projeto**: decide escopo, ferramentas e o que exige acordo (constitution §IV) —
    [research: Clarificações].
  - **Cliente único (operador do sistema)**: afetado indiretamente. Dado inconsistente de imóvel
    se propaga para contratos e cobranças — [research: Clarificações, item 10]. Hoje o cliente
    não tem dor relatada.

## Goals

- **G1 — Dado inválido de imóvel não persiste, mesmo se o domínio for contornado.** As
  invariantes simples de `Imovel` (critério: regra sobre um membro da classe que no código vira
  um `if`) e a regra decidida pelo dono de valor com precisão definida também são garantidas
  no armazenamento de produção — [research: itens 4, 7, 9]. Endereço **não** é único: vários
  imóveis podem compartilhar o mesmo endereço (ex.: apartamentos de um prédio) — [clarificação
  abaixo, item 1].
- **G2 — A estrutura do banco de produção é reproduzível e auditável.** Qualquer ambiente
  PostgreSQL chega à mesma estrutura, cada mudança fica registrada e versionada, e uma
  divergência entre o código e o banco é detectada em vez de corrigida silenciosamente —
  [research: item 3; Prior Art › Externo].
- **G3 — O comportamento da persistência de imóveis é provado contra o banco de produção**
  (PostgreSQL), não só contra o banco de uso rápido (H2) — [research: itens 1, 8].
- **G4 — O resultado serve de modelo explícito** para persistir os outros quatro agregados sem
  precisar redecidir as mesmas questões (nomes, tipos de dinheiro, chaves, onde mora cada
  regra) — [research: item 2].

## Non-Goals

- Persistir `Inquilino`, `Contrato`, `Fatura` ou `Usuario` (fica para features futuras que
  seguirão o modelo) — [research: item 2].
- Criptografia do CPF: é regra registrada para quando `Inquilino` for persistido, fora deste
  escopo — [research: item 6].
- Corrigir a perda de estado ao reconstruir entidades lidas do banco (imóvel inativo voltando
  como ativo etc.): vai como correção à parte — [research: item 5].
- Fazer o H2 reproduzir a estrutura de produção: o H2 continua usando a estrutura gerada
  automaticamente — [research: item 8].
- Otimizar desempenho: o volume é de cerca de 14 imóveis e 14 faturas — [research: item 10].
- Levar regras complexas (que dependem de outros objetos ou de estado externo) para o banco —
  [research: item 4].
- Impedir endereços repetidos: o mesmo endereço pode servir a vários imóveis.
- Adicionar casos de uso novos de imóvel (listar, buscar, inativar via API).

## Success Metrics

- **M1 — Invariantes de `imoveis` garantidas pelo banco de produção**: das 4 regras decididas
  (endereço não nulo, endereço não vazio, valor não nulo, valor > 0) mais a precisão explícita
  do valor, quantas o PostgreSQL impõe por conta própria.
  (baseline: 2 de 5 — só os dois "não nulo"; a precisão é a padrão implícita do Hibernate —
  [research: Prior Art › Interno])
- **M2 — Cada regra de M1 tem um teste que tenta gravar o dado inválido direto no PostgreSQL e
  vê a rejeição.** (baseline: 0 testes de persistência —
  [research: "Não existe nenhum teste de persistência"])
- **M3 — Mudanças de estrutura registradas e versionadas**: fração das alterações de estrutura
  de produção que têm registro versionado no repositório. (baseline: 0%; a estrutura nasce de
  `ddl-auto=update` — [research: Prior Art › Interno])
- **M4 — Divergência detectada**: ao subir contra um PostgreSQL cuja estrutura difere do
  mapeamento, a aplicação falha ao iniciar em vez de alterar o banco. (baseline: não; hoje o
  Hibernate altera o banco silenciosamente)
- **M5 — Reprodutibilidade dos testes de integração**: os testes de persistência rodam sempre
  contra a mesma versão do PostgreSQL. **Dentro do escopo** (clarificação, item 3).
  (baseline: não; a imagem é `postgres:latest` — [research: Prior Art › Interno])
- **M6 (qualitativa) — Reuso do modelo**: ao persistir o próximo agregado, nenhuma das decisões
  de G4 precisa ser rediscutida. Será medida na primeira feature de persistência seguinte.

## Cost of Inaction

Com H2 em memória e uma tabela, o custo **imediato** é quase zero; o projeto nem roda em
produção [research: Market & Context]. O custo aparece quando:

- os outros quatro agregados forem persistidos copiando o padrão atual (sem garantia no banco,
  precisão implícita, sem teste contra PostgreSQL), multiplicando por cinco o trabalho de
  corrigir depois;
- o PostgreSQL de produção passar a guardar dados reais com `ddl-auto=update`: renomear ou
  remover um campo pode criar colunas órfãs ou apagar dados, sem registro do que mudou
  [research: Prior Art › Externo];
- a primeira escrita que contorne o domínio (script, correção manual, bug em adapter) gravar
  um imóvel com valor zero ou endereço vazio, e esse dado alimentar contratos e cobranças.

Fazer agora é o mais barato possível: não há dados a migrar e há uma só tabela
[research: Market & Context].

## Clarificações do usuário (2026-10-29)

1. **Endereço não é único.** O mesmo endereço pode servir a vários imóveis, por exemplo
   apartamentos de um mesmo prédio, separados apenas pela numeração. A regra de unicidade
   decidida em research.md (item 9) foi **revogada**.
2. **Cadastro com endereço repetido é aceito normalmente**, como consequência do item 1. Não há
   rejeição nem mensagem de erro para esse caso.
3. **Fixar a versão do PostgreSQL usada nos testes entra no escopo.**

## Open Questions

- Nenhuma pergunta bloqueante restante para a etapa de shape.
- A considerar no shape (não bloqueante): o item 1 sugere que o imóvel é identificado por
  endereço + numeração da unidade, mas hoje `Imovel` só tem `endereco`, um texto livre. Se a
  unidade (ex.: "apto 101") deve virar um campo próprio, isso muda o modelo de domínio e o
  escopo; até haver decisão, assume-se que a numeração continua dentro do texto do endereço.
- A considerar no shape (não bloqueante): a imagem do Redis está na mesma configuração de testes
  que a do PostgreSQL. O item 3 cobre explicitamente só o PostgreSQL; fixar o Redis junto é
  ASSUMPTION a confirmar.
