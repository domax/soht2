/* SOHT2 © Licensed under MIT 2025. */
package net.soht2.server.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.http.MediaType.APPLICATION_OCTET_STREAM;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.vavr.control.Try;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import lombok.val;
import net.soht2.common.dto.Soht2Connection;
import net.soht2.common.dto.Soht2User;
import net.soht2.server.config.SecurityConfig;
import net.soht2.server.config.Soht2ServerConfig;
import net.soht2.server.dto.*;
import net.soht2.server.service.ServerConnection;
import net.soht2.server.service.Soht2HistoryService;
import net.soht2.server.service.Soht2Service;
import net.soht2.server.test.UTHelper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.autoconfigure.endpoint.web.CorsEndpointProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.Authentication;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest({SecurityConfig.class, Soht2ServerConfig.class, ConnectionController.class})
@EnableConfigurationProperties(CorsEndpointProperties.class)
@ActiveProfiles("test")
@WithMockUser(username = "system", password = "test", authorities = "USER")
class ConnectionControllerTest {

  static final String AUTH =
      "Basic " + Base64.getEncoder().encodeToString("system:test".getBytes());

  @Autowired MockMvc mockMvc;
  @Autowired Soht2ServerConfig soht2ServerConfig;
  @Autowired ObjectMapper objectMapper;

  @MockitoBean("soht2Service")
  Soht2Service soht2Service;

  @MockitoBean Soht2HistoryService soht2HistoryService;

