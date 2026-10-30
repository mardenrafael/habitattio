---

description: "Tasks do módulo 2 — proprietário, login e isolamento de dados"
---

# Tasks: Proprietário, login e isolamento de dados (módulo 2)

**Input**: `specs/003-proprietario-login-isolamento/` — [spec.md](./spec.md), [plan.md](./plan.md),
[research.md](./research.md), [data-model.md](./data-model.md),
[contracts/api-http.md](./contracts/api-http.md), [contracts/schema-v2.md](./contracts/schema-v2.md),
[quickstart.md](./quickstart.md)

**Tests**: obrigatórios. TDD é inegociável (constitution §II, AGENTS §3): toda tarefa de teste vem
antes da implementação que ela justifica e MUST ser vista falhando **pelo motivo esperado** antes
da tarefa de implementação. Teste visto falhar é read-only.

**Testes existentes** (Clarifications Q2 + P1): `ImovelTest`, `CadastrarImovelUseCaseTest`,
`ImovelRepositoryAdapterTest` e `ImoveisPostgresConstraintsTest` só podem ganhar dono e
identificador nos dados de preparação; o fake privado de `CadastrarImovelUseCaseTest` pode ganhar
os métodos novos da porta. **Nenhuma asserção, nome de teste ou cenário provado muda.** Qualquer
outro teste existente que quebrar: parar e reportar ao dono.

**Organização**: por história de usuário. A US4 (estrutura no banco, P2) é executada na fase
**Foundational**, porque a migração V2 é pré-requisito de toda persistência no profile `prod`
(`ddl-auto=validate` recusa entity sem tabela). As tarefas dela levam o rótulo de fase, não de
história, e citam US4 na descrição.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: pode rodar em paralelo (arquivos diferentes, sem dependência de tarefa incompleta)
- **[Story]**: US1, US2, US3 (spec.md)

## Path Conventions

- `M/` = `src/main/java/br/com/marden/habitattio/`
- `T/` = `src/test/java/br/com/marden/habitattio/`
- `R/` = `src/main/resources/`

Os caminhos nas tarefas são completos; os prefixos acima só abreviam esta legenda.

---

## Phase 1: Setup

**Purpose**: dependência de segurança no build, sem quebrar nada que existe.

- [ ] T001 Acrescentar `spring-boot-starter-security` (escopo padrão) e `spring-boot-starter-security-test` (escopo `test`), sem versão (geridas pelo BOM 4.1.2), em `pom.xml` — acordo Q19, research R12
- [ ] T002 Rodar `./mvnw test` e confirmar que a suíte existente continua verde com o Spring Security no classpath; se algum teste existente quebrar, **parar e reportar** (AGENTS §3). Registrar o resultado no relatório da tarefa

**Checkpoint**: build com segurança; nenhuma regressão.

---

## Phase 2: Foundational — estrutura V2 no banco de produção (US4)

**Purpose**: entregar a US4 — `usuarios` e dono/identificador em `imoveis`, com todas as
restrições de [contracts/schema-v2.md](./contracts/schema-v2.md) — antes de qualquer entity nova.

**⚠️ CRITICAL**: nenhuma história começa antes desta fase.

**Independent Test (US4)**: contra o PostgreSQL do Testcontainers no profile `prod`, as tentativas
G-U1…G-I7 são recusadas (G-I6 aceita) e a subida aplica V1 e V2 (G-S1).

### Testes (escrever primeiro, ver falhar: tabela `usuarios` e colunas novas não existem)

- [ ] T003 [P] Criar `src/test/java/br/com/marden/habitattio/adapters/outbound/persistence/UsuariosEDonoPostgresConstraintsTest.java` (`@DataJpaTest`, `@AutoConfigureTestDatabase(replace = NONE)`, `@ActiveProfiles("prod")`, `@Import(TestcontainersConfiguration.class)`, `JdbcClient`, no estilo de `ImoveisPostgresConstraintsTest`) com um teste por garantia, verificando o nome da restrição na mensagem: G-U1 `nome` só espaços → `ck_usuarios_nome_nao_vazio`; G-U2 `email` com maiúscula e com espaço nas pontas → `ck_usuarios_email_normalizado`; G-U3 e-mail repetido → `uq_usuarios_email`; G-U4 `senha_hash = 'segredo123'` → `ck_usuarios_senha_hash_bcrypt`; G-I1 imóvel sem `usuario_id` → NOT NULL; G-I2 `usuario_id` inexistente → `fk_imoveis_usuario`; G-I3 `identificador` vazio, só espaços e `' Apto 101'` → `ck_imoveis_identificador_nao_vazio` / `ck_imoveis_identificador_sem_espacos_nas_pontas`; G-I4 51 caracteres recusado e 50 caracteres acentuados aceito; G-I5 "Apto 101" e "APTO 101" para o mesmo dono → `uq_imoveis_usuario_identificador`; G-I6 "Apto 101" para dois donos → aceito; G-I7 `DELETE FROM usuarios` de quem tem imóvel → `fk_imoveis_usuario`. Hash válido de exemplo nos inserts: um literal BCrypt de 60 caracteres `$2a$10$...`
- [ ] T004 [P] Criar `src/test/java/br/com/marden/habitattio/EstruturaBancoProducaoV2Test.java` (mesmo arranjo de `EstruturaBancoProducaoTest`) com `deveAplicarV2DepoisDaV1EmBancoVazio`: subida no `prod` em banco vazio registra as versões `1` e `2` com `success = true` em `flyway_schema_history` e a tabela `usuarios` existe (G-S1). Não alterar `EstruturaBancoProducaoTest`
- [ ] T005 Rodar T003 e T004 e confirmar que falham porque `usuarios`/colunas não existem (e não por erro de compilação ou de arranjo)

### Implementação

