/* SOHT2 © Licensed under MIT 2025. */
package net.soht2.common.dto;

import static java.util.UUID.randomUUID;

import java.time.LocalDateTime;
import java.util.UUID;
import lombok.Builder;

public record Soht2Connection(
    UUID id,
    Soht2User user,
    String clientHost,
    String targetHost,
    Integer targetPort,
    LocalDateTime openedAt,
    LocalDateTime closedAt) {

  @Builder
  private Soht2Connection(
      Soht2User user, String clientHost, String targetHost, Integer targetPort) {
    this(randomUUID(), user, clientHost, targetHost, targetPort, LocalDateTime.now(), null);
  }

  public Soht2Connection withUser(Soht2User user) {
    return new Soht2Connection(id, user, clientHost, targetHost, targetPort, openedAt, closedAt);
  }

  public Soht2Connection withClosedAt(LocalDateTime closedAt) {
    return new Soht2Connection(id, user, clientHost, targetHost, targetPort, openedAt, closedAt);
  }
}
