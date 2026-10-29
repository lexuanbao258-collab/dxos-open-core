package com.tricore.dxos.portal;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientProviderBuilder;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.web.HttpSessionOAuth2AuthorizedClientRepository;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizedClientRepository;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestCustomizers;
import org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.client.oidc.web.logout.OidcClientInitiatedLogoutSuccessHandler;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;

@Configuration(proxyBeanMethods = false)
class PortalSecurity {
    @Bean
    SecurityFilterChain portalFilterChain(HttpSecurity http, ClientRegistrationRepository registrations,
            @Value("${dxos.portal-bff.public-origin}") String publicOrigin) throws Exception {
        String origin = PortalOrigin.checked(publicOrigin);
        var registration = registrations.findByRegistrationId("keycloak");
        if (registration == null || registration.getClientSecret() == null
                || registration.getClientSecret().isBlank()
                || !AuthorizationGrantType.AUTHORIZATION_CODE.equals(registration.getAuthorizationGrantType())
                || !(origin + "/login/oauth2/code/keycloak").equals(registration.getRedirectUri())) {
            throw new IllegalArgumentException("Portal OIDC client must be confidential with the pinned HTTPS callback");
        }
        var resolver = new DefaultOAuth2AuthorizationRequestResolver(registrations, "/oauth2/authorization");
        resolver.setAuthorizationRequestCustomizer(OAuth2AuthorizationRequestCustomizers.withPkce());
        var logout = new OidcClientInitiatedLogoutSuccessHandler(registrations);
        logout.setPostLogoutRedirectUri(origin + "/");
        http.authorizeHttpRequests(authorize -> authorize
                .requestMatchers(HttpMethod.GET, "/bff/session").permitAll()
                .requestMatchers("/bff/api/**").authenticated()
                .requestMatchers(HttpMethod.POST, "/bff/logout").authenticated()
                .requestMatchers("/bff/**").denyAll()
                .anyRequest().permitAll())
            .oauth2Login(oauth -> oauth.authorizationEndpoint(endpoint -> endpoint.authorizationRequestResolver(resolver)))
            .httpBasic(AbstractHttpConfigurer::disable)
            .formLogin(AbstractHttpConfigurer::disable)
            .exceptionHandling(exceptions -> exceptions.defaultAuthenticationEntryPointFor(
                    new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED),
                    request -> request.getServletPath().startsWith("/bff/api/")))
            .logout(config -> config.logoutUrl("/bff/logout")
                    .invalidateHttpSession(true).deleteCookies("__Host-DXOS-Portal")
                    .logoutSuccessHandler(logout));
        // CSRF remains enabled. The session endpoint supplies the token for same-origin writes.
        return http.build();
    }

    @Bean
    OAuth2AuthorizedClientRepository authorizedClientRepository() {
        return new HttpSessionOAuth2AuthorizedClientRepository();
    }

    @Bean
    OAuth2AuthorizedClientManager authorizedClientManager(
            ClientRegistrationRepository registrations, OAuth2AuthorizedClientRepository repository) {
        var manager = new DefaultOAuth2AuthorizedClientManager(registrations, repository);
        manager.setAuthorizedClientProvider(OAuth2AuthorizedClientProviderBuilder.builder()
                .authorizationCode().refreshToken().build());
        return manager;
    }
}
