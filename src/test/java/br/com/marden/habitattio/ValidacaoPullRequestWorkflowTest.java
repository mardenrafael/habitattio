package br.com.marden.habitattio;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

/**
 * Contrato do workflow de validação de PR
 * (specs/004-pr-validation-ci/contracts/workflow-validacao-pr.md, invariantes W1 a W16). Sem
 * contexto Spring e sem Docker: lê o arquivo do workflow e verifica o que ele declara.
 */
class ValidacaoPullRequestWorkflowTest {

	private static final Path CAMINHO = Path.of(".github/workflows/validacao-pr.yml");

	private static final Pattern ACAO_FIXADA_POR_SHA = Pattern.compile("^[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+@[0-9a-f]{40}$");

	private static final Pattern OBJETIVO_VERIFY = Pattern.compile("\\bverify\\b");

	private static final List<String> OPCOES_PROIBIDAS = List.of("-DskipTests", "-Dmaven.test.skip", "-Dtest=", "-Dgroups",
			"-DexcludedGroups", " -fae", " -fn", "--fail-at-end", "--fail-never");

	private static String texto() throws IOException {
		return Files.readString(CAMINHO);
	}

	private static Map<Object, Object> workflow() throws IOException {
		return new Yaml().load(texto());
	}

	@SuppressWarnings("unchecked")
	private static Map<Object, Object> mapa(Object valor) {
		return (Map<Object, Object>) valor;
	}

	/** O SnakeYAML segue o YAML 1.1 e lê a chave {@code on} como {@code Boolean.TRUE}. */
	private static Map<Object, Object> gatilhos() throws IOException {
		Map<Object, Object> raiz = workflow();
		return mapa(raiz.containsKey("on") ? raiz.get("on") : raiz.get(Boolean.TRUE));
	}

	private static Map<Object, Object> jobs() throws IOException {
		return mapa(workflow().get("jobs"));
	}

	private static Map<Object, Object> job() throws IOException {
		return mapa(jobs().get("validar"));
	}

	@SuppressWarnings("unchecked")
	private static List<Map<Object, Object>> passos() throws IOException {
		return (List<Map<Object, Object>>) job().get("steps");
	}

	private static Map<Object, Object> passoQueUsa(String acao) throws IOException {
		return passos().stream()
				.filter(passo -> String.valueOf(passo.get("uses")).startsWith(acao + "@"))
				.findFirst()
				.orElseThrow(() -> new AssertionError("nenhum passo usa a ação " + acao));
	}

	private static Map<Object, Object> parametros(Map<Object, Object> passo) {
		return mapa(passo.get("with"));
	}

	private static int indiceDoPassoQueRoda(String trecho) throws IOException {
		List<Map<Object, Object>> passos = passos();
		for (int i = 0; i < passos.size(); i++) {
			if (String.valueOf(passos.get(i).get("run")).contains(trecho)) {
				return i;
			}
		}
		return -1;
	}

	private static List<String> comandos() throws IOException {
		return passos().stream().map(passo -> passo.get("run")).filter(Objects::nonNull).map(String::valueOf).toList();
	}

	@Test
	void deveDispararSomenteEmPullRequest() throws IOException {
		Map<Object, Object> gatilhos = gatilhos();

		assertEquals(Set.of("pull_request"), gatilhos.keySet());
	}

	@Test
	void deveValidarSomentePullRequestComAlvoMain() throws IOException {
		Map<Object, Object> pullRequest = mapa(gatilhos().get("pull_request"));

		assertEquals(List.of("main"), pullRequest.get("branches"));
	}

	@Test
	void deveRevalidarANovosCommitsEReabertura() throws IOException {
		Map<Object, Object> pullRequest = mapa(gatilhos().get("pull_request"));

		Object tipos = pullRequest.get("types");

		assertTrue(tipos == null || ((List<?>) tipos).containsAll(List.of("opened", "synchronize", "reopened")),
				"o gatilho precisa cobrir abertura, novos commits e reabertura, mas os tipos eram " + tipos);
	}

	@Test
	void deveTerPermissaoSomenteDeLeitura() throws IOException {
		Object permissoes = workflow().get("permissions");

		assertEquals(Map.of("contents", "read"), permissoes);
	}

	@Test
	void deveNaoUsarSegredos() throws IOException {
		String conteudo = texto();

		assertFalse(conteudo.contains("secrets."), "o workflow não pode depender de segredos");
	}

	@Test
	void deveFixarAcoesPorSha() throws IOException {
		List<String> acoes = passos().stream().map(passo -> passo.get("uses")).filter(Objects::nonNull).map(String::valueOf).toList();

		assertFalse(acoes.isEmpty(), "o workflow deveria usar ao menos uma ação");
		for (String acao : acoes) {
			assertTrue(ACAO_FIXADA_POR_SHA.matcher(acao).matches(), "ação não fixada por SHA completo: " + acao);
		}
	}

