package br.com.marden.habitattio.adapters.outbound.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
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
class ImoveisPostgresConstraintsTest {

	private static final String INSERT = "INSERT INTO imoveis (endereco, valor_sugerido, ativo) VALUES (:e, :v, true)";

	private final JdbcClient jdbc;

	@Autowired
	ImoveisPostgresConstraintsTest(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	private void inserir(String endereco, BigDecimal valor) {
		jdbc.sql(INSERT).param("e", endereco).param("v", valor).update();
	}

	private long contarPorEndereco(String endereco) {
		return jdbc.sql("SELECT COUNT(*) FROM imoveis WHERE endereco = :e")
				.param("e", endereco)
				.query(Long.class)
				.single();
	}

	private BigDecimal lerValorSugerido(String endereco) {
		return jdbc.sql("SELECT valor_sugerido FROM imoveis WHERE endereco = :e")
				.param("e", endereco)
				.query(BigDecimal.class)
				.single();
	}

	private void assertMensagemContem(Exception excecao, String trecho) {
		assertTrue(excecao.getMessage().contains(trecho),
				"Mensagem deveria conter '" + trecho + "', mas foi: " + excecao.getMessage());
	}

	// G1
	@Test
	void deveRejeitarEnderecoNulo() {
		var sql = "INSERT INTO imoveis (endereco, valor_sugerido, ativo) VALUES (NULL, 1500.00, true)";

		var executar = (Executable) () -> jdbc.sql(sql).update();

		assertThrows(DataIntegrityViolationException.class, executar);
	}

	// G2
	@Test
	void deveRejeitarEnderecoVazio() {
		var valor = new BigDecimal("1500.00");

		var excecao = assertThrows(DataIntegrityViolationException.class, () -> inserir("", valor));

		assertMensagemContem(excecao, "ck_imoveis_endereco_nao_vazio");
	}

	// G2
	@Test
	void deveRejeitarEnderecoSoComEspacos() {
		var valor = new BigDecimal("1500.00");

		var excecao = assertThrows(DataIntegrityViolationException.class, () -> inserir("   ", valor));

		assertMensagemContem(excecao, "ck_imoveis_endereco_nao_vazio");
	}

	// G2
	@Test
	void deveRejeitarEnderecoSoComTabEQuebraDeLinha() {
		var valor = new BigDecimal("1500.00");

		var excecao = assertThrows(DataIntegrityViolationException.class, () -> inserir("\t\n", valor));

		assertMensagemContem(excecao, "ck_imoveis_endereco_nao_vazio");
	}

	// G2
	@Test
	void deveRejeitarEnderecoSoComRetornoDeCarroFormFeedETabVertical() {
		var valor = new BigDecimal("1500.00");

		var excecao = assertThrows(DataIntegrityViolationException.class, () -> inserir("\r\f\u000B", valor));

		assertMensagemContem(excecao, "ck_imoveis_endereco_nao_vazio");
	}

	// G3
	@Test
	void deveRejeitarEnderecoCom256Caracteres() {
		var endereco = "a".repeat(256);
		var valor = new BigDecimal("1500.00");

		var executar = (Executable) () -> inserir(endereco, valor);

		assertThrows(DataIntegrityViolationException.class, executar);
	}

	// G4
	@Test
	void deveAceitarEnderecoCom255Caracteres() {
		var endereco = "a".repeat(255);

		inserir(endereco, new BigDecimal("1500.00"));

		assertEquals(1L, contarPorEndereco(endereco));
	}

	// G4b
	@Test
	void deveAceitarEnderecoCom255CaracteresMultibyte() {
		var endereco = "ã".repeat(254) + "🏠";

		inserir(endereco, new BigDecimal("1500.00"));

		assertEquals(255, endereco.codePointCount(0, endereco.length()));
		assertEquals(1L, contarPorEndereco(endereco));
	}

	// G5
	@Test
	void deveRejeitarValorSugeridoNulo() {
		var sql = "INSERT INTO imoveis (endereco, valor_sugerido, ativo) VALUES ('Rua A, 100', NULL, true)";

		var executar = (Executable) () -> jdbc.sql(sql).update();

		assertThrows(DataIntegrityViolationException.class, executar);
	}

	// G6
	@Test
	void deveRejeitarValorSugeridoZero() {
		var valor = BigDecimal.ZERO;

		var excecao = assertThrows(DataIntegrityViolationException.class, () -> inserir("Rua A, 100", valor));

		assertMensagemContem(excecao, "ck_imoveis_valor_sugerido_positivo");
	}

	// G6
	@Test
	void deveRejeitarValorSugeridoNegativo() {
		var valor = new BigDecimal("-1500.00");

		var excecao = assertThrows(DataIntegrityViolationException.class, () -> inserir("Rua A, 100", valor));

		assertMensagemContem(excecao, "ck_imoveis_valor_sugerido_positivo");
	}

	// G7
	@Test
	void deveRejeitarValorSugeridoQueArredondaParaZero() {
		var valor = new BigDecimal("0.00001");

		var excecao = assertThrows(DataIntegrityViolationException.class, () -> inserir("Rua A, 100", valor));

		assertMensagemContem(excecao, "ck_imoveis_valor_sugerido_positivo");
	}

	// G8
	@Test
	void deveArredondarValorSugeridoParaQuatroCasas() {
		var endereco = "Rua G8, 8";
		inserir(endereco, new BigDecimal("1500.12345"));

		var lido = lerValorSugerido(endereco);

		assertEquals(0, lido.compareTo(new BigDecimal("1500.1235")), "valor lido: " + lido);
		assertEquals(4, lido.scale(), "valor lido: " + lido);
	}

	// G9
	@Test
	void deveGuardarValorSugeridoComQuatroCasasExatamente() {
		var endereco = "Rua G9, 9";
		inserir(endereco, new BigDecimal("1500.1234"));

		var lido = lerValorSugerido(endereco);

		assertEquals(0, lido.compareTo(new BigDecimal("1500.1234")), "valor lido: " + lido);
	}

	// G10
	@Test
	void deveAceitarDoisImoveisComMesmoEndereco() {
		var endereco = "Rua A, 100, apto 101";

		inserir(endereco, new BigDecimal("1500.00"));
		inserir(endereco, new BigDecimal("1600.00"));

		assertEquals(2L, contarPorEndereco(endereco));
	}

	// G11
	@Test
	void deveRejeitarValorSugeridoAcimaDaCapacidade() {
		var valor = new BigDecimal("1000000000000000");

		var executar = (Executable) () -> inserir("Rua G11, 11", valor);

		assertThrows(DataIntegrityViolationException.class, executar);
	}

}
