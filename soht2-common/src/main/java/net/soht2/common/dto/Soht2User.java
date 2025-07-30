/* SOHT2 © Licensed under MIT 2025. */
package net.soht2.common.dto;

import static java.util.Optional.ofNullable;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Set;
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

  @SuppressWarnings("java:S3864")
  public boolean isAllowedTarget(String host, int port) {
    val target = host + ":" + port;
    return ofNullable(allowedTargets).stream()
        .flatMap(Collection::stream)
        .map(p -> "^" + p.replaceAll("[^*]+", "\\\\Q$0\\\\E").replaceAll("\\*+", ".*") + "$")
        .peek(regex -> log.trace("isAllowedTarget: regex={}, target={}", regex, target))
        .map(Pattern::compile)
        .anyMatch(p -> p.asMatchPredicate().test(target));
  }
}
