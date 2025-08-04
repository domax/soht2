/* SOHT2 © Licensed under MIT 2025. */
package net.soht2.client;

import static org.assertj.core.api.Assertions.assertThat;

import net.soht2.client.service.ConnectionRunner;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest
@ActiveProfiles("test")
class Soht2ClientApplicationTests {

  @Autowired ApplicationContext applicationContext;
  @MockitoBean ConnectionRunner connectionRunner;

  @Test
  void contextLoads() {
    assertThat(applicationContext).isNotNull();
  }
}
