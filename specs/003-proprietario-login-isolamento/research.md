# Research: Proprietário, login e isolamento de dados (módulo 2)

**Feature**: [spec.md](./spec.md) · **Plan**: [plan.md](./plan.md) · **Data**: 2026-10-30

Versões conferidas no BOM do Spring Boot 4.1.2 (`~/.m2/.../spring-boot-dependencies/4.1.2`):
Spring Security **7.1.2**, Hibernate 7.4.13, Jackson 3.1.7. As APIs do Spring Security citadas
abaixo foram conferidas com `javap` nos jars 7.1.2 do Maven Central.

---

## R1. Forma da autenticação: sessão no servidor com cookie

- **Decision**: sessão HTTP no servidor (cookie `JSESSIONID`, `HttpOnly`, `SameSite=Lax`,
  `Secure` no profile `prod`), guardada na memória do container. Expiração por inatividade de
  **30 minutos** (`server.servlet.session.timeout=30m`). O frontend (repositório separado, Q23)
  acessa a API pela **mesma origem**, com o servidor do Next.js repassando `/api/**` para o
  backend; por isso não há CORS neste módulo.
- **Rationale**: é o mecanismo nativo do Spring Security (Q19), não pede dependência nova e dá
  logout real (a sessão é invalidada no servidor, FR-011). O cookie `HttpOnly` não fica ao
  alcance de JavaScript, o que um token em `localStorage` não garante. Volume baixo (RNF-21) e
  instância única: sessão em memória basta.
- **Alternatives considered**:
  - *JWT (resource server)*: exige `spring-boot-starter-oauth2-resource-server` (dependência
    nova, sem acordo), gestão de chave de assinatura, e logout só por lista de revogação. Rejeitado.
  - *HTTP Basic a cada requisição*: o frontend teria de guardar a senha. Rejeitado.
  - *Sessão no Redis (Spring Session)*: sobreviveria a reinício e a várias instâncias, mas exige
    `spring-session-data-redis` (dependência nova). Fica como evolução; nada no desenho impede
    trocar o repositório de sessão depois.
  - *CORS com cookie entre sites (`SameSite=None`)*: amplia a superfície de CSRF e depende de
    configuração de domínio que ainda não existe. Rejeitado em favor do proxy de mesma origem.

## R2. Login pelo filtro padrão do Spring Security, delegando ao caso de uso

- **Decision**: `formLogin` com `loginProcessingUrl("/sessao")`,
  `usernameParameter("email")`, `passwordParameter("senha")` (corpo
  `application/x-www-form-urlencoded`), handlers de sucesso (`204`) e de falha (`401` em JSON)
  próprios, e um `AuthenticationProvider` adapter (`ProprietarioAuthenticationProvider`) que só
  traduz: chama `AutenticarProprietarioUseCase.executar(...)` e devolve um
  `UsernamePasswordAuthenticationToken.authenticated(new ProprietarioAutenticado(...), null, List.of())`.
  O caso de uso, sem Spring, decide se as credenciais valem.
- **Rationale**: o filtro padrão já faz o que é fácil errar à mão: troca o id da sessão no login
  (proteção contra fixação de sessão), renova o token CSRF, grava o `SecurityContext` na sessão.
  A regra de autenticação continua no `application/`, testável com fakes (Princípio I).
  Com um `AuthenticationProvider` como bean, a autoconfiguração do Boot não cria o usuário em
  memória com senha gerada.
- **Alternatives considered**:
  - *Controller próprio de login com JSON*, salvando o contexto via `SecurityContextRepository`:
    teria de reimplementar troca de id de sessão e renovação de CSRF. Rejeitado.
  - *`UserDetailsService` + `DaoAuthenticationProvider`*: poria a regra de credencial no
    framework, fora de `application/`, e o adapter acessaria a porta de saída sem caso de uso.
    Rejeitado.
- A página HTML de login gerada pelo Spring não é exposta: o ponto de entrada de autenticação
  responde `401` em JSON (R6) e a `loginPage` é declarada para não gerar o filtro da página padrão.

