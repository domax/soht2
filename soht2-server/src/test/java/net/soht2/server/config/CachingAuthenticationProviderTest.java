/* SOHT2 © Licensed under MIT 2026. */
package net.soht2.server.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.github.benmanes.caffeine.cache.Ticker;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import lombok.val;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

@SuppressWarnings("java:S5778")
class CachingAuthenticationProviderTest {

  static final Duration TTL = Duration.ofMinutes(10);

  final Map<String, String> hashes = new HashMap<>();
  final Map<String, String> roles = new HashMap<>();
  final AtomicLong nanos = new AtomicLong();
  final Ticker ticker = nanos::get;

  PasswordEncoder passwordEncoder;
  CachingAuthenticationProvider provider;

  @BeforeEach
  void beforeEach() {
    passwordEncoder = spy(new BCryptPasswordEncoder(4));
    hashes.put("user", passwordEncoder.encode("secret"));
    roles.put("user", "USER");
    provider =
        new CachingAuthenticationProvider(
            username ->
                User.withUsername(username)
                    .password(
                        Optional.ofNullable(hashes.get(username))
                            .orElseThrow(() -> new UsernameNotFoundException("not found")))
                    .authorities(roles.get(username))
                    .build(),
            passwordEncoder,
            TTL,
            ticker);
    clearInvocations(passwordEncoder);
  }

  @SuppressWarnings("SameParameterValue")
  static UsernamePasswordAuthenticationToken token(String username, String password) {
    return UsernamePasswordAuthenticationToken.unauthenticated(username, password);
  }

  @Test
  void cacheHit_skipsPasswordEncoder() {
    val first = provider.authenticate(token("user", "secret"));
    val second = provider.authenticate(token("user", "secret"));

    assertThat(first.isAuthenticated()).isTrue();
    assertThat(second.isAuthenticated()).isTrue();
    assertThat(second.getAuthorities())
        .extracting(GrantedAuthority::getAuthority)
        .containsAll(first.getAuthorities().stream().map(GrantedAuthority::getAuthority).toList())
        .contains("USER");
    verify(passwordEncoder, times(1)).matches(any(), any());
  }

  @Test
  void wrongPassword_rejectedEvenWhenCached() {
    provider.authenticate(token("user", "secret"));

    assertThrows(BadCredentialsException.class, () -> provider.authenticate(token("user", "bad")));
    verify(passwordEncoder, times(2)).matches(any(), any());
  }

  @Test
  void failures_areNotCached() {
    assertThrows(BadCredentialsException.class, () -> provider.authenticate(token("user", "bad")));
    assertThrows(BadCredentialsException.class, () -> provider.authenticate(token("user", "bad")));
    verify(passwordEncoder, times(2)).matches(any(), any());

    assertThat(provider.authenticate(token("user", "secret")).isAuthenticated()).isTrue();
    verify(passwordEncoder, times(3)).matches(any(), any());
  }

  @Test
  void expiry_forcesReVerification() {
    provider.authenticate(token("user", "secret"));
    nanos.addAndGet(TTL.plusSeconds(1).toNanos());
    provider.authenticate(token("user", "secret"));

    verify(passwordEncoder, times(2)).matches(any(), any());
  }

  @Test
  void changedStoredHash_forcesReVerification() {
    provider.authenticate(token("user", "secret"));
    hashes.put("user", passwordEncoder.encode("other"));
    clearInvocations(passwordEncoder);

    assertThrows(
        BadCredentialsException.class, () -> provider.authenticate(token("user", "secret")));
    assertThat(provider.authenticate(token("user", "other")).isAuthenticated()).isTrue();
    verify(passwordEncoder, times(2)).matches(any(), any());
  }

  @Test
  void roleChange_appliedOnCacheHit() {
    provider.authenticate(token("user", "secret"));
    roles.put("user", "ADMIN");

    assertThat(provider.authenticate(token("user", "secret")).getAuthorities())
        .extracting(GrantedAuthority::getAuthority)
        .contains("ADMIN")
        .doesNotContain("USER");
    verify(passwordEncoder, times(1)).matches(any(), any());
  }

  @Test
  void evict_forcesReVerification() {
    provider.authenticate(token("user", "secret"));
    provider.evict("USER");
    provider.authenticate(token("user", "secret"));

    verify(passwordEncoder, times(2)).matches(any(), any());
  }
}