- [ ] T006 Criar `src/main/resources/db/migration/V2__cria_usuarios_e_dono_de_imoveis.sql` exatamente como [contracts/schema-v2.md](./contracts/schema-v2.md): tabela `usuarios` (`id bigint GENERATED BY DEFAULT AS IDENTITY`, `nome varchar(100) NOT NULL`, `email varchar(254) NOT NULL`, `senha_hash varchar(60) NOT NULL`; `pk_usuarios`; `ck_usuarios_nome_nao_vazio CHECK (nome !~ '^\s*$')`; `ck_usuarios_email_normalizado CHECK (email = lower(btrim(email)) AND email <> '')`; `uq_usuarios_email UNIQUE (email)`; `ck_usuarios_senha_hash_bcrypt CHECK (senha_hash ~ '^\$2[aby]\$\d{2}\$[./A-Za-z0-9]{53}$')`); em `imoveis`: `usuario_id bigint NOT NULL`, `identificador varchar(50) NOT NULL`, `fk_imoveis_usuario FOREIGN KEY (usuario_id) REFERENCES usuarios (id) ON DELETE RESTRICT`, `ck_imoveis_identificador_nao_vazio CHECK (identificador !~ '^\s*$')`, `ck_imoveis_identificador_sem_espacos_nas_pontas CHECK (identificador = btrim(identificador))`, e `CREATE UNIQUE INDEX uq_imoveis_usuario_identificador ON imoveis (usuario_id, lower(identificador))`. Sem migração de dados; não alterar a V1
- [ ] T007 Adaptar **só os dados** de `src/test/java/br/com/marden/habitattio/adapters/outbound/persistence/ImoveisPostgresConstraintsTest.java` (autorizado, Q2): um `@BeforeEach` insere um proprietário dono em `usuarios` (hash BCrypt literal válido) e guarda o id; a constante `INSERT` e os dois inserts literais passam a incluir `usuario_id` e `identificador`, com identificador único por linha (ex.: contador `"Imóvel " + n`) para não colidir nos testes que repetem endereço. Nenhuma asserção, nome de teste ou garantia G1… muda
- [ ] T008 Rodar `./mvnw test -Dtest='UsuariosEDonoPostgresConstraintsTest,EstruturaBancoProducaoV2Test,ImoveisPostgresConstraintsTest,EstruturaBancoProducaoTest'` e confirmar tudo verde

**Checkpoint**: US4 entregue — o banco de produção garante dono, identificador e e-mail único.

---

## Phase 3: User Story 1 — Proprietário cria a própria conta (Priority: P1) 🎯 MVP

**Goal**: `POST /proprietarios` cria proprietário com nome, e-mail normalizado e único, senha só em
hash BCrypt; nenhuma resposta traz senha ou hash.

**Independent Test**: cadastrar a Ana; recusar `ANA@exemplo.com ` (409), e-mail inválido, nome em
branco, senha curta e senha > 72 bytes (400); o valor guardado é `$2…`, não contém a senha, e
duas contas com a mesma senha têm hashes diferentes.

### Testes para US1 (escrever primeiro, ver falhar)

- [ ] T009 [P] [US1] Criar `src/test/java/br/com/marden/habitattio/domain/model/SenhaTest.java`: `deveAceitarSenhaComOitoCaracteres`; `deveRejeitarSenhaNula` e `deveRejeitarSenhaVazia` ("A senha não pode ser vazia."); `deveRejeitarSenhaComMenosDeOitoCaracteres` ("A senha deve ter no mínimo 8 caracteres."), contando code points (7 emojis recusados); `deveAceitarSenhaComSetentaEDoisBytes`; `deveRejeitarSenhaComMaisDeSetentaEDoisBytesEmUtf8` (ex.: 37 letras acentuadas = 74 bytes) com "A senha é longa demais: use no máximo 72 bytes (cerca de 72 letras sem acento)."; `naoDeveExporOValorNoToString` (`toString()` sem o texto da senha)
- [ ] T010 [P] [US1] **Acrescentar** a `src/test/java/br/com/marden/habitattio/domain/model/UsuarioTest.java` (sem tocar os testes existentes): `deveNormalizarEmailRemovendoEspacosEMaiusculas` (`" Ana@Exemplo.COM "` → `"ana@exemplo.com"`); `deveRejeitarNomeComMaisDeCemCaracteres` ("O nome do usuário deve ter no máximo 100 caracteres.") e `deveAceitarNomeComCemCaracteresAcentuados`; `deveRejeitarEmailComMaisDe254Caracteres` ("O e-mail do usuário deve ter no máximo 254 caracteres."); `normalizarEmailDeveDevolverNuloParaNulo`
- [ ] T011 [P] [US1] Criar os fakes compartilhados em `src/test/java/br/com/marden/habitattio/fakes/UsuarioRepositoryFake.java` (Map em memória, ids sequenciais, `buscarPorEmail`/`existePorEmail` por e-mail exato já normalizado, opção `simularCorridaNoProximoSalvar()` que faz `salvar` lançar `EmailJaCadastradoException`) e `src/test/java/br/com/marden/habitattio/fakes/CodificadorSenhaFake.java` (`codificar(s)` = `"hash:" + s + ":" + contador`, de modo que duas codificações da mesma senha diferem; `confere` compara o trecho da senha; conta chamadas de `confere` para os testes da US2)
- [ ] T012 [US1] Desenhar só a forma (sem lógica; `throw new UnsupportedOperationException()` onde preciso compilar): `src/main/java/br/com/marden/habitattio/domain/model/Senha.java`, `src/main/java/br/com/marden/habitattio/domain/exception/EmailJaCadastradoException.java`, `src/main/java/br/com/marden/habitattio/ports/inbound/CadastrarProprietarioCommand.java` (record `nome`, `email`, `senha`), `src/main/java/br/com/marden/habitattio/ports/outbound/UsuarioRepositoryPort.java` (`Usuario salvar(Usuario)`, `Optional<Usuario> buscarPorEmail(String)`, `boolean existePorEmail(String)`), `src/main/java/br/com/marden/habitattio/ports/outbound/CodificadorSenhaPort.java` (`String codificar(String)`, `boolean confere(String senha, String hash)`), `src/main/java/br/com/marden/habitattio/application/usecase/CadastrarProprietarioUseCase.java` (`Usuario executar(CadastrarProprietarioCommand)`), assinatura estática `Usuario.normalizarEmail(String)` em `src/main/java/br/com/marden/habitattio/domain/model/Usuario.java`
- [ ] T013 [P] [US1] Criar `src/test/java/br/com/marden/habitattio/application/usecase/CadastrarProprietarioUseCaseTest.java` (fakes de T011): `deveCadastrarProprietarioComEmailNormalizadoESenhaCodificada` (hash ≠ senha); `deveRejeitarEmailJaCadastradoIgnorandoCaixaEEspacos` (`EmailJaCadastradoException`, nada salvo, `codificar` não chamado); `deveRejeitarSenhaCurtaAntesDeConsultarORepositorio`; `deveRejeitarNomeEmBranco`; `deveRejeitarEmailInvalido`; `devePropagarEmailJaCadastradoQuandoORepositorioDetectaCorrida`; `naoDeveExporASenhaNoToStringDoCommand`
- [ ] T014 [P] [US1] Criar `src/test/java/br/com/marden/habitattio/adapters/outbound/seguranca/BCryptCodificadorSenhaAdapterTest.java` (JUnit puro, `new BCryptCodificadorSenhaAdapter(4)`): `deveGerarHashBCryptDeSessentaCaracteres` (casa `^\$2[aby]\$04\$.{53}$`); `deveGerarHashesDiferentesParaAMesmaSenha`; `deveConferirSenhaCorreta`; `deveRecusarSenhaErrada`; `deveDevolverFalsoEmVezDeLancarParaSenhaComMaisDe72Bytes` (`confere`)
- [ ] T015 [P] [US1] Criar `src/test/java/br/com/marden/habitattio/adapters/outbound/persistence/UsuarioRepositoryAdapterTest.java` (`@DataJpaTest` + `@ActiveProfiles("prod")` + `@Import(TestcontainersConfiguration.class)`, adapter instanciado por construtor no `@BeforeEach`): `deveSalvarERecuperarPorEmail` (ida e volta de todos os campos); `deveInformarSeEmailExiste`; `deveTraduzirViolacaoDeEmailUnicoParaEmailJaCadastrado` (salvar dois `Usuario` com o mesmo e-mail direto pelo adapter → `EmailJaCadastradoException`, simulando corrida)
- [ ] T016 [P] [US1] Criar `src/test/java/br/com/marden/habitattio/adapters/inbound/rest/ProprietarioControllerTest.java` (`@WebMvcTest(ProprietarioController.class)`, `@Import` de `SegurancaConfiguration` e de uma `@TestConfiguration` que registra `CadastrarProprietarioUseCase` real sobre os fakes; sem Mockito; `csrf()` do `spring-security-test`): `deveCriarProprietarioERetornar201SemSenhaNemHash` (corpo `{id, nome, email}` com e-mail normalizado; JSON não contém `senha` nem `hash`); `deveRetornar409ParaEmailJaCadastrado` com "Já existe um proprietário com este e-mail." em `application/problem+json`; `deveRetornar400ParaSenhaCurta`, `deveRetornar400ParaEmailInvalido`, `deveRetornar400ParaNomeEmBranco` (detail = mensagem do domínio); `deveRetornar400ParaJsonMalformado` ("Corpo da requisição inválido."); `deveRetornar403SemTokenCsrf`; `naoDeveExigirAutenticacaoParaCadastrar`
- [ ] T017 [US1] Rodar T009, T010, T013–T016 e confirmar que falham pelo motivo esperado (regra ausente, `UnsupportedOperationException`, rota/bean inexistente) — não por erro de compilação acidental

