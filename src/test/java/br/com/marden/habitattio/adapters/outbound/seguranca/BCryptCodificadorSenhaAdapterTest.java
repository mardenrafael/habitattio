package br.com.marden.habitattio.adapters.outbound.seguranca;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class BCryptCodificadorSenhaAdapterTest {

	private final BCryptCodificadorSenhaAdapter codificador = new BCryptCodificadorSenhaAdapter(4);

	@Test
	void deveGerarHashBCryptDeSessentaCaracteres() {
		var hash = codificador.codificar("senha-segura-123");

		assertTrue(hash.matches("^\\$2[aby]\\$04\\$.{53}$"), "Hash fora do formato BCrypt: " + hash);
	}

	@Test
	void deveGerarHashesDiferentesParaAMesmaSenha() {
		var primeiro = codificador.codificar("senha-segura-123");
		var segundo = codificador.codificar("senha-segura-123");

		assertNotEquals(primeiro, segundo);
	}

	@Test
	void deveConferirSenhaCorreta() {
		var hash = codificador.codificar("senha-segura-123");

		assertTrue(codificador.confere("senha-segura-123", hash));
	}

	@Test
	void deveRecusarSenhaErrada() {
		var hash = codificador.codificar("senha-segura-123");

		assertFalse(codificador.confere("outra-senha-456", hash));
	}

	@Test
	void deveDevolverFalsoEmVezDeLancarParaSenhaComMaisDe72Bytes() {
		var hash = codificador.codificar("senha-segura-123");
		var senhaLonga = "a".repeat(73);

		assertFalse(codificador.confere(senhaLonga, hash));
	}
}
