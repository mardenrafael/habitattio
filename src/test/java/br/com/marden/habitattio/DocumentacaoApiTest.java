package br.com.marden.habitattio;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * Rotas de documentação no modo rápido, sem login (contracts/documentacao-http.md).
 */
@SpringBootTest
@AutoConfigureMockMvc
class DocumentacaoApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void deveServirADescricaoSemLogin() throws Exception {
        ResultActions resposta = mockMvc.perform(get("/v3/api-docs"));

        resposta.andExpect(status().isOk())
                .andExpect(jsonPath("$.info.title").value("Habitattio API"));
    }

    @Test
    void deveServirADescricaoEmYamlSemLogin() throws Exception {
        ResultActions resposta = mockMvc.perform(get("/v3/api-docs.yaml"));

        resposta.andExpect(status().isOk())
                .andExpect(content().string(containsString("title: Habitattio API")));
    }

    @Test
    void deveRedirecionarParaAPaginaSemLogin() throws Exception {
        ResultActions resposta = mockMvc.perform(get("/swagger-ui.html"));

        resposta.andExpect(status().is3xxRedirection());
    }

    @Test
    void deveServirAPaginaSemLogin() throws Exception {
        ResultActions resposta = mockMvc.perform(get("/swagger-ui/index.html"));

        resposta.andExpect(status().isOk());
    }

    @Test
    void deveEnviarOTokenCsrfNasChamadasDaPagina() throws Exception {
        ResultActions resposta = mockMvc.perform(get("/swagger-ui/swagger-initializer.js"));

        resposta.andExpect(status().isOk())
                .andExpect(content().string(containsString("requestInterceptor")))
                .andExpect(content().string(containsString("XSRF-TOKEN")))
                .andExpect(content().string(containsString("X-XSRF-TOKEN")));
    }
}
