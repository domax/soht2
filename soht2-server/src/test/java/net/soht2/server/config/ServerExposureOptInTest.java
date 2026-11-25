/* SOHT2 © Licensed under MIT 2026. */
package net.soht2.server.config;

import static net.soht2.server.entity.UserEntity.ROLE_ADMIN;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.context.WebApplicationContext;

/** Verifies admin-only access to opted-in actuator endpoints and opt-in CORS origins. */
@SpringBootTest(
    properties = {
      "management.endpoints.web.exposure.include=health,env",
      "soht2.server.cors.allowed-origins=http://localhost:5173"
    })
@ActiveProfiles("test")
class ServerExposureOptInTest {

  static final String DEV_ORIGIN = "http://localhost:5173";

  @Autowired WebApplicationContext webApplicationContext;

  MockMvc mockMvc;

  @BeforeEach
  void beforeEach() {
    mockMvc = webAppContextSetup(webApplicationContext).apply(springSecurity()).build();
  }

  @Test
  void exposedEndpoint_anonymous_UNAUTHORIZED() throws Exception {
    mockMvc.perform(get("/actuator/env")).andExpect(status().isUnauthorized());
  }

  @Test
  void exposedEndpoint_user_FORBIDDEN() throws Exception {
    mockMvc
        .perform(get("/actuator/env").with(user("user").authorities(() -> "USER")))
        .andExpect(status().isForbidden());
  }

  @Test
  void exposedEndpoint_admin_OK() throws Exception {
    mockMvc
        .perform(get("/actuator/env").with(user("admin").authorities(() -> ROLE_ADMIN)))
        .andExpect(status().isOk());
  }

  @Test
  void health_anonymous_OK() throws Exception {
    mockMvc.perform(get("/actuator/health")).andExpect(status().isOk());
  }

  @Test
  void cors_listedOrigin_allowed() throws Exception {
    mockMvc
        .perform(
            options("/api/user")
                .header("Origin", DEV_ORIGIN)
                .header("Access-Control-Request-Method", "GET"))
        .andExpect(header().string("Access-Control-Allow-Origin", DEV_ORIGIN));
  }

  @Test
  void cors_unlistedOrigin_denied() throws Exception {
    mockMvc
        .perform(
            options("/api/user")
                .header("Origin", "https://evil.example")
                .header("Access-Control-Request-Method", "GET"))
        .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
  }
}
