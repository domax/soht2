/* SOHT2 © Licensed under MIT 2026. */
package net.soht2.server.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup;

import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.val;
import net.soht2.server.service.Soht2UserService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultMatcher;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest
@ActiveProfiles("test")
class CachingAuthenticationIntegrationTest {

  static final AtomicInteger COUNTER = new AtomicInteger();
  static final String SELF = "/api/user/self";
  static final String USERS = "/api/user";

  @Autowired WebApplicationContext webApplicationContext;
  @Autowired AuthenticationManager authenticationManager;
  @Autowired Soht2UserService soht2UserService;

  @MockitoSpyBean PasswordEncoder passwordEncoder;

  MockMvc mockMvc;
  String username;

  @BeforeEach
  void beforeEach() {
    mockMvc = webAppContextSetup(webApplicationContext).apply(springSecurity()).build();
    username = "cache_user_" + COUNTER.incrementAndGet();
    soht2UserService.createUser(username, "pwd1", "USER", Set.of());
    clearInvocations(passwordEncoder);
  }

  @AfterEach
  void afterEach() {
    try {
      soht2UserService.deleteUser(username, true, false);
    } catch (Exception ignored) {
      // already deleted by the test
    }
  }

  void expect(String path, String password, ResultMatcher matcher) throws Exception {
    mockMvc.perform(get(path).with(httpBasic(username, password))).andExpect(matcher);
  }

  @Test
  void consecutiveRequests_verifyPasswordOnce() throws Exception {
    expect(SELF, "pwd1", status().isOk());
    expect(SELF, "pwd1", status().isOk());

    verify(passwordEncoder, times(1)).matches(any(), any());
  }

  @Test
  void onlyCachingProviderIsActive() {
    assertThat(authenticationManager).isInstanceOf(ProviderManager.class);
    val providers = ((ProviderManager) authenticationManager).getProviders();
    assertThat(providers).hasSize(1).first().isInstanceOf(CachingAuthenticationProvider.class);
  }

  @Test
  void passwordUpdate_rejectsOldAcceptsNew() throws Exception {
    expect(SELF, "pwd1", status().isOk());
    soht2UserService.updateUser(username, "pwd2", null, null);

    expect(SELF, "pwd1", status().isUnauthorized());
    expect(SELF, "pwd2", status().isOk());
  }

  @Test
  void passwordChange_rejectsOldAcceptsNew() throws Exception {
    expect(SELF, "pwd1", status().isOk());
    soht2UserService.changePassword(
        "pwd1",
        "pwd2",
        UsernamePasswordAuthenticationToken.authenticated(username, null, Set.of()));

    expect(SELF, "pwd1", status().isUnauthorized());
    expect(SELF, "pwd2", status().isOk());
  }

  @Test
  void delete_rejectsCredentials() throws Exception {
    expect(SELF, "pwd1", status().isOk());
    soht2UserService.deleteUser(username, false, false);

    expect(SELF, "pwd1", status().isUnauthorized());
  }

  @Test
  void roleChange_appliesNewAuthorities() throws Exception {
    expect(USERS, "pwd1", status().isForbidden());
    soht2UserService.updateUser(username, null, "ADMIN", null);

    expect(USERS, "pwd1", status().isOk());
  }
}
