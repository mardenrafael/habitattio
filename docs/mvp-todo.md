# TODO — conclusão do MVP

- **Atualizado**: 2026-11-02
- **Base**: `main` com as specs 001, 002 e 003 mergeadas (PR #130).
- **Fonte dos requisitos**: [`requisitos-e-der.md`](requisitos-e-der.md). Os IDs (`RF-xx`, `RNF-xx`,
  `Dx`, `Qx`) apontam para lá. Esta lista **ordena o trabalho**. Ela não redefine requisito: se um
  item daqui divergir do `requisitos-e-der.md`, vale o de lá.

## O que é o MVP

O proprietário se cadastra, faz login e, só com os próprios dados:

1. cadastra, edita, lista e inativa **imóveis**;
2. cadastra, edita e lista **inquilinos**;
3. cria e encerra **contratos** (imóvel + inquilino);
4. recebe **faturas emitidas automaticamente** todo mês, que viram `ATRASADA` sozinhas;
5. **marca a fatura como paga**;
6. vê o **painel mensal** com as faturas da competência.

**Fora do MVP** (⏸): pagamentos parciais e histórico (RF-29–RF-32, RNF-17), reajuste anual (Q11),
multa e juros (Q6).

## Como usar esta lista

- Cada **módulo** abaixo é **uma feature SDD**: `/speckit-specify` → `clarify` → `plan` → `tasks` →
  `analyze` → `implement` (AGENTS §4), em TDD estrito (AGENTS §3). Os itens dentro do módulo são o
  escopo a passar para o `/speckit-specify`, não tasks prontas.
- A ordem é de dependência: um módulo só começa com o anterior mergeado na `main`.
- Marque `[x]` quando o módulo estiver mergeado e o `requisitos-e-der.md` atualizado com o novo
  status (✅).
- As perguntas marcadas **❓** precisam de resposta do dono **antes do `/speckit-plan`** do módulo
  (AGENTS §7: regra de cobrança não se inventa).

---

## Fase 0 — Arrumação (direto, sem spec)

- [ ] Atualizar o cabeçalho do `requisitos-e-der.md`: módulos 001 e 003 já estão na `main` (D8
      resolvido; §8 itens 1 e 4).
- [ ] Mergear na `main` os commits da coleção da API (`docs/api/habitattion-api`, health check),
      hoje só na branch `worktree-spec-proprietario-isolamento`.
- [ ] Decidir a versão do `pom.xml` (`0.0.1-SNAPSHOT` → `0.0.1` está alterado e não commitado na
      cópia principal).

## Fase 1 — Base transversal

### Módulo 004 — Tratamento global de erros (D9)

- [ ] Resposta `500` padronizada em `ProblemDetail` para exceção não prevista, sem vazar detalhe
      interno (stack trace, SQL, nome de classe).
- [ ] O erro completo vai para o log do servidor.
- [ ] Os tratamentos de 400/401/403/404/409 que já existem continuam iguais (testes existentes
      intactos).

## Fase 2 — Cadastros

### Módulo 005 — Imóvel completo (RF-09, RF-10)

- [ ] Listar os imóveis do proprietário autenticado (`GET /imoveis`).
- [ ] Editar identificador, endereço e valor sugerido, mantendo a unicidade do identificador por
      dono, sem distinguir caixa.
- [ ] Imóvel de outro dono responde 404 também na edição (RNF-15).
- ❓ A listagem mostra os inativos? (sugestão: filtro `ativo`, padrão só ativos)
- ❓ Imóvel inativo pode ser editado? E reativado?
- ❓ Paginação já no MVP? (RNF-21: volume baixo; sugestão: não)

### Módulo 006 — Inquilino (RF-13–RF-17, RNF-13)

- [ ] `Inquilino` ganha `usuarioId` (dono). Hoje a classe não tem dono.
- [ ] Validar o dígito verificador do CPF no domínio (Q4).
- [ ] CPF único **por proprietário** (Q3 + Q14): `uq_inquilinos_usuario_cpf (usuario_id, cpf_hash)`.
- [ ] CPF guardado cifrado (`cpf_cifrado`, IV aleatório) + `cpf_hash` (HMAC com chave), com a
      chave vinda de variável de ambiente (RNF-14). Ver DER, decisão de modelagem 6.
- [ ] Cadastrar, editar e listar; consultar por id com isolamento (404 para inquilino alheio).
- [ ] Migração `V3` seguindo [`persistencia.md`](persistencia.md), com os três testes
      obrigatórios.
- ❓ Como o CPF aparece na resposta da API: completo ou mascarado (`***.456.789-**`)? (LGPD)
- ❓ Inquilino pode ser inativado? (nada é apagado de fato; hoje não há regra)
- ❓ O CPF pode ser editado depois do cadastro?
- ❓ Algoritmo e gestão da chave de cifragem: decisão do `plan.md`, mas a **troca de chave** com
      dados gravados precisa de acordo do dono.

### Módulo 007 — Contrato (RF-12, RF-18, RF-19, RF-21, D1)

- [ ] Corrigir D1 em TDD: `Contrato` valida `valorAluguel > 0` e `imovelId`, `inquilinoId` e
      `dataInicio` não nulos.
- [ ] Criar contrato com imóvel e inquilino **do mesmo proprietário** (regra no caso de uso).
- [ ] Imóvel inativo não recebe contrato (RF-12).
- [ ] No máximo um contrato ativo por imóvel (Q1): caso de uso + `uq_contratos_imovel_ativo`
      (índice único parcial `WHERE ativo`).
- [ ] Encerrar contrato guardando `dataEncerramento` (Q2).
- [ ] Listar e consultar contratos do proprietário (por imóvel e por inquilino), com isolamento.
- [ ] Migração `V4`.
- ❓ `dataInicio` no passado é permitida? Se sim, o sistema emite as faturas retroativas? (afeta o
      módulo 009)
- ❓ A data de encerramento pode ser futura ou retroativa? Um contrato com início futuro pode ser
      encerrado?
- ❓ Ao encerrar, o que acontece com faturas `PENDENTE` já emitidas de competências posteriores à
      data de encerramento?
- ❓ Inquilino com contrato ativo pode ser inativado (se o módulo 006 criar essa ação)?

### Módulo 008 — Anexo do contrato (RF-20) — ❓ entra no MVP?

- [ ] Porta `ArmazenamentoArquivoPort` com adapter de disco local em dev (Q18).
- [ ] Upload e download de PDF ou imagem, com tamanho máximo definido no plan.
- [ ] `contratos.anexo_chave` guarda só a chave; a URL é gerada na leitura.
- ❓ Onde fica o arquivo em produção (volume do container, objeto em nuvem)? Sem isso o anexo some
      a cada deploy.

## Fase 3 — Cobrança

### Módulo 009 — Faturas: emissão e atraso (RF-22–RF-26, RNF-16, D2)

- [ ] Corrigir D2 em TDD: `Fatura` valida no construtor; `mesReferencia` vira `YearMonth`.
- [ ] Regra de vencimento no domínio: dia do contrato na competência; em mês mais curto, o último
      dia do mês (RF-25).
- [ ] Caso de uso de emissão: uma fatura por contrato ativo e competência, pelo valor integral
      copiado do contrato (Q13), primeira fatura pela regra de Q26/Q27.
- [ ] Fatura criada junto com o contrato quando o primeiro vencimento cai no mês do início (Q27):
      liga o módulo 007 ao 009.
- [ ] Contrato encerrado não gera fatura (RF-22).
- [ ] Unicidade `uq_faturas_contrato_mes` no banco (RF-24) e emissão idempotente.
- [ ] Caso de uso de atraso: `PENDENTE` vira `ATRASADA` a partir do dia seguinte ao vencimento;
      `PAGA` não muda (RF-26, RF-28).
- [ ] Rotinas agendadas (dia 1 para emissão, diária para atraso) como **adapters de entrada** que
      só chamam os casos de uso (RNF-16). `@EnableScheduling` fica na configuração, nunca no
      domínio.
- [ ] Consultar e listar faturas do proprietário (por contrato, por competência), com isolamento.
- [ ] Migração `V5`.
- ❓ Fuso horário das rotinas e do "hoje" (sugestão: `America/Sao_Paulo`, via `Clock` injetado).
- ❓ Se a app ficar fora do ar no dia 1, a emissão roda depois para recuperar o mês perdido?
      (sugestão: sim, emissão idempotente roda todo dia e emite o que faltar)
- ❓ Com mais de uma instância da app, quem garante uma execução só? Liga a RNF-19 (Redis para
      locks) — ou o MVP roda com uma instância só e a unicidade no banco basta.

### Módulo 010 — Marcar fatura como paga (RF-34)

- [ ] Caso de uso + endpoint: o proprietário marca a fatura como paga, informando a data; valor
      sempre integral (Q28). `Fatura.pagar` e seus testes ficam como estão (D5).
- [ ] Fatura já paga responde erro de estado (409); fatura alheia responde 404.
- [ ] Marcar como paga funciona em `PENDENTE` e em `ATRASADA`.
- ❓ A data de pagamento pode ser futura? Pode ser anterior à emissão?
- ❓ Desfazer uma baixa feita por engano é necessário no MVP? (hoje `PAGA` é terminal — desfazer
      contraria AGENTS §7 e exigiria decisão explícita)

### Módulo 011 — Painel mensal (RF-33)

- [ ] Para uma competência: faturas do proprietário com imóvel, inquilino, valor, vencimento e
      status.
- [ ] Totais da competência: esperado, recebido, pendente, atrasado.
- ❓ Quais totais e filtros o frontend precisa? (alinhar com o repositório do frontend antes do
      plan)

## Fase 4 — Operação e integração com o frontend

### Módulo 012 — API pronta para o frontend (RNF-18)

- [ ] CORS liberado só para a origem do frontend, por variável de ambiente.
- [ ] Cookie de sessão e CSRF (`csrf.spa()` já configurado) funcionando entre domínios: `SameSite`,
      `Secure` e fluxo do token documentados para o frontend.
- [ ] Contrato da API documentado e atualizado (coleção em `docs/api/`).
- ❓ Frontend e API no mesmo domínio (proxy reverso) ou em domínios diferentes? Muda o desenho de
      cookie e CORS.

### Módulo 013 — Ambiente e CI (RNF-20)

- [ ] Docker Compose com app + PostgreSQL (+ Redis, se RNF-19 entrar) para subir o ambiente.
- [ ] GitHub Actions: `./mvnw verify` com Docker em todo PR (inclui Testcontainers e
      `ImagemContainerTest`).
- [ ] Publicação da imagem (ver [`container.md`](container.md)).
- ❓ Onde o MVP vai rodar (provedor, banco gerenciado ou não, backup)?

### Decisões transversais pendentes

- ❓ **RNF-19 — Redis**: usar no MVP (lock das rotinas, sessão compartilhada) ou adiar? Hoje está
      na stack e não é usado; sessão em Redis exigiria dependência nova (`spring-session`), que
      precisa de acordo (AGENTS §9).
- ❓ **LGPD**: além do CPF cifrado, o MVP precisa de exclusão/anonimização a pedido do titular?
      Conflita com "nada é apagado de fato".
- ❓ **Senha**: o MVP precisa de troca e de recuperação de senha ("esqueci minha senha")? Não está
      em nenhum RF.

## Fase 5 — Fechamento do MVP

- [ ] AGENTS.md §6–§7 atualizados (D4; "Dívida restante" vazia).
- [ ] `requisitos-e-der.md` sem ❌ nem 🟡 dentro do escopo do MVP.
- [ ] Atualizar o documento do Drive conforme `requisitos-e-der.md` §7.
- [ ] Quickstart de ponta a ponta: cadastro → login → imóvel → inquilino → contrato → fatura
      emitida → atraso → marcar como paga → painel, em modo `prod`.
- [ ] `./mvnw verify` verde na CI.
- [ ] Tag de versão e imagem publicada.

---

## Resumo da ordem

| # | Módulo | Depende de | Requisitos |
|---|---|---|---|
| 0 | Arrumação | — | D8 |
| 004 | Erros globais | — | D9 |
| 005 | Imóvel completo | — | RF-09, RF-10 |
| 006 | Inquilino | — | RF-13–17, RNF-13 |
| 007 | Contrato | 005, 006 | RF-12, RF-18, RF-19, RF-21, D1 |
| 008 | Anexo ❓ | 007 | RF-20 |
| 009 | Faturas | 007 | RF-22–26, RNF-16, D2 |
| 010 | Marcar como paga | 009 | RF-34 |
| 011 | Painel | 009, 010 | RF-33 |
| 012 | API para o frontend | — (antes do frontend integrar) | RNF-18 |
| 013 | Ambiente e CI | — (quanto antes, melhor) | RNF-20 |

004, 005, 006, 012 e 013 não dependem entre si e podem andar em paralelo. A numeração dos
módulos é sugestão: o número real sai do `/speckit-specify`.
