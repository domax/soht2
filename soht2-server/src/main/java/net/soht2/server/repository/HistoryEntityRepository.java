/* SOHT2 © Licensed under MIT 2025. */
package net.soht2.server.repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.UUID;
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
      FROM HistoryEntity h
      WHERE (:#{#userNames.isEmpty()} = true OR LOWER(h.userName) IN :userNames)
        AND (:#{#connectionIds.isEmpty()} = true OR h.connectionId IN :connectionIds)
        AND (:#{#targetPorts.isEmpty()} = true OR h.targetPort IN :targetPorts)
        AND (:clientHost IS NULL OR h.clientHost LIKE %:clientHost%)
        AND (:targetHost IS NULL OR h.targetHost LIKE %:targetHost%)
        AND (:openedAfter IS NULL OR h.openedAt >= :openedAfter)
        AND (:openedBefore IS NULL OR h.openedAt <= :openedBefore)
        AND (:closedAfter IS NULL OR h.closedAt >= :closedAfter)
        AND (:closedBefore IS NULL OR h.closedAt <= :closedBefore)
      """;

  @Query("SELECT h " + QRY_FIND_ALL)
  Page<HistoryEntity> findAll(
      @Param("userNames") Collection<String> userNames,
      @Param("connectionIds") Collection<UUID> connectionIds,
      @Param("clientHost") @Nullable String clientHost,
      @Param("targetHost") @Nullable String targetHost,
      @Param("targetPorts") Collection<Integer> targetPorts,
      @Param("openedAfter") @Nullable LocalDateTime openedAfter,
      @Param("openedBefore") @Nullable LocalDateTime openedBefore,
      @Param("closedAfter") @Nullable LocalDateTime closedAfter,
      @Param("closedBefore") @Nullable LocalDateTime closedBefore,
      Pageable pageable);

  @Query("SELECT COUNT(h) " + QRY_FIND_ALL)
  long countAll(
      @Param("userNames") Collection<String> userNames,
      @Param("connectionIds") Collection<UUID> connectionIds,
      @Param("clientHost") @Nullable String clientHost,
      @Param("targetHost") @Nullable String targetHost,
      @Param("targetPorts") Collection<Integer> targetPorts,
      @Param("openedAfter") @Nullable LocalDateTime openedAfter,
      @Param("openedBefore") @Nullable LocalDateTime openedBefore,
      @Param("closedAfter") @Nullable LocalDateTime closedAfter,
      @Param("closedBefore") @Nullable LocalDateTime closedBefore);

  void deleteAllByUserNameIgnoreCase(String userName);
}
