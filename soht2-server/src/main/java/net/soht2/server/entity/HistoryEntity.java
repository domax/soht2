/* SOHT2 © Licensed under MIT 2025. */
package net.soht2.server.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;
import lombok.*;
import net.soht2.common.dto.Soht2Connection;
import net.soht2.common.dto.Soht2User;
import org.hibernate.proxy.HibernateProxy;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@Entity
@EntityListeners(AuditingEntityListener.class)
@Table(name = "soht2_history")
@Getter
@Setter
@ToString
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@NoArgsConstructor
public class HistoryEntity {

  @Id
  @Column(name = "history_id", nullable = false, updatable = false)
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "user_name", nullable = false, updatable = false)
  private String userName;

  @Column(name = "connection_id", nullable = false, updatable = false)
  private UUID connectionId;

  @Column(name = "client_host", nullable = false, updatable = false)
  private String clientHost;

  @Column(name = "target_host", nullable = false, updatable = false)
  private String targetHost;

  @Column(name = "target_port", nullable = false, updatable = false)
  private Integer targetPort;

  @Column(name = "opened_at", nullable = false, updatable = false)
  private LocalDateTime openedAt;

  @Column(name = "closed_at", nullable = false, updatable = false)
  private LocalDateTime closedAt;

  @Column(name = "bytes_read", nullable = false, updatable = false)
  private Long bytesRead;

  @Column(name = "bytes_written", nullable = false, updatable = false)
  private Long bytesWritten;

  @SuppressWarnings("java:S2097")
  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null) return false;
    val oEffectiveClass =
        o instanceof HibernateProxy hp
            ? hp.getHibernateLazyInitializer().getPersistentClass()
            : o.getClass();
    val thisEffectiveClass =
        this instanceof HibernateProxy hp
            ? hp.getHibernateLazyInitializer().getPersistentClass()
            : this.getClass();
    if (thisEffectiveClass != oEffectiveClass) return false;
    val that = (HistoryEntity) o;
    return Objects.equals(id, that.id)
        && Objects.equals(userName, that.userName)
        && Objects.equals(connectionId, that.connectionId)
        && Objects.equals(clientHost, that.clientHost)
        && Objects.equals(targetHost, that.targetHost)
        && Objects.equals(targetPort, that.targetPort)
        && Objects.equals(openedAt, that.openedAt)
        && Objects.equals(closedAt, that.closedAt)
        && Objects.equals(bytesRead, that.bytesRead)
        && Objects.equals(bytesWritten, that.bytesWritten);
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        id, userName, connectionId, clientHost, targetHost, targetPort, openedAt, closedAt);
  }

  public Soht2Connection toSoht2Connection() {
    return Soht2Connection.builder()
        .id(connectionId)
        .user(Soht2User.builder().username(userName).build())
        .clientHost(clientHost)
        .targetHost(targetHost)
        .targetPort(targetPort)
        .openedAt(openedAt)
        .closedAt(closedAt)
        .bytesRead(bytesRead)
        .bytesWritten(bytesWritten)
        .build();
  }
}
