/* SOHT2 © Licensed under MIT 2025. */
package net.soht2.client.service;

import java.time.Duration;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.RequiredArgsConstructor;
import lombok.val;

@Builder
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public final class LinearPollStrategy implements PollStrategy {

  private final Duration initialDelay;
  private final Duration maxDelay;

  @Override
  public Duration getDelay(int iteration) {
    val delay = initialDelay.multipliedBy(iteration);
    return delay.compareTo(maxDelay) > 0 ? maxDelay : delay;
  }
}
