package br.com.marden.habitattio.adapters.inbound.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

import br.com.marden.habitattio.application.usecase.AutenticarProprietarioUseCase;
import br.com.marden.habitattio.application.usecase.CadastrarProprietarioUseCase;
import br.com.marden.habitattio.domain.model.Usuario;
import br.com.marden.habitattio.fakes.CodificadorSenhaFake;
import br.com.marden.habitattio.fakes.UsuarioRepositoryFake;
import br.com.marden.habitattio.ports.inbound.CadastrarProprietarioCommand;

class ProprietarioAuthenticationProviderTest {

    private Usuario cadastrado;
    private ProprietarioAuthenticationProvider provider;

    @BeforeEach
    void preparar() {
        UsuarioRepositoryFake repositorio = new UsuarioRepositoryFake();
        CodificadorSenhaFake codificador = new CodificadorSenhaFake();
        cadastrado = new CadastrarProprietarioUseCase(repositorio, codificador)
                .executar(new CadastrarProprietarioCommand("Ana Souza", "ana@exemplo.com", "senha-segura-1"));
        provider = new ProprietarioAuthenticationProvider(
                new AutenticarProprietarioUseCase(repositorio, codificador));
    }

    @Test
    void deveDevolverAutenticacaoComProprietarioAutenticadoSemSenha() {
        Authentication entrada =
                UsernamePasswordAuthenticationToken.unauthenticated("ana@exemplo.com", "senha-segura-1");

        Authentication resultado = provider.authenticate(entrada);

        assertTrue(resultado.isAuthenticated());
        assertNull(resultado.getCredentials());
        ProprietarioAutenticado principal = assertInstanceOf(ProprietarioAutenticado.class, resultado.getPrincipal());
        assertNotNull(principal.id());
        assertEquals(cadastrado.getId(), principal.id());
        assertEquals("Ana Souza", principal.nome());
        assertEquals("ana@exemplo.com", principal.email());
    }

    @Test
    void deveLancarBadCredentialsParaCredenciaisInvalidas() {
        Authentication entrada =
                UsernamePasswordAuthenticationToken.unauthenticated("ana@exemplo.com", "senha-errada-9");

        assertThrows(BadCredentialsException.class, () -> provider.authenticate(entrada));
    }

    @Test
    void deveSuportarUsernamePasswordAuthenticationToken() {
        boolean suporta = provider.supports(UsernamePasswordAuthenticationToken.class);

        assertTrue(suporta);
    }
}
