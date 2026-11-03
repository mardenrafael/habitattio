package br.com.marden.habitattio;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

/**
 * O MockMvc não emite o Set-Cookie de sessão; por isso o cliente HTTP real do JDK.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@ActiveProfiles("prod")
@Import(TestcontainersConfiguration.class)
class SessaoCookieIntegracaoTest {

	@LocalServerPort
	private int porta;

	private final HttpClient cliente = HttpClient.newBuilder()
			.cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ALL))
			.build();

	private String url(String caminho) {
		return "http://localhost:" + porta + caminho;
	}

	private static String valorDoCookie(HttpResponse<?> resposta, String nome) {
		for (String cabecalho : resposta.headers().allValues("Set-Cookie")) {
			if (cabecalho.startsWith(nome + "=")) {
				String valor = cabecalho.substring(nome.length() + 1);
				int fim = valor.indexOf(';');
				return fim >= 0 ? valor.substring(0, fim) : valor;
			}
		}
		return null;
	}

	private static String codificar(String valor) {
		return URLEncoder.encode(valor, StandardCharsets.UTF_8);
	}

	@Test
	void deveEmitirCookieDeSessaoHttpOnlySameSiteLaxESecureNoProd() throws Exception {
		String email = "ana-" + UUID.randomUUID() + "@exemplo.com";
		String senha = "segredo-cookie-123";
		HttpResponse<String> inicial = cliente.send(
				HttpRequest.newBuilder(URI.create(url("/sessao"))).GET().build(), BodyHandlers.ofString());
		String token = valorDoCookie(inicial, "XSRF-TOKEN");
		assertNotNull(token, "GET /sessao deveria emitir o cookie XSRF-TOKEN");
		String json = "{\"nome\":\"Ana Souza\",\"email\":\"" + email + "\",\"senha\":\"" + senha + "\"}";
		HttpResponse<String> cadastro = cliente.send(HttpRequest.newBuilder(URI.create(url("/proprietarios")))
				.header("Content-Type", "application/json")
				.header("Cookie", "XSRF-TOKEN=" + token)
				.header("X-XSRF-TOKEN", token)
				.POST(BodyPublishers.ofString(json))
				.build(), BodyHandlers.ofString());
		assertEquals(201, cadastro.statusCode());

		HttpResponse<String> login = cliente.send(HttpRequest.newBuilder(URI.create(url("/sessao")))
				.header("Content-Type", "application/x-www-form-urlencoded")
				.header("Cookie", "XSRF-TOKEN=" + token)
				.header("X-XSRF-TOKEN", token)
				.POST(BodyPublishers.ofString("email=" + codificar(email) + "&senha=" + codificar(senha)))
				.build(), BodyHandlers.ofString());

		assertEquals(204, login.statusCode());
		String sessao = login.headers().allValues("Set-Cookie").stream()
				.filter(c -> c.startsWith("JSESSIONID="))
				.findFirst()
				.orElse(null);
		assertNotNull(sessao, "o login deveria emitir o cookie JSESSIONID");
		String atributos = sessao.toLowerCase(Locale.ROOT);
		assertTrue(atributos.contains("httponly"), sessao);
		assertTrue(atributos.contains("samesite=lax"), sessao);
		assertTrue(atributos.contains("secure"), sessao);
	}

}
