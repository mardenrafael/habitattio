package br.com.marden.habitattio;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * Contrato do endpoint de saúde (contracts/saude-http.md), no modo rápido e sem Redis rodando:
 * só {@code /actuator/health}, só o estado geral, Redis fora do cálculo.
 */
@SpringBootTest
@AutoConfigureMockMvc
class SaudeAplicacaoTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void deveResponderSaudavelSemRedis() throws Exception {
        ResultActions resposta = mockMvc.perform(get("/actuator/health"));

        resposta.andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void deveResponderSoComEstadoGeral() throws Exception {
        ResultActions resposta = mockMvc.perform(get("/actuator/health"));

        resposta.andExpect(jsonPath("$.status").exists())
                .andExpect(jsonPath("$.components").doesNotExist())
                .andExpect(jsonPath("$.details").doesNotExist())
                .andExpect(jsonPath("$.groups").doesNotExist());
    }

    @Test
    void naoDeveExporOutrosEndpointsOperacionais() throws Exception {
        String[] caminhos = {"/actuator", "/actuator/env", "/actuator/metrics", "/actuator/info",
                "/actuator/beans", "/actuator/configprops"};

        for (String caminho : caminhos) {
            mockMvc.perform(get(caminho)).andExpect(status().isNotFound());
        }
    }
}
