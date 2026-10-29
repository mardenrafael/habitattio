package br.com.marden.habitattio;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.SQLException;

import javax.sql.DataSource;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class ModoRapidoTest {

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    private DataSource dataSource;

    @Test
    void deveSubirNoModoRapidoQuandoNenhumProfileEhEscolhido() throws SQLException {
        String url;
        try (Connection conexao = dataSource.getConnection()) {
            url = conexao.getMetaData().getURL();
        }

        Flyway flyway = applicationContext.getBeanProvider(Flyway.class).getIfAvailable();

        assertTrue(url.startsWith("jdbc:h2:"), "esperava H2 no modo rápido, mas a URL era " + url);
        assertNull(flyway, "o Flyway não deve rodar no modo rápido");
    }
}
