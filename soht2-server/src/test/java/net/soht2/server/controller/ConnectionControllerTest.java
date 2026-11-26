/* SOHT2 © Licensed under MIT 2025. */
package net.soht2.server.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.http.MediaType.APPLICATION_OCTET_STREAM;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.anonymous;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup;

import io.vavr.control.Try;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import lombok.val;
import net.soht2.common.dto.Soht2Connection;
import net.soht2.common.dto.Soht2User;
import net.soht2.common.util.AuxUtil;
import net.soht2.server.config.RequestSizeLimitFilter;
import net.soht2.server.config.SecurityConfig;
import net.soht2.server.config.Soht2ServerConfig;
import net.soht2.server.dto.*;
import net.soht2.server.service.DownloadResult;
import net.soht2.server.service.ExceptionHelper;
import net.soht2.server.service.ServerConnection;
import net.soht2.server.service.Soht2HistoryService;
import net.soht2.server.service.Soht2Service;
import net.soht2.server.test.UTHelper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.context.WebApplicationContext;
import tools.jackson.databind.json.JsonMapper;

@WebMvcTest({
  SecurityConfig.class,
  Soht2ServerConfig.class,
  RequestSizeLimitFilter.class,
  ConnectionController.class
})
@ActiveProfiles("test")
@WithMockUser(username = "system", password = "test", authorities = "USER")
class ConnectionControllerTest {

  @Autowired MockMvc mockMvc;
  @Autowired WebApplicationContext webApplicationContext;
  @Autowired Soht2ServerConfig soht2ServerConfig;
  @Autowired JsonMapper jsonMapper;
  @Autowired RequestSizeLimitFilter requestSizeLimitFilter;

  @MockitoBean("soht2Service")
  Soht2Service soht2Service;

  @MockitoBean Soht2HistoryService soht2HistoryService;

  @SuppressWarnings("SpringTestingOverridenBeanResolveInspection")
  @MockitoBean
  UserDetailsService userDetailsService;

  @org.junit.jupiter.api.BeforeEach
  void authenticateRequests() {
    mockMvc =
        webAppContextSetup(webApplicationContext)
            .apply(springSecurity())
            .addFilters(requestSizeLimitFilter)
            .defaultRequest(get("/").with(user("system").authorities(() -> "USER")))
            .build();
  }

