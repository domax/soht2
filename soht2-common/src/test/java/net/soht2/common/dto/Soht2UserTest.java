/* SOHT2 © Licensed under MIT 2025. */
package net.soht2.common.dto;

import static ch.qos.logback.classic.Level.TRACE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.slf4j.Logger.ROOT_LOGGER_NAME;

import ch.qos.logback.classic.Logger;
import io.vavr.control.Try;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import lombok.val;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.LoggerFactory;

class Soht2UserTest {

  @BeforeAll
  static void beforeAll() {
    ((Logger) LoggerFactory.getLogger(ROOT_LOGGER_NAME)).setLevel(TRACE);
  }

  @ParameterizedTest
  @MethodSource("isAllowedTarget_Args")
  void isAllowedTarget_Patterns(String host, int port, boolean expected) {
    val user =
        Soht2User.builder()
            .allowedTargets(Set.of("localhost:8080", "*.example.com:80", "ssh.*:22", "**.net:*8*"))
            .build();
    assertThat(user.isAllowedTarget(host, port)).isEqualTo(expected);
  }

  @ParameterizedTest
  @MethodSource("isAllowedTarget_Args")
  void isAllowedTarget_AllowAll(String host, int port) {
    val user = Soht2User.builder().allowedTargets(Set.of("*:*")).build();
    assertThat(user.isAllowedTarget(host, port)).isTrue();
  }

  @ParameterizedTest
  @MethodSource("isAllowedTarget_Args")
  void isAllowedTarget_AllowNone(String host, int port) {
    val user = Soht2User.builder().allowedTargets(Set.of()).build();
    assertThat(user.isAllowedTarget(host, port)).isFalse();
  }

  static Stream<Arguments> isAllowedTarget_Args() {
    return Stream.of(
        Arguments.of("localhost", 8080, true),
        Arguments.of("example.com", 80, false),
        Arguments.of("foo.example.com", 80, true),
        Arguments.of("foo.example.com", 81, false),
        Arguments.of("ssh.example.com", 22, true),
        Arguments.of("ssh.example.com", 20, false),
        Arguments.of("foo.net", 8, true),
        Arguments.of("foo.bar.net", 80, true),
        Arguments.of(".net", 808, true),
        Arguments.of("foo.net", 182, true),
        Arguments.of("foo.net", 172, false));
  }

  @ParameterizedTest
  @MethodSource("isAllowedTarget_Args")
  void isAllowedTarget_cachedPatternsGiveSameResults(String host, int port, boolean expected) {
    val user =
        Soht2User.builder()
            .allowedTargets(Set.of("localhost:8080", "*.example.com:80", "ssh.*:22", "**.net:*8*"))
            .build();
    // the first call compiles and caches the patterns, the second one uses the cache
    assertThat(user.isAllowedTarget(host, port)).isEqualTo(expected);
    assertThat(Soht2User.PATTERN_CACHE).containsKeys("localhost:8080", "**.net:*8*");
    assertThat(user.isAllowedTarget(host, port)).isEqualTo(expected);
  }

  /** The implementation before pattern caching, used as the reference for unusual input. */
  static boolean legacyIsAllowedTarget(Set<String> allowedTargets, String host, int port) {
    val target = host + ":" + port;
    return allowedTargets.stream()
        .map(p -> "^" + p.replaceAll("[^*]+", "\\\\Q$0\\\\E").replaceAll("\\*+", ".*") + "$")
        .map(java.util.regex.Pattern::compile)
        .anyMatch(p -> p.asMatchPredicate().test(target));
  }

  @ParameterizedTest
  @ValueSource(
      strings = {"a.b(c[:1+", "a.b(c[:1*", "x\\E.*:2", "x\\E(:2", "\\Q*\\E:*", "[:3", "*\\:*"})
  void isAllowedTarget_unusualExpressionsBehaveAsBefore(String expression) {
    val targets = Set.of(expression);
    val user = Soht2User.builder().allowedTargets(targets).build();
    for (val host : List.of("a.b(c[", "axb(c[", "x\\E.y", "x", "\\Q", "[", "h\\")) {
      for (val port : List.of(1, 2, 3, 12)) {
        val legacy = Try.of(() -> legacyIsAllowedTarget(targets, host, port));
        val actual = Try.of(() -> user.isAllowedTarget(host, port));
        if (legacy.isSuccess()) assertThat(actual.get()).isEqualTo(legacy.get());
        else assertThat(actual.getCause()).isExactlyInstanceOf(legacy.getCause().getClass());
      }
    }
  }

  @Test
  void isAllowedTarget_nullExpressionFailsAsBefore() {
    val targets = new HashSet<String>();
    targets.add(null);
    val user = Soht2User.builder().allowedTargets(targets).build();
    assertThatNullPointerException().isThrownBy(() -> user.isAllowedTarget("host", 1));
  }

  @Test
  void targetPattern_cacheIsBounded() {
    for (int i = 0; i < Soht2User.PATTERN_CACHE_MAX_SIZE + 100; i++)
      Soht2User.targetPattern("bounded-" + i + ".example.com:*");
    assertThat(Soht2User.PATTERN_CACHE).hasSizeLessThanOrEqualTo(Soht2User.PATTERN_CACHE_MAX_SIZE);
    // expressions beyond the bound are still compiled correctly, just not cached
    val user =
        Soht2User.builder()
            .allowedTargets(Set.of("uncached-" + System.nanoTime() + ".example.com:*"))
            .build();
    assertThat(user.isAllowedTarget("host", 1)).isFalse();
    assertThat(Soht2User.targetPattern("beyond.*:1").matcher("beyond.x:1").matches()).isTrue();
  }
}