### Implementação para US1

- [ ] T018 [P] [US1] Implementar `src/main/java/br/com/marden/habitattio/domain/model/Senha.java`: obrigatória; `valor.codePointCount(...) >= 8`; `valor.getBytes(UTF_8).length <= 72`; mensagens de T009; `toString()` = `"Senha[******]"`
- [ ] T019 [P] [US1] Implementar em `src/main/java/br/com/marden/habitattio/domain/model/Usuario.java`: `normalizarEmail` (`null` → `null`; senão `strip()` + `toLowerCase(Locale.ROOT)`), aplicada no construtor **antes** de validar; nome ≤ 100 code points; e-mail ≤ 254 code points; mensagens de T010; validações existentes intactas
- [ ] T020 [P] [US1] Implementar `src/main/java/br/com/marden/habitattio/domain/exception/EmailJaCadastradoException.java` (`RuntimeException`, mensagem "Já existe um proprietário com este e-mail.") e o `toString()` mascarado de `src/main/java/br/com/marden/habitattio/ports/inbound/CadastrarProprietarioCommand.java`
- [ ] T021 [US1] Implementar `src/main/java/br/com/marden/habitattio/application/usecase/CadastrarProprietarioUseCase.java` na ordem do data-model: `new Senha(...)` → `Usuario.normalizarEmail` + `existePorEmail` (lança `EmailJaCadastradoException`) → `codificar` → `new Usuario(null, nome, email, hash)` → `salvar`
- [ ] T022 [P] [US1] Implementar `src/main/java/br/com/marden/habitattio/adapters/outbound/seguranca/BCryptCodificadorSenhaAdapter.java` (`implements CodificadorSenhaPort`, construtor recebe o custo, `BCryptPasswordEncoder`; `confere` devolve `false` para senha > 72 bytes em vez de propagar `IllegalArgumentException`)
- [ ] T023 [P] [US1] Criar `src/main/java/br/com/marden/habitattio/adapters/outbound/persistence/entity/UsuarioJpaEntity.java` (`@Table(name = "usuarios", uniqueConstraints = @UniqueConstraint(name = "uq_usuarios_email", columnNames = "email"))`; `nome` `length = 100`, `email` `length = 254`, `senhaHash` `length = 60`, todos `nullable = false`; getters à mão) e `src/main/java/br/com/marden/habitattio/adapters/outbound/persistence/SpringDataUsuarioRepository.java` (`findByEmail`, `existsByEmail`)
- [ ] T024 [US1] Implementar `src/main/java/br/com/marden/habitattio/adapters/outbound/persistence/UsuarioRepositoryAdapter.java` (`implements UsuarioRepositoryPort`; mapeamento manual nas duas direções; `DataIntegrityViolationException` cuja causa mais específica cite `uq_usuarios_email` sem distinguir caixa → `EmailJaCadastradoException`; outras são relançadas)
- [ ] T025 [US1] Criar `src/main/java/br/com/marden/habitattio/adapters/inbound/rest/TratamentoErrosHandler.java` (`@RestControllerAdvice`, `ProblemDetail` com `detail` em português): `IllegalArgumentException` → 400 (mensagem do domínio); `HttpMessageNotReadableException` → 400 "Corpo da requisição inválido."; `EmailJaCadastradoException` → 409 (research R6). **Sem** handler para `Exception` genérica: a captura global é feature à parte (spec, Clarifications C1); cada erro conhecido é tratado explicitamente onde ocorre
- [ ] T026 [US1] Criar `src/main/java/br/com/marden/habitattio/adapters/config/SegurancaConfiguration.java` (`@Configuration`, `SecurityFilterChain`): `csrf(c -> c.spa())`; `POST /proprietarios` liberado; todo o resto `authenticated()`; `AccessDeniedHandler` (403 "Token CSRF ausente ou inválido.", justificado por `deveRetornar403SemTokenCsrf` de T016) que delega ao `HandlerExceptionResolver` para sair pelo `TratamentoErrosHandler` (handler em `src/main/java/br/com/marden/habitattio/adapters/inbound/security/RespostasSeguranca.java`, com o mapeamento 403 correspondente em `TratamentoErrosHandler`). A resposta 401 **não** entra aqui: vem na US2 (T039), junto dos testes que a exigem (T033, T034)
- [ ] T027 [US1] Criar `src/main/java/br/com/marden/habitattio/adapters/inbound/rest/ProprietarioController.java` (`POST /proprietarios` → `CadastrarProprietarioCommand` → 201 com `ProprietarioResponse(id, nome, email)`; `CadastrarProprietarioRequest(nome, email, senha)` e `ProprietarioResponse` como records aninhados; sem `Location`)
- [ ] T028 [US1] Registrar em `src/main/java/br/com/marden/habitattio/adapters/config/BeanConfiguration.java` os beans `UsuarioRepositoryPort` (`UsuarioRepositoryAdapter`), `CodificadorSenhaPort` (`BCryptCodificadorSenhaAdapter` com `@Value("${habitattio.seguranca.bcrypt-custo}")`) e `CadastrarProprietarioUseCase`; acrescentar `habitattio.seguranca.bcrypt-custo=10` em `src/main/resources/application.properties` e `habitattio.seguranca.bcrypt-custo=12` em `src/main/resources/application-prod.properties`
- [ ] T029 [US1] Rodar `./mvnw test` e confirmar verde (inclui `HabitattioApplicationTests` e os testes do `prod`)

