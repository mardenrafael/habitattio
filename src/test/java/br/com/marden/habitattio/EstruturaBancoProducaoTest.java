package br.com.marden.habitattio;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Locale;

import org.junit.jupiter.api.Test;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Garantias de subida no profile {@code prod} (contrato imoveis-schema, S1–S4; research R7).
 * Cada cenário usa um PostgreSQL próprio, porque altera a estrutura do banco.
 */
class EstruturaBancoProducaoTest {

	private static PostgreSQLContainer novoContainer() {
		return new PostgreSQLContainer(DockerImageName.parse(TestcontainersConfiguration.POSTGRES_IMAGE));
	}

	private static ConfigurableApplicationContext subir(PostgreSQLContainer postgres) {
		return new SpringApplicationBuilder(HabitattioApplication.class)
				.profiles("prod")
				.run(
						"--spring.main.web-application-type=none",
						"--spring.datasource.url=" + postgres.getJdbcUrl(),
						"--spring.datasource.username=" + postgres.getUsername(),
						"--spring.datasource.password=" + postgres.getPassword(),
						"--spring.data.redis.host=localhost");
	}

	private static void subirEFechar(PostgreSQLContainer postgres) {
		try (ConfigurableApplicationContext contexto = subir(postgres)) {
			assertTrue(contexto.isActive());
		}
	}

	private static Connection conectar(PostgreSQLContainer postgres) throws SQLException {
		return DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
	}

	private static void executar(PostgreSQLContainer postgres, String sql) throws SQLException {
		try (Connection conexao = conectar(postgres); Statement comando = conexao.createStatement()) {
			comando.execute(sql);
		}
	}

	private static long contar(PostgreSQLContainer postgres, String sql, String parametro) throws SQLException {
		try (Connection conexao = conectar(postgres); PreparedStatement comando = conexao.prepareStatement(sql)) {
			comando.setString(1, parametro);
			try (ResultSet resultado = comando.executeQuery()) {
				resultado.next();
				return resultado.getLong(1);
			}
		}
	}

	private static boolean cadeiaDeCausasContem(Throwable excecao, String trecho) {
		String procurado = trecho.toLowerCase(Locale.ROOT);
		for (Throwable atual = excecao; atual != null; atual = atual.getCause()) {
			String mensagem = atual.getMessage();
			if (mensagem != null && mensagem.toLowerCase(Locale.ROOT).contains(procurado)) {
				return true;
			}
			if (atual.getCause() == atual) {
				break;
			}
		}
		return false;
	}

	// S1
	@Test
	void deveCriarEstruturaDeImoveisEmBancoVazio() throws SQLException {
		try (PostgreSQLContainer postgres = novoContainer()) {
			postgres.start();

			subirEFechar(postgres);

			assertEquals(1, contar(postgres,
					"SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = 'public' AND table_name = ?",
					"imoveis"));
			assertEquals(1, contar(postgres, "SELECT COUNT(*) FROM pg_constraint WHERE conname = ?",
					"ck_imoveis_endereco_nao_vazio"));
			assertEquals(1, contar(postgres, "SELECT COUNT(*) FROM pg_constraint WHERE conname = ?",
					"ck_imoveis_valor_sugerido_positivo"));
			assertEquals(1, contar(postgres,
					"SELECT COUNT(*) FROM flyway_schema_history WHERE version = ? AND success = true", "1"));
		}
	}

	// S2
	@Test
	void naoDeveReaplicarMigracoesJaAplicadas() throws SQLException {
		try (PostgreSQLContainer postgres = novoContainer()) {
			postgres.start();
			subirEFechar(postgres);

			assertDoesNotThrow(() -> subirEFechar(postgres));

			assertEquals(1, contar(postgres, "SELECT COUNT(*) FROM flyway_schema_history WHERE version = ?", "1"));
		}
	}

	// S3
	@Test
	void deveRecusarSubidaQuandoEstruturaDiverge() throws SQLException {
		try (PostgreSQLContainer postgres = novoContainer()) {
			postgres.start();
			subirEFechar(postgres);
			executar(postgres, "ALTER TABLE imoveis DROP COLUMN endereco");

			Throwable excecao = assertThrows(Throwable.class, () -> subirEFechar(postgres));

			assertTrue(cadeiaDeCausasContem(excecao, "endereco"),
					"A cadeia de causas deveria mencionar 'endereco', mas foi: " + excecao);
			assertEquals(0, contar(postgres,
					"SELECT COUNT(*) FROM information_schema.columns WHERE table_name = 'imoveis' AND column_name = ?",
					"endereco"));
		}
	}

	// S4
	@Test
	void deveRecusarSubidaQuandoMigracaoAplicadaFoiModificada() throws SQLException {
		try (PostgreSQLContainer postgres = novoContainer()) {
			postgres.start();
			subirEFechar(postgres);
			executar(postgres, "UPDATE flyway_schema_history SET checksum = checksum + 1 WHERE version = '1'");

			Throwable excecao = assertThrows(Throwable.class, () -> subirEFechar(postgres));

			assertTrue(cadeiaDeCausasContem(excecao, "checksum"),
					"A cadeia de causas deveria mencionar 'checksum', mas foi: " + excecao);
		}
	}

}
