/* SOHT2 © Licensed under MIT 2025. */
package net.soht2.client.service;

import static java.time.Duration.ZERO;

import io.vavr.Function1;
import jakarta.annotation.PostConstruct;
import java.time.Duration;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import lombok.val;

@Slf4j
@RequiredArgsConstructor
public final class ExponentPollStrategy implements PollStrategy {

  private static final int KF_SLOPE = 5;
  private static final Duration INITIAL = Duration.ofMillis(100);
  private static final Duration MAX_DELAY = Duration.ofSeconds(10);

  private final Function1<Integer, Duration> periodCache =
      Function1.of(this::computeDelay).memoized();

  private int maxIteration = Integer.MAX_VALUE;

  @PostConstruct
  ExponentPollStrategy init() {
    maxIteration =
        (int)
            Stream.iterate(0, i -> i + 1)
                .map(this::getDelay)
                .takeWhile(v -> v.compareTo(MAX_DELAY) < 0)
                .count();
    log.info("init: maxIteration={}", maxIteration);
    return this;
  }

  @Override
  public Duration getDelay(int iteration) {
    val delay = iteration == 0 ? ZERO : periodCache.apply(Math.min(iteration, maxIteration));
    return delay.compareTo(MAX_DELAY) > 0 ? MAX_DELAY : delay;
  }

  private Duration computeDelay(int iteration) {
    return Duration.ofMillis((long) (INITIAL.toMillis() * Math.exp((double) iteration / KF_SLOPE)));
  }
}
