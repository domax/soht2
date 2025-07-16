/* SOHT2 © Licensed under MIT 2025. */
package net.soht2.client.service;

import java.time.Duration;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public final class ConstantPollStrategy implements PollStrategy {

  private static final Duration DELAY = Duration.ofSeconds(1);

  @Override
  public Duration getDelay(int iteration) {
    return iteration == 0 ? Duration.ZERO : DELAY;
  }
}
