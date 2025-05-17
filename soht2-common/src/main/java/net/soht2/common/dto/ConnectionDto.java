/* SOHT2 © Licensed under MIT 2025. */
package net.soht2.common.dto;

import java.time.LocalDateTime;
import java.util.UUID;
import lombok.Builder;

public record ConnectionDto(
    UUID id,
    String username,
    String clientHost,
    String targetHost,
    Integer targetPort,
    LocalDateTime openedAt) {

  @Builder
  private ConnectionDto(String username, String clientHost, String targetHost, Integer targetPort) {
    this(UUID.randomUUID(), username, clientHost, targetHost, targetPort, LocalDateTime.now());
  }
}