  @SuppressWarnings("resource")
  @Test
  void open_OK() throws Exception {
    val randomUUID = UUID.randomUUID();
    val now = LocalDateTime.parse("2025-07-10T23:23:19");

    try (val localDateTime = mockStatic(LocalDateTime.class);
        val uuid = mockStatic(UUID.class)) {
      localDateTime.when(LocalDateTime::now).thenReturn(now);
      uuid.when(UUID::randomUUID).thenReturn(randomUUID);

      val sohtConnection =
          Soht2Connection.builder()
              .user(Soht2User.builder().username("system").build())
              .clientHost("localhost")
              .targetHost("targetHost")
              .targetPort(12345)
              .build();

      val connectionInfo = mock(ServerConnection.class);
      doReturn(sohtConnection).when(connectionInfo).soht2();
      doReturn(true)
          .when(soht2Service)
          .isTargetAllowed(any(Authentication.class), anyString(), anyInt());
      doReturn(connectionInfo)
          .when(soht2Service)
          .open(any(Soht2Connection.class), any(Authentication.class));

      mockMvc
          .perform(
              post("/api/connection")
                  .header(HttpHeaders.AUTHORIZATION, AUTH)
                  .queryParam("host", "targetHost")
                  .queryParam("port", "12345"))
          .andExpect(status().isOk())
          .andExpect(content().contentType(APPLICATION_JSON))
          .andExpect(jsonPath("$.id").value(sohtConnection.id().toString()))
          .andExpect(jsonPath("$.user.username").value(sohtConnection.user().username()))
          .andExpect(jsonPath("$.clientHost").value(sohtConnection.clientHost()))
          .andExpect(jsonPath("$.targetHost").value(sohtConnection.targetHost()))
          .andExpect(jsonPath("$.targetPort").value(sohtConnection.targetPort().toString()))
          .andExpect(jsonPath("$.openedAt").value(sohtConnection.openedAt().toString()));

      verify(soht2Service).isTargetAllowed(any(Authentication.class), eq("targetHost"), eq(12345));
      verify(soht2Service).open(eq(sohtConnection), any(Authentication.class));
      verify(connectionInfo).soht2();
    }
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void exchange_OK(boolean nonEmpty) throws Exception {
    val sohtConnection =
        Soht2Connection.builder()
            .user(Soht2User.builder().username("system").role("USER").build())
            .clientHost("localhost")
            .targetHost("targetHost")
            .targetPort(12345)
            .build();

    val bufferSize = (int) soht2ServerConfig.getReadBufferSize().toBytes();
    val bytesEmpty = new byte[0];
    val bytesIn = nonEmpty ? UTHelper.createBinData(bufferSize * 2 / 3) : bytesEmpty;
    val bytesOut = nonEmpty ? UTHelper.createBinData(bufferSize * 3 / 2) : bytesEmpty;

    doReturn(true).when(soht2Service).isConnectionOwner(any(Authentication.class), any(UUID.class));
    doReturn(Try.success(bytesOut)).when(soht2Service).exchange(any(UUID.class), any(), any());

    mockMvc
        .perform(
            post("/api/connection/" + sohtConnection.id())
                .contentType(APPLICATION_OCTET_STREAM)
                .header(HttpHeaders.AUTHORIZATION, AUTH)
                .content(bytesIn))
        .andExpect(status().isOk())
        .andExpect(content().contentType(APPLICATION_OCTET_STREAM))
        .andExpect(content().bytes(bytesOut));

    verify(soht2Service).isConnectionOwner(any(Authentication.class), eq(sohtConnection.id()));
    verify(soht2Service).exchange(sohtConnection.id(), bytesIn.length > 0 ? bytesIn : null, null);
  }

  @Test
  void searchHistory_OK() throws Exception {
    val id1 = UUID.randomUUID();
    val id2 = UUID.randomUUID();
    val paging =
        HistoryPaging.builder()
            .pageNumber(1)
            .pageSize(2)
            .sorting(
                List.of(
                    HistoryOrder.builder()
                        .field(HistorySorting.userName)
                        .direction(SortingDir.ASC)
                        .build(),
                    HistoryOrder.builder()
                        .field(HistorySorting.openedAt)
                        .direction(SortingDir.DESC)
                        .build()))
            .build();
    val page =
        HistoryPage.builder()
            .paging(paging)
            .totalItems(1000L)
            .data(
                List.of(
                    Soht2Connection.builder()
                        .id(id1)
                        .user(Soht2User.builder().username("user1").build())
                        .clientHost("192.168.1.100")
                        .targetHost("example.com")
                        .targetPort(22)
                        .openedAt(LocalDateTime.parse("2025-08-07T21:10"))
                        .closedAt(LocalDateTime.parse("2025-08-07T22:10"))
                        .build(),
                    Soht2Connection.builder()
                        .id(id2)
                        .user(Soht2User.builder().username("user2").build())
                        .clientHost("192.168.1.101")
                        .targetHost("test.com")
                        .targetPort(443)
                        .openedAt(LocalDateTime.parse("2025-08-07T21:20"))
                        .closedAt(LocalDateTime.parse("2025-08-07T22:20"))
                        .build()))
            .build();

    doReturn(page)
        .when(soht2HistoryService)
        .searchHistory(
            anyCollection(),
            anyCollection(),
            any(),
            any(),
            anyCollection(),
            any(),
            any(),
            any(),
            any(),
            any(HistoryPaging.class),
            any(Authentication.class));

    mockMvc
        .perform(
            get("/api/connection/history")
                .header(HttpHeaders.AUTHORIZATION, AUTH)
                .queryParam("un", "user1,user2")
                .queryParam("id", id1 + "," + id2)
                .queryParam("ch", "168.1")
                .queryParam("th", ".com")
                .queryParam("tp", "22,80,443")
                .queryParam("oa", "2025-08-07T21:00")
                .queryParam("ob", "2025-08-07T22:00")
                .queryParam("ca", "2025-08-07T21:30")
                .queryParam("cb", "2025-08-07T22:30")
                .queryParam("sort", "userName:asc,openedAt:desc")
                .queryParam("pg", "1")
                .queryParam("sz", "2")
                .contentType(APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(content().contentType(APPLICATION_JSON))
        .andDo(
            rh ->
                assertThat(
                        objectMapper.readValue(
                            rh.getResponse().getContentAsString(), HistoryPage.class))
                    .isEqualTo(page));

    verify(soht2HistoryService)
        .searchHistory(
            eq(Set.of("user1", "user2")),
            eq(Set.of(id1, id2)),
            eq("168.1"),
            eq(".com"),
            eq(Set.of(22, 80, 443)),
            eq(LocalDateTime.parse("2025-08-07T21:00")),
            eq(LocalDateTime.parse("2025-08-07T22:00")),
            eq(LocalDateTime.parse("2025-08-07T21:30")),
            eq(LocalDateTime.parse("2025-08-07T22:30")),
            eq(paging),
            any(Authentication.class));
  }
}
