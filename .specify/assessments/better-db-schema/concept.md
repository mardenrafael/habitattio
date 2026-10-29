# Concept: `imoveis` como tabela-modelo com estrutura versionada e regras no banco

- **Slug**: better-db-schema
- **Created**: 2026-10-29
- **Recommended option**: Option C — Estrutura versionada para o PostgreSQL, H2 continua automático

Referências: problem.md (objetivos G1–G4, métricas M1–M6, não-objetivos), research.md
(evidências e clarificações).

## Options

### Option A — Não fazer agora; decidir o padrão dentro de cada feature de persistência

- **Sketch**: nada muda hoje. `imoveis` continua como está, com a estrutura gerada pelo
  Hibernate. As decisões de estrutura (regras no banco, tipo de dinheiro, versionamento) são
  tomadas na primeira feature que persistir outro agregado (ex.: `Contrato`), via SDD, e
  `imoveis` é ajustada nessa ocasião.
- **Appetite**: nenhum agora (o custo é adiado).
- **Trade-offs**:
  - Ganha: zero trabalho imediato; alinhado ao YAGNI da constitution §IV; nenhum risco novo.
  - Sacrifica: G4 (o modelo não existe quando o próximo agregado chegar, e a feature seguinte
    carrega duas decisões em vez de uma). M1–M5 seguem nos valores de hoje (2/5, 0, 0%, não,
    não). O dono pediu explicitamente para acertar `imoveis` primeiro como modelo (research,
    clarificação 2), então esta opção contraria uma decisão já tomada.
- **Rabbit holes**: misturar "como modelar o banco" com "como persistir `Contrato`" numa só
  feature tende a inflar a spec dela.

### Option B — Menor coisa que funciona: regras no mapeamento, estrutura ainda automática

- **Sketch**: a estrutura continua sendo gerada pelo Hibernate a partir das classes, mas o
  mapeamento de `imoveis` passa a declarar as regras simples (não vazio, valor > 0, precisão do
  valor), de modo que o banco gerado já nasça com elas. Entram testes de persistência contra
  PostgreSQL real, com versão fixa, que tentam gravar dado inválido e veem a rejeição. Nada de
  ferramenta nova.
- **Appetite**: small (1–2 dias).
- **Trade-offs**:
  - Ganha: G1 e G3 com pouco esforço; M1 → 5/5, M2 → 4+, M5 → sim. Sem dependência nova.
  - Sacrifica: G2. Sem registro versionado (M3 fica em 0%) e sem detecção de divergência (M4
    fica em "não"), porque a geração automática continua sendo a dona da estrutura. Com
    `update`, uma regra nova no mapeamento **não** é aplicada a uma tabela que já existe: o
    Hibernate só acrescenta, não altera restrições. O problema reaparece quando houver dados
    reais. O modelo (G4) sairia incompleto: ensinaria a declarar regras, mas não a evoluir a
    estrutura.
- **Rabbit holes**: o suporte do Hibernate a restrições declaradas na classe varia por versão
  e por banco; pode não gerar exatamente o que se espera no H2 e no PostgreSQL.

### Option C — Estrutura versionada para o PostgreSQL, H2 continua automático

- **Sketch**: a estrutura de produção (PostgreSQL) passa a ser definida por scripts versionados
  no repositório, aplicados em ordem ao subir a aplicação. O primeiro script cria `imoveis` com
  todas as regras decididas: obrigatoriedade, endereço não vazio, valor > 0 e precisão
  explícita. Contra o PostgreSQL, a aplicação passa a apenas **conferir** se o mapeamento bate
  com o banco e se recusa a subir quando diverge, sem alterá-lo. O H2 segue usando a estrutura
  gerada automaticamente, só para uso rápido (research, clarificação 8). Testes de persistência
  rodam contra PostgreSQL de versão fixa e provam cada regra. O resultado fica documentado como
  o padrão a seguir para os próximos agregados.
- **Appetite**: small (alguns dias). Uma tabela, nenhum dado a migrar, volume irrelevante.
- **Trade-offs**:
  - Ganha: todos os objetivos. G1 (M1 → 5/5), G2 (M3 → 100%, M4 → sim), G3 (M2 → um teste
    por regra, M5 → sim) e G4 (modelo completo, incluindo como evoluir a estrutura). É a prática
    de mercado convergente nas fontes da research.
  - Sacrifica: uma dependência nova no `pom.xml` (ferramenta de migração), com acordo para
    avaliá-la já dado (research, clarificação 3). Passa a haver duas fontes de estrutura:
    script no PostgreSQL e geração automática no H2. Elas podem divergir sem ninguém perceber,
    e o H2 pode aceitar dado que o PostgreSQL rejeitaria. Isso é aceitável porque o H2 é só de
    uso rápido e a prova das regras roda no PostgreSQL. A config hoje tem o H2 como padrão, então
    é preciso separar claramente "rodar rápido com H2" de "rodar como produção com PostgreSQL".