**Checkpoint**: US1 entregue e testável sozinha — proprietários podem se cadastrar.

---

## Phase 4: User Story 2 — Proprietário entra no sistema (Priority: P1)

**Goal**: `POST /sessao` (form) autentica por sessão no servidor; `GET /sessao` diz quem está
autenticado; `DELETE /sessao` sai; recusa idêntica para e-mail inexistente e senha errada.

**Independent Test**: com a Ana cadastrada, entrar com a senha certa (e-mail em qualquer caixa) →
204 e `JSESSIONID` trocado; senha errada e e-mail inexistente → 401 idênticos; depois de sair, a
mesma sessão recebe 401; sem token CSRF → 403.

**Depende de**: US1 (cadastro e persistência de proprietário).

### Testes para US2 (escrever primeiro, ver falhar)

- [ ] T030 [US2] Desenhar só a forma: `src/main/java/br/com/marden/habitattio/domain/exception/CredenciaisInvalidasException.java`, `src/main/java/br/com/marden/habitattio/ports/inbound/AutenticarProprietarioCommand.java` (record `email`, `senha`), `src/main/java/br/com/marden/habitattio/application/usecase/AutenticarProprietarioUseCase.java` (`Usuario executar(AutenticarProprietarioCommand)`), `src/main/java/br/com/marden/habitattio/adapters/inbound/security/ProprietarioAutenticado.java` (record `id`, `nome`, `email`, `Serializable`), `src/main/java/br/com/marden/habitattio/adapters/inbound/security/ProprietarioAuthenticationProvider.java` (`implements AuthenticationProvider`), `src/main/java/br/com/marden/habitattio/adapters/inbound/rest/SessaoController.java`
- [ ] T031 [P] [US2] Criar `src/test/java/br/com/marden/habitattio/application/usecase/AutenticarProprietarioUseCaseTest.java` (fakes de T011): `deveAutenticarComEmailESenhaCorretos`; `deveAutenticarComEmailEmOutraCaixaEComEspacos`; `deveRejeitarSenhaErrada` (`CredenciaisInvalidasException`, "E-mail ou senha inválidos."); `deveRejeitarEmailInexistenteComAMesmaExcecaoEMensagem`; `deveConferirSenhaMesmoQuandoEmailNaoExiste` (contador de `confere` do `CodificadorSenhaFake` = 1 — interação é o comportamento, research R3); `naoDeveExporASenhaNoToStringDoCommand`
- [ ] T032 [P] [US2] Criar `src/test/java/br/com/marden/habitattio/adapters/inbound/security/ProprietarioAuthenticationProviderTest.java` (JUnit puro, caso de uso real sobre fakes): `deveDevolverAutenticacaoComProprietarioAutenticadoSemSenha` (principal com id/nome/e-mail; credenciais nulas; `isAuthenticated()`); `deveLancarBadCredentialsParaCredenciaisInvalidas`; `deveSuportarUsernamePasswordAuthenticationToken`
- [ ] T033 [P] [US2] Criar `src/test/java/br/com/marden/habitattio/adapters/inbound/rest/SessaoControllerTest.java` (`@WebMvcTest(SessaoController.class)` + `@Import(SegurancaConfiguration)` + `@TestConfiguration` com fakes): `deveRetornarProprietarioDaSessao` (`with(authentication(...))` com `ProprietarioAutenticado` → 200 `{id, nome, email}`); `deveRetornar401SemSessaoComCookieXsrfToken` ("Autenticação necessária." e `Set-Cookie: XSRF-TOKEN`)
- [ ] T034 [P] [US2] Criar `src/test/java/br/com/marden/habitattio/SessaoIntegracaoTest.java` (`@SpringBootTest` + `@AutoConfigureMockMvc` + `@ActiveProfiles("prod")` + `@Import(TestcontainersConfiguration.class)`; proprietário criado por `POST /proprietarios`): `deveEntrarComCredenciaisCorretasERetornar204` (e-mail em maiúsculas); `deveTrocarOIdDaSessaoAoEntrar` (fixação de sessão); `deveResponderIgualParaSenhaErradaEEmailInexistente` (mesmo status 401, mesmo corpo, mesmo content type); `deveRecusarOperacaoProtegidaDepoisDeSair` (`DELETE /sessao` → 204, depois `GET /sessao` com a mesma sessão → 401); `deveRecusarLoginSemTokenCsrf` (403); `deveConfigurarExpiracaoDaSessaoEmTrintaMinutos` (`server.servlet.session.timeout` = 30m no ambiente); `naoDeveExporPaginaDeLoginPadrao` (`GET /login` ≠ 200 com HTML); e, numa classe separada `src/test/java/br/com/marden/habitattio/SessaoCookieIntegracaoTest.java` (`@SpringBootTest(webEnvironment = RANDOM_PORT)` + `@ActiveProfiles("prod")` + Testcontainers, cliente `java.net.http.HttpClient` do JDK — o MockMvc não emite `Set-Cookie` de sessão), `deveEmitirCookieDeSessaoHttpOnlySameSiteLaxESecureNoProd` (obtém `XSRF-TOKEN` por `GET /sessao`, cadastra, entra por `POST /sessao`; o `Set-Cookie` de `JSESSIONID` traz `HttpOnly`, `SameSite=Lax` e `Secure`); de volta em `SessaoIntegracaoTest`: `naoDeveRegistrarASenhaNoLog` (`@ExtendWith(OutputCaptureExtension.class)`: cadastro, login com senha certa e com senha errada; a saída capturada não contém a senha usada — SC-004)
- [ ] T035 [US2] Rodar `./mvnw test -Dtest='AutenticarProprietarioUseCaseTest,ProprietarioAuthenticationProviderTest,SessaoControllerTest,SessaoIntegracaoTest,SessaoCookieIntegracaoTest'` (arquivos de T031–T034 em `src/test/java/br/com/marden/habitattio/`) e confirmar falha pelo motivo esperado (`UnsupportedOperationException`, rota ou configuração ausente), não por erro de compilação