	@Test
	void deveNaoPersistirCredenciais() throws IOException {
		Map<Object, Object> checkout = passoQueUsa("actions/checkout");

		assertEquals(Boolean.FALSE, parametros(checkout).get("persist-credentials"));
	}

	@Test
	void deveTerUmUnicoJobComNomeEstavel() throws IOException {
		Map<Object, Object> jobs = jobs();

		assertEquals(Set.of("validar"), jobs.keySet());
		assertEquals("validar", job().get("name"));
		assertFalse(job().containsKey("strategy"), "uma matrix mudaria o nome do check");
	}

	@Test
	void deveRodarEmMaquinaLinuxDeVersaoFixa() throws IOException {
		Object maquina = job().get("runs-on");

		assertEquals("ubuntu-24.04", maquina);
	}

	@Test
	void deveTerTetoDeDuracao() throws IOException {
		Object teto = job().get("timeout-minutes");

		Integer minutos = assertInstanceOf(Integer.class, teto);
		assertTrue(minutos >= 1 && minutos <= 60, "o teto deveria estar entre 1 e 60 minutos, mas era " + minutos);
	}

	@Test
	void deveCancelarValidacaoAnteriorDoMesmoPr() throws IOException {
		Map<Object, Object> raiz = workflow();

		Object bloco = raiz.containsKey("concurrency") ? raiz.get("concurrency") : job().get("concurrency");

		assertNotNull(bloco, "o workflow deveria declarar concurrency");
		assertTrue(String.valueOf(mapa(bloco).get("group")).contains("github.event.pull_request.number"),
				"o grupo precisa ser por PR, mas era " + mapa(bloco).get("group"));
		assertEquals(Boolean.TRUE, mapa(bloco).get("cancel-in-progress"));
	}

	@Test
	void deveReprovarAoPrimeiroPassoQueFalhar() throws IOException {
		Map<Object, Object> job = job();

		assertNotEquals(Boolean.TRUE, job.get("continue-on-error"), "o job não pode tolerar falha");
		for (Map<Object, Object> passo : passos()) {
			assertNotEquals(Boolean.TRUE, passo.get("continue-on-error"), "passo não pode tolerar falha: " + passo);
		}
	}

	@Test
	void deveValidarOEstadoCombinadoComMain() throws IOException {
		Map<Object, Object> checkout = passoQueUsa("actions/checkout");

		assertFalse(parametros(checkout).containsKey("ref"), "sem ref, o checkout usa o merge do PR com main");
	}

	@Test
	void deveUsarJava25() throws IOException {
		Map<Object, Object> java = parametros(passoQueUsa("actions/setup-java"));

		assertEquals("temurin", java.get("distribution"));
		assertEquals("25", String.valueOf(java.get("java-version")));
	}

	@Test
	void deveRodarSuiteCompletaEEmpacotarPeloWrapper() throws IOException {
		List<String> comandos = comandos();

		assertTrue(comandos.stream().anyMatch(comando -> comando.contains("./mvnw") && OBJETIVO_VERIFY.matcher(comando).find()),
				"nenhum passo roda ./mvnw verify: " + comandos);
		for (String comando : comandos) {
			for (String opcao : OPCOES_PROIBIDAS) {
				assertFalse(comando.contains(opcao), "opção proibida " + opcao + " em: " + comando);
			}
		}
	}

	@Test
	void deveReprovarQuandoAlgumTesteForPulado() throws IOException {
		int build = indiceDoPassoQueRoda("./mvnw");

		int verificador = indiceDoPassoQueRoda("VerificarTestesPulados");

		assertTrue(build >= 0, "nenhum passo roda ./mvnw");
		assertTrue(verificador > build, "o verificador de testes pulados precisa rodar depois do build");
	}

	@Test
	void deveAnexarRelatoriosDeTesteQuandoFalhar() throws IOException {
		Map<Object, Object> upload = passoQueUsa("actions/upload-artifact");

		Map<Object, Object> parametros = parametros(upload);

		assertTrue(List.of("failure()", "${{ failure() }}").contains(upload.get("if")), "condição era " + upload.get("if"));
		assertEquals("relatorios-de-teste", parametros.get("name"));
		assertTrue(String.valueOf(parametros.get("path")).contains("target/surefire-reports"), "caminho era " + parametros.get("path"));
		assertEquals(14, parametros.get("retention-days"));
		assertEquals("ignore", parametros.get("if-no-files-found"));
	}
}
