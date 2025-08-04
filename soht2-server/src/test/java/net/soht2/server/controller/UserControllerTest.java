package net.soht2.server.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

import java.time.LocalDateTime;
import java.util.Base64;
import java.util.LinkedHashSet;
import java.util.List;
import lombok.val;
import net.soht2.common.dto.Soht2User;
import net.soht2.server.config.SecurityConfig;
import net.soht2.server.config.Soht2ServerConfig;
import net.soht2.server.service.Soht2UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.autoconfigure.endpoint.web.CorsEndpointProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest({SecurityConfig.class, Soht2ServerConfig.class, UserController.class})
@EnableConfigurationProperties(CorsEndpointProperties.class)
@ActiveProfiles("test")
@WithMockUser(username = "system", password = "test", authorities = "ADMIN")
class UserControllerTest {

  static final String AUTH =
      "Basic " + Base64.getEncoder().encodeToString("system:test".getBytes());

  @Autowired MockMvc mockMvc;

  @MockitoBean Soht2UserService soht2UserService;

  Soht2User soht2User;

  @BeforeEach
  void beforeEach() {
    val now = LocalDateTime.parse("2025-07-10T23:23:19");
    val targets = new LinkedHashSet<>(List.of("localhost:*", "example.com:8080"));
    soht2User =
        Soht2User.builder()
            .username("a_b")
            .role("USER")
            .allowedTargets(targets)
            .createdAt(now)
            .updatedAt(now)
            .build();
  }

  @Test
  void create_OK() throws Exception {
    doReturn(soht2User)
        .when(soht2UserService)
        .createUser(anyString(), anyString(), any(), anySet());

    mockMvc
        .perform(
            post("/api/user")
                .header(HttpHeaders.AUTHORIZATION, AUTH)
                .queryParam("username", soht2User.username())
                .queryParam("password", "1qaz@WSX")
                .queryParam("target", String.join(",", soht2User.allowedTargets())))
        .andExpect(status().isOk())
        .andExpect(content().contentType(APPLICATION_JSON))
        .andExpect(jsonPath("$.username").value(soht2User.username()))
        .andExpect(jsonPath("$.role").value(soht2User.role()))
        .andExpect(jsonPath("$.allowedTargets").isArray())
        .andExpect(jsonPath("$.allowedTargets[0]").value("localhost:*"))
        .andExpect(jsonPath("$.allowedTargets[1]").value("example.com:8080"))
        .andExpect(jsonPath("$.createdAt").value(soht2User.createdAt().toString()))
        .andExpect(jsonPath("$.updatedAt").value(soht2User.updatedAt().toString()));

    verify(soht2UserService)
        .createUser(soht2User.username(), "1qaz@WSX", null, soht2User.allowedTargets());
  }

  @Test
  void create_Errors() throws Exception {
    mockMvc
        .perform(
            post("/api/user")
                .header(HttpHeaders.AUTHORIZATION, AUTH)
                .queryParam("username", "a-b")
                .queryParam("password", "1qaz")
                .queryParam("target", String.join(",", soht2User.allowedTargets())))
        .andExpect(status().is4xxClientError());

    verify(soht2UserService, never()).createUser(anyString(), anyString(), any(), anySet());
  }

  @Test
  void update_OK() throws Exception {
    doReturn(soht2User).when(soht2UserService).updateUser(anyString(), any(), any(), any());

    mockMvc
        .perform(
            put("/api/user/" + soht2User.username())
                .header(HttpHeaders.AUTHORIZATION, AUTH)
                .queryParam("password", "1qaz@WSX")
                .queryParam("role", soht2User.role())
                .queryParam("target", String.join(",", soht2User.allowedTargets())))
        .andExpect(status().isOk())
        .andExpect(content().contentType(APPLICATION_JSON))
        .andExpect(jsonPath("$.username").value(soht2User.username()))
        .andExpect(jsonPath("$.role").value(soht2User.role()))
        .andExpect(jsonPath("$.allowedTargets").isArray())
        .andExpect(jsonPath("$.allowedTargets[0]").value("localhost:*"))
        .andExpect(jsonPath("$.allowedTargets[1]").value("example.com:8080"))
        .andExpect(jsonPath("$.createdAt").value(soht2User.createdAt().toString()))
        .andExpect(jsonPath("$.updatedAt").value(soht2User.updatedAt().toString()));

    verify(soht2UserService)
        .updateUser(soht2User.username(), "1qaz@WSX", soht2User.role(), soht2User.allowedTargets());
  }