### Implementação para US2

- [ ] T036 [P] [US2] Implementar `src/main/java/br/com/marden/habitattio/domain/exception/CredenciaisInvalidasException.java` ("E-mail ou senha inválidos.") e o `toString()` mascarado de `src/main/java/br/com/marden/habitattio/ports/inbound/AutenticarProprietarioCommand.java`
- [ ] T037 [US2] Implementar `src/main/java/br/com/marden/habitattio/application/usecase/AutenticarProprietarioUseCase.java` (`UsuarioRepositoryPort`, `CodificadorSenhaPort` por construtor; hash fictício calculado uma vez no construtor; normaliza e-mail com `Usuario.normalizarEmail`; e-mail inexistente → `confere(senha, hashFicticio)` e depois `CredenciaisInvalidasException`; senha errada → mesma exceção)
- [ ] T038 [US2] Implementar `src/main/java/br/com/marden/habitattio/adapters/inbound/security/ProprietarioAuthenticationProvider.java` (traduz `UsernamePasswordAuthenticationToken` → command → `UsernamePasswordAuthenticationToken.authenticated(new ProprietarioAutenticado(...), null, List.of())`; `CredenciaisInvalidasException` → `BadCredentialsException`)
- [ ] T039 [US2] Estender `src/main/java/br/com/marden/habitattio/adapters/config/SegurancaConfiguration.java`: `formLogin` com `loginProcessingUrl("/sessao")`, `usernameParameter("email")`, `passwordParameter("senha")`, `loginPage("/sessao")` (sem página HTML gerada), sucesso 204 e falha 401 "E-mail ou senha inválidos." via `RespostasSeguranca`; `logout` com `PathPatternRequestMatcher` em `DELETE /sessao` e `HttpStatusReturningLogoutSuccessHandler(NO_CONTENT)`; `POST /sessao` liberado; `GET /sessao` e `DELETE /sessao` autenticados; `AuthenticationEntryPoint` com 401 "Autenticação necessária." delegando ao `HandlerExceptionResolver` (movido da US1, análise C2). Acrescentar os mapeamentos de `AuthenticationException` (entry point, "Autenticação necessária.") e de `BadCredentialsException` (falha de login, "E-mail ou senha inválidos.") → 401 em `src/main/java/br/com/marden/habitattio/adapters/inbound/rest/TratamentoErrosHandler.java` e os handlers em `src/main/java/br/com/marden/habitattio/adapters/inbound/security/RespostasSeguranca.java`
- [ ] T040 [US2] Implementar `src/main/java/br/com/marden/habitattio/adapters/inbound/rest/SessaoController.java` (`GET /sessao` → `@AuthenticationPrincipal ProprietarioAutenticado` → `SessaoResponse(id, nome, email)` aninhado)
- [ ] T041 [US2] Registrar em `src/main/java/br/com/marden/habitattio/adapters/config/BeanConfiguration.java` os beans `AutenticarProprietarioUseCase` e `AuthenticationProvider` (`ProprietarioAuthenticationProvider`); acrescentar em `src/main/resources/application.properties` `server.servlet.session.timeout=30m` e `server.servlet.session.cookie.same-site=lax`, e em `src/main/resources/application-prod.properties` `server.servlet.session.cookie.secure=true`
- [ ] T042 [US2] Rodar `./mvnw test` e confirmar verde

**Checkpoint**: US1 + US2 — proprietário se cadastra, entra e sai.

---

## Phase 5: User Story 3 — Cada proprietário só enxerga e altera os próprios imóveis (Priority: P1)

**Goal**: imóvel com dono (da sessão) e identificador único por dono sem distinguir caixa;
`GET /imoveis/{id}` e `POST /imoveis/{id}/inativacao` só para o dono; imóvel alheio = 404 idêntico a
inexistente; D3 resolvido no cadastro de imóvel.

**Independent Test**: Ana e Bruno, um imóvel cada; cada um consulta e tenta inativar o do outro →
404 e nada muda; no próprio funciona; "Apto 101" repetido para a Ana → 409, para o Bruno → 201;
valor sugerido 0 → 400 (antes 500).

**Depende de**: US2 (o dono vem da sessão).

### Testes para US3 (escrever primeiro, ver falhar)

