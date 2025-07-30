/* SOHT2 © Licensed under MIT 2025. */
package net.soht2.common.dto;

import static ch.qos.logback.classic.Level.TRACE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.slf4j.Logger.ROOT_LOGGER_NAME;

import ch.qos.logback.classic.Logger;
import java.util.Set;
import java.util.stream.Stream;
import lombok.val;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
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
}
