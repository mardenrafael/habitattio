package br.com.marden.habitattio.adapters.inbound.rest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import br.com.marden.habitattio.adapters.config.SegurancaConfiguration;
import br.com.marden.habitattio.adapters.inbound.security.ProprietarioAuthenticationProvider;
import br.com.marden.habitattio.adapters.inbound.security.ProprietarioAutenticado;
import br.com.marden.habitattio.application.usecase.AutenticarProprietarioUseCase;
import br.com.marden.habitattio.application.usecase.CadastrarImovelUseCase;
import br.com.marden.habitattio.application.usecase.ConsultarImovelUseCase;
import br.com.marden.habitattio.application.usecase.InativarImovelUseCase;
import br.com.marden.habitattio.domain.model.Imovel;
import br.com.marden.habitattio.fakes.CodificadorSenhaFake;
import br.com.marden.habitattio.fakes.ImovelRepositoryFake;
import br.com.marden.habitattio.fakes.UsuarioRepositoryFake;

@WebMvcTest(ImovelController.class)
@Import({ SegurancaConfiguration.class, ImovelControllerTest.Config.class })
class ImovelControllerTest {

    @TestConfiguration
    static class Config {

        @Bean
        ImovelRepositoryFake imovelRepositoryFake() {
            return new ImovelRepositoryFake();
        }

        @Bean
        CadastrarImovelUseCase cadastrarImovelUseCase(ImovelRepositoryFake repositorio) {
            return new CadastrarImovelUseCase(repositorio);
        }

        @Bean
        ConsultarImovelUseCase consultarImovelUseCase(ImovelRepositoryFake repositorio) {
            return new ConsultarImovelUseCase(repositorio);
        }

        @Bean
        InativarImovelUseCase inativarImovelUseCase(ImovelRepositoryFake repositorio) {
            return new InativarImovelUseCase(repositorio);
        }

        @Bean
        UsuarioRepositoryFake usuarioRepositoryFake() {
            return new UsuarioRepositoryFake();
        }

        @Bean
        CodificadorSenhaFake codificadorSenhaFake() {
            return new CodificadorSenhaFake();
        }

        @Bean
        AutenticarProprietarioUseCase autenticarProprietarioUseCase(UsuarioRepositoryFake repositorio,
                CodificadorSenhaFake codificador) {
            return new AutenticarProprietarioUseCase(repositorio, codificador);
        }

        @Bean
        ProprietarioAuthenticationProvider proprietarioAuthenticationProvider(
                AutenticarProprietarioUseCase autenticarProprietarioUseCase) {
            return new ProprietarioAuthenticationProvider(autenticarProprietarioUseCase);
        }
    }

    private static final Long ANA = 1L;
    private static final Long BRUNO = 2L;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ImovelRepositoryFake imovelRepositoryFake;

    @BeforeEach
    void limparRepositorio() {
        imovelRepositoryFake.limpar();
    }

    private static RequestPostProcessor comoAna() {
        return como(new ProprietarioAutenticado(ANA, "Ana Souza", "ana@exemplo.com"));
    }

    private static RequestPostProcessor comoBruno() {
        return como(new ProprietarioAutenticado(BRUNO, "Bruno Lima", "bruno@exemplo.com"));
    }

    private static RequestPostProcessor como(ProprietarioAutenticado principal) {
        return authentication(UsernamePasswordAuthenticationToken.authenticated(principal, null, List.of()));
    }

    private Long salvarImovel(Long donoId, String identificador) {
        return imovelRepositoryFake.salvar(
                new Imovel(null, donoId, identificador, "Rua A, 10", new BigDecimal("1500.00"))).getId();
    }

    private static String corpo(String identificador, String valor) {
        return """
                { "identificador": "%s", "endereco": "Rua A, 10", "valorSugerido": %s }
                """.formatted(identificador, valor);
    }

    @Test
    void deveCadastrarImovelParaODonoDaSessaoERetornar201() throws Exception {
        mockMvc.perform(post("/imoveis").with(comoAna()).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(corpo("Apto 101", "1500.00")))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/imoveis/1"))
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.identificador").value("Apto 101"))
                .andExpect(jsonPath("$.endereco").value("Rua A, 10"))
                .andExpect(jsonPath("$.valorSugerido").value(1500.00))
                .andExpect(jsonPath("$.ativo").value(true));

        assertEquals(ANA, imovelRepositoryFake.buscarDoProprietario(1L, ANA).orElseThrow().getUsuarioId());
    }