- [ ] T043 [US3] Desenhar só a forma: novos construtores de `src/main/java/br/com/marden/habitattio/domain/model/Imovel.java` — `Imovel(Long id, Long usuarioId, String identificador, String endereco, BigDecimal valorSugerido)` e `(..., boolean ativo)` — com getters `getUsuarioId()`/`getIdentificador()` e `mesmoIdentificador(String)`, **substituindo** os construtores atuais; `src/main/java/br/com/marden/habitattio/domain/exception/IdentificadorImovelDuplicadoException.java` e `ImovelNaoEncontradoException.java` no mesmo pacote; `src/main/java/br/com/marden/habitattio/ports/inbound/CadastrarImovelCommand.java` passa a `(Long usuarioId, String identificador, String endereco, BigDecimal valorSugerido)`; `ConsultarImovelCommand` e `InativarImovelCommand` (`usuarioId`, `imovelId`) em `src/main/java/br/com/marden/habitattio/ports/inbound/`; `existeIdentificador(Long usuarioId, String identificador)` e `Optional<Imovel> buscarDoProprietario(Long imovelId, Long usuarioId)` em `src/main/java/br/com/marden/habitattio/ports/outbound/ImovelRepositoryPort.java`; `ConsultarImovelUseCase` e `InativarImovelUseCase` em `src/main/java/br/com/marden/habitattio/application/usecase/`. Ajustar as chamadas de produção existentes (`CadastrarImovelUseCase`, `ImovelRepositoryAdapter`, `ImovelController`) só para compilar, com `throw new UnsupportedOperationException()` nos métodos novos
- [ ] T044 [US3] Adaptar **só os dados** (autorizado, Q2) de `src/test/java/br/com/marden/habitattio/domain/model/ImovelTest.java`: toda chamada `new Imovel(1L, endereco, valor)` passa a `new Imovel(1L, 1L, "Apto 101", endereco, valor)`; nenhuma asserção ou nome muda
- [ ] T045 [US3] Adaptar `src/test/java/br/com/marden/habitattio/application/usecase/CadastrarImovelUseCaseTest.java` (autorizado, Q2 + P1): o fake privado constrói `Imovel` com `usuarioId`/`identificador` e **ganha** `existeIdentificador` e `buscarDoProprietario` (implementação simples sobre o `Map`); os commands dos dois testes passam a `new CadastrarImovelCommand(1L, "Apto 101", ...)`. Nenhuma asserção ou nome muda
- [ ] T046 [US3] Adaptar **só os dados** de `src/test/java/br/com/marden/habitattio/adapters/outbound/persistence/ImovelRepositoryAdapterTest.java` (autorizado, Q2): `new Imovel(null, 1L, "Apto 101", "Rua das Flores, 123", ...)`. Nenhuma asserção ou nome muda
- [ ] T047 [P] [US3] **Acrescentar** a `src/test/java/br/com/marden/habitattio/domain/model/ImovelTest.java` (testes novos, depois da adaptação de T044): `deveRejeitarImovelSemProprietario` ("O imóvel deve pertencer a um proprietário."); `deveRejeitarIdentificadorNulo`, `deveRejeitarIdentificadorVazio`, `deveRejeitarIdentificadorSoComEspacos` ("O identificador do imóvel não pode ser vazio."); `deveRejeitarIdentificadorComMaisDeCinquentaCaracteres` ("O identificador do imóvel deve ter no máximo 50 caracteres."); `deveAceitarIdentificadorComCinquentaCaracteresAcentuados`; `deveRemoverEspacosNasPontasDoIdentificador` (`" Apto 101 "` → `"Apto 101"`, grafia mantida); `deveConsiderarMesmoIdentificadorIgnorandoCaixaEEspacos`; `naoDeveConsiderarMesmoIdentificadorComAcentoDiferente` ("Térreo" × "Terreo"); `deveManterInativoAoInativarDeNovo`
- [ ] T048 [P] [US3] Criar `src/test/java/br/com/marden/habitattio/fakes/ImovelRepositoryFake.java` (Map em memória; `existeIdentificador` via `Imovel.mesmoIdentificador` filtrando pelo dono; `buscarDoProprietario` vazio para id inexistente **ou** de outro dono; opção `simularCorridaNoProximoSalvar()` → `IdentificadorImovelDuplicadoException`)
- [ ] T049 [P] [US3] Criar `src/test/java/br/com/marden/habitattio/application/usecase/CadastrarImovelComDonoUseCaseTest.java` (fake de T048): `deveCadastrarImovelParaODonoDoCommand`; `deveRejeitarIdentificadorRepetidoParaOMesmoDonoIgnorandoCaixa` (`IdentificadorImovelDuplicadoException`, "Você já tem um imóvel com o identificador 'Apto 101'.", nada salvo); `deveAceitarMesmoIdentificadorParaDonosDiferentes`; `devePropagarDuplicidadeQuandoORepositorioDetectaCorrida`; `deveRejeitarValorSugeridoZero`
- [ ] T050 [P] [US3] Criar `src/test/java/br/com/marden/habitattio/application/usecase/ConsultarImovelUseCaseTest.java`: `deveRetornarImovelDoProprio`; `deveLancarNaoEncontradoParaImovelDeOutroDono` ("Imóvel não encontrado."); `deveLancarNaoEncontradoParaIdInexistente` (mesma exceção e mensagem)
- [ ] T051 [P] [US3] Criar `src/test/java/br/com/marden/habitattio/application/usecase/InativarImovelUseCaseTest.java`: `deveInativarImovelDoProprio`; `deveSerIdempotenteAoInativarImovelJaInativo`; `deveLancarNaoEncontradoEManterAtivoImovelDeOutroDono` (o imóvel do outro continua `ativo` no fake); `deveLancarNaoEncontradoParaIdInexistente`
- [ ] T052 [P] [US3] Criar `src/test/java/br/com/marden/habitattio/adapters/outbound/persistence/ImovelRepositoryAdapterDonoTest.java` (`@DataJpaTest` + `@ActiveProfiles("prod")` + Testcontainers; proprietários Ana e Bruno inseridos antes): `deveSalvarERecuperarDonoEIdentificador`; `deveBuscarImovelDoProprio`; `naoDeveEncontrarImovelDeOutroDono`; `deveInformarIdentificadorExistenteIgnorandoCaixa` ("APTO 101" × "Apto 101", mesmo dono → true; outro dono → false); `deveTraduzirViolacaoDeIdentificadorUnicoParaDuplicidade` (salvar "Apto 101" e "apto 101" do mesmo dono direto pelo adapter → `IdentificadorImovelDuplicadoException`)
- [ ] T053 [P] [US3] Criar `src/test/java/br/com/marden/habitattio/adapters/inbound/rest/ImovelControllerTest.java` (`@WebMvcTest(ImovelController.class)` + `@Import(SegurancaConfiguration)` + `@TestConfiguration` com casos de uso reais sobre `ImovelRepositoryFake`; `with(authentication(...))` com `ProprietarioAutenticado`; `csrf()`): `deveCadastrarImovelParaODonoDaSessaoERetornar201` (`Location: /imoveis/{id}`, corpo `{id, identificador, endereco, valorSugerido, ativo: true}`); `deveIgnorarDonoInformadoNoCorpo` (campo `usuarioId: 99` no JSON ignorado; dono = sessão); `deveRetornar400ParaValorSugeridoZero` (D3: não 500); `deveRetornar400ParaIdentificadorEmBranco`; `deveRetornar409ParaIdentificadorRepetido`; `deveRetornar401SemSessao` (nada gravado no fake); `deveRetornar403SemTokenCsrf`; `deveConsultarImovelProprio` (200 com `ativo`); `deveRetornar404ParaImovelDeOutroDono` e `deveRetornar404ParaIdInexistente` (corpos idênticos, "Imóvel não encontrado."); `deveInativarImovelProprio` (200, `ativo: false`, de novo 200); `deveRetornar404AoInativarImovelDeOutroDono`; `deveRetornar400ParaIdNaoNumerico` ("Identificador de imóvel inválido.")
- [ ] T054 [P] [US3] Criar `src/test/java/br/com/marden/habitattio/IsolamentoProprietariosIntegracaoTest.java` (`@SpringBootTest` + `@AutoConfigureMockMvc` + `@ActiveProfiles("prod")` + Testcontainers; login real de Ana e Bruno por `POST /sessao`): `naoDeveRevelarNemAlterarImoveisDeOutroProprietario` (SC-002: Bruno percorre `GET /imoveis/{id}` e `POST /imoveis/{id}/inativacao` para todos os ids existentes e vizinhos, `min−2 … max+2`, e só obtém 200 nos próprios; imóveis da Ana continuam ativos); `naoDeveExecutarOperacaoDeImovelSemAutenticacao` (SC-003: as três rotas de imóvel sem sessão → 401 e nenhuma linha nova); `deveAceitarMesmoIdentificadorParaDonosDiferentes`
- [ ] T055 [US3] Rodar `./mvnw test -Dtest='ImovelTest,CadastrarImovelUseCaseTest,CadastrarImovelComDonoUseCaseTest,ConsultarImovelUseCaseTest,InativarImovelUseCaseTest,ImovelRepositoryAdapterTest,ImovelRepositoryAdapterDonoTest,ImovelControllerTest,IsolamentoProprietariosIntegracaoTest'` (arquivos de T044–T054 em `src/test/java/br/com/marden/habitattio/`): os testes adaptados (T044–T046) continuam verdes e os novos falham pelo motivo esperado

