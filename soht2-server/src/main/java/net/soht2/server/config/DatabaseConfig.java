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
   * Initializes the database with default data or performs necessary setup actions for the
   * application during startup.
   *
   * @param soht2UserService the service used to handle user-related operations, including verifying
   *     the existence of an admin user in the system
   * @return a {@link CommandLineRunner} that executes the required initialization logic
   */
  @Bean
  CommandLineRunner initDatabase(Soht2UserService soht2UserService) {
    return args -> soht2UserService.checkAdminUserExists();
  }
}
