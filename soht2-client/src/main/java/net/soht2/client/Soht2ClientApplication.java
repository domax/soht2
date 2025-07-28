/* SOHT2 © Licensed under MIT 2025. */
package net.soht2.client;

import lombok.val;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.ApplicationPidFileWriter;

@SpringBootApplication
public class Soht2ClientApplication {

  public static void main(String[] args) {
    val app = new SpringApplication(Soht2ClientApplication.class);
    app.addListeners(new ApplicationPidFileWriter());
    app.run(args);
  }
}
