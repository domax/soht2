/* SOHT2 © Licensed under MIT 2025. */
package net.soht2.client.service;

import java.time.Duration;
import lombok.RequiredArgsConstructor;
import lombok.val;

@RequiredArgsConstructor
public final class LinearPollStrategy implements PollStrategy {

  private static final Duration INITIAL = Duration.ofMillis(100);
  private static final Duration MAX_DELAY = Duration.ofSeconds(10);

  @Override
  public Duration getDelay(int iteration) {
    val delay = INITIAL.multipliedBy(iteration);
    return delay.compareTo(MAX_DELAY) > 0 ? MAX_DELAY : delay;
  }
}
