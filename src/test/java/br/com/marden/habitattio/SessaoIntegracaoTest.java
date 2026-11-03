package br.com.marden.habitattio;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.Environment;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("prod")
@Import(TestcontainersConfiguration.class)
@ExtendWith(OutputCaptureExtension.class)
class SessaoIntegracaoTest {

	private static final String SENHA = "segredo-T034-123";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private Environment ambiente;

	private static String emailUnico() {
		return "ana-" + UUID.randomUUID() + "@exemplo.com";
	}

	private void cadastrar(String email, String senha) throws Exception {
		String json = "{\"nome\":\"Ana Souza\",\"email\":\"" + email + "\",\"senha\":\"" + senha + "\"}";

		mockMvc.perform(post("/proprietarios").contentType(MediaType.APPLICATION_JSON).content(json).with(csrf()))
				.andReturn();
	}

	private MvcResult entrar(String email, String senha, MockHttpSession sessao) throws Exception {
		MockHttpServletRequestBuilder requisicao = post("/sessao").param("email", email).param("senha", senha)
				.with(csrf());
		if (sessao != null) {
			requisicao.session(sessao);
		}
		return mockMvc.perform(requisicao).andReturn();
	}

	@Test
	void deveEntrarComCredenciaisCorretasERetornar204() throws Exception {
		String email = emailUnico();
		cadastrar(email, SENHA);

		MvcResult resultado = entrar("  " + email.toUpperCase() + " ", SENHA, null);

		assertEquals(204, resultado.getResponse().getStatus());
	}

	@Test
	void deveTrocarOIdDaSessaoAoEntrar() throws Exception {
		String email = emailUnico();
		cadastrar(email, SENHA);
		MockHttpSession sessao = new MockHttpSession();
		mockMvc.perform(get("/sessao").session(sessao)).andReturn();
		String idAntes = sessao.getId();

		MvcResult resultado = entrar(email, SENHA, sessao);

		assertEquals(204, resultado.getResponse().getStatus());
		assertNotNull(resultado.getRequest().getSession(false));
		assertNotEquals(idAntes, resultado.getRequest().getSession(false).getId());
	}

	@Test
	void deveResponderIgualParaSenhaErradaEEmailInexistente() throws Exception {
		String email = emailUnico();
		cadastrar(email, SENHA);

		MvcResult senhaErrada = entrar(email, "outra-senha-errada", null);
		MvcResult emailInexistente = entrar(emailUnico(), SENHA, null);

		assertEquals(401, senhaErrada.getResponse().getStatus());
		assertEquals(senhaErrada.getResponse().getStatus(), emailInexistente.getResponse().getStatus());
		assertEquals(senhaErrada.getResponse().getContentAsString(),
				emailInexistente.getResponse().getContentAsString());
		assertEquals(senhaErrada.getResponse().getContentType(), emailInexistente.getResponse().getContentType());
	}

	@Test
	void deveRecusarOperacaoProtegidaDepoisDeSair() throws Exception {
		String email = emailUnico();
		cadastrar(email, SENHA);
		MvcResult login = entrar(email, SENHA, new MockHttpSession());
		MockHttpSession autenticada = (MockHttpSession) login.getRequest().getSession(false);
		assertNotNull(autenticada);

		MvcResult saida = mockMvc.perform(delete("/sessao").session(autenticada).with(csrf())).andReturn();
		MvcResult depois = mockMvc.perform(get("/sessao").session(autenticada)).andReturn();

		assertEquals(204, saida.getResponse().getStatus());
		assertEquals(401, depois.getResponse().getStatus());
	}

	@Test
	void deveRecusarLoginSemTokenCsrf() throws Exception {
		String email = emailUnico();
		cadastrar(email, SENHA);

		MvcResult resultado = mockMvc.perform(post("/sessao").param("email", email).param("senha", SENHA))
				.andReturn();

		assertEquals(403, resultado.getResponse().getStatus());
	}

	@Test
	void deveConfigurarExpiracaoDaSessaoEmTrintaMinutos() {
		String timeout = ambiente.getProperty("server.servlet.session.timeout");

		assertEquals("30m", timeout);
	}

	@Test
	void naoDeveExporPaginaDeLoginPadrao() throws Exception {
		MvcResult resultado = mockMvc.perform(get("/login")).andReturn();

		String tipo = resultado.getResponse().getContentType();
		boolean paginaHtml = resultado.getResponse().getStatus() == 200 && tipo != null && tipo.contains("html");
		assertFalse(paginaHtml);
	}

	@Test
	void naoDeveRegistrarASenhaNoLog(CapturedOutput saida) throws Exception {
		String senhaCerta = "Cert4-Xk7Qp-Marcador-9917";
		String senhaErrada = "Err4d4-Zw3Rt-Marcador-5521";
		String email = emailUnico();

		cadastrar(email, senhaCerta);
		MvcResult certa = entrar(email, senhaCerta, null);
		MvcResult errada = entrar(email, senhaErrada, null);

		assertEquals(204, certa.getResponse().getStatus());
		assertEquals(401, errada.getResponse().getStatus());
		assertFalse(saida.getAll().contains(senhaCerta));
		assertFalse(saida.getAll().contains(senhaErrada));
	}

}