### Implementação para US3

- [ ] T056 [US3] Implementar `src/main/java/br/com/marden/habitattio/domain/model/Imovel.java`: `usuarioId` obrigatório ("O imóvel deve pertencer a um proprietário."); `identificador` com `strip()` e então obrigatório, não em branco ("O identificador do imóvel não pode ser vazio."), ≤ 50 code points ("O identificador do imóvel deve ter no máximo 50 caracteres."); `mesmoIdentificador` (strip + `equalsIgnoreCase`, acentos distinguem); regras de endereço e valor do módulo 1 intactas
- [ ] T057 [P] [US3] Implementar `src/main/java/br/com/marden/habitattio/domain/exception/IdentificadorImovelDuplicadoException.java` ("Você já tem um imóvel com o identificador '<identificador>'.") e `src/main/java/br/com/marden/habitattio/domain/exception/ImovelNaoEncontradoException.java` ("Imóvel não encontrado.", sem id nem dono)
- [ ] T058 [US3] Implementar `src/main/java/br/com/marden/habitattio/application/usecase/CadastrarImovelUseCase.java` (`new Imovel(null, usuarioId, identificador, ...)` → `existeIdentificador` → `IdentificadorImovelDuplicadoException` → `salvar`), `ConsultarImovelUseCase.java` (`buscarDoProprietario` ou `ImovelNaoEncontradoException`) e `InativarImovelUseCase.java` (`buscarDoProprietario` → `inativar()` → `salvar`) em `src/main/java/br/com/marden/habitattio/application/usecase/`
- [ ] T059 [US3] Alterar `src/main/java/br/com/marden/habitattio/adapters/outbound/persistence/entity/ImovelJpaEntity.java` (`usuarioId` `Long` com `@Column(name = "usuario_id", nullable = false)`, sem `@ManyToOne`; `identificador` `@Column(nullable = false, length = 50)`; `@Table(name = "imoveis", uniqueConstraints = @UniqueConstraint(name = "uq_imoveis_usuario_identificador", columnNames = {"usuario_id", "identificador"}))`) e `src/main/java/br/com/marden/habitattio/adapters/outbound/persistence/SpringDataImovelRepository.java` (`findByIdAndUsuarioId`, `existsByUsuarioIdAndIdentificadorIgnoreCase`)
- [ ] T060 [US3] Implementar em `src/main/java/br/com/marden/habitattio/adapters/outbound/persistence/ImovelRepositoryAdapter.java` o mapeamento dos campos novos nas duas direções, `existeIdentificador`, `buscarDoProprietario` e a tradução de `DataIntegrityViolationException` citando `uq_imoveis_usuario_identificador` (sem distinguir caixa) → `IdentificadorImovelDuplicadoException`
- [ ] T061 [US3] Alterar `src/main/java/br/com/marden/habitattio/adapters/inbound/rest/ImovelController.java`: `@AuthenticationPrincipal ProprietarioAutenticado` → `usuarioId` no command; `POST /imoveis` 201 com `Location`; `GET /imoveis/{id}`; `POST /imoveis/{id}/inativacao`; `CadastrarImovelRequest(identificador, endereco, valorSugerido)` (sem campo de dono) e `ImovelResponse(id, identificador, endereco, valorSugerido, ativo)` aninhados
- [ ] T062 [US3] Acrescentar a `src/main/java/br/com/marden/habitattio/adapters/inbound/rest/TratamentoErrosHandler.java`: `ImovelNaoEncontradoException` → 404; `IdentificadorImovelDuplicadoException` → 409; `MethodArgumentTypeMismatchException` → 400 "Identificador de imóvel inválido."
- [ ] T063 [US3] Registrar em `src/main/java/br/com/marden/habitattio/adapters/config/BeanConfiguration.java` os beans `ConsultarImovelUseCase` e `InativarImovelUseCase`
- [ ] T064 [US3] Rodar `./mvnw test` e confirmar verde; conferir no diff que T044–T046 não mudaram asserções nem nomes de teste

