# Idea Intake: Geração automática da especificação OpenAPI verificada no CI

- **Slug**: auto-docs-api
- **Created**: 2026-11-02
- **Source**: pasted text (comando `/speckit-assess-intake`), relacionado ao repo em `docs/api/openapi.yaml`
- **Type**: improvement

## Idea (as captured)

> "quero implementar um gerador automatico de OpenAPI/swaager que possa ser usado no CI, para evitar q esteja desatualizado"

## Restated

Gerar a especificação OpenAPI/Swagger da API automaticamente, em vez de mantê-la à mão, de forma
que um pipeline de CI possa produzi-la ou conferi-la e assim impedir que a documentação fique
defasada em relação ao código.

## Origin & Context

- **Raised by**: dono do projeto (Marden Rafael), em 2026-11-02.
- **Trigger**: logo antes, o commit `a0901d0` ("docs(api): especificação OpenAPI 3.1 para importar
  nos API clients") adicionou `docs/api/openapi.yaml` (527 linhas) **escrito à mão** e conferido
  manualmente contra a app rodando. Cobre proprietários, sessão (login por formulário, cookie
  `JSESSIONID`, CSRF em `X-XSRF-TOKEN`), imóveis e saúde. A ideia surge do risco de esse arquivo
  divergir dos controllers conforme a API evoluir.
- **Estado observado no repo (só registro, sem avaliação)**:
  - Não existe pipeline de CI versionado (não há diretório `.github/` nem outro arquivo de CI na raiz).
  - O `pom.xml` não tem nenhuma dependência de geração de OpenAPI.
  - AGENTS.md §9: nenhuma dependência nova no `pom.xml` sem acordo; o Actuator expõe só
    `/actuator/health` e nenhum outro endpoint sem acordo.
  - A cadeia de segurança (Spring Security) protege as rotas por padrão (`SegurancaConfiguration`).

## Clarifications (dono do projeto, 2026-11-02)

- **CI**: por ora, nenhum pipeline é criado; a geração deve apenas ficar **pronta para ser plugada
  num CI futuro** (rodável por um comando, sem passo manual).
- **Comportamento**: a especificação é **regenerada** a partir do código (não um check que só falha
  o build ao detectar divergência).
- **Arquivo atual**: o `docs/api/openapi.yaml` escrito à mão é **substituído** pelo gerado
  (code-first).
- **Detalhes do arquivo manual** (login por formulário, CSRF em `X-XSRF-TOKEN`, cookie
  `JSESSIONID`, exemplos, descrições em português): **preservar se possível**.
- **Em tempo de execução**: **Swagger UI**, por ora. Acordo explícito para expor esse endpoint
  além de `/actuator/health` (exceção ao §9 do AGENTS.md, restrita a esta feature).
- **Dependências**: **acordo explícito** para adicionar dependência(s) nova(s) ao `pom.xml` para
  esta feature (exceção ao §9 do AGENTS.md).

## First-Glance Unknowns

- [NEEDS CLARIFICATION: Quem consome a especificação: só os API clients locais (Bruno/opencollection, Postman, Insomnia) ou também terceiros/geração de SDK?]
- [NEEDS CLARIFICATION: Como isso se encaixa na regra de TDD do projeto, ou seja, qual teste prova que a especificação está atualizada?]
- [NEEDS CLARIFICATION: A Swagger UI fica disponível em todos os profiles (incluindo `prod`) e com ou sem autenticação? (surgiu das respostas acima)]
