/* SOHT2 © Licensed under MIT 2025. */
package net.soht2.server.service;

import static java.util.Optional.ofNullable;
import static net.soht2.server.service.ExceptionHelper.forbidden;
import static net.soht2.server.service.ExceptionHelper.serviceUnavailable;
import static net.soht2.server.service.Soht2UserService.getCurrentUser;

import io.vavr.control.Try;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import net.soht2.common.dto.Soht2Connection;
import net.soht2.server.config.Soht2ServerConfig;
import net.soht2.server.dto.HistoryPage;
import net.soht2.server.dto.HistoryPaging;
import net.soht2.server.entity.HistoryEntity;
import net.soht2.server.repository.HistoryEntityRepository;
import org.springframework.lang.Nullable;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * Service for managing connection history in the SOHT2 application. This service provides
 * functionality to add, delete, and search connection history records. History tracking can be
 * enabled or disabled through the server configuration.
 */
@Slf4j
@RequiredArgsConstructor
@Service
public class Soht2HistoryService {

  private static final String ERR_HISTORY_DISABLED = "History logging is disabled";

  private final Soht2ServerConfig soht2ServerConfig;
  private final HistoryEntityRepository historyEntityRepository;

  /**
   * Adds a connection history record if history logging is enabled.
   *
   * @param connection the connection details used to create a history record
   * @throws org.springframework.web.client.HttpServerErrorException.ServiceUnavailable if history
   *     logging is disabled
   */
  @Transactional
  public void addHistory(Soht2Connection connection) {
    if (!soht2ServerConfig.isEnableHistory()) throw serviceUnavailable(ERR_HISTORY_DISABLED);
    Try.of(
            () ->
                HistoryEntity.builder()
                    .userName(connection.user().username())
                    .connectionId(connection.id())
                    .clientHost(connection.clientHost())
                    .targetHost(connection.targetHost())
                    .targetPort(connection.targetPort())
                    .openedAt(connection.openedAt())
                    .closedAt(connection.closedAt())
                    .bytesRead(connection.bytesRead())
                    .bytesWritten(connection.bytesWritten())
                    .build())
        .mapTry(historyEntityRepository::save)
        .onSuccess(v -> log.info("addHistory: {}", v))
        .onFailure(e -> log.error("addHistory: {}", e.toString()));
  }

  /**
   * Deletes all history records associated with the given username, ignoring case sensitivity.
   *
   * @param userName the username for which all history records should be deleted
   * @throws org.springframework.web.client.HttpServerErrorException.ServiceUnavailable if history
   *     logging is disabled
   */
  @Transactional
  public void deleteHistory(String userName) {
    if (!soht2ServerConfig.isEnableHistory()) throw serviceUnavailable(ERR_HISTORY_DISABLED);
    log.info("deleteHistory: userName={}", userName);
    historyEntityRepository.deleteAllByUserNameIgnoreCase(userName);
  }

  /**
   * Searches the connection history based on the specified filters.
   *
   * @param userName the usernames to filter the history records by
   * @param connectionId the connection IDs to filter the history records by
   * @param clientHost the client hosts to filter the history records by
   * @param targetHost the target hosts to filter the history records by
   * @param targetPorts a collection of target ports to filter the history records by
   * @param openedAfter the minimum opened date-time to filter the history records by
   * @param openedBefore the maximum opened date-time to filter the history records by
   * @param closedAfter the minimum closed date-time to filter the history records by
   * @param closedBefore the maximum closed date-time to filter the history records by
   * @param paging the paging and sorting information for the search
   * @param authentication the authentication object for verifying user permissions
   * @return a {@link HistoryPage} object containing the filtered history records, total count, and
   *     paging information
   * @throws org.springframework.web.client.HttpClientErrorException.Forbidden if the search is
   *     performed without proper authentication
   * @throws org.springframework.web.client.HttpServerErrorException.ServiceUnavailable if history
   *     logging is disabled
   */
  @SuppressWarnings("java:S107")
  @Transactional(readOnly = true)
  public HistoryPage searchHistory(
      @Nullable String userName,
      @Nullable String connectionId,
      @Nullable String clientHost,
      @Nullable String targetHost,
      Collection<Integer> targetPorts,
      @Nullable LocalDateTime openedAfter,
      @Nullable LocalDateTime openedBefore,
      @Nullable LocalDateTime closedAfter,
      @Nullable LocalDateTime closedBefore,
      HistoryPaging paging,
      Authentication authentication) {
    if (!soht2ServerConfig.isEnableHistory()) throw serviceUnavailable(ERR_HISTORY_DISABLED);
    log.info(
        "searchHistory"
            + ": userName={}"
            + ", connectionId={}"
            + ", clientHost={}"
            + ", targetHost={}"
            + ", targetPorts={}"
            + ", openedAfter={}"
            + ", openedBefore={}"
            + ", closedAfter={}"
            + ", closedBefore={}"
            + ", paging={}"
            + ", authentication={}",
        userName,
        connectionId,
        clientHost,
        targetHost,
        targetPorts,
        openedAfter,
        openedBefore,
        closedAfter,
        closedBefore,
        paging,
        authentication);

    val un =
        getCurrentUser(authentication)
            .map(cu -> cu.isAdmin() ? ofNullable(userName).orElse("") : cu.name())
            .orElseThrow(() -> forbidden("Search requires authentication"));

    val total =
        historyEntityRepository.countAll(
            asLikeParam(un),
            asLikeParam(connectionId),
            asLikeParam(clientHost),
            asLikeParam(targetHost),
            targetPorts,
            openedAfter,
            openedBefore,
            closedAfter,
            closedBefore);
    val data =
        total > 0
            ? historyEntityRepository
                .findAll(
                    asLikeParam(un),
                    asLikeParam(connectionId),
                    asLikeParam(clientHost),
                    asLikeParam(targetHost),
                    targetPorts,
                    openedAfter,
                    openedBefore,
                    closedAfter,
                    closedBefore,
                    paging.toPageable(true))
                .stream()
                .map(HistoryEntity::toSoht2Connection)
                .toList()
            : List.<Soht2Connection>of();
    return HistoryPage.builder().paging(paging).totalItems(total).data(data).build();
  }

  static @Nullable String asLikeParam(@Nullable String param) {
    if (!StringUtils.hasLength(param)) return null;
    return param.replaceAll("[%_]", "\\\\$0").replaceFirst("^\\*", "%").replaceFirst("\\*$", "%");
  }
}
