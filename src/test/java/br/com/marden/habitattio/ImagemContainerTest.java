package br.com.marden.habitattio;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import com.github.dockerjava.api.command.HealthState;
import com.github.dockerjava.api.command.InspectContainerResponse;
import com.github.dockerjava.api.command.InspectImageResponse;
import com.github.dockerjava.api.command.TopContainerResponse;
import org.junit.jupiter.api.Test;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.Container.ExecResult;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.images.builder.ImageFromDockerfile;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Constrói a imagem a partir do {@code Dockerfile} do repositório e prova subida, recusa,
 * saúde e conteúdo (contrato imagem-container; research R8). Sem contexto Spring e sem Redis.
 * O builder usado aqui é o clássico do daemon (research R3).
 */
class ImagemContainerTest {

	private static final String NOME_IMAGEM = "habitattio:teste";

	private static final int PORTA = 8080;

	private static final ImageFromDockerfile IMAGEM = new ImageFromDockerfile(NOME_IMAGEM, false)
			.withDockerfile(Path.of("Dockerfile").toAbsolutePath())
			.withBuildArg("VERSION", "teste")
			.withBuildArg("REVISION", "teste");

	private static final Network REDE = Network.newNetwork();

	private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

	private record Resposta(int status, String corpo) {
	}

	private static PostgreSQLContainer novoPostgres() {
		return new PostgreSQLContainer(DockerImageName.parse(TestcontainersConfiguration.POSTGRES_IMAGE))
				.withNetwork(REDE)
				.withNetworkAliases("postgres");
	}

	private static GenericContainer<?> containerProducao(PostgreSQLContainer postgres) {
		return new GenericContainer<>(IMAGEM)
				.withNetwork(REDE)
				.withExposedPorts(PORTA)
				.withEnv("SPRING_PROFILES_ACTIVE", "prod")
				.withEnv("SPRING_DATASOURCE_URL", "jdbc:postgresql://postgres:5432/" + postgres.getDatabaseName())
				.withEnv("SPRING_DATASOURCE_USERNAME", postgres.getUsername())
				.withEnv("SPRING_DATASOURCE_PASSWORD", postgres.getPassword());
	}

	private static GenericContainer<?> containerRapido() {
		return new GenericContainer<>(IMAGEM).withExposedPorts(PORTA);
	}

