package br.com.marden.habitattio.adapters.inbound.security;

import java.util.List;

import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;

import br.com.marden.habitattio.application.usecase.AutenticarProprietarioUseCase;
import br.com.marden.habitattio.domain.exception.CredenciaisInvalidasException;
import br.com.marden.habitattio.domain.model.Usuario;
import br.com.marden.habitattio.ports.inbound.AutenticarProprietarioCommand;

public class ProprietarioAuthenticationProvider implements AuthenticationProvider {

    private final AutenticarProprietarioUseCase autenticarProprietarioUseCase;

    public ProprietarioAuthenticationProvider(AutenticarProprietarioUseCase autenticarProprietarioUseCase) {
        this.autenticarProprietarioUseCase = autenticarProprietarioUseCase;
    }

    @Override
    public Authentication authenticate(Authentication authentication) throws AuthenticationException {
        String email = authentication.getName();
        Object credenciais = authentication.getCredentials();
        String senha = credenciais == null ? null : credenciais.toString();

        try {
            Usuario usuario = autenticarProprietarioUseCase
                    .executar(new AutenticarProprietarioCommand(email, senha));
            ProprietarioAutenticado principal =
                    new ProprietarioAutenticado(usuario.getId(), usuario.getNome(), usuario.getEmail());
            return UsernamePasswordAuthenticationToken.authenticated(principal, null, List.of());
        } catch (CredenciaisInvalidasException e) {
            throw new BadCredentialsException(e.getMessage(), e);
        }
    }

    @Override
    public boolean supports(Class<?> authentication) {
        return UsernamePasswordAuthenticationToken.class.isAssignableFrom(authentication);
    }
}
