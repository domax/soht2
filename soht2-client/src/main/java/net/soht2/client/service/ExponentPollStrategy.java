/* SOHT2 © Licensed under MIT 2025. */
package net.soht2.client.service;

import static java.time.Duration.ZERO;

import io.vavr.Function1;
import java.time.Duration;
import lombok.Builder;
import lombok.extern.slf4j.Slf4j;
import lombok.val;

@Slf4j
public final class ExponentPollStrategy implements PollStrategy {

  private final int factor;
  private final Duration initialDelay;
  private final Duration maxDelay;

  private final Function1<Integer, Duration> periodCache =
      Function1.of(this::computeDelay).memoized();

  private int maxIteration = Integer.MAX_VALUE;

  @Builder
  private ExponentPollStrategy(int factor, Duration initialDelay, Duration maxDelay) {
    this.factor = factor;
    this.initialDelay = initialDelay;
    this.maxDelay = maxDelay;
  }

  @Override
  public Duration getDelay(int iteration) {
    val delay = iteration == 0 ? ZERO : periodCache.apply(Math.min(iteration, maxIteration));
    if (delay.compareTo(maxDelay) >= 0) {
      maxIteration = iteration;
      return maxDelay;
    }
    return delay;
  }

  private Duration computeDelay(int iteration) {
    return Duration.ofMillis(
        (long) (initialDelay.toMillis() * Math.exp((double) iteration / factor)));
  }
}
