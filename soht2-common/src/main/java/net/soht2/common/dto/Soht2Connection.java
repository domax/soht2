/* SOHT2 © Licensed under MIT 2025. */
package net.soht2.common.dto;

import static java.util.Optional.ofNullable;

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
  public Soht2Connection(
      UUID id,
      Soht2User user,
      String clientHost,
      String targetHost,
      Integer targetPort,
      LocalDateTime openedAt,
      LocalDateTime closedAt) {
    this.id = ofNullable(id).orElseGet(UUID::randomUUID);
    this.user = user;
    this.clientHost = clientHost;
    this.targetHost = targetHost;
    this.targetPort = targetPort;
    this.openedAt = ofNullable(openedAt).orElseGet(LocalDateTime::now);
    this.closedAt = closedAt;
  }

  public Soht2Connection withUser(Soht2User user) {
    return new Soht2Connection(id, user, clientHost, targetHost, targetPort, openedAt, closedAt);
  }

  public Soht2Connection withClosedAt(LocalDateTime closedAt) {
    return new Soht2Connection(id, user, clientHost, targetHost, targetPort, openedAt, closedAt);
  }
}
