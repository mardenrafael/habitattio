package br.com.marden.habitattio.adapters.inbound.security;

import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;

import br.com.marden.habitattio.application.usecase.AutenticarProprietarioUseCase;

public class ProprietarioAuthenticationProvider implements AuthenticationProvider {

    private final AutenticarProprietarioUseCase autenticarProprietarioUseCase;

    public ProprietarioAuthenticationProvider(AutenticarProprietarioUseCase autenticarProprietarioUseCase) {
        this.autenticarProprietarioUseCase = autenticarProprietarioUseCase;
    }

    @Override
    public Authentication authenticate(Authentication authentication) throws AuthenticationException {
        throw new UnsupportedOperationException("Não implementado");
    }

    @Override
    public boolean supports(Class<?> authentication) {
        return false;
    }
}
