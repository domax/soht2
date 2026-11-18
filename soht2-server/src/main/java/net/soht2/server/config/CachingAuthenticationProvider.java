/* SOHT2 © Licensed under MIT 2026. */
package net.soht2.server.config;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Ticker;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Locale;
import java.util.Objects;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * A {@link DaoAuthenticationProvider} that remembers successfully verified credentials for a
 * limited time, so repeated HTTP Basic requests with the same credentials do not run the
 * (deliberately slow) password hash check every time.
 *
 * <p>Only an HMAC-SHA256 fingerprint of the presented password is kept, computed with a random key
 * generated per process. A cached entry is valid only while the user's stored password hash is
 * unchanged. Failed verifications are never cached.
 */
@Slf4j
public class CachingAuthenticationProvider extends DaoAuthenticationProvider {

  static final long MAX_ENTRIES = 1000;
  private static final String HMAC_ALGORITHM = "HmacSHA256";

  private record Entry(String passwordHash, byte[] fingerprint) {

    @Override
    public boolean equals(Object obj) {
      if (this == obj) return true;
      if (!(obj instanceof Entry(String objPasswordHash, byte[] objFingerprint))) return false;
      return Objects.equals(passwordHash, objPasswordHash)
          && MessageDigest.isEqual(fingerprint, objFingerprint);
    }

    @Override
    public int hashCode() {
      return Objects.hash(passwordHash, MessageDigest.isEqual(fingerprint, fingerprint));
    }

    @SuppressWarnings("NullableProblems")
    @Override
    public String toString() {
      return "Entry{passwordHash='[PROTECTED]', fingerprint=[PROTECTED]}";
    }
  }

  private final Cache<String, Entry> credentialCache;
  private final SecretKeySpec processKey;

  public CachingAuthenticationProvider(
      UserDetailsService userDetailsService, PasswordEncoder passwordEncoder, Duration ttl) {
    this(userDetailsService, passwordEncoder, ttl, Ticker.systemTicker());
  }

  CachingAuthenticationProvider(
      UserDetailsService userDetailsService,
      PasswordEncoder passwordEncoder,
      Duration ttl,
      Ticker ticker) {
    super(userDetailsService);
    setPasswordEncoder(passwordEncoder);
    this.credentialCache =
        Caffeine.newBuilder().maximumSize(MAX_ENTRIES).expireAfterWrite(ttl).ticker(ticker).build();
    val key = new byte[32];
    new SecureRandom().nextBytes(key);
    this.processKey = new SecretKeySpec(key, HMAC_ALGORITHM);
    log.info("CachingAuthenticationProvider: ttl={}, maxEntries={}", ttl, MAX_ENTRIES);
  }

  /**
   * Removes remembered credentials of the given user.
   *
   * @param username the name of the user
   */
  public void evict(String username) {
    credentialCache.invalidate(cacheKey(username));
  }

  @Override
  protected void additionalAuthenticationChecks(
      UserDetails userDetails, UsernamePasswordAuthenticationToken authentication)
      throws AuthenticationException {
    val credentials = authentication.getCredentials();
    val storedPasswordHash = userDetails.getPassword();
    if (credentials == null || storedPasswordHash == null) {
      super.additionalAuthenticationChecks(userDetails, authentication);
      return;
    }

    val key = cacheKey(userDetails.getUsername());
    val fingerprint = fingerprint(key, credentials.toString());
    val newEntry = new Entry(storedPasswordHash, fingerprint);
    val entry = credentialCache.getIfPresent(key);
    if (newEntry.equals(entry)) {
      log.trace("additionalAuthenticationChecks: cache hit for {}", key);
      return;
    }

    super.additionalAuthenticationChecks(userDetails, authentication);
    credentialCache.put(key, newEntry);
  }

  private static String cacheKey(String username) {
    return username.toLowerCase(Locale.ROOT);
  }

  private byte[] fingerprint(String key, String password) {
    try {
      val mac = Mac.getInstance(HMAC_ALGORITHM);
      mac.init(processKey);
      return mac.doFinal((key + ':' + password).getBytes(StandardCharsets.UTF_8));
    } catch (GeneralSecurityException e) {
      throw new IllegalStateException("Cannot compute credential fingerprint", e);
    }
  }
}
