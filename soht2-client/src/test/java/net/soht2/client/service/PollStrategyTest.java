/* SOHT2 © Licensed under MIT 2025. */
package net.soht2.client.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.junit.jupiter.api.Test;

@Slf4j
class PollStrategyTest {

  @Test
  void pollStrategiesTest() {
    val factor = 5;
    val initialDelay = Duration.ofMillis(100);
    val maxDelay = Duration.ofSeconds(10);
    val duringTime = Duration.ofMinutes(2);

    val exponent =
        allPeriods(
                ExponentPollStrategy.builder()
                    .initialDelay(initialDelay)
                    .maxDelay(maxDelay)
                    .factor(factor)
                    .build(),
                duringTime)
            .toList();
    val linear =
        allPeriods(
                LinearPollStrategy.builder().initialDelay(initialDelay).maxDelay(maxDelay).build(),
                duringTime)
            .toList();
    val constant =
        allPeriods(ConstantPollStrategy.builder().delay(Duration.ofSeconds(1)).build(), duringTime)
            .toList();

    log.info("exponent[{}]: {}", exponent.size(), exponent);
    log.info("linear[{}]: {}", linear.size(), linear);
    log.info("constant[{}]: {}", constant.size(), constant);

    assertThat(exponent.size()).isLessThan(linear.size());
    assertThat(linear.size()).isLessThan(constant.size());
  }

  Stream<Duration> allPeriods(PollStrategy pollStrategy, Duration duringTime) {
    val time = new AtomicReference<>(Duration.ZERO);
    return Stream.iterate(0, i -> i + 1)
        .map(pollStrategy::getDelay)
        .takeWhile(v -> time.getAndAccumulate(v, Duration::plus).compareTo(duringTime) < 0);
  }
}
