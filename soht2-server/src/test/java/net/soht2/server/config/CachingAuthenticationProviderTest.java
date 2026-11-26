/* SOHT2 © Licensed under MIT 2026. */
package net.soht2.server.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.github.benmanes.caffeine.cache.Ticker;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.IntStream;
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

  /** The fingerprint as computed before {@code Mac} reuse: a new, initialized instance per call. */
  private byte[] referenceFingerprint(String key, String password) throws Exception {
    val mac = javax.crypto.Mac.getInstance("HmacSHA256");
    mac.init(provider.processKey);
    return mac.doFinal((key + ':' + password).getBytes(StandardCharsets.UTF_8));
  }

  @Test
  void fingerprint_matchesFreshMacForFixedInputs() throws Exception {
    for (val input : List.of("user:secret", "admin:p@ss:word", ":", "üser:пароль")) {
      val sep = input.indexOf(':');
      val key = input.substring(0, sep);
      val password = input.substring(sep + 1);
      assertThat(provider.fingerprint(key, password))
          .isEqualTo(referenceFingerprint(key, password))
          // repeated calls on the reused prototype are not affected by earlier ones
          .isEqualTo(provider.fingerprint(key, password));
    }
  }

  @Test
  void fingerprint_isThreadSafe() throws Exception {
    val expected = new HashMap<String, byte[]>();
    for (int i = 0; i < 16; i++)
      expected.put("user" + i, referenceFingerprint("user" + i, "pw" + i));
    try (val executor = Executors.newVirtualThreadPerTaskExecutor()) {
      val futures =
          IntStream.range(0, 2000)
              .mapToObj(n ->
                  executor.submit(() -> {
                    val i = n % 16;
                    return Arrays.equals(
                        provider.fingerprint("user" + i, "pw" + i), expected.get("user" + i));
                  }))
              .toList();
      for (val f : futures) assertThat(f.get()).isTrue();
    }
  }
}
