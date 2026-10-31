package br.com.marden.habitattio.adapters.inbound.security;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.web.servlet.HandlerExceptionResolver;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Delega 401/403 ao HandlerExceptionResolver do MVC, para que a resposta
 * saia pelo TratamentoErrosHandler (ProblemDetail em português).
 */
public class RespostasSeguranca
        implements AccessDeniedHandler, AuthenticationEntryPoint, AuthenticationFailureHandler,
        AuthenticationSuccessHandler {

    private final HandlerExceptionResolver resolver;

    public RespostasSeguranca(HandlerExceptionResolver resolver) {
        this.resolver = resolver;
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
            AccessDeniedException accessDeniedException) {
        resolver.resolveException(request, response, null, accessDeniedException);
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
            AuthenticationException authException) {
        resolver.resolveException(request, response, null, authException);
    }

    @Override
    public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response,
            AuthenticationException exception) {
        resolver.resolveException(request, response, null, exception);
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
            Authentication authentication) {
        response.setStatus(HttpServletResponse.SC_NO_CONTENT);
    }
}
