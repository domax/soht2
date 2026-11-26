/* SOHT2 © Licensed under MIT 2025. */
package net.soht2.common.dto;

import static java.util.Optional.ofNullable;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import lombok.Builder;
import lombok.extern.slf4j.Slf4j;
import lombok.val;

@Slf4j
@Builder
public record Soht2User(
    String username,
    String role,
    LocalDateTime createdAt,
    LocalDateTime updatedAt,
    Set<String> allowedTargets) {

  /** The maximum number of compiled allowed-target patterns kept in {@link #PATTERN_CACHE}. */
  static final int PATTERN_CACHE_MAX_SIZE = 1024;

  /** Compiled allowed-target patterns, keyed by allowed-target expression. */
  static final Map<String, Pattern> PATTERN_CACHE = new ConcurrentHashMap<>();

  @SuppressWarnings("java:S3864")
  public boolean isAllowedTarget(String host, int port) {
    val target = host + ":" + port;
    return ofNullable(allowedTargets).stream()
        .flatMap(Collection::stream)
        .map(Soht2User::targetPattern)
        .peek(p -> log.trace("isAllowedTarget: pattern={}, target={}", p, target))
        .anyMatch(p -> p.matcher(target).matches());
  }

  /**
   * Returns the compiled pattern of an allowed-target expression, where {@code *} matches any
   * characters and everything else matches literally. Patterns are cached until the cache holds
   * {@link #PATTERN_CACHE_MAX_SIZE} entries; after that, new expressions are compiled per call.
   */
  static Pattern targetPattern(String expression) {
    val cached = PATTERN_CACHE.get(expression);
    if (cached != null) return cached;
    val pattern =
        Pattern.compile(
            "^" + expression.replaceAll("[^*]+", "\\\\Q$0\\\\E").replaceAll("\\*+", ".*") + "$");
    if (PATTERN_CACHE.size() < PATTERN_CACHE_MAX_SIZE)
      PATTERN_CACHE.putIfAbsent(expression, pattern);
    return pattern;
  }
}
