package br.com.marden.habitattio;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("prod")
@Import(TestcontainersConfiguration.class)
class IsolamentoProprietariosIntegracaoTest {

	private static final String SENHA = "segredo-T054-123";

	private static final Pattern PADRAO_ID = Pattern.compile("\"id\"\\s*:\\s*(\\d+)");

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JdbcClient jdbc;

	private static String emailUnico(String prefixo) {
		return prefixo + "-" + UUID.randomUUID() + "@exemplo.com";
	}

	private MockHttpSession cadastrarEEntrar(String prefixo) throws Exception {
		String email = emailUnico(prefixo);
		String json = "{\"nome\":\"" + prefixo + "\",\"email\":\"" + email + "\",\"senha\":\"" + SENHA + "\"}";
		MvcResult cadastro = mockMvc
				.perform(post("/proprietarios").contentType(MediaType.APPLICATION_JSON).content(json).with(csrf()))
				.andReturn();
		assertEquals(201, cadastro.getResponse().getStatus());

		MvcResult login = mockMvc.perform(
				post("/sessao").param("email", email).param("senha", SENHA).with(csrf()).session(new MockHttpSession()))
				.andReturn();
		assertEquals(204, login.getResponse().getStatus());
		MockHttpSession sessao = (MockHttpSession) login.getRequest().getSession(false);
		assertNotNull(sessao);
		return sessao;
	}

	private MvcResult cadastrarImovel(MockHttpSession sessao, String identificador) throws Exception {
		String json = "{\"identificador\":\"" + identificador
				+ "\",\"endereco\":\"Rua A, 10\",\"valorSugerido\":1500.00}";

		return mockMvc.perform(
				post("/imoveis").session(sessao).contentType(MediaType.APPLICATION_JSON).content(json).with(csrf()))
				.andReturn();
	}

	private long cadastrarImovelERetornarId(MockHttpSession sessao, String identificador) throws Exception {
		MvcResult resultado = cadastrarImovel(sessao, identificador);
		assertEquals(201, resultado.getResponse().getStatus());
		Matcher encontrado = PADRAO_ID.matcher(resultado.getResponse().getContentAsString());
		assertTrue(encontrado.find(), "A resposta 201 deve conter o id do imóvel");
		return Long.parseLong(encontrado.group(1));
	}

	private long contarImoveis() {
		return jdbc.sql("select count(*) from imoveis").query(Long.class).single();
	}

	@Test
	void naoDeveRevelarNemAlterarImoveisDeOutroProprietario() throws Exception {
		MockHttpSession ana = cadastrarEEntrar("ana");
		MockHttpSession bruno = cadastrarEEntrar("bruno");
		String sufixo = UUID.randomUUID().toString();
		List<Long> idsAna = new ArrayList<>();
		List<Long> idsBruno = new ArrayList<>();
		for (int i = 1; i <= 3; i++) {
			idsAna.add(cadastrarImovelERetornarId(ana, "Ana-" + i + "-" + sufixo));
			idsBruno.add(cadastrarImovelERetornarId(bruno, "Bruno-" + i + "-" + sufixo));
		}
		List<Long> todos = new ArrayList<>(idsAna);
		todos.addAll(idsBruno);
		long minimo = todos.stream().mapToLong(Long::longValue).min().orElseThrow();
		long maximo = todos.stream().mapToLong(Long::longValue).max().orElseThrow();
		List<Long> idsComSucessoAlheios = new ArrayList<>();

		for (long id = minimo - 2; id <= maximo + 2; id++) {
			MvcResult consulta = mockMvc.perform(get("/imoveis/" + id).session(bruno)).andReturn();
			MvcResult inativacao = mockMvc.perform(post("/imoveis/" + id + "/inativacao").session(bruno).with(csrf()))
					.andReturn();
			boolean sucesso = consulta.getResponse().getStatus() == 200 || inativacao.getResponse().getStatus() == 200;
			if (sucesso && !idsBruno.contains(id)) {
				idsComSucessoAlheios.add(id);
			}
		}

		assertTrue(idsComSucessoAlheios.isEmpty(), "Bruno obteve 200 em imóvel que não é dele: " + idsComSucessoAlheios);
		for (long idAna : idsAna) {
			MvcResult consulta = mockMvc.perform(get("/imoveis/" + idAna).session(ana)).andReturn();
			assertEquals(200, consulta.getResponse().getStatus());
			assertTrue(consulta.getResponse().getContentAsString().replace(" ", "").contains("\"ativo\":true"),
					"Imóvel da Ana foi alterado: " + consulta.getResponse().getContentAsString());
		}
	}

	@Test
	void naoDeveExecutarOperacaoDeImovelSemAutenticacao() throws Exception {
		MockHttpSession ana = cadastrarEEntrar("ana");
		long idExistente = cadastrarImovelERetornarId(ana, "Apto-" + UUID.randomUUID());
		long antes = contarImoveis();
		String json = "{\"identificador\":\"Sem-Sessao-" + UUID.randomUUID()
				+ "\",\"endereco\":\"Rua A, 10\",\"valorSugerido\":1500.00}";

		MvcResult cadastro = mockMvc
				.perform(post("/imoveis").contentType(MediaType.APPLICATION_JSON).content(json).with(csrf()))
				.andReturn();
		MvcResult consulta = mockMvc.perform(get("/imoveis/" + idExistente)).andReturn();
		MvcResult inativacao = mockMvc.perform(post("/imoveis/" + idExistente + "/inativacao").with(csrf()))
				.andReturn();

		assertEquals(401, cadastro.getResponse().getStatus());
		assertEquals(401, consulta.getResponse().getStatus());
		assertEquals(401, inativacao.getResponse().getStatus());
		assertEquals(antes, contarImoveis());
	}

	@Test
	void deveAceitarMesmoIdentificadorParaDonosDiferentes() throws Exception {
		MockHttpSession ana = cadastrarEEntrar("ana");
		MockHttpSession bruno = cadastrarEEntrar("bruno");
		String identificador = "Apto 101 " + UUID.randomUUID();

		MvcResult daAna = cadastrarImovel(ana, identificador);
		MvcResult doBruno = cadastrarImovel(bruno, identificador);

		assertEquals(201, daAna.getResponse().getStatus());
		assertEquals(201, doBruno.getResponse().getStatus());
	}

}
