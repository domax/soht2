/* SOHT2 © Licensed under MIT 2025. */
package net.soht2.server.config;

import static org.springframework.security.config.Customizer.withDefaults;

import lombok.val;
import org.springframework.boot.actuate.autoconfigure.endpoint.web.CorsEndpointProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.CsrfConfigurer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

  @Bean
  SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
    return http.csrf(CsrfConfigurer::disable)
        .cors(withDefaults())
        .httpBasic(withDefaults())
        .authorizeHttpRequests(
            registry ->
                registry
                    .requestMatchers("/api/**")
                    // .authenticated()
                    .permitAll()
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
}