    @Test
    void deveIgnorarDonoInformadoNoCorpo() throws Exception {
        String json = """
                { "usuarioId": 99, "identificador": "Apto 101", "endereco": "Rua A, 10", "valorSugerido": 1500.00 }
                """;

        mockMvc.perform(post("/imoveis").with(comoAna()).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json))
                .andExpect(status().isCreated());

        assertEquals(true, imovelRepositoryFake.buscarDoProprietario(1L, ANA).isPresent());
        assertEquals(false, imovelRepositoryFake.buscarDoProprietario(1L, 99L).isPresent());
    }

    @Test
    void deveRetornar400ParaValorSugeridoZero() throws Exception {
        mockMvc.perform(post("/imoveis").with(comoAna()).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(corpo("Apto 101", "0")))
                .andExpect(status().isBadRequest());

        assertEquals(0, imovelRepositoryFake.quantidade());
    }

    @Test
    void deveRetornar400ParaIdentificadorEmBranco() throws Exception {
        mockMvc.perform(post("/imoveis").with(comoAna()).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(corpo("   ", "1500.00")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("O identificador do imóvel não pode ser vazio."));
    }

    @Test
    void deveRetornar409ParaIdentificadorRepetido() throws Exception {
        salvarImovel(ANA, "Apto 101");

        mockMvc.perform(post("/imoveis").with(comoAna()).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(corpo("APTO 101", "1500.00")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Você já tem um imóvel com o identificador 'APTO 101'."));
    }

    @Test
    void deveRetornar401SemSessao() throws Exception {
        mockMvc.perform(post("/imoveis").with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(corpo("Apto 101", "1500.00")))
                .andExpect(status().isUnauthorized());

        assertEquals(0, imovelRepositoryFake.quantidade());
    }

    @Test
    void deveRetornar403SemTokenCsrf() throws Exception {
        mockMvc.perform(post("/imoveis").with(comoAna())
                .contentType(MediaType.APPLICATION_JSON)
                .content(corpo("Apto 101", "1500.00")))
                .andExpect(status().isForbidden());

        assertEquals(0, imovelRepositoryFake.quantidade());
    }

    @Test
    void deveConsultarImovelProprio() throws Exception {
        Long id = salvarImovel(ANA, "Apto 101");

        mockMvc.perform(get("/imoveis/{id}", id).with(comoAna()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.identificador").value("Apto 101"))
                .andExpect(jsonPath("$.ativo").value(true));
    }

    @Test
    void deveRetornar404ParaImovelDeOutroDono() throws Exception {
        Long idDaAna = salvarImovel(ANA, "Apto 101");

        mockMvc.perform(get("/imoveis/{id}", idDaAna).with(comoBruno()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Imóvel não encontrado."));
    }

    @Test
    void deveRetornar404ParaIdInexistente() throws Exception {
        mockMvc.perform(get("/imoveis/{id}", 9999).with(comoBruno()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Imóvel não encontrado."));
    }

    @Test
    void deveRetornarCorposIdenticosParaImovelDeOutroDonoEIdInexistente() throws Exception {
        Long idDaAna = salvarImovel(ANA, "Apto 101");

        String corpoOutroDono = mockMvc.perform(get("/imoveis/{id}", idDaAna).with(comoBruno()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Imóvel não encontrado."))
                .andReturn().getResponse().getContentAsString();
        String corpoInexistente = mockMvc.perform(get("/imoveis/{id}", 9999).with(comoBruno()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Imóvel não encontrado."))
                .andReturn().getResponse().getContentAsString();

        assertEquals(corpoInexistente.replace("9999", "ID"), corpoOutroDono.replace(String.valueOf(idDaAna), "ID"));
    }

    @Test
    void deveInativarImovelProprio() throws Exception {
        Long id = salvarImovel(ANA, "Apto 101");

        mockMvc.perform(post("/imoveis/{id}/inativacao", id).with(comoAna()).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ativo").value(false));
        mockMvc.perform(post("/imoveis/{id}/inativacao", id).with(comoAna()).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ativo").value(false));
    }

    @Test
    void deveRetornar404AoInativarImovelDeOutroDono() throws Exception {
        Long idDaAna = salvarImovel(ANA, "Apto 101");

        mockMvc.perform(post("/imoveis/{id}/inativacao", idDaAna).with(comoBruno()).with(csrf()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Imóvel não encontrado."));

        assertEquals(true, imovelRepositoryFake.buscarDoProprietario(idDaAna, ANA).orElseThrow().isAtivo());
    }

    @Test
    void deveRetornar400ParaIdNaoNumerico() throws Exception {
        mockMvc.perform(get("/imoveis/abc").with(comoAna()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Identificador de imóvel inválido."));
    }
}
