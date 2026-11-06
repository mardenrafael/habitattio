package br.com.marden.habitattio;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Em produção nada da documentação fica online; a descrição vive só no repositório
 * (contracts/documentacao-http.md, FR-010).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("prod")
@Import(TestcontainersConfiguration.class)
class DocumentacaoApiProducaoTest {

    private static final List<String> ROTAS = List.of("/v3/api-docs", "/v3/api-docs.yaml", "/swagger-ui.html",
            "/swagger-ui/index.html");
    private static final String ARQUIVO_DA_PAGINA = "/webjars/swagger-ui/index.html";
    private static final String ROTA_INEXISTENTE = "/rota-que-nao-existe";

    @Autowired
    private MockMvc mockMvc;

    @Test
    void deveResponder404NaDocumentacaoParaAnonimo() throws Exception {
        for (String rota : ROTAS) {
            mockMvc.perform(get(rota)).andExpect(status().isNotFound());
        }
    }

    @Test
    void deveResponder404NaDocumentacaoParaAutenticado() throws Exception {
        for (String rota : ROTAS) {
            mockMvc.perform(get(rota).with(user("ana"))).andExpect(status().isNotFound());
        }
    }

    @Test
    void deveTratarArquivosDaPaginaComoRotaInexistente() throws Exception {
        int anonimoInexistente = statusAnonimo(ROTA_INEXISTENTE);
        int autenticadoInexistente = statusAutenticado(ROTA_INEXISTENTE);

        int anonimoArquivo = statusAnonimo(ARQUIVO_DA_PAGINA);
        int autenticadoArquivo = statusAutenticado(ARQUIVO_DA_PAGINA);

        assertEquals(anonimoInexistente, anonimoArquivo, "anônimo");
        assertEquals(autenticadoInexistente, autenticadoArquivo, "autenticado");
    }

    private int statusAnonimo(String rota) throws Exception {
        return mockMvc.perform(get(rota)).andReturn().getResponse().getStatus();
    }

    private int statusAutenticado(String rota) throws Exception {
        return mockMvc.perform(get(rota).with(user("ana"))).andReturn().getResponse().getStatus();
    }
}
