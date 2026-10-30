package br.com.marden.habitattio.adapters.outbound.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import br.com.marden.habitattio.TestcontainersConfiguration;
import br.com.marden.habitattio.domain.exception.EmailJaCadastradoException;
import br.com.marden.habitattio.domain.model.Usuario;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("prod")
@Import(TestcontainersConfiguration.class)
class UsuarioRepositoryAdapterTest {

	private static final String HASH_BCRYPT = "$2a$10$abcdefghijklmnopqrstuuABCDEFGHIJKLMNOPQRSTUVWXYZ01234";

	@Autowired
	private SpringDataUsuarioRepository springDataRepository;

	private UsuarioRepositoryAdapter adapter;

	@BeforeEach
	void montarAdapter() {
		adapter = new UsuarioRepositoryAdapter(springDataRepository);
	}

	@Test
	void deveSalvarERecuperarPorEmail() {
		var novo = new Usuario(null, "Maria Silva", "maria@exemplo.com", HASH_BCRYPT);

		var salvo = adapter.salvar(novo);
		var recuperado = adapter.buscarPorEmail("maria@exemplo.com");

		assertNotNull(salvo.getId());
		assertTrue(recuperado.isPresent());
		assertEquals(salvo.getId(), recuperado.get().getId());
		assertEquals("Maria Silva", recuperado.get().getNome());
		assertEquals("maria@exemplo.com", recuperado.get().getEmail());
		assertEquals(HASH_BCRYPT, recuperado.get().getSenhaHash());
	}

	@Test
	void deveInformarSeEmailExiste() {
		adapter.salvar(new Usuario(null, "Maria Silva", "maria@exemplo.com", HASH_BCRYPT));

		assertTrue(adapter.existePorEmail("maria@exemplo.com"));
		assertFalse(adapter.existePorEmail("outro@exemplo.com"));
	}

	@Test
	void deveTraduzirViolacaoDeEmailUnicoParaEmailJaCadastrado() {
		adapter.salvar(new Usuario(null, "Maria Silva", "maria@exemplo.com", HASH_BCRYPT));
		var duplicado = new Usuario(null, "Outra Maria", "maria@exemplo.com", HASH_BCRYPT);

		assertThrows(EmailJaCadastradoException.class, () -> adapter.salvar(duplicado));
	}
}
