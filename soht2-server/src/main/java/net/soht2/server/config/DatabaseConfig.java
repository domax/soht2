/* SOHT2 © Licensed under MIT 2025. */
package net.soht2.server.config;

import net.soht2.server.repository.UserEntityRepository;
import net.soht2.server.service.Soht2UserService;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.transaction.annotation.EnableTransactionManagement;

@EnableTransactionManagement
@EnableJpaRepositories(basePackageClasses = UserEntityRepository.class)
@EnableJpaAuditing
@Configuration
public class DatabaseConfig {

  /**
   * Initializes the SOHT2 server by checking if the admin user exists.
   *
   * <p>This method is executed at application startup to ensure that the default admin user is
   * present in the database.
   *
   * @param soht2UserService the service to interact with the SOHT2 server
   * @return a CommandLineRunner that checks for the admin user
   */
  @Bean
  CommandLineRunner initDatabase(Soht2UserService soht2UserService) {
    return args -> soht2UserService.checkAdminUserExists();
  }
}