	private static Resposta get(GenericContainer<?> container, String caminho) throws IOException, InterruptedException {
		URI uri = URI.create("http://" + container.getHost() + ":" + container.getMappedPort(PORTA) + caminho);
		HttpRequest requisicao = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(5)).GET().build();
		HttpResponse<String> resposta = HTTP.send(requisicao, HttpResponse.BodyHandlers.ofString());
		return new Resposta(resposta.statusCode(), resposta.body());
	}

	private static String estadoDeSaude(GenericContainer<?> container) {
		HealthState saude = container.getCurrentContainerInfo().getState().getHealth();
		return saude == null ? null : saude.getStatus();
	}

	private static void subirAteIniciar(GenericContainer<?> container) {
		container.waitingFor(Wait.forLogMessage(".*Started HabitattioApplication.*", 1)
				.withStartupTimeout(Duration.ofMinutes(2)));
		container.start();
	}

	/**
	 * Sobe esperando a falha de subida e devolve o código de saída. O container precisa continuar
	 * existindo para o código ser lido, por isso não se espera {@code ContainerLaunchException}.
	 */
	private static long subirEsperandoFalha(GenericContainer<?> container) throws InterruptedException {
		container.waitingFor(Wait.forLogMessage(".*Application run failed.*", 1)
				.withStartupTimeout(Duration.ofMinutes(2)));
		container.start();
		long limite = System.nanoTime() + Duration.ofSeconds(30).toNanos();
		InspectContainerResponse.ContainerState estado = inspecionar(container).getState();
		while (Boolean.TRUE.equals(estado.getRunning()) && System.nanoTime() < limite) {
			Thread.sleep(500);
			estado = inspecionar(container).getState();
		}
		assertFalse(Boolean.TRUE.equals(estado.getRunning()), "o container deveria ter parado após a falha de subida");
		return estado.getExitCodeLong();
	}

	private static InspectContainerResponse inspecionar(GenericContainer<?> container) {
		return DockerClientFactory.instance().client().inspectContainerCmd(container.getContainerId()).exec();
	}

	private static InspectImageResponse inspecionarImagem() {
		return DockerClientFactory.instance().client().inspectImageCmd(IMAGEM.get()).exec();
	}

	private static void executar(PostgreSQLContainer postgres, String sql) throws SQLException {
		try (Connection conexao = DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(),
				postgres.getPassword()); Statement comando = conexao.createStatement()) {
			comando.execute(sql);
		}
	}

	@Test
	void deveSubirEmProducaoComPostgresSemRedis() throws IOException, InterruptedException {
		try (PostgreSQLContainer postgres = novoPostgres()) {
			postgres.start();
			try (GenericContainer<?> aplicacao = containerProducao(postgres)) {

				subirAteIniciar(aplicacao);

				assertTrue(aplicacao.isRunning());
				Resposta resposta = get(aplicacao, "/imoveis");
				assertTrue(resposta.status() < 500,
						"a aplicação deveria responder na porta configurada, mas deu " + resposta.status());
			}
		}
	}

	@Test
	void deveRecusarSubidaEmProducaoQuandoBancoDiverge() throws SQLException, InterruptedException {
		try (PostgreSQLContainer postgres = novoPostgres()) {
			postgres.start();
			executar(postgres, "CREATE TABLE imoveis (id bigint)");
			try (GenericContainer<?> aplicacao = containerProducao(postgres)) {

				long codigoDeSaida = subirEsperandoFalha(aplicacao);

				assertNotEquals(0L, codigoDeSaida);
			}
		}
	}

	@Test
	void naoDeveSubirEmProducaoSemVariaveisDeConexao() throws InterruptedException {
		try (GenericContainer<?> aplicacao = new GenericContainer<>(IMAGEM)
				.withExposedPorts(PORTA)
				.withEnv("SPRING_PROFILES_ACTIVE", "prod")) {

			long codigoDeSaida = subirEsperandoFalha(aplicacao);

			assertNotEquals(0L, codigoDeSaida);
			assertFalse(aplicacao.getLogs().contains("Started HabitattioApplication"),
					"não deveria subir em silêncio sem as variáveis de conexão");
		}
	}

	@Test
	void deveRodarProcessoDaAplicacaoSemSerRoot() {
		try (PostgreSQLContainer postgres = novoPostgres()) {
			postgres.start();
			try (GenericContainer<?> aplicacao = containerProducao(postgres)) {
				subirAteIniciar(aplicacao);

				TopContainerResponse processos = DockerClientFactory.instance().client()
						.topContainerCmd(aplicacao.getContainerId()).exec();

				List<String> titulos = Arrays.asList(processos.getTitles());
				int colunaUid = titulos.indexOf("UID");
				int colunaComando = titulos.indexOf("CMD");
				String[] processoJava = Arrays.stream(processos.getProcesses())
						.filter(linha -> linha[colunaComando].contains("java"))
						.findFirst()
						.orElseThrow();
				assertEquals("65532", processoJava[colunaUid]);
			}
		}
	}

	@Test
	void deveConterSoORuntimeJava() throws IOException, InterruptedException {
		try (GenericContainer<?> aplicacao = containerRapido()) {
			subirAteIniciar(aplicacao);

			ExecResult modulos = aplicacao.execInContainer("java", "--list-modules");

			assertEquals(0, modulos.getExitCode());
			assertFalse(modulos.getStdout().contains("jdk.compiler"), "a imagem final não deveria ter o compilador");
			assertThrows(Exception.class, () -> aplicacao.copyFileFromContainer("/app/pom.xml", InputStream::readAllBytes));
			assertThrows(Exception.class, () -> aplicacao.copyFileFromContainer("/app/src", InputStream::readAllBytes));
			assertThrows(Exception.class, () -> aplicacao.copyFileFromContainer("/app/target", InputStream::readAllBytes));
		}
	}

	@Test
	void deveRegistrarVersaoERevisaoNosLabels() {
		InspectImageResponse imagem = inspecionarImagem();

		Map<String, String> labels = imagem.getConfig().getLabels();

		assertEquals("teste", labels.get("org.opencontainers.image.version"));
		assertEquals("teste", labels.get("org.opencontainers.image.revision"));
	}

	@Test
	void deveSerConstruidaParaLinuxAmd64() {
		InspectImageResponse imagem = inspecionarImagem();

		String plataforma = imagem.getOs() + "/" + imagem.getArch();

		assertEquals("linux/amd64", plataforma);
	}

}
