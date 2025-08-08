/* SOHT2 © Licensed under MIT 2025. */
package net.soht2.server.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.Set;
import lombok.*;
import net.soht2.common.dto.Soht2User;
import org.hibernate.proxy.HibernateProxy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@Entity
@EntityListeners(AuditingEntityListener.class)
@Table(name = "soht2_users")
@Getter
@Setter
@ToString
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@NoArgsConstructor
public class UserEntity {

  public static final String ROLE_ADMIN = "ADMIN";
  public static final String ROLE_USER = "USER";
  public static final Set<String> ROLES = Set.of(ROLE_ADMIN, ROLE_USER);

  @Id
  @Column(name = "user_id", nullable = false, updatable = false)
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "user_name", unique = true, nullable = false)
  private String name;

  @ToString.Exclude
  @Column(name = "user_password", nullable = false)
  private String password;

  @Builder.Default
  @Column(name = "user_role", nullable = false)
  private String role = ROLE_USER;

  @Builder.Default
  @Column(name = "targets", nullable = false)
  private Set<String> allowedTargets = Set.of("*:*");

  @CreatedDate
  @Column(name = "created_at", nullable = false, updatable = false)
  private LocalDateTime createdAt;

  @LastModifiedDate
  @Column(name = "updated_at", nullable = false)
  private LocalDateTime updatedAt;

  @SuppressWarnings("java:S2097")
  @Override
  public final boolean equals(Object o) {
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
    val that = (UserEntity) o;
    return Objects.equals(id, that.id)
        && Objects.equals(name, that.name)
        && Objects.equals(password, that.password)
        && Objects.equals(role, that.role)
        && Objects.equals(allowedTargets, that.allowedTargets);
  }

  @Override
  public final int hashCode() {
    return Objects.hash(id, name, password, role, allowedTargets);
  }

  public Soht2User toSoht2User() {
    return Soht2User.builder()
        .username(name)
        .role(role)
        .createdAt(createdAt)
        .updatedAt(updatedAt)
        .allowedTargets(allowedTargets)
        .build();
  }
}
