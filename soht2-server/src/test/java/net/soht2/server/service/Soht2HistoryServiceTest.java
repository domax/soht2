/* SOHT2 © Licensed under MIT 2025. */
package net.soht2.server.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import net.soht2.common.dto.Soht2Connection;
import net.soht2.common.dto.Soht2User;
import net.soht2.server.dto.HistoryOrder;
import net.soht2.server.dto.HistoryPaging;
import net.soht2.server.dto.HistorySorting;
import net.soht2.server.dto.SortingDir;
import net.soht2.server.entity.HistoryEntity;
import net.soht2.server.entity.UserEntity;
import net.soht2.server.repository.HistoryEntityRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;

@Slf4j
@SpringBootTest
@ActiveProfiles("test")
@WithMockUser(username = "admin", password = "test", authorities = UserEntity.ROLE_ADMIN)
class Soht2HistoryServiceTest {

  @Autowired Soht2HistoryService soht2HistoryService;
  @Autowired HistoryEntityRepository historyEntityRepository;

  Authentication auth;

  @BeforeEach
  void beforeEach() {
    auth = SecurityContextHolder.getContext().getAuthentication();
  }

  @AfterEach
  void afterEach() {
    historyEntityRepository.deleteAll();
  }

  @Test
  void addHistory_OK() {
    val soht2 =
        Soht2Connection.builder()
            .id(UUID.randomUUID())
            .user(Soht2User.builder().username("admin").build())
            .clientHost("192.168.1.100")
            .targetHost("localhost")
            .targetPort(22)
            .openedAt(LocalDateTime.parse("2025-08-08T16:00"))
            .closedAt(LocalDateTime.parse("2025-08-08T17:00"))
            .build();

    soht2HistoryService.addHistory(soht2);

    assertThat(
            historyEntityRepository.findAll().stream()
                .filter(e -> e.getConnectionId().equals(soht2.id()))
                .findAny())
        .isNotEmpty();
  }

  @Test
  void deleteHistory_OK() {
    val he1 =
        HistoryEntity.builder()
            .userName("user1")
            .connectionId(UUID.randomUUID())
            .clientHost("localhost")
            .targetHost("example.com")
            .targetPort(443)
            .openedAt(LocalDateTime.now())
            .closedAt(LocalDateTime.now())
            .bytesRead(100L)
            .bytesWritten(200L)
            .build();
    val he2 =
        HistoryEntity.builder()
            .userName("user2")
            .connectionId(UUID.randomUUID())
            .clientHost("localhost")
            .targetHost("example.com")
            .targetPort(443)
            .openedAt(LocalDateTime.now())
            .closedAt(LocalDateTime.now())
            .bytesRead(300L)
            .bytesWritten(400L)
            .build();
    historyEntityRepository.saveAllAndFlush(List.of(he1, he2));

    soht2HistoryService.deleteHistory("user1");

    val history = historyEntityRepository.findAll();
    assertThat(
            history.stream()
                .filter(e -> e.getConnectionId().equals(he1.getConnectionId()))
                .findAny())
        .isEmpty();
    assertThat(
            history.stream()
                .filter(e -> e.getConnectionId().equals(he2.getConnectionId()))
                .findAny())
        .isNotEmpty();
  }

  @ParameterizedTest
  @CsvSource({"0,3:2:1:0:7", "1,6:5:4"})
  void searchHistory_Full_OK(int pageNumber, String expectedIndexes) {
    val historySoht2 = createHistory().stream().map(HistoryEntity::toSoht2Connection).toList();
    val expected =
        Arrays.stream(expectedIndexes.split(":"))
            .map(Integer::parseInt)
            .map(historySoht2::get)
            .toList();
    val paging =
        HistoryPaging.builder()
            .pageNumber(pageNumber)
            .pageSize(5)
            .sorting(
                List.of(
                    HistoryOrder.builder()
                        .field(HistorySorting.username)
                        .direction(SortingDir.ASC)
                        .build(),
                    HistoryOrder.builder()
                        .field(HistorySorting.openedAt)
                        .direction(SortingDir.DESC)
                        .build()))
            .build();

    val actual =
        soht2HistoryService.searchHistory(
            null, null, null, null, Set.of(), null, null, null, null, paging, auth);

    assertThat(actual.totalItems()).isEqualTo(historySoht2.size());
    assertThat(actual.paging()).isEqualTo(paging);
    assertThat(actual.data()).containsExactlyElementsOf(expected);
  }

  @ParameterizedTest
  @MethodSource("searchHistory_Filter_OK_Args")
  void searchHistory_Filter_OK(
      String userName,
      String connectionId,
      String clientHost,
      String targetHost,
      Set<Integer> targetPorts,
      LocalDateTime openedAfter,
      LocalDateTime openedBefore,
      LocalDateTime closedAfter,
      LocalDateTime closedBefore,
      List<Integer> indexes) {
    val historySoht2 = createHistory().stream().map(HistoryEntity::toSoht2Connection).toList();
    val expected = indexes.stream().map(historySoht2::get).toList();
    val paging =
        HistoryPaging.builder()
            .pageNumber(0)
            .pageSize(10)
            .sorting(List.of(HistoryOrder.builder().field(HistorySorting.openedAt).build()))
            .build();

    val actual =
        soht2HistoryService.searchHistory(
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
            auth);

    assertThat(actual.totalItems()).isEqualTo(indexes.size());
    assertThat(actual.paging()).isEqualTo(paging);
    assertThat(actual.data()).containsExactlyElementsOf(expected);
  }

