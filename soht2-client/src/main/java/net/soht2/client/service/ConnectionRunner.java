/* SOHT2 © Licensed under MIT 2025. */
package net.soht2.client.service;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Service;

@RequiredArgsConstructor
@Service
public class ConnectionRunner implements CommandLineRunner {

  private final ConnectionService connectionService;

  @Override
  public void run(String... args) {
    connectionService.startConnections().join();
  }
}
