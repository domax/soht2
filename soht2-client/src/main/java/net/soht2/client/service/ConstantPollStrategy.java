/* SOHT2 © Licensed under MIT 2025. */
package net.soht2.client.service;

import java.time.Duration;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.RequiredArgsConstructor;

@Builder
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public final class ConstantPollStrategy implements PollStrategy {

  private final Duration delay;

  @Override
  public Duration getDelay(int iteration) {
    return iteration == 0 ? Duration.ZERO : delay;
  }
}
