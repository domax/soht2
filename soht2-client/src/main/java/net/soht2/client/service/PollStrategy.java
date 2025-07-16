/* SOHT2 © Licensed under MIT 2025. */
package net.soht2.client.service;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;
import lombok.val;

public sealed interface PollStrategy
    permits ConstantPollStrategy, LinearPollStrategy, ExponentPollStrategy {

  Duration getDelay(int iteration);

  default Stream<Duration> allPeriods(Duration duringTime) {
    val time = new AtomicReference<>(Duration.ZERO);
    return Stream.iterate(0, i -> i + 1)
        .map(this::getDelay)
        .takeWhile(v -> time.getAndAccumulate(v, Duration::plus).compareTo(duringTime) < 0);
  }
}
