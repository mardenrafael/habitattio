package br.com.marden.habitattio.adapters.outbound.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;

import br.com.marden.habitattio.TestcontainersConfiguration;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("prod")
@Import(TestcontainersConfiguration.class)
class UsuariosEDonoPostgresConstraintsTest {

	private static final String HASH = "$2a$10$abcdefghijklmnopqrstuuABCDEFGHIJKLMNOPQRSTUVWXYZ01234";

	private final JdbcClient jdbc;

	@Autowired
	UsuariosEDonoPostgresConstraintsTest(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	private void inserirUsuario(String nome, String email, String senhaHash) {
		jdbc.sql("INSERT INTO usuarios (nome, email, senha_hash) VALUES (:n, :e, :h)")
				.param("n", nome).param("e", email).param("h", senhaHash).update();
	}

	private long criarUsuario(String email) {
		return jdbc.sql("INSERT INTO usuarios (nome, email, senha_hash) VALUES ('Dono', :e, :h) RETURNING id")
				.param("e", email).param("h", HASH).query(Long.class).single();
	}

	private void inserirImovel(Long usuarioId, String identificador) {
		jdbc.sql("INSERT INTO imoveis (endereco, valor_sugerido, ativo, usuario_id, identificador) "
				+ "VALUES ('Rua A, 100', 1500.00, true, :u, :i)")
				.param("u", usuarioId).param("i", identificador).update();
	}

	private void assertMensagemContem(Exception excecao, String trecho) {
		assertTrue(excecao.getMessage().contains(trecho),
				"Mensagem deveria conter '" + trecho + "', mas foi: " + excecao.getMessage());
	}

	// G-U1
	@Test
	void deveRejeitarNomeSoComEspacos() {
		var excecao = assertThrows(DataIntegrityViolationException.class,
				() -> inserirUsuario("   ", "a@exemplo.com", HASH));

		assertMensagemContem(excecao, "ck_usuarios_nome_nao_vazio");
	}

	// G-U2
	@Test
	void deveRejeitarEmailComMaiuscula() {
		var excecao = assertThrows(DataIntegrityViolationException.class,
				() -> inserirUsuario("Ana", "Ana@Exemplo.com", HASH));

		assertMensagemContem(excecao, "ck_usuarios_email_normalizado");
	}

	// G-U2
	@Test
	void deveRejeitarEmailComEspacoNasPontas() {
		var excecao = assertThrows(DataIntegrityViolationException.class,
				() -> inserirUsuario("Ana", " ana@exemplo.com ", HASH));

		assertMensagemContem(excecao, "ck_usuarios_email_normalizado");
	}

	// G-U3
	@Test
	void deveRejeitarEmailRepetido() {
		inserirUsuario("Ana", "ana@exemplo.com", HASH);

		var excecao = assertThrows(DataIntegrityViolationException.class,
				() -> inserirUsuario("Ana 2", "ana@exemplo.com", HASH));

		assertMensagemContem(excecao, "uq_usuarios_email");
	}

	// G-U4
	@Test
	void deveRejeitarSenhaHashEmTextoPuro() {
		var excecao = assertThrows(DataIntegrityViolationException.class,
				() -> inserirUsuario("Ana", "ana@exemplo.com", "segredo123"));

		assertMensagemContem(excecao, "ck_usuarios_senha_hash_bcrypt");
	}

	// G-I1
	@Test
	void deveRejeitarImovelSemDono() {
		var sql = "INSERT INTO imoveis (endereco, valor_sugerido, ativo, identificador) "
				+ "VALUES ('Rua A, 100', 1500.00, true, 'Apto 101')";

		assertThrows(DataIntegrityViolationException.class, () -> jdbc.sql(sql).update());
	}

	// G-I2
	@Test
	void deveRejeitarImovelComDonoInexistente() {
		var excecao = assertThrows(DataIntegrityViolationException.class,
				() -> inserirImovel(999999L, "Apto 101"));

		assertMensagemContem(excecao, "fk_imoveis_usuario");
	}

	// G-I3
	@Test
	void deveRejeitarIdentificadorVazio() {
		var dono = criarUsuario("dono@exemplo.com");

		var excecao = assertThrows(DataIntegrityViolationException.class, () -> inserirImovel(dono, ""));

		assertMensagemContem(excecao, "ck_imoveis_identificador_nao_vazio");
	}

	// G-I3
	@Test
	void deveRejeitarIdentificadorSoComEspacos() {
		var dono = criarUsuario("dono@exemplo.com");

		var excecao = assertThrows(DataIntegrityViolationException.class, () -> inserirImovel(dono, "   "));

		assertMensagemContem(excecao, "ck_imoveis_identificador_nao_vazio");
	}

	// G-I3
	@Test
	void deveRejeitarIdentificadorComEspacoNasPontas() {
		var dono = criarUsuario("dono@exemplo.com");

		var excecao = assertThrows(DataIntegrityViolationException.class, () -> inserirImovel(dono, " Apto 101"));

		assertMensagemContem(excecao, "ck_imoveis_identificador_sem_espacos_nas_pontas");
	}

	// G-I4
	@Test
	void deveRejeitarIdentificadorCom51Caracteres() {
		var dono = criarUsuario("dono@exemplo.com");
		var identificador = "a".repeat(51);

		assertThrows(DataIntegrityViolationException.class, () -> inserirImovel(dono, identificador));
	}

	// G-I4
	@Test
	void deveAceitarIdentificadorCom50CaracteresAcentuados() {
		var dono = criarUsuario("dono@exemplo.com");
		var identificador = "ã".repeat(50);

		inserirImovel(dono, identificador);

		var total = jdbc.sql("SELECT COUNT(*) FROM imoveis WHERE identificador = :i")
				.param("i", identificador).query(Long.class).single();
		assertEquals(1L, total);
	}

	// G-I5
	@Test
	void deveRejeitarIdentificadorRepetidoSemDistinguirCaixaParaMesmoDono() {
		var dono = criarUsuario("dono@exemplo.com");
		inserirImovel(dono, "Apto 101");

		var excecao = assertThrows(DataIntegrityViolationException.class, () -> inserirImovel(dono, "APTO 101"));

		assertMensagemContem(excecao, "uq_imoveis_usuario_identificador");
	}

	// G-I6
	@Test
	void deveAceitarMesmoIdentificadorParaDonosDiferentes() {
		var donoA = criarUsuario("a@exemplo.com");
		var donoB = criarUsuario("b@exemplo.com");

		inserirImovel(donoA, "Apto 101");
		inserirImovel(donoB, "Apto 101");

		var total = jdbc.sql("SELECT COUNT(*) FROM imoveis WHERE identificador = 'Apto 101'")
				.query(Long.class).single();
		assertEquals(2L, total);
	}

	// G-I7
	@Test
	void deveRejeitarExclusaoDeUsuarioComImovel() {
		var dono = criarUsuario("dono@exemplo.com");
		inserirImovel(dono, "Apto 101");

		var excecao = assertThrows(DataIntegrityViolationException.class,
				() -> jdbc.sql("DELETE FROM usuarios WHERE id = :id").param("id", dono).update());

		assertMensagemContem(excecao, "fk_imoveis_usuario");
	}

}
