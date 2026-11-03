package br.com.marden.habitattio;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import org.junit.jupiter.api.Test;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Garantia de subida no profile {@code prod} com a migração V2 (contrato schema-v2, G-S1).
 */
class EstruturaBancoProducaoV2Test {

	private static PostgreSQLContainer novoContainer() {
		return new PostgreSQLContainer(DockerImageName.parse(TestcontainersConfiguration.POSTGRES_IMAGE));
	}

	private static void subirEFechar(PostgreSQLContainer postgres) {
		try (ConfigurableApplicationContext contexto = new SpringApplicationBuilder(HabitattioApplication.class)
				.profiles("prod")
				.run(
						"--spring.main.web-application-type=none",
						"--spring.datasource.url=" + postgres.getJdbcUrl(),
						"--spring.datasource.username=" + postgres.getUsername(),
						"--spring.datasource.password=" + postgres.getPassword(),
						"--spring.data.redis.host=localhost")) {
			assertTrue(contexto.isActive());
		}
	}

	private static long contar(PostgreSQLContainer postgres, String sql, String parametro) throws SQLException {
		try (Connection conexao = DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(),
				postgres.getPassword()); PreparedStatement comando = conexao.prepareStatement(sql)) {
			comando.setString(1, parametro);
			try (ResultSet resultado = comando.executeQuery()) {
				resultado.next();
				return resultado.getLong(1);
			}
		}
	}

	// G-S1
	@Test
	void deveAplicarV2DepoisDaV1EmBancoVazio() throws SQLException {
		try (PostgreSQLContainer postgres = novoContainer()) {
			postgres.start();

			subirEFechar(postgres);

			var sqlVersao = "SELECT COUNT(*) FROM flyway_schema_history WHERE version = ? AND success = true";
			assertEquals(1, contar(postgres, sqlVersao, "1"));
			assertEquals(1, contar(postgres, sqlVersao, "2"));
			assertEquals(1, contar(postgres,
					"SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = 'public' AND table_name = ?",
					"usuarios"));
		}
	}

}
