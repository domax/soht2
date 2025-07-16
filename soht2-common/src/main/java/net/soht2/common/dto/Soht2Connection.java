/* SOHT2 © Licensed under MIT 2025. */
package net.soht2.common.dto;

import java.time.LocalDateTime;
import java.util.UUID;
import lombok.Builder;

public record Soht2Connection(
    UUID id,
    String username,
    String clientHost,
    String targetHost,
    Integer targetPort,
    LocalDateTime openedAt) {

  @Builder
  private Soht2Connection(
      String username, String clientHost, String targetHost, Integer targetPort) {
    this(UUID.randomUUID(), username, clientHost, targetHost, targetPort, LocalDateTime.now());
  }
}
