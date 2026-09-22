package com.distribuidora.backend.config;

import com.distribuidora.backend.security.JwtAuthFilter;
import com.distribuidora.backend.security.LoginRateLimitFilter;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

// Regras por URL para o catalogo legado; modulos do ERP usam @PreAuthorize
// com a permissao no proprio controller.
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private static final String PRODUTOS_EDITAR = "produtos.editar";

    private final JwtAuthFilter jwtAuthFilter;
    private final LoginRateLimitFilter loginRateLimitFilter;
    private final UserDetailsService userDetailsService;
    private final List<String> corsAllowedOrigins;

    public SecurityConfig(
            JwtAuthFilter jwtAuthFilter,
            LoginRateLimitFilter loginRateLimitFilter,
            UserDetailsService userDetailsService,
            @Value("${app.cors.allowed-origins}") List<String> corsAllowedOrigins) {
        this.jwtAuthFilter = jwtAuthFilter;
        this.loginRateLimitFilter = loginRateLimitFilter;
        this.userDetailsService = userDetailsService;
        this.corsAllowedOrigins = corsAllowedOrigins;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public DaoAuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
        provider.setUserDetailsService(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder());
        return provider;
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                // Sem login (ou token vencido) = 401; logado sem permissao = 403.
                // O front usa essa diferenca para mandar de volta ao login.
                .exceptionHandling(ex -> ex.authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
                .authorizeHttpRequests(auth -> auth
                        // pagina de erro padrao do Spring Boot: precisa ser publica, senao
                        // qualquer erro 5xx num request anonimo vira 403 (o dispatch interno
                        // para /error e barrado pelo anyRequest().authenticated() abaixo),
                        // mascarando o erro real.
                        .requestMatchers("/error").permitAll()
                        // login/cadastro sao publicos
                        .requestMatchers("/api/auth/**").permitAll()
                        // console do H2, so para desenvolvimento
                        .requestMatchers("/h2-console/**").permitAll()
                        // documentacao da API (Swagger/OpenAPI) e publica
                        .requestMatchers(
                                "/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**")
                        .permitAll()
                        // catalogo (produtos e categorias) e publico para visualizacao
                        .requestMatchers(HttpMethod.GET, "/api/products/**", "/api/categories/**").permitAll()
                        // imagens de produto enviadas pelo admin sao publicas (qualquer
                        // visitante precisa ver a foto no catalogo)
                        .requestMatchers(HttpMethod.GET, "/uploads/**").permitAll()
                        // alterar produtos/precos e enviar imagem de produto
                        .requestMatchers(HttpMethod.POST, "/api/products/**").hasAuthority(PRODUTOS_EDITAR)
                        .requestMatchers(HttpMethod.PUT, "/api/products/**").hasAuthority(PRODUTOS_EDITAR)
                        .requestMatchers(HttpMethod.PATCH, "/api/products/**").hasAuthority(PRODUTOS_EDITAR)
                        .requestMatchers(HttpMethod.DELETE, "/api/products/**").hasAuthority(PRODUTOS_EDITAR)
                        .requestMatchers(HttpMethod.POST, "/api/uploads/**").hasAuthority(PRODUTOS_EDITAR)
                        // um usuario logado pode ver as proprias mensagens enviadas
                        .requestMatchers(HttpMethod.GET, "/api/contacts/mine").authenticated()
                        // ver todas as mensagens recebidas
                        .requestMatchers(HttpMethod.GET, "/api/contacts/**").hasAuthority("contatos.ver")
                        // enviar mensagem de contato exige estar logado (ADMIN ou USER)
                        .requestMatchers(HttpMethod.POST, "/api/contacts/**").authenticated()
                        .anyRequest().authenticated())
                .headers(headers -> headers.frameOptions(frameOptions -> frameOptions.sameOrigin())) // necessario p/ h2-console
                .authenticationProvider(authenticationProvider())
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(loginRateLimitFilter, JwtAuthFilter.class);

        return http.build();
    }

    // JwtAuthFilter e LoginRateLimitFilter ja sao adicionados explicitamente
    // na cadeia do Spring Security acima (addFilterBefore). Sem isto, o
    // Spring Boot tambem os registraria automaticamente como filtros de
    // servlet genericos (por serem beans Filter), fazendo cada um rodar
    // duas vezes por requisicao - inofensivo pro JWT (idempotente), mas
    // faria o rate limit do login consumir 2 tentativas por request real.
    @Bean
    public FilterRegistrationBean<JwtAuthFilter> jwtAuthFilterRegistration(JwtAuthFilter filter) {
        FilterRegistrationBean<JwtAuthFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }

    @Bean
    public FilterRegistrationBean<LoginRateLimitFilter> loginRateLimitFilterRegistration(LoginRateLimitFilter filter) {
        FilterRegistrationBean<LoginRateLimitFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        // Origens permitidas configuraveis via app.cors.allowed-origins /
        // CORS_ALLOWED_ORIGINS (ver application.properties).
        configuration.setAllowedOrigins(corsAllowedOrigins);
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