- **Rabbit holes**:
  - **Escolha da ferramenta** (Flyway vs. Liquibase): pode virar debate. Para uma tabela e um
    banco só, qualquer uma serve; a decisão cabe ao plan, com prazo curto.
  - **Separação de ambientes**: transformar o padrão atual (H2 + `update`) em dois modos
    (rápido/produção) pode crescer se tentar cobrir execução local, testes e produção de uma vez.
    Limite: só o necessário para os testes de persistência e a subida contra PostgreSQL.
  - **Conferência estrita**: a verificação do mapeamento pode acusar diferenças de tipo
    (precisão numérica, tamanho de texto) que exigem acertar o mapeamento até bater. Isso é
    desejável, mas pode consumir tempo.
  - **Defeito de reidratação** (fora do escopo): o adapter devolve todo imóvel como ativo. Os
    testes de persistência desta opção devem verificar regras do banco sem depender da leitura
    de estado, ou vão esbarrar no defeito e puxar a correção para dentro.
  - **Numeração da unidade** (problem.md, questão aberta): transformar "apto 101" em campo
    próprio muda o domínio e dobra o escopo. Fica fora; a numeração continua dentro do texto do
    endereço.

## Recommendation

**Option C.** É a única que atende os quatro objetivos de problem.md e move todas as métricas
quantitativas (M1–M5) para o alvo. O custo é pequeno justamente agora: uma tabela, nenhum dado,
cerca de 14 imóveis. Fazer depois, com dados reais e cinco tabelas, custa mais (problem.md,
Cost of Inaction).

- A Option B entrega G1/G3 mais barato, mas deixa G2 de fora, e G2 é o que impede perda
  silenciosa de dados quando houver produção. Além disso, ensina um modelo incompleto (G4).
- A Option A contraria a decisão explícita do dono de acertar `imoveis` primeiro como modelo.

O risco principal da C, as duas fontes de estrutura (H2 automático, PostgreSQL por script), é
aceito conscientemente: a decisão de manter o H2 automático já foi tomada (research,
clarificação 8), e a prova das regras roda no PostgreSQL.

## Out of Scope (for the recommended option)

- Persistir `Inquilino`, `Contrato`, `Fatura` ou `Usuario`; eles apenas seguirão o modelo depois.
- Criptografia do CPF (regra registrada para quando `Inquilino` for persistido).
- Corrigir o defeito de reidratação (correção à parte via `/speckit-bug-assess`).
- Fazer o H2 seguir os scripts versionados.
- Unicidade de endereço (revogada) e campo próprio para numeração da unidade.
- Levar ao banco regras que dependem de outros objetos ou de estado externo.
- Casos de uso novos de imóvel (listar, buscar, inativar via API).
- Otimização de desempenho; troca do tipo de chave primária por motivo de performance.
- Implantação/infra de produção (provisionar o PostgreSQL, pipeline de deploy).

## Assumptions to Validate

- O acordo da clarificação 3 se estende a **adicionar** a dependência da ferramenta de migração
  e a usar o modo "só conferir" contra o PostgreSQL. A constitution §IV exige acordo explícito
  para cada uma; confirmar no `/speckit-assess-decide` ou na spec.
- Não existe nenhum PostgreSQL com dados reais de `imoveis` hoje. Se existir, o primeiro script
  precisa partir da estrutura atual em vez de criá-la do zero.
- A numeração da unidade pode continuar dentro do texto do endereço sem prejudicar o cliente.
- Fixar a versão da imagem do Redis junto com a do PostgreSQL é aceitável (as duas estão na
  mesma configuração de testes; a clarificação 3 citou só o PostgreSQL).
- Testes de persistência com Testcontainers exigem Docker disponível onde a suíte roda (AGENTS.md
  §1). Assume-se que o ambiente do dono tem Docker.
- A divergência entre H2 (automático) e PostgreSQL (script) não vai mascarar bugs relevantes,
  porque as regras simples continuam validadas no domínio e provadas no PostgreSQL.