## R3. Mesma resposta para e-mail inexistente e senha errada (FR-009, SC-005)

- **Decision**: `AutenticarProprietarioUseCase` lança `CredenciaisInvalidasException` nos dois
  casos. Quando o e-mail não existe, o caso de uso **ainda confere a senha** contra um hash
  fictício calculado uma vez no construtor (`codificadorSenha.codificar(...)`), para que o tempo de
  resposta não denuncie a existência da conta. O provider traduz para `BadCredentialsException`, e
  o handler de falha responde sempre `401` com "E-mail ou senha inválidos.".
- **Rationale**: conteúdo e tipo idênticos é o que a spec exige; igualar o tempo é barato e fecha
  o canal lateral óbvio.
- **Alternatives considered**: deixar o `DaoAuthenticationProvider` (faz o mesmo truque) —
  rejeitado pelo motivo de R2.
- Senha com mais de 72 bytes **no login** não é erro de validação: conta como credencial inválida
  (a política de senha não é revelada no login). O adapter de hash devolve `false` em vez de
  propagar a `IllegalArgumentException` do BCrypt.

## R4. Hash de senha atrás de uma porta (FR-005, FR-006)

- **Decision**: porta `CodificadorSenhaPort` (`codificar(senha)`, `confere(senha, hash)`) e adapter
  `BCryptCodificadorSenhaAdapter` em `adapters/outbound/seguranca`, sobre `BCryptPasswordEncoder`
  (versão `$2a`/`$2b`, salt aleatório embutido). Custo configurável por
  `habitattio.seguranca.bcrypt-custo`: **10** no modo rápido, **12** no `prod`. Trocar o custo não
  exige migração: cada hash guarda o próprio custo.
- **Limite da senha**: mínimo **8 caracteres** (contados em code points) e máximo **72 bytes em
  UTF-8** — o BCrypt 7.1.2 recusa acima disso ("password cannot be more than 72 bytes",
  conferido no jar). A regra vive num objeto de domínio `Senha`, com mensagem em português; nunca
  há truncamento silencioso (FR-005).
- **Rationale**: mantém o Spring fora do caso de uso (Princípio I) e permite fake trivial nos
  testes de `application/`.
- **Alternatives considered**: Argon2/PBKDF2 (`DelegatingPasswordEncoder`) — BCrypt foi decidido
  em Q25; o prefixo `{bcrypt}` do delegating não agrega nada com um algoritmo só e quebraria o
  `CHECK` de formato do banco. Rejeitado.

## R5. CSRF para cliente SPA

- **Decision**: CSRF **ligado**, com `csrf(c -> c.spa())` (existe em `CsrfConfigurer` 7.1.2, com
  `SpaCsrfTokenRequestHandler`): token em cookie `XSRF-TOKEN` legível pelo JavaScript, devolvido
  no cabeçalho `X-XSRF-TOKEN` em `POST`/`DELETE`. O cookie é emitido em qualquer resposta; o
  cliente faz `GET /sessao` ao abrir a aplicação (responde `401` sem sessão, mas já traz o
  cookie). Cadastro de proprietário e login também exigem o token (protege contra *login CSRF*).
- **Rationale**: com sessão em cookie, CSRF é o risco principal; `SameSite=Lax` ajuda, mas não
  substitui o token. `spa()` é a configuração recomendada pelo Spring Security para SPAs.
- **Alternatives considered**: desligar CSRF confiando só em `SameSite` — rejeitado (defesa em
  profundidade; navegadores antigos). Isentar cadastro e login — rejeitado por *login CSRF*.

## R6. Tratamento de erro único (FR-026, D3)