  static Stream<Arguments> searchHistory_Filter_OK_Args() {
    return Stream.of(
        Arguments.of("user1", "*-41*", null, null, Set.of(), null, null, null, null, List.of(2, 3)),
        Arguments.of(
            null,
            null,
            "*0.0.1",
            "example*",
            Set.of(443),
            null,
            null,
            null,
            null,
            List.of(5)),
        Arguments.of(
            null,
            null,
            null,
            null,
            Set.of(),
            LocalDateTime.parse("2025-08-08T16:10"),
            LocalDateTime.parse("2025-08-08T16:50"),
            LocalDateTime.parse("2025-08-08T17:20"),
            LocalDateTime.parse("2025-08-08T18:00"),
            List.of(2, 3, 4, 5)));
  }

  List<HistoryEntity> createHistory() {
    return historyEntityRepository.saveAllAndFlush(
        List.of(
            HistoryEntity.builder() // 0
                .userName("user1")
                .connectionId(UUID.fromString("5788abf9-d021-4a96-88ab-f9d0219a9695"))
                .clientHost("localhost")
                .targetHost("example.com")
                .targetPort(22)
                .openedAt(LocalDateTime.parse("2025-08-08T16:00"))
                .closedAt(LocalDateTime.parse("2025-08-08T17:00"))
                .bytesRead(100L)
                .bytesWritten(200L)
                .build(),
            HistoryEntity.builder() // 1
                .userName("user1")
                .connectionId(UUID.fromString("a3273715-0d9b-4c66-a737-150d9b6c6628"))
                .clientHost("localhost")
                .targetHost("example.com")
                .targetPort(22)
                .openedAt(LocalDateTime.parse("2025-08-08T16:10"))
                .closedAt(LocalDateTime.parse("2025-08-08T17:10"))
                .bytesRead(300L)
                .bytesWritten(400L)
                .build(),
            HistoryEntity.builder() // 2
                .userName("user1")
                .connectionId(UUID.fromString("a522f1bc-6e46-41e6-a2f1-bc6e465ae63c"))
                .clientHost("localhost")
                .targetHost("test.com")
                .targetPort(22)
                .openedAt(LocalDateTime.parse("2025-08-08T16:20"))
                .closedAt(LocalDateTime.parse("2025-08-08T17:20"))
                .bytesRead(500L)
                .bytesWritten(600L)
                .build(),
            HistoryEntity.builder() // 3
                .userName("user1")
                .connectionId(UUID.fromString("6e8f991f-183b-419a-8f99-1f183b919af8"))
                .clientHost("127.0.0.1")
                .targetHost("test.com")
                .targetPort(22)
                .openedAt(LocalDateTime.parse("2025-08-08T16:30"))
                .closedAt(LocalDateTime.parse("2025-08-08T17:30"))
                .bytesRead(700L)
                .bytesWritten(800L)
                .build(),
            HistoryEntity.builder() // 4
                .userName("user2")
                .connectionId(UUID.fromString("3f3837a1-67fa-42c2-b837-a167fa12c248"))
                .clientHost("127.0.0.1")
                .targetHost("example.com")
                .targetPort(22)
                .openedAt(LocalDateTime.parse("2025-08-08T16:40"))
                .closedAt(LocalDateTime.parse("2025-08-08T17:40"))
                .bytesRead(900L)
                .bytesWritten(1000L)
                .build(),
            HistoryEntity.builder() // 5
                .userName("user2")
                .connectionId(UUID.fromString("5bc9279e-c94d-41a1-8927-9ec94db1a1a8"))
                .clientHost("127.0.0.1")
                .targetHost("example.com")
                .targetPort(443)
                .openedAt(LocalDateTime.parse("2025-08-08T16:50"))
                .closedAt(LocalDateTime.parse("2025-08-08T17:50"))
                .bytesRead(1100L)
                .bytesWritten(1200L)
                .build(),
            HistoryEntity.builder() // 6
                .userName("user2")
                .connectionId(UUID.fromString("93b85bae-5fff-4f65-b85b-ae5fff4f6567"))
                .clientHost("192.168.1.100")
                .targetHost("test.com")
                .targetPort(443)
                .openedAt(LocalDateTime.parse("2025-08-08T17:00"))
                .closedAt(LocalDateTime.parse("2025-08-08T18:00"))
                .bytesRead(1200L)
                .bytesWritten(1300L)
                .build(),
            HistoryEntity.builder() // 7
                .userName("user2")
                .connectionId(UUID.fromString("120f7053-0c48-4010-8f70-530c4890106c"))
                .clientHost("192.168.1.100")
                .targetHost("test.com")
                .targetPort(443)
                .openedAt(LocalDateTime.parse("2025-08-08T17:10"))
                .closedAt(LocalDateTime.parse("2025-08-08T18:10"))
                .bytesRead(1400L)
                .bytesWritten(1500L)
                .build()));
  }

  @Test
  void asLikeParam() {
    assertThat(Soht2HistoryService.asLikeParam(null)).isNull();
    assertThat(Soht2HistoryService.asLikeParam("")).isNull();
    assertThat(Soht2HistoryService.asLikeParam("test")).isEqualTo("test");
    assertThat(Soht2HistoryService.asLikeParam("*test")).isEqualTo("%test");
    assertThat(Soht2HistoryService.asLikeParam("test*")).isEqualTo("test%");
    assertThat(Soht2HistoryService.asLikeParam("*test*")).isEqualTo("%test%");
    assertThat(Soht2HistoryService.asLikeParam("*te*st*")).isEqualTo("%te*st%");
    assertThat(Soht2HistoryService.asLikeParam("_te%st_")).isEqualTo("\\_te\\%st\\_");
    assertThat(Soht2HistoryService.asLikeParam("__te%%st__")).isEqualTo("\\_\\_te\\%\\%st\\_\\_");
  }
}
