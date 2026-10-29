# Idea Intake: Revisão e evolução do esquema do banco de dados

- **Slug**: better-db-schema
- **Created**: 2026-10-29
- **Source**: pasted text (comando `/speckit-assess-intake`) + repo path `src/main/java/br/com/marden/habitattio/adapters/outbound/persistence/`, `src/main/resources/application.properties`
- **Type**: exploration

## Idea (as captured)

> "Mudanças no esquema do banco, validar a atual estrutura, pesquisar melhorias e praticas de mercado"

Contexto do repositório no momento da captura (apenas observação, sem juízo):

- Existe uma única tabela mapeada: `imoveis` (`ImovelJpaEntity`: `id` IDENTITY, `endereco`, `valorSugerido`, `ativo`).
- `Inquilino`, `Contrato`, `Fatura` e `Usuario` existem só como modelo de domínio, sem entity JPA nem tabela (AGENTS.md §6, "Dívida restante").
- O esquema é gerado pelo Hibernate (`spring.jpa.hibernate.ddl-auto=update`), em H2 no modo PostgreSQL; os testes de integração usam PostgreSQL via Testcontainers.
- Não há ferramenta de migração de esquema (Flyway/Liquibase) no `pom.xml`.

## Restated

Validar a estrutura atual do banco de dados do habitattio, pesquisar melhorias e práticas de mercado para modelagem e evolução de esquema e, a partir disso, propor mudanças no esquema.

## Origin & Context

- **Raised by**: Marden Rafael (dono do projeto), via comando direto
- **Trigger**: [NEEDS CLARIFICATION: houve um problema concreto (ex.: perda de dados, divergência entre H2 e PostgreSQL), ou é uma revisão preventiva antes de persistir os demais agregados?]

## First-Glance Unknowns

- [NEEDS CLARIFICATION: o escopo é só a tabela `imoveis` existente, ou também desenhar as tabelas de `Inquilino`, `Contrato`, `Fatura` e `Usuario`, que hoje não estão persistidos?]
- [NEEDS CLARIFICATION: "mudanças no esquema" inclui adotar uma ferramenta de migração versionada e deixar de depender de `ddl-auto=update`? AGENTS.md §9 exige acordo para mudar `ddl-auto` e para adicionar dependências.]
- [NEEDS CLARIFICATION: qual é o banco-alvo de produção? PostgreSQL (como nos testes) ou H2 (como na configuração padrão)?]
- [NEEDS CLARIFICATION: quais "práticas de mercado" interessam: convenções de nomes, chaves (IDENTITY vs. sequence vs. UUID), constraints (unicidade de fatura por contrato+competência, CHECK de valores positivos), precisão/escala de `BigDecimal`, colunas de auditoria, índices, exclusão lógica?]
- [NEEDS CLARIFICATION: as regras de negócio do AGENTS.md §7 devem ser garantidas também no banco (constraints), ou só no domínio?]
- [NEEDS CLARIFICATION: existem dados a preservar (ambiente com dados reais), ou o banco pode ser recriado do zero?]
- [NEEDS CLARIFICATION: qual é o resultado esperado da avaliação: um relatório de diagnóstico, uma spec de feature, ou ambos?]
