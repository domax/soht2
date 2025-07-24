/* SOHT2 © Licensed under MIT 2025. */
package net.soht2.server.config;

import static net.soht2.server.entity.UserEntity.ROLE_ADMIN;
import static org.springframework.security.config.Customizer.withDefaults;

import io.vavr.control.Try;
import lombok.val;
import org.springframework.boot.actuate.autoconfigure.endpoint.web.CorsEndpointProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.CsrfConfigurer;
import org.springframework.security.config.annotation.web.configurers.HeadersConfigurer;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

  @Bean
  SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
    return http.csrf(CsrfConfigurer::disable)
        .cors(withDefaults())
        .httpBasic(withDefaults())
        .headers(h -> h.frameOptions(HeadersConfigurer.FrameOptionsConfig::disable))
        .authorizeHttpRequests(
            registry ->
                registry
                    .requestMatchers("/api/**")
                    .authenticated()
                    .requestMatchers("/h2-console/**")
                    .hasAuthority(ROLE_ADMIN)
                    .anyRequest()
                    .permitAll())
        .build();
  }

  @Bean
  CorsConfigurationSource corsConfigurationSource(CorsEndpointProperties corsEndpointProperties) {
    val configuration = new UrlBasedCorsConfigurationSource();
    configuration.registerCorsConfiguration("/**", corsEndpointProperties.toCorsConfiguration());
    return configuration;
  }

  @Bean
  PasswordEncoder passwordEncoder() {
    return PasswordEncoderFactories.createDelegatingPasswordEncoder();
  }

  @Bean
  AuthenticationManager authenticationManager(AuthenticationConfiguration config) {
    return Try.of(config::getAuthenticationManager).get();
  }
}
