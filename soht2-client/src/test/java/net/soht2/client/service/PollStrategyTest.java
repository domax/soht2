/* SOHT2 © Licensed under MIT 2025. */
package net.soht2.client.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.junit.jupiter.api.Test;

@Slf4j
class PollStrategyTest {

  @Test
  void pollStrategiesTest() {
    val duringTime = Duration.ofMinutes(2);

    val exponent = new ExponentPollStrategy().init().allPeriods(duringTime).toList();
    val linear = new LinearPollStrategy().allPeriods(duringTime).toList();
    val constant = new ConstantPollStrategy().allPeriods(duringTime).toList();

    log.info("exponent[{}]: {}", exponent.size(), exponent);
    log.info("linear[{}]: {}", linear.size(), linear);
    log.info("constant[{}]: {}", constant.size(), constant);

    assertThat(exponent.size()).isLessThan(linear.size());
    assertThat(linear.size()).isLessThan(constant.size());
  }
}