  @SuppressWarnings("resource")
  @Test
  void open_OK() throws Exception {
    val randomUUID = UUID.randomUUID();
    val now = LocalDateTime.parse("2025-07-10T23:23:19");

    try (val localDateTime = mockStatic(LocalDateTime.class);
        val auxUtil = mockStatic(AuxUtil.class)) {
      localDateTime.when(LocalDateTime::now).thenReturn(now);
      auxUtil.when(AuxUtil::generateUUIDv7).thenReturn(randomUUID);

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
                  .with(csrf())
                  .queryParam("host", "targetHost")
                  .queryParam("port", "12345"))
          .andExpect(status().isOk())
          .andExpect(content().contentType(APPLICATION_JSON))
          .andExpect(jsonPath("$.id").value(sohtConnection.id().toString()))
          .andExpect(jsonPath("$.user.username").value(sohtConnection.user().username()))
          .andExpect(jsonPath("$.clientHost").value(sohtConnection.clientHost()))
          .andExpect(jsonPath("$.targetHost").value(sohtConnection.targetHost()))
          .andExpect(jsonPath("$.targetPort").value(sohtConnection.targetPort().toString()))
          .andExpect(jsonPath("$.openedAt").value(sohtConnection.openedAt().toString()))
          .andExpect(header().string("X-SOHT2-Transport", "duplex"));

      verify(soht2Service).isTargetAllowed(any(Authentication.class), eq("targetHost"), eq(12345));
      verify(soht2Service).open(eq(sohtConnection), any(Authentication.class));
      verify(connectionInfo).soht2();
    }
  }

  @Test
  void open_withoutCsrfToken_FORBIDDEN() throws Exception {
    mockMvc
        .perform(
            post("/api/connection").queryParam("host", "targetHost").queryParam("port", "12345"))
        .andExpect(status().isForbidden());

    verifyNoInteractions(soht2Service);
  }

  @Test
  void open_withInvalidCsrfToken_FORBIDDEN() throws Exception {
    mockMvc
        .perform(
            post("/api/connection")
                .with(csrf().useInvalidToken())
                .queryParam("host", "targetHost")
                .queryParam("port", "12345"))
        .andExpect(status().isForbidden());

    verifyNoInteractions(soht2Service);
  }

  @SuppressWarnings("resource")
  @ParameterizedTest
  @ValueSource(strings = {"connect-refused", "connect-timeout"})
  void open_targetConnectFailure_propagatesSameWay(String failure) {
    val cause =
        "connect-timeout".equals(failure)
            ? new java.net.SocketTimeoutException("Connect timed out")
            : new java.net.ConnectException("Connection refused");
    doReturn(true)
        .when(soht2Service)
        .isTargetAllowed(any(Authentication.class), anyString(), anyInt());
    doAnswer(
            inv -> {
              throw cause;
            })
        .when(soht2Service)
        .open(any(Soht2Connection.class), any(Authentication.class));

    val result =
        Try.of(() ->
            mockMvc.perform(
                post("/api/connection")
                    .header("X-SOHT2-Client", "soht2-client")
                    .queryParam("host", "targetHost")
                    .queryParam("port", "12345")));

    // not mapped by the application: the servlet container answers 500 for both failures
    assertThat(result.isFailure()).isTrue();
    assertThat(result.getCause()).hasRootCauseInstanceOf(cause.getClass());
  }

  @SuppressWarnings("resource")
  @Test
  void open_withNonBrowserClientHeader_OK() throws Exception {
    val randomUUID = UUID.randomUUID();
    val sohtConnection =
        Soht2Connection.builder()
            .id(randomUUID)
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
                .header("X-SOHT2-Client", "soht2-client")
                .queryParam("host", "targetHost")
                .queryParam("port", "12345"))
        .andExpect(status().isOk());

    verify(soht2Service).open(any(Soht2Connection.class), any(Authentication.class));
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
                .with(csrf())
                .contentType(APPLICATION_OCTET_STREAM)
                .content(bytesIn))
        .andExpect(status().isOk())
        .andExpect(content().contentType(APPLICATION_OCTET_STREAM))
        .andExpect(content().bytes(bytesOut));

    verify(soht2Service).isConnectionOwner(any(Authentication.class), eq(sohtConnection.id()));
    verify(soht2Service).exchange(sohtConnection.id(), bytesIn.length > 0 ? bytesIn : null, null);
  }

  @Test
  void upload_OK() throws Exception {
    val id = UUID.randomUUID();
    val bytesIn = UTHelper.createBinData(100);
    doReturn(true).when(soht2Service).isConnectionOwner(any(Authentication.class), any(UUID.class));
    doReturn(Try.success(null)).when(soht2Service).upload(any(UUID.class), anyLong(), any(), any());

    mockMvc
        .perform(
            post("/api/connection/" + id + "/upload")
                .header("X-SOHT2-Client", "soht2-client")
                .header("X-SOHT2-Seq", "7")
                .header("Content-Encoding", "gzip")
                .contentType(APPLICATION_OCTET_STREAM)
                .content(bytesIn))
        .andExpect(status().isNoContent());

    verify(soht2Service).isConnectionOwner(any(Authentication.class), eq(id));
    verify(soht2Service).upload(id, 7L, bytesIn, "gzip");
  }

  @Test
  void upload_sequenceConflict_CONFLICT() throws Exception {
    val id = UUID.randomUUID();
    doReturn(true).when(soht2Service).isConnectionOwner(any(Authentication.class), any(UUID.class));
    doReturn(Try.failure(ExceptionHelper.conflict("Out-of-order")))
        .when(soht2Service)
        .upload(any(UUID.class), anyLong(), any(), any());

    mockMvc
        .perform(
            post("/api/connection/" + id + "/upload")
                .with(csrf())
                .header("X-SOHT2-Seq", "3")
                .contentType(APPLICATION_OCTET_STREAM)
                .content(new byte[] {1}))
        .andExpect(status().isConflict());
  }

  @Test
  void upload_notOwner_FORBIDDEN() throws Exception {
    val id = UUID.randomUUID();
    doReturn(false)
        .when(soht2Service)
        .isConnectionOwner(any(Authentication.class), any(UUID.class));

    mockMvc
        .perform(
            post("/api/connection/" + id + "/upload")
                .with(csrf())
                .header("X-SOHT2-Seq", "1")
                .contentType(APPLICATION_OCTET_STREAM)
                .content(new byte[] {1}))
        .andExpect(status().isForbidden());

    verify(soht2Service, never()).upload(any(UUID.class), anyLong(), any(), any());
  }

  @Test
  void upload_unauthenticated_UNAUTHORIZED() throws Exception {
    mockMvc
        .perform(
            post("/api/connection/" + UUID.randomUUID() + "/upload")
                .with(anonymous())
                .with(csrf())
                .header("X-SOHT2-Seq", "1")
                .contentType(APPLICATION_OCTET_STREAM)
                .content(new byte[] {1}))
        .andExpect(status().isUnauthorized());

    verifyNoInteractions(soht2Service);
  }

  @ParameterizedTest
  @ValueSource(strings = {"", "/upload"})
  void tunnelData_oversized_CONTENT_TOO_LARGE(String suffix) throws Exception {
    val maxBytes = (int) soht2ServerConfig.getMaxRequestSize().toBytes();
    doReturn(true).when(soht2Service).isConnectionOwner(any(Authentication.class), any(UUID.class));

    mockMvc
        .perform(
            post("/api/connection/" + UUID.randomUUID() + suffix)
                .header("X-SOHT2-Client", "soht2-client")
                .header("X-SOHT2-Seq", "1")
                .contentType(APPLICATION_OCTET_STREAM)
                .content(new byte[maxBytes + 1]))
        .andExpect(status().isContentTooLarge());

    verify(soht2Service, never()).upload(any(UUID.class), anyLong(), any(), any());
    verify(soht2Service, never()).exchange(any(UUID.class), any(), any());
  }

  @Test
  void upload_withinLimit_OK() throws Exception {
    val id = UUID.randomUUID();
    val bytesIn = UTHelper.createBinData(1024 * 1024);
    doReturn(true).when(soht2Service).isConnectionOwner(any(Authentication.class), any(UUID.class));
    doReturn(Try.success(null)).when(soht2Service).upload(any(UUID.class), anyLong(), any(), any());

    mockMvc
        .perform(
            post("/api/connection/" + id + "/upload")
                .header("X-SOHT2-Client", "soht2-client")
                .header("X-SOHT2-Seq", "1")
                .contentType(APPLICATION_OCTET_STREAM)
                .content(bytesIn))
        .andExpect(status().isNoContent());

    verify(soht2Service).upload(id, 1L, bytesIn, null);
  }

  @Test
  void upload_oversizedUnauthenticated_UNAUTHORIZED() throws Exception {
    val maxBytes = (int) soht2ServerConfig.getMaxRequestSize().toBytes();
    mockMvc
        .perform(
            post("/api/connection/" + UUID.randomUUID() + "/upload")
                .with(anonymous())
                .header("X-SOHT2-Client", "soht2-client")
                .header("X-SOHT2-Seq", "1")
                .contentType(APPLICATION_OCTET_STREAM)
                .content(new byte[maxBytes + 1]))
        .andExpect(status().isUnauthorized());

    verifyNoInteractions(soht2Service);
  }

  @Test
  void download_data_OK() throws Exception {
    val id = UUID.randomUUID();
    val bytesOut = UTHelper.createBinData(100);
    doReturn(true).when(soht2Service).isConnectionOwner(any(Authentication.class), any(UUID.class));
    doReturn(Try.success(DownloadResult.of(bytesOut))).when(soht2Service).download(id, 5L);

    mockMvc
        .perform(get("/api/connection/" + id + "/download").header("X-SOHT2-Seq", "5"))
        .andExpect(status().isOk())
        .andExpect(content().contentType(APPLICATION_OCTET_STREAM))
        .andExpect(content().bytes(bytesOut));

    verify(soht2Service).isConnectionOwner(any(Authentication.class), eq(id));
    verify(soht2Service).download(id, 5L);
  }

  @Test
  void download_empty_NO_CONTENT() throws Exception {
    val id = UUID.randomUUID();
    doReturn(true).when(soht2Service).isConnectionOwner(any(Authentication.class), any(UUID.class));
    doReturn(Try.success(DownloadResult.EMPTY)).when(soht2Service).download(id, 1L);

    mockMvc
        .perform(get("/api/connection/" + id + "/download").header("X-SOHT2-Seq", "1"))
        .andExpect(status().isNoContent())
        .andExpect(content().bytes(new byte[0]));
  }

  @Test
  void download_eos_GONE() throws Exception {
    val id = UUID.randomUUID();
    doReturn(true).when(soht2Service).isConnectionOwner(any(Authentication.class), any(UUID.class));
    doReturn(Try.success(DownloadResult.EOS)).when(soht2Service).download(id, 2L);

    mockMvc
        .perform(get("/api/connection/" + id + "/download").header("X-SOHT2-Seq", "2"))
        .andExpect(status().isGone())
        .andExpect(header().string("X-SOHT2-EOS", "true"));
  }

  @Test
  void download_connectionGone_GONE_withoutEosHeader() throws Exception {
    val id = UUID.randomUUID();
    doReturn(true).when(soht2Service).isConnectionOwner(any(Authentication.class), any(UUID.class));
    doReturn(Try.failure(ExceptionHelper.gone("Connection closed")))
        .when(soht2Service)
        .download(id, 2L);

    mockMvc
        .perform(get("/api/connection/" + id + "/download").header("X-SOHT2-Seq", "2"))
        .andExpect(status().isGone())
        .andExpect(header().doesNotExist("X-SOHT2-EOS"));
  }

  @Test
  void download_notOwner_FORBIDDEN() throws Exception {
    val id = UUID.randomUUID();
    doReturn(false)
        .when(soht2Service)
        .isConnectionOwner(any(Authentication.class), any(UUID.class));

    mockMvc
        .perform(get("/api/connection/" + id + "/download").header("X-SOHT2-Seq", "1"))
        .andExpect(status().isForbidden());

    verify(soht2Service, never()).download(any(UUID.class), anyLong());
  }

  @Test
  void download_unauthenticated_UNAUTHORIZED() throws Exception {
    mockMvc
        .perform(
            get("/api/connection/" + UUID.randomUUID() + "/download")
                .with(anonymous())
                .header("X-SOHT2-Seq", "1"))
        .andExpect(status().isUnauthorized());

    verifyNoInteractions(soht2Service);
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
                        .field(HistorySorting.username)
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
            any(),
            any(),
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
                .queryParam("un", "user*")
                .queryParam("id", "*-4f*")
                .queryParam("ch", "168.1")
                .queryParam("th", ".com")
                .queryParam("tp", "22,80,443")
                .queryParam("oa", "2025-08-07T21:00")
                .queryParam("ob", "2025-08-07T22:00")
                .queryParam("ca", "2025-08-07T21:30")
                .queryParam("cb", "2025-08-07T22:30")
                .queryParam("sort", "username:asc,openedAt:desc")
                .queryParam("pg", "1")
                .queryParam("sz", "2")
                .contentType(APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(content().contentType(APPLICATION_JSON))
        .andDo(rh ->
            assertThat(
                    jsonMapper.readValue(rh.getResponse().getContentAsString(), HistoryPage.class))
                .isEqualTo(page));

    verify(soht2HistoryService)
        .searchHistory(
            eq("user*"),
            eq("*-4f*"),
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
