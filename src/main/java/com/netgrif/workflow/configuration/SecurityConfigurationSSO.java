package com.netgrif.workflow.configuration;

import com.netgrif.workflow.auth.domain.Authority;
import com.netgrif.workflow.auth.service.OauthUserService;
import com.netgrif.workflow.auth.service.UserService;
import com.netgrif.workflow.auth.service.interfaces.IAuthorityService;
import com.netgrif.workflow.auth.service.interfaces.IUserService;
import com.netgrif.workflow.configuration.properties.SecurityConfigProperties;
import com.netgrif.workflow.configuration.security.PublicAuthenticationFilter;
import com.netgrif.workflow.configuration.security.RestAuthenticationEntryPoint;
import com.netgrif.workflow.configuration.security.jwt.IJwtService;
import com.netgrif.workflow.petrinet.service.interfaces.IProcessRoleService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.security.SecurityProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AnonymousAuthenticationProvider;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizedClientRepository;
import org.springframework.security.oauth2.client.web.reactive.function.client.ServletOAuth2AuthorizedClientExchangeFilterFunction;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.web.authentication.logout.HttpStatusReturningLogoutSuccessHandler;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;
import org.springframework.security.web.header.writers.StaticHeadersWriter;
import org.springframework.session.web.http.HeaderHttpSessionIdResolver;
import org.springframework.session.web.http.HttpSessionIdResolver;
import org.springframework.stereotype.Controller;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.HashSet;

import static org.springframework.http.HttpMethod.OPTIONS;

@Slf4j
@Configuration
@Controller
@EnableWebSecurity
@Order(SecurityProperties.BASIC_AUTH_ORDER)
@ConditionalOnProperty(
        value = "server.security.static.enabled",
        havingValue = "false"
)
public class SecurityConfigurationSSO extends AbstractSecurityConfiguration {

    @Autowired
    private Environment env;

    @Autowired
    private RestAuthenticationEntryPoint authenticationEntryPoint;

    @Autowired
    private IAuthorityService authorityService;

    @Autowired
    private IJwtService jwtService;

    @Autowired
    private IProcessRoleService roleService;

    @Autowired
    private IUserService userService;

    @Autowired
    private SecurityConfigProperties properties;

    @Value("${nae.security.server-patterns}")
    private String[] serverPatterns;

    private static final String ANONYMOUS_USER = "anonymousUser";

    @Bean
    public HttpSessionIdResolver httpSessionIdResolver() {
        return HeaderHttpSessionIdResolver.xAuthToken();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration().applyPermitDefaultValues();
        config.addAllowedMethod("*");
        config.addAllowedHeader("*");
        config.addExposedHeader("X-Auth-Token");
        config.addExposedHeader("X-Jwt-Token");
        config.addAllowedOrigin("*");
        config.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);

        return source;
    }

    @Override
    protected void configure(HttpSecurity http) throws Exception {
        log.info("Configuration with frontend separated");
//        @formatter:off
        http
                .httpBasic()
                .authenticationEntryPoint(authenticationEntryPoint)
                .and()
                .cors()
                .and()
                .addFilterBefore(createPublicAuthenticationFilter(), BasicAuthenticationFilter.class)
                .authorizeRequests()
                .antMatchers(getPatterns()).permitAll()
                .antMatchers(OPTIONS).permitAll()
                .anyRequest().authenticated()
                .and()
                .logout()
                .logoutUrl("/api/auth/logout")
                .invalidateHttpSession(true)
                .logoutSuccessHandler((new HttpStatusReturningLogoutSuccessHandler(HttpStatus.OK)))
                .and()
                .headers()
                .frameOptions().disable()
                .httpStrictTransportSecurity().includeSubDomains(true).maxAgeInSeconds(31536000)
                .and()
                .addHeaderWriter(new StaticHeadersWriter("X-Content-Security-Policy", "frame-src: 'none'"))
                .and()
                .oauth2Login()
//                .userInfoEndpoint()
//                .oidcUserService(new CustomOidcUserServiceImpl())
        ;
//        @formatter:on
        setCsrf(http);
    }

//    public class CustomOidcUserServiceImpl implements OAuth2UserService<OidcUserRequest, OidcUser> {
//
//        private OidcUserService oidcUserService = new OidcUserService();
//
//        @Override
//        public OidcUser loadUser(OidcUserRequest userRequest) throws OAuth2AuthenticationException {
//            OidcUser oidcUser = oidcUserService.loadUser(userRequest);
//            return oidcUser;
//        }
//    }

    @Bean
    WebClient webClient(ClientRegistrationRepository clientRegistrationRepository,
                        OAuth2AuthorizedClientRepository authorizedClientRepository) {
        ServletOAuth2AuthorizedClientExchangeFilterFunction oauth2 =
                new ServletOAuth2AuthorizedClientExchangeFilterFunction(clientRegistrationRepository,
                        authorizedClientRepository);
        oauth2.setDefaultOAuth2AuthorizedClient(true);
        return WebClient.builder().apply(oauth2.oauth2Configuration()).build();
    }

    @Override
    protected ProviderManager authenticationManager() throws Exception {
        return (ProviderManager) super.authenticationManager();
    }

    @Override
    boolean isOpenRegistration() {
        return this.serverAuthProperties.isOpenRegistration();
    }

    @Override
    boolean isCsrfEnabled() {
        return properties.isCsrf();
    }

    @Override
    String[] getStaticPatterns() {
        return new String[]{
                "/**/favicon.ico", "/favicon.ico", "/**/manifest.json", "/manifest.json", "/configuration/**", "/swagger-resources/**", "/swagger-ui.html", "/webjars/**"
        };
    }

    @Override
    String[] getServerPatterns() {
        return this.serverPatterns;
    }

    @Override
    Environment getEnvironment() {
        return env;
    }

    private PublicAuthenticationFilter createPublicAuthenticationFilter() throws Exception {
        Authority authority = authorityService.getOrCreate(Authority.anonymous);
        authority.setUsers(new HashSet<>());
        return new PublicAuthenticationFilter(
                authenticationManager(),
                new AnonymousAuthenticationProvider(ANONYMOUS_USER),
                authority,
                this.serverPatterns,
                this.jwtService,
                this.userService
        );
    }
}