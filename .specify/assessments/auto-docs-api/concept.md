# Concept: Descrição da API derivada do código, exportável por um comando

- **Slug**: auto-docs-api
- **Created**: 2026-11-02
- **Recommended option**: B — Descrição gerada do código, exportada pela suíte de testes

## Options

### Option A — Manter o arquivo manual e só detectar a divergência

- **Sketch**: o `docs/api/openapi.yaml` continua escrito à mão. Um teste automatizado compara as
  rotas (método + caminho) que a aplicação realmente expõe com as listadas no arquivo e falha se
  divergirem. Nada novo fica exposto em tempo de execução; o API client segue importando o mesmo
  arquivo.
- **Appetite**: small (1–2 dias).
- **Trade-offs**: ganha zero dependência nova, zero superfície nova e preserva 100% do texto
  explicativo; sacrifica o que o dono pediu explicitamente: regenerar e **substituir** o arquivo e ter
  uma Swagger UI (intake, Clarifications). Só pega rotas novas/removidas, não campos ou status
  alterados, e toda mudança ainda exige edição manual (métrica "0% de edição manual" não é
  atingida).
- **Rabbit holes**: comparar campos e esquemas, além das rotas, transforma o teste num gerador
  disfarçado; incluir as rotas de filtro (login/logout) e do Actuator na comparação.

### Option B — Descrição gerada do código, exportada pela suíte de testes

- **Sketch**: a aplicação passa a produzir sua própria descrição OpenAPI a partir dos controllers,
  enriquecida com o texto que hoje só existe no arquivo manual (guia de CSRF/sessão, descrições em
  português, exemplos) e com as rotas que não são controllers (login, logout, saúde). Com a app
  rodando no modo rápido, uma Swagger UI fica navegável. Um teste da suíte normal sobe a aplicação,
  obtém a descrição e a grava em `docs/api/openapi.yaml`, que passa a ser **artefato gerado**.
  Basta rodar a suíte (o mesmo comando que um CI futuro rodaria) para regenerar o arquivo, e um
  `git diff` não vazio mostra que alguém esqueceu de regenerar.
- **Appetite**: small → medium (alguns dias; incerteza alta por causa das rotas de segurança e do
  texto explicativo — ver Rabbit holes).
- **Trade-offs**: atende às decisões do dono (regenerar, substituir, Swagger UI, dependência
  acordada) e às métricas "mudança de controller aparece sem editar doc" e "um comando". Encaixa
  na regra de TDD: o próprio teste é a verificação. Sacrifica: uma dependência nova; texto
  explicativo passa a viver em anotações/configuração na camada de adapters (nunca no domínio nem na
  aplicação, pelo AGENTS.md §2); a cadeia de segurança ganha exceções para a documentação; a
  fonte de verdade do contrato precisa ser redefinida.
- **Rabbit holes**:
  - Descrever login/logout (filtros do Spring Security, formulário, CSRF SPA) de forma fiel —
    pode exigir montagem manual dessas operações (research: UNVERIFIED).
  - "Try it out" na Swagger UI com CSRF em `X-XSRF-TOKEN` — fazer funcionar pode virar projeto
    próprio; aceitar só leitura é a saída barata.
  - Reproduzir byte a byte o YAML atual: não é objetivo, já que o arquivo é substituído.
  - Expor a documentação em `prod` e decidir sobre autenticação.
  - Compatibilidade da dependência com Spring Boot 4.1.2 / Java 25 (research: UNVERIFIED).

### Option C — Geração no build via plugin, com a app rodando de verdade

- **Sketch**: igual à B na aplicação (descrição gerada + Swagger UI), mas o arquivo é produzido
  por um plugin de build que sobe a aplicação inteira numa fase do Maven, baixa a descrição e a
  grava. É o caminho "oficial" documentado pelo gerador (research: Prior Art).
- **Appetite**: medium (1–2 semanas, por causa do ciclo de vida da app no build).
- **Trade-offs**: ganha separação clara entre "testar" e "gerar documentação"; sacrifica
  simplicidade: um `./mvnw verify` mais lento, subir/derrubar a aplicação de fora do teste,
  porta fixa, dependência de infra na subida (Redis não verificado) e um plugin a mais além da
  dependência de runtime. Não é um teste, então não ajuda na regra de TDD.
- **Rabbit holes**: subir a app no build num CI sem Docker/Redis; portas ocupadas; o plugin
  sinaliza erro por padrão sem falhar o build (`failOnError=false`) e precisa ser configurado.

## Recommendation

**Option B.** É a única que atende ao mesmo tempo às decisões já tomadas pelo dono (regenerar,
substituir o arquivo, Swagger UI, dependência acordada) e às métricas centrais do `problem.md`:
mudança no controller aparece sem editar documentação, um único comando, e um teste automatizado
como prova (exigência do TDD). Gerar dentro da suíte de testes, no modo rápido (H2), evita o custo
operacional da C, e o comando é o mesmo que um CI futuro rodaria. A Option A é o fallback se as rotas de
segurança ou o texto explicativo se mostrarem caros demais de reproduzir, mas contraria o pedido de
substituir o arquivo.

Ressalva: o apetite é **small → medium** com incerteza real. Se descrever login/logout com fidelidade
passar de alguns dias, cortar "Try it out" funcional e manter só a descrição dessas rotas.

## Out of Scope (for the recommended option)

- Criar pipeline de CI (só deixar o comando pronto).
- Geração de SDK/clientes e publicação para terceiros.
- Sincronizar a coleção Bruno (`docs/api/habitattion-api/`) automaticamente.
- Mudar rotas, comportamento ou regras de autenticação da API.
- Documentar `Inquilino`, `Contrato` e `Fatura` (ainda sem rotas).
- "Try it out" totalmente funcional para POST/DELETE com CSRF, se custar além do apetite.
- Reproduzir literalmente a estrutura/ordem do YAML manual atual.

## Assumptions to Validate

- O gerador code-first é compatível com Spring Boot 4.1.2 e Java 25 (research: UNVERIFIED).
- Dá para incluir na descrição as rotas de login/logout (filtros) e `/actuator/health` com conteúdo
  fiel ao contrato da spec 003.
- O texto explicativo (CSRF/sessão, português, exemplos) pode ser carregado em anotações ou
  configuração na camada de adapters, sem tocar `domain/` e `application/`.
- A aplicação sobe no modo rápido dentro de um teste sem Docker/Redis, de modo que a exportação roda
  em qualquer CI.
- O dono aceita que `docs/api/openapi.yaml` vire artefato gerado e que o "contrato de referência"
  seja redefinido (spec 003 `api-http.md` vs. descrição gerada).
- A política de exposição da Swagger UI e de `/v3/api-docs` (profiles, autenticação) será decidida
  na especificação; por padrão, pelo §9 do AGENTS.md, nada fica exposto em `prod` sem decisão explícita.