  @Test
  void delete_OK() throws Exception {
    mockMvc
        .perform(
            delete("/api/user/" + soht2User.username())
                .header(HttpHeaders.AUTHORIZATION, AUTH)
                .queryParam("force", "true"))
        .andExpect(status().isOk());

    verify(soht2UserService).deleteUser(soht2User.username(), true);
  }

  @Test
  void list_OK() throws Exception {
    doReturn(List.of(soht2User)).when(soht2UserService).listUsers();

    mockMvc
        .perform(get("/api/user").header(HttpHeaders.AUTHORIZATION, AUTH))
        .andExpect(status().isOk())
        .andExpect(content().contentType(APPLICATION_JSON))
        .andExpect(jsonPath("$[0].username").value(soht2User.username()))
        .andExpect(jsonPath("$[0].role").value(soht2User.role()))
        .andExpect(jsonPath("$[0].allowedTargets").isArray())
        .andExpect(jsonPath("$[0].allowedTargets[0]").value("localhost:*"))
        .andExpect(jsonPath("$[0].allowedTargets[1]").value("example.com:8080"))
        .andExpect(jsonPath("$[0].createdAt").value(soht2User.createdAt().toString()))
        .andExpect(jsonPath("$[0].updatedAt").value(soht2User.updatedAt().toString()));

    verify(soht2UserService).listUsers();
  }

  @Test
  void self_OK() throws Exception {
    soht2User =
        Soht2User.builder()
            .username("system")
            .role("ADMIN")
            .allowedTargets(soht2User.allowedTargets())
            .createdAt(soht2User.createdAt())
            .updatedAt(soht2User.updatedAt())
            .build();

    doReturn(soht2User).when(soht2UserService).getSelf(any(Authentication.class));

    mockMvc
        .perform(get("/api/user/self").header(HttpHeaders.AUTHORIZATION, AUTH))
        .andExpect(status().isOk())
        .andExpect(content().contentType(APPLICATION_JSON))
        .andExpect(jsonPath("$.username").value(soht2User.username()))
        .andExpect(jsonPath("$.role").value(soht2User.role()))
        .andExpect(jsonPath("$.allowedTargets").isArray())
        .andExpect(jsonPath("$.allowedTargets[0]").value("localhost:*"))
        .andExpect(jsonPath("$.allowedTargets[1]").value("example.com:8080"))
        .andExpect(jsonPath("$.createdAt").value(soht2User.createdAt().toString()))
        .andExpect(jsonPath("$.updatedAt").value(soht2User.updatedAt().toString()));

    verify(soht2UserService).getSelf(assertArg(this::assertArgAuth));
  }

  @Test
  void password_OK() throws Exception {
    soht2User =
        Soht2User.builder()
            .username("system")
            .role("ADMIN")
            .allowedTargets(soht2User.allowedTargets())
            .createdAt(soht2User.createdAt())
            .updatedAt(soht2User.updatedAt())
            .build();

    doReturn(soht2User)
        .when(soht2UserService)
        .changePassword(anyString(), anyString(), any(Authentication.class));

    mockMvc
        .perform(
            put("/api/user/self")
                .header(HttpHeaders.AUTHORIZATION, AUTH)
                .queryParam("old", "test")
                .queryParam("new", "1qaz@WSX"))
        .andExpect(status().isOk())
        .andExpect(content().contentType(APPLICATION_JSON))
        .andExpect(jsonPath("$.username").value(soht2User.username()))
        .andExpect(jsonPath("$.role").value(soht2User.role()))
        .andExpect(jsonPath("$.allowedTargets").isArray())
        .andExpect(jsonPath("$.allowedTargets[0]").value("localhost:*"))
        .andExpect(jsonPath("$.allowedTargets[1]").value("example.com:8080"))
        .andExpect(jsonPath("$.createdAt").value(soht2User.createdAt().toString()))
        .andExpect(jsonPath("$.updatedAt").value(soht2User.updatedAt().toString()));

    verify(soht2UserService)
        .changePassword(eq("test"), eq("1qaz@WSX"), assertArg(this::assertArgAuth));
  }

  void assertArgAuth(Authentication a) {
    assertThat(a.getName()).isEqualTo(soht2User.username());
    assertThat(a.getAuthorities().stream().map(GrantedAuthority::getAuthority).toList())
        .containsExactly(soht2User.role());
  }
}
