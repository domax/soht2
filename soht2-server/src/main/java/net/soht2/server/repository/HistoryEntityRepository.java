/* SOHT2 © Licensed under MIT 2025. */
package net.soht2.server.repository;

import java.time.LocalDateTime;
import java.util.Collection;
import net.soht2.server.entity.HistoryEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Repository;

@SuppressWarnings("java:S107")
@Repository
public interface HistoryEntityRepository extends JpaRepository<HistoryEntity, Long> {

  String QRY_FIND_ALL =
      """
      FROM soht2_history h
      WHERE (:userName IS NULL OR LOWER(h.user_name) LIKE LOWER(:userName) ESCAPE '\\')
        AND (:connectionId IS NULL OR LOWER(CAST(h.connection_id AS VARCHAR)) LIKE LOWER(:connectionId) ESCAPE '\\')
        AND (:clientHost IS NULL OR LOWER(h.client_host) LIKE LOWER(:clientHost) ESCAPE '\\')
        AND (:targetHost IS NULL OR LOWER(h.target_host) LIKE LOWER(:targetHost) ESCAPE '\\')
        AND (:#{#targetPorts.isEmpty()} = true OR h.target_port IN :targetPorts)
        AND (:openedAfter IS NULL OR h.opened_at >= :openedAfter)
        AND (:openedBefore IS NULL OR h.opened_at <= :openedBefore)
        AND (:closedAfter IS NULL OR h.closed_at >= :closedAfter)
        AND (:closedBefore IS NULL OR h.closed_at <= :closedBefore)
      """;

  @Query(value = "SELECT * " + QRY_FIND_ALL, nativeQuery = true)
  Page<HistoryEntity> findAll(
      @Param("userName") @Nullable String userName,
      @Param("connectionId") @Nullable String connectionId,
      @Param("clientHost") @Nullable String clientHost,
      @Param("targetHost") @Nullable String targetHost,
      @Param("targetPorts") Collection<Integer> targetPorts,
      @Param("openedAfter") @Nullable LocalDateTime openedAfter,
      @Param("openedBefore") @Nullable LocalDateTime openedBefore,
      @Param("closedAfter") @Nullable LocalDateTime closedAfter,
      @Param("closedBefore") @Nullable LocalDateTime closedBefore,
      Pageable pageable);

  @Query(value = "SELECT COUNT(*) " + QRY_FIND_ALL, nativeQuery = true)
  long countAll(
      @Param("userName") @Nullable String userName,
      @Param("connectionId") @Nullable String connectionId,
      @Param("clientHost") @Nullable String clientHost,
      @Param("targetHost") @Nullable String targetHost,
      @Param("targetPorts") Collection<Integer> targetPorts,
      @Param("openedAfter") @Nullable LocalDateTime openedAfter,
      @Param("openedBefore") @Nullable LocalDateTime openedBefore,
      @Param("closedAfter") @Nullable LocalDateTime closedAfter,
      @Param("closedBefore") @Nullable LocalDateTime closedBefore);

  void deleteAllByUserNameIgnoreCase(String userName);
}
