package br.com.marden.habitattio.adapters.inbound.rest;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import br.com.marden.habitattio.adapters.config.SegurancaConfiguration;
import br.com.marden.habitattio.application.usecase.CadastrarProprietarioUseCase;
import br.com.marden.habitattio.fakes.CodificadorSenhaFake;
import br.com.marden.habitattio.fakes.UsuarioRepositoryFake;
import br.com.marden.habitattio.ports.inbound.CadastrarProprietarioCommand;

@WebMvcTest(ProprietarioController.class)
@Import({ SegurancaConfiguration.class, ProprietarioControllerTest.Config.class })
class ProprietarioControllerTest {

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
        CadastrarProprietarioUseCase cadastrarProprietarioUseCase(UsuarioRepositoryFake repositorio,
                CodificadorSenhaFake codificador) {
            return new CadastrarProprietarioUseCase(repositorio, codificador);
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CadastrarProprietarioUseCase cadastrarProprietarioUseCase;

    @Autowired
    private UsuarioRepositoryFake usuarioRepositoryFake;

    @BeforeEach
    void limparRepositorio() {
        usuarioRepositoryFake.limpar();
    }

    private static String json(String nome, String email, String senha) {
        return "{\"nome\":\"" + nome + "\",\"email\":\"" + email + "\",\"senha\":\"" + senha + "\"}";
    }

    @Test
    void deveCriarProprietarioERetornar201SemSenhaNemHash() throws Exception {
        String corpo = json("Ana Souza", "Ana@Exemplo.com ", "segredo123");

        mockMvc.perform(post("/proprietarios").with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(corpo))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.nome").value("Ana Souza"))
                .andExpect(jsonPath("$.email").value("ana@exemplo.com"))
                .andExpect(content().string(not(containsString("senha"))))
                .andExpect(content().string(not(containsString("hash"))));
    }

    @Test
    void deveRetornar409ParaEmailJaCadastrado() throws Exception {
        cadastrarProprietarioUseCase.executar(
                new CadastrarProprietarioCommand("Ana Souza", "ana@exemplo.com", "segredo123"));
        String corpo = json("Outra Ana", "ANA@exemplo.com", "outrasenha123");

        mockMvc.perform(post("/proprietarios").with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(corpo))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value("Já existe um proprietário com este e-mail."));
    }

    @Test
    void deveRetornar400ParaSenhaCurta() throws Exception {
        String corpo = json("Ana Souza", "ana@exemplo.com", "curta");

        mockMvc.perform(post("/proprietarios").with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(corpo))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value("A senha deve ter no mínimo 8 caracteres."));
    }

    @Test
    void deveRetornar400ParaEmailInvalido() throws Exception {
        String corpo = json("Ana Souza", "sem-arroba", "segredo123");

        mockMvc.perform(post("/proprietarios").with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(corpo))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value("O e-mail do usuário não é válido."));
    }

    @Test
    void deveRetornar400ParaNomeEmBranco() throws Exception {
        String corpo = json("   ", "ana@exemplo.com", "segredo123");

        mockMvc.perform(post("/proprietarios").with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(corpo))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value("O nome do usuário não pode ser vazio."));
    }

    @Test
    void deveRetornar400ParaJsonMalformado() throws Exception {
        String corpo = "{\"nome\": \"Ana\", ";

        mockMvc.perform(post("/proprietarios").with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(corpo))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value("Corpo da requisição inválido."));
    }

    @Test
    void deveRetornar403SemTokenCsrf() throws Exception {
        String corpo = json("Ana Souza", "ana@exemplo.com", "segredo123");

        mockMvc.perform(post("/proprietarios")
                .contentType(MediaType.APPLICATION_JSON).content(corpo))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value("Token CSRF ausente ou inválido."));
    }

    @Test
    void naoDeveExigirAutenticacaoParaCadastrar() throws Exception {
        String corpo = json("Ana Souza", "ana@exemplo.com", "segredo123");

        mockMvc.perform(post("/proprietarios").with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(corpo))
                .andExpect(status().isCreated());
    }
}
