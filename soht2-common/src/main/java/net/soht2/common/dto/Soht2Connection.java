/* SOHT2 © Licensed under MIT 2025. */
package net.soht2.common.dto;

import static java.util.Optional.ofNullable;

import java.time.LocalDateTime;
import java.util.UUID;
import lombok.Builder;
import net.soht2.common.util.AuxUtil;

public record Soht2Connection(
    UUID id,
    Soht2User user,
    String clientHost,
    String targetHost,
    Integer targetPort,
    LocalDateTime openedAt,
    LocalDateTime closedAt,
    Long bytesRead,
    Long bytesWritten) {

  @Builder
  public Soht2Connection(
      UUID id,
      Soht2User user,
      String clientHost,
      String targetHost,
      Integer targetPort,
      LocalDateTime openedAt,
      LocalDateTime closedAt,
      Long bytesRead,
      Long bytesWritten) {
    this.id = ofNullable(id).orElseGet(AuxUtil::generateUUIDv7);
    this.user = user;
    this.clientHost = clientHost;
    this.targetHost = targetHost;
    this.targetPort = targetPort;
    this.openedAt = ofNullable(openedAt).orElseGet(LocalDateTime::now);
    this.closedAt = closedAt;
    this.bytesRead = ofNullable(bytesRead).orElse(0L);
    this.bytesWritten = ofNullable(bytesWritten).orElse(0L);
  }

  public Soht2Connection withUser(Soht2User user) {
    return new Soht2Connection(
        id, user, clientHost, targetHost, targetPort, openedAt, closedAt, bytesRead, bytesWritten);
  }

  public Soht2Connection withClosedAt(LocalDateTime closedAt) {
    return new Soht2Connection(
        id, user, clientHost, targetHost, targetPort, openedAt, closedAt, bytesRead, bytesWritten);
  }

  public Soht2Connection withBytesExchanged(Long bytesRead, Long bytesWritten) {
    return new Soht2Connection(
        id, user, clientHost, targetHost, targetPort, openedAt, closedAt, bytesRead, bytesWritten);
  }
}
