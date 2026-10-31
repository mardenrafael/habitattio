package br.com.marden.habitattio.adapters.inbound.rest;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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

import br.com.marden.habitattio.adapters.config.SegurancaConfiguration;
import br.com.marden.habitattio.adapters.inbound.security.ProprietarioAuthenticationProvider;
import br.com.marden.habitattio.adapters.inbound.security.ProprietarioAutenticado;
import br.com.marden.habitattio.application.usecase.AutenticarProprietarioUseCase;
import br.com.marden.habitattio.fakes.CodificadorSenhaFake;
import br.com.marden.habitattio.fakes.UsuarioRepositoryFake;

@WebMvcTest(SessaoController.class)
@Import({ SegurancaConfiguration.class, SessaoControllerTest.Config.class })
class SessaoControllerTest {

    @TestConfiguration
    static class Config {

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

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UsuarioRepositoryFake usuarioRepositoryFake;

    @BeforeEach
    void limparRepositorio() {
        usuarioRepositoryFake.limpar();
    }

    @Test
    void deveRetornarProprietarioDaSessao() throws Exception {
        ProprietarioAutenticado principal = new ProprietarioAutenticado(1L, "Ana Souza", "ana@exemplo.com");

        mockMvc.perform(get("/sessao").with(authentication(
                UsernamePasswordAuthenticationToken.authenticated(principal, null, List.of()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.nome").value("Ana Souza"))
                .andExpect(jsonPath("$.email").value("ana@exemplo.com"));
    }

    @Test
    void deveRetornar401SemSessaoComCookieXsrfToken() throws Exception {
        mockMvc.perform(get("/sessao"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value("Autenticação necessária."))
                .andExpect(cookie().exists("XSRF-TOKEN"));
    }
}