**Checkpoint**: todas as histórias entregues; isolamento provado ponta a ponta.

---

## Phase 6: Polish & Cross-Cutting

- [ ] T065 [P] Atualizar `AGENTS.md` (FR-027): §2 com `domain/exception`, `adapters/inbound/security`, `adapters/outbound/seguranca` e `SegurancaConfiguration`; §6 com `Usuario` = proprietário (não "operador do sistema"), dizendo explicitamente que "proprietário" (negócio), `Usuario` (classe) e `usuarios` (tabela) são o mesmo conceito, `Imovel` com `usuarioId` e `identificador`, `Senha`, e a lista de "Dívida restante" sem `Usuario`; §7 com "imóvel pertence a um proprietário; identificador único por dono sem distinguir caixa" e "toda consulta e escrita filtra pelo proprietário autenticado; imóvel de outro dono responde como inexistente"; §9 com Spring Security no stack
- [ ] T066 Emendar `.specify/memory/constitution.md` para 1.0.2 (PATCH) via `/speckit-constitution`: Spring Security na seção "Stack Tecnológico e Restrições" e na lista do Princípio IV, com Sync Impact Report; mesma mudança sincroniza `AGENTS.md` §9 (T065)
- [ ] T067 [P] Atualizar `docs/requisitos-e-der.md`: RF-01, RF-02, RF-03, RF-04 (só imóveis), RF-07, RF-08, RNF-11, RNF-12, RNF-15 → ✅ ou 🟡 conforme o escopo entregue; D3, D6 e D7 marcados como resolvidos pela spec 003; DER com `senha_hash varchar(60)` e a nota da research R9; §8 passo 2 e 4 concluídos; registrar como lacuna nova (§4) e próximo passo (§8) a feature à parte de **tratamento global de exceções genéricas** (resposta 500 padronizada), decidida na análise da spec 003 (C1)
- [ ] T068 Verificar as fronteiras: `grep -rE "import (org\.springframework|jakarta\.persistence)" src/main/java/br/com/marden/habitattio/domain src/main/java/br/com/marden/habitattio/application` sem resultado; nenhum `*JpaEntity` fora de `adapters/outbound/persistence`; nenhum `*Request`/`*Response` fora dos controllers
- [ ] T069 Rodar `./mvnw verify` completo; o relatório diz explicitamente se os testes de integração (Docker) rodaram
- [ ] T070 Executar o roteiro manual de [quickstart.md](./quickstart.md) (seções 2 e 3), incluindo procurar a senha de teste no console da aplicação (SC-004), e registrar o resultado
- [ ] T071 Marcar no `specs/003-proprietario-login-isolamento/checklists/requirements.md` a conferência pós-implementação e atualizar `spec.md` se algum comportamento tiver mudado no caminho

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: sem dependência.
- **Foundational / US4 (Phase 2)**: depende do Setup; **bloqueia todas as histórias**.
- **US1 (Phase 3)**: depende da Phase 2.
- **US2 (Phase 4)**: depende da US1 (proprietário persistido, `SegurancaConfiguration`, `TratamentoErrosHandler`).
- **US3 (Phase 5)**: depende da US2 (dono vem da sessão; `ProprietarioAutenticado`).
- **Polish (Phase 6)**: depende das histórias concluídas.

As três histórias P1 formam uma cadeia (cadastro → login → isolamento) porque cada uma usa o
produto da anterior; dentro de cada fase há bastante paralelismo.

### Within Each User Story

1. Forma (portas, commands, assinaturas) → 2. testes → 3. ver falhar → 4. implementação de dentro
para fora (domínio → caso de uso → persistência → web) → 5. fiação → 6. suíte verde.

### Parallel Opportunities

- Phase 2: T003 ∥ T004.
- US1: T009 ∥ T010 ∥ T011; depois de T012, T013 ∥ T014 ∥ T015 ∥ T016; na implementação T018 ∥ T019 ∥ T020 ∥ T022 ∥ T023.
- US2: T031 ∥ T032 ∥ T033 ∥ T034.
- US3: T047 ∥ T048 ∥ T049 ∥ T050 ∥ T051 ∥ T052 ∥ T053 ∥ T054 (depois de T043–T046).
- Polish: T065 ∥ T067.

---

## Parallel Example: User Story 1

```bash
# Testes de US1 em paralelo, depois da forma (T012):
Task: "T013 CadastrarProprietarioUseCaseTest em src/test/java/.../application/usecase/"
Task: "T014 BCryptCodificadorSenhaAdapterTest em src/test/java/.../adapters/outbound/seguranca/"
Task: "T015 UsuarioRepositoryAdapterTest em src/test/java/.../adapters/outbound/persistence/"
Task: "T016 ProprietarioControllerTest em src/test/java/.../adapters/inbound/rest/"

# Implementação de domínio e adapters independentes em paralelo:
Task: "T018 Senha"  |  Task: "T019 Usuario"  |  Task: "T022 BCryptCodificadorSenhaAdapter"  |  Task: "T023 UsuarioJpaEntity + SpringDataUsuarioRepository"
```

## Parallel Example: User Story 3

```bash
# Depois da forma (T043) e das adaptações autorizadas (T044–T046):
Task: "T047 testes novos de ImovelTest"
Task: "T049 CadastrarImovelComDonoUseCaseTest"  |  Task: "T050 ConsultarImovelUseCaseTest"  |  Task: "T051 InativarImovelUseCaseTest"
Task: "T052 ImovelRepositoryAdapterDonoTest"    |  Task: "T053 ImovelControllerTest"        |  Task: "T054 IsolamentoProprietariosIntegracaoTest"
```

---

## Implementation Strategy

### MVP

Phases 1 → 2 → 3: o banco de produção já garante a estrutura (US4) e proprietários podem se
cadastrar (US1). Parar e validar com a seção 2, passos 3–5, do quickstart.

### Incremental

1. Setup + Foundational (US4) → estrutura pronta.
2. US1 → cadastro de proprietário (MVP).
3. US2 → login e logout.
4. US3 → imóveis com dono e isolamento (entrega o valor central do módulo e resolve D3).
5. Polish → documentação, constitution, verificação final.

Cada fase termina com `./mvnw test` verde. Tarefas de teste MUST ser vistas falhando antes da
implementação correspondente; nenhum teste visto falhar é editado depois (AGENTS §3).