- **Decision**: um `@RestControllerAdvice` (`TratamentoErrosHandler`) em
  `adapters/inbound/rest` produz `ProblemDetail` (RFC 9457, `application/problem+json`) com
  `detail` em português. Os pontos de entrada de segurança (`AuthenticationEntryPoint`,
  `AccessDeniedHandler`, handler de falha de login) **delegam ao `HandlerExceptionResolver`** do
  MVC, para que também passem pelo mesmo advice. Mapeamento:

  | Situação | Origem | Status |
  |---|---|---|
  | Invariante de domínio violada | `IllegalArgumentException` | 400 |
  | JSON malformado ou tipo errado | `HttpMessageNotReadableException` | 400 |
  | Id de caminho não numérico | `MethodArgumentTypeMismatchException` | 400 |
  | Não autenticado / sessão expirada | `AuthenticationException` (entry point) | 401 |
  | Login falhou | `BadCredentialsException` (handler de falha) | 401 |
  | Token CSRF ausente ou inválido | `AccessDeniedException` (`CsrfException`) | 403 |
  | Imóvel inexistente **ou de outro dono** | `ImovelNaoEncontradoException` | 404 |
  | E-mail já cadastrado | `EmailJaCadastradoException` | 409 |
  | Identificador repetido para o dono | `IdentificadorImovelDuplicadoException` | 409 |

- **Sem captura global de `Exception`** (Clarifications, C1): o mecanismo global de tradução de
  exceções genéricas é uma feature à parte. Neste módulo, cada erro conhecido é tratado
  explicitamente onde ocorre — violação de unicidade no adapter de persistência (R8), limite de
  72 bytes no adapter de hash (R3/R4) — e chega ao advice já como exceção de negócio mapeada
  acima. Erro não previsto segue o comportamento padrão do Spring até a feature dedicada.

- **Rationale**: resolve D3 num ponto só e torna "inválido", "duplicado", "não autenticado",
  "proibido" e "não encontrado" distinguíveis pelo status (FR-026).
- **Bean Validation nos `*Request`**: não usada neste módulo. As regras já estão no domínio,
  com mensagem em português; repetir em anotação duplicaria regra e mensagem. Campo ausente no
  JSON chega como `null` e o domínio recusa com 400.
- **Risco aceito**: `IllegalArgumentException` vinda de infraestrutura também viraria 400. O
  domínio é o único lugar que lança `IllegalArgumentException` de propósito; o adapter de hash
  converte a do BCrypt (R3).

## R7. Exceções de negócio em `domain/exception`

- **Decision**: `EmailJaCadastradoException`, `IdentificadorImovelDuplicadoException`,
  `ImovelNaoEncontradoException`, `CredenciaisInvalidasException` — Java puro, `RuntimeException`,
  mensagem em português, num pacote novo `domain/exception/`.
- **Rationale**: são lançadas pelos casos de uso **e** pelos adapters de persistência (corrida,
  R8); morando no domínio, qualquer camada pode importá-las sem que o domínio importe ninguém.
- **Alternatives considered**: `application/exception` — obrigaria o adapter de saída a importar
  de `application/`. Rejeitado.

## R8. Unicidade: verificação no caso de uso + restrição no banco + tradução da corrida

- **Decision**: o caso de uso consulta a porta (`existePorEmail`, `existeIdentificador`) e lança a
  exceção de duplicidade. Como dois cadastros simultâneos podem passar pela consulta, o banco tem
  a restrição (R9), e o adapter traduz a `DataIntegrityViolationException` cujo causador cite
  `uq_usuarios_email` / `uq_imoveis_usuario_identificador` (comparação sem caixa: o H2 usa
  maiúsculas) para a mesma exceção de negócio (FR-025).
- **Rationale**: mensagem boa no caso comum, garantia real no caso raro, mesma resposta nos dois.
- **Comparação sem caixa** (Clarifications Q5): no PostgreSQL, índice único sobre
  `(usuario_id, lower(identificador))`; na consulta, `existsByUsuarioIdAndIdentificadorIgnoreCase`.
  O domínio compara com `Locale.ROOT`. Diferenças de *case folding* entre Java e PostgreSQL em
  alfabetos exóticos (ß, I turco) são aceitas: o banco é a última palavra e devolve a mesma
  recusa.
