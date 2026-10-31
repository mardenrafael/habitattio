package br.com.marden.habitattio.adapters.outbound.persistence;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import br.com.marden.habitattio.adapters.outbound.persistence.entity.ImovelJpaEntity;
import br.com.marden.habitattio.domain.model.Imovel;
import jakarta.persistence.EntityManager;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
class ImovelRepositoryAdapterTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer(DockerImageName.parse("postgres:latest"));

    @Autowired
    private SpringDataImovelRepository repository;

    @Autowired
    private EntityManager entityManager;

    private ImovelRepositoryAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new ImovelRepositoryAdapter(repository);
    }

    @Test
    void deveManterImovelInativoAoSalvar() {
        Imovel imovel = new Imovel(null, 1L, "Apto 101", "Rua das Flores, 123", new BigDecimal("1500.00"));
        imovel.inativar();

        Imovel salvo = adapter.salvar(imovel);
        entityManager.flush();
        entityManager.clear();
        ImovelJpaEntity persistido = repository.findById(salvo.getId()).orElseThrow();

        assertNotNull(salvo.getId());
        assertFalse(salvo.isAtivo());
        assertFalse(persistido.isAtivo());
    }

    @Test
    void deveManterImovelAtivoAoSalvar() {
        Imovel imovel = new Imovel(null, 1L, "Apto 101", "Rua das Flores, 123", new BigDecimal("1500.00"));

        Imovel salvo = adapter.salvar(imovel);
        entityManager.flush();
        entityManager.clear();
        ImovelJpaEntity persistido = repository.findById(salvo.getId()).orElseThrow();

        assertNotNull(salvo.getId());
        assertTrue(salvo.isAtivo());
        assertTrue(persistido.isAtivo());
    }
}
