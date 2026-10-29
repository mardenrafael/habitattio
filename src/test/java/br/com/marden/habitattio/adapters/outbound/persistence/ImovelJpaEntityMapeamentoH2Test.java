package br.com.marden.habitattio.adapters.outbound.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Map;

import javax.sql.DataSource;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.jdbc.core.simple.JdbcClient;

@DataJpaTest
class ImovelJpaEntityMapeamentoH2Test {

    @Autowired
    private DataSource dataSource;

    @Test
    void deveMapearValorSugeridoComPrecisao19EEscala4() {
        JdbcClient jdbcClient = JdbcClient.create(dataSource);

        Map<String, Object> coluna = jdbcClient.sql("""
                SELECT NUMERIC_PRECISION, NUMERIC_SCALE
                FROM INFORMATION_SCHEMA.COLUMNS
                WHERE TABLE_NAME = 'IMOVEIS' AND COLUMN_NAME = 'VALOR_SUGERIDO'
                """)
                .query()
                .singleRow();

        assertEquals(19, ((Number) coluna.get("NUMERIC_PRECISION")).intValue());
        assertEquals(4, ((Number) coluna.get("NUMERIC_SCALE")).intValue());
    }

    @Test
    void deveMapearEnderecoComTamanho255() {
        JdbcClient jdbcClient = JdbcClient.create(dataSource);

        Number tamanho = jdbcClient.sql("""
                SELECT CHARACTER_MAXIMUM_LENGTH
                FROM INFORMATION_SCHEMA.COLUMNS
                WHERE TABLE_NAME = 'IMOVEIS' AND COLUMN_NAME = 'ENDERECO'
                """)
                .query(Number.class)
                .single();

        assertEquals(255, tamanho.intValue());
    }
}