- **Modo rápido (H2, `ddl-auto=update`)**: o índice sobre `lower(...)` não é gerado a partir da
  entity; ali vale uma `@UniqueConstraint` exata (`usuario_id`, `identificador`) mais a consulta
  sem caixa do caso de uso. Garantia completa só no `prod`, como no módulo 1.

## R9. Migração V2

- **Decision**: `V2__cria_usuarios_e_dono_de_imoveis.sql` cria `usuarios` e acrescenta
  `usuario_id` e `identificador` a `imoveis`, com as restrições de
  [contracts/schema-v2.md](./contracts/schema-v2.md). `ADD COLUMN ... NOT NULL` sem `DEFAULT`
  funciona porque não há dados de produção; num banco de dev com linhas da V1 a migração falha —
  recriar o volume (registrado no quickstart).
- `senha_hash` vira **`varchar(60)`** com `CHECK` de formato BCrypt, e não `char(60)` como no
  DER: `char` vira `bpchar` no PostgreSQL e o `ddl-auto=validate` do Hibernate espera `varchar`
  para `String`. O `CHECK` garante, no banco, que nunca se grava senha em texto (SC-004).
- O dono é mapeado na entity como coluna `Long usuarioId` simples, sem `@ManyToOne` — as
  entidades se referenciam por id (AGENTS §6). A FK existe só na migração, como as demais
  restrições do `prod`.

## R10. Isolamento: o dono entra em toda consulta

- **Decision**: o id do proprietário vem **só** do principal autenticado
  (`@AuthenticationPrincipal ProprietarioAutenticado`), vai para o command, e a porta só expõe
  buscas que recebem o dono: `buscarDoProprietario(imovelId, usuarioId)` →
  `findByIdAndUsuarioId`. Não existe `buscarPorId(id)` sem dono na porta. Imóvel de outro dono e
  imóvel inexistente produzem o mesmo `Optional.empty()` → `ImovelNaoEncontradoException` → 404.
- **Rationale**: o isolamento fica impossível de esquecer num caso de uso novo, porque a porta não
  oferece o caminho inseguro. Os `*Request` não têm campo de dono; um campo extra no JSON é
  ignorado pelo Jackson (FR-014).
- **Inativação**: `POST /imoveis/{id}/inativacao` — ação sobre o recurso, idempotente; `DELETE`
  foi descartado porque nada é apagado (RNF-08).

## R11. Testes sem framework de mocking

- **Decision**: fakes escritos à mão em `src/test/java/.../fakes/`
  (`UsuarioRepositoryFake`, `ImovelRepositoryFake`, `CodificadorSenhaFake`), usados pelos testes
  de caso de uso e pelos `@WebMvcTest` (via `@TestConfiguration` que registra os casos de uso
  reais sobre os fakes). Autenticação nos testes de web com `spring-security-test`
  (`authentication(...)` com um `ProprietarioAutenticado`, `csrf()`). Nada de `@MockitoBean`
  (AGENTS §9).
- O fake privado de `CadastrarImovelUseCaseTest` (existente) **não** é substituído: ganha só os
  métodos novos da porta, por decisão do dono (P1, opção A).
- **Isolamento ponta a ponta** (SC-002) e fluxo de sessão: `@SpringBootTest` + MockMvc no profile
  `prod` com Testcontainers, com login real de dois proprietários.

## R12. Dependências

- **Decision**: `spring-boot-starter-security` (main) e `spring-boot-starter-security-test`
  (test), ambos geridos pelo BOM 4.1.2. Cobertos pelo acordo Q19 (Spring Security aprovado); o
  starter de teste é o par de teste do mesmo acordo, como `spring-boot-starter-webmvc-test` é do
  web. A constitution (§Stack) e o AGENTS §9 recebem a menção ao Spring Security (emenda PATCH,
  como foi com o Flyway em 1.0.1).
