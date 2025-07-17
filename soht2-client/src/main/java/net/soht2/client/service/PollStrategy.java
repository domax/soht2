/* SOHT2 © Licensed under MIT 2025. */
package net.soht2.client.service;

import java.time.Duration;

public sealed interface PollStrategy
    permits ConstantPollStrategy, LinearPollStrategy, ExponentPollStrategy {

  Duration getDelay(int iteration);
}
