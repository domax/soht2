/* SOHT2 © Licensed under MIT 2025. */
package net.soht2.server.config;

import static net.soht2.server.entity.UserEntity.ROLE_ADMIN;
import static org.springframework.security.config.Customizer.withDefaults;

import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.vavr.control.Try;
import java.util.List;
import lombok.val;
import org.springframework.boot.health.actuate.endpoint.HealthEndpoint;
import org.springframework.boot.security.autoconfigure.actuate.web.servlet.EndpointRequest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@SecurityScheme(name = "Basic Authentication", type = SecuritySchemeType.HTTP, scheme = "basic")
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

  private static final String CLIENT_HEADER = "X-SOHT2-Client";
  private static final String CLIENT_VALUE = "soht2-client";

  @SuppressWarnings("java:S3330")
  @Bean
  SecurityFilterChain securityFilterChain(HttpSecurity http) {
    val csrfTokenRepository = CookieCsrfTokenRepository.withHttpOnlyFalse();
    return http.csrf(csrf ->
            csrf.csrfTokenRepository(csrfTokenRepository)
                .csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler())
                .ignoringRequestMatchers(rq -> CLIENT_VALUE.equals(rq.getHeader(CLIENT_HEADER))))
        .cors(withDefaults())
        .httpBasic(withDefaults())
        .authorizeHttpRequests(registry ->
            registry
                .requestMatchers(EndpointRequest.to(HealthEndpoint.class))
                .permitAll()
                .requestMatchers(EndpointRequest.toAnyEndpoint())
                .hasAuthority(ROLE_ADMIN)
                .requestMatchers("/api/**")
                .authenticated()
                .anyRequest()
                .permitAll())
        .build();
  }

  /**
   * Cross-origin access is granted only to origins listed in {@code
   * soht2.server.cors.allowed-origins}; with an empty list no CORS configuration is registered.
   */
  @Bean
  CorsConfigurationSource corsConfigurationSource(Soht2ServerConfig soht2ServerConfig) {
    val source = new UrlBasedCorsConfigurationSource();
    val allowedOrigins = soht2ServerConfig.getCors().getAllowedOrigins();
    if (allowedOrigins != null && !allowedOrigins.isEmpty()) {
      val cc = new CorsConfiguration();
      cc.setAllowedOrigins(List.copyOf(allowedOrigins));
      cc.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
      cc.setAllowedHeaders(
          List.of(
              "Authorization", "Content-Type", "Content-Encoding", "X-XSRF-TOKEN", CLIENT_HEADER));
      cc.setAllowCredentials(false);
      source.registerCorsConfiguration("/**", cc);
    }
    return source;
  }

  @Bean
  PasswordEncoder passwordEncoder() {
    return PasswordEncoderFactories.createDelegatingPasswordEncoder();
  }

  @Bean
  CachingAuthenticationProvider cachingAuthenticationProvider(
      UserDetailsService userDetailsService,
      PasswordEncoder passwordEncoder,
      Soht2ServerConfig soht2ServerConfig) {
    return new CachingAuthenticationProvider(
        userDetailsService, passwordEncoder, soht2ServerConfig.getUserCacheTtl());
  }

  @Bean
  AuthenticationManager authenticationManager(AuthenticationConfiguration config) {
    return Try.of(config::getAuthenticationManager).get();
  }
}
