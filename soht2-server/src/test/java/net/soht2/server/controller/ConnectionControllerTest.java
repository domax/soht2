/* SOHT2 © Licensed under MIT 2025. */
package net.soht2.server.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.http.MediaType.APPLICATION_OCTET_STREAM;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import io.vavr.control.Try;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.UUID;
import lombok.val;
import net.soht2.common.dto.Soht2Connection;
import net.soht2.common.dto.Soht2User;
import net.soht2.server.config.SecurityConfig;
import net.soht2.server.config.Soht2ServerConfig;
import net.soht2.server.service.ServerConnection;
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

  @Autowired MockMvc mockMvc;
  @Autowired Soht2ServerConfig soht2ServerConfig;

  @MockitoBean("soht2Service")
  Soht2Service soht2Service;

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
      doReturn(connectionInfo)
          .when(soht2Service)
          .open(any(Soht2Connection.class), any(Authentication.class));

      mockMvc
          .perform(
              post("/api/connection")
                  .header(
                      HttpHeaders.AUTHORIZATION,
                      "Basic " + Base64.getEncoder().encodeToString("system:test".getBytes()))
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
                .header(
                    HttpHeaders.AUTHORIZATION,
                    "Basic " + Base64.getEncoder().encodeToString("system:test".getBytes()))
                .content(bytesIn))
        .andExpect(status().isOk())
        .andExpect(content().contentType(APPLICATION_OCTET_STREAM))
        .andExpect(content().bytes(bytesOut));

    verify(soht2Service).isConnectionOwner(any(Authentication.class), eq(sohtConnection.id()));
    verify(soht2Service).exchange(sohtConnection.id(), bytesIn.length > 0 ? bytesIn : null, null);
  }
}
