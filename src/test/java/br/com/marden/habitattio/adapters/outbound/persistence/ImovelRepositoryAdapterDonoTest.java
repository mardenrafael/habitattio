package br.com.marden.habitattio.adapters.outbound.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;

import br.com.marden.habitattio.TestcontainersConfiguration;
import br.com.marden.habitattio.domain.exception.IdentificadorImovelDuplicadoException;
import br.com.marden.habitattio.domain.model.Imovel;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("prod")
@Import(TestcontainersConfiguration.class)
class ImovelRepositoryAdapterDonoTest {

	private static final String HASH = "$2a$10$abcdefghijklmnopqrstuuABCDEFGHIJKLMNOPQRSTUVWXYZ01234";

	@Autowired
	private SpringDataImovelRepository springDataRepository;

	@Autowired
	private JdbcClient jdbc;

	private ImovelRepositoryAdapter adapter;
	private Long anaId;
	private Long brunoId;

	@BeforeEach
	void prepararDonosEAdapter() {
		adapter = new ImovelRepositoryAdapter(springDataRepository);
		anaId = inserirProprietario("Ana");
		brunoId = inserirProprietario("Bruno");
	}

	private Long inserirProprietario(String nome) {
		var email = nome.toLowerCase() + "-" + UUID.randomUUID() + "@exemplo.com";
		return jdbc.sql("INSERT INTO usuarios (nome, email, senha_hash) VALUES (:n, :e, :h) RETURNING id")
				.param("n", nome).param("e", email).param("h", HASH).query(Long.class).single();
	}

	private Imovel novoImovel(Long usuarioId, String identificador) {
		return new Imovel(null, usuarioId, identificador, "Rua A, 100", new BigDecimal("1500.00"));
	}

	@Test
	void deveSalvarERecuperarDonoEIdentificador() {
		var salvo = adapter.salvar(novoImovel(anaId, "Apto 101"));

		var recuperado = adapter.buscarDoProprietario(salvo.getId(), anaId);

		assertNotNull(salvo.getId());
		assertEquals(anaId, salvo.getUsuarioId());
		assertEquals("Apto 101", salvo.getIdentificador());
		assertTrue(recuperado.isPresent());
		assertEquals(anaId, recuperado.get().getUsuarioId());
		assertEquals("Apto 101", recuperado.get().getIdentificador());
		assertEquals("Rua A, 100", recuperado.get().getEndereco());
	}

	@Test
	void deveBuscarImovelDoProprio() {
		var salvo = adapter.salvar(novoImovel(anaId, "Apto 101"));

		var encontrado = adapter.buscarDoProprietario(salvo.getId(), anaId);

		assertTrue(encontrado.isPresent());
		assertEquals(salvo.getId(), encontrado.get().getId());
	}

	@Test
	void naoDeveEncontrarImovelDeOutroDono() {
		var salvo = adapter.salvar(novoImovel(anaId, "Apto 101"));

		var encontrado = adapter.buscarDoProprietario(salvo.getId(), brunoId);

		assertTrue(encontrado.isEmpty());
	}

	@Test
	void deveInformarIdentificadorExistenteIgnorandoCaixaParaOMesmoDono() {
		adapter.salvar(novoImovel(anaId, "Apto 101"));

		var existe = adapter.existeIdentificador(anaId, "APTO 101");

		assertTrue(existe);
	}

	@Test
	void naoDeveInformarIdentificadorExistenteParaOutroDono() {
		adapter.salvar(novoImovel(anaId, "Apto 101"));

		var existe = adapter.existeIdentificador(brunoId, "APTO 101");

		assertFalse(existe);
	}

	@Test
	void deveTraduzirViolacaoDeIdentificadorUnicoParaDuplicidade() {
		adapter.salvar(novoImovel(anaId, "Apto 101"));

		assertThrows(IdentificadorImovelDuplicadoException.class,
				() -> adapter.salvar(novoImovel(anaId, "apto 101")));
	}
}
