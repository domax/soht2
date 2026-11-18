/* SOHT2 © Licensed under MIT 2026. */
package net.soht2.client.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import lombok.val;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class ThreadExecutorTest {

  @Test
  void threadExecutor_platformThreads_usesDedicatedCachedPool() throws Exception {
    val executor = new Soht2ClientConfig().threadExecutor(false);
    try {
      assertThat(executor).isNotSameAs(ForkJoinPool.commonPool());
      val delegate = (ExecutorService) ReflectionTestUtils.getField(executor, "delegate");
      assertThat(delegate)
          .isInstanceOf(ThreadPoolExecutor.class)
          .isNotInstanceOf(ForkJoinPool.class);
      assertThat(executor.submit(() -> Thread.currentThread().isVirtual()).get(5, TimeUnit.SECONDS))
          .isFalse();
    } finally {
      executor.shutdownNow();
    }
  }

  @SuppressWarnings("resource")
  @Test
  void threadExecutor_virtualThreads_usesVirtualThreads() throws Exception {
    val executor = new Soht2ClientConfig().threadExecutor(true);
    try {
      assertThat(executor.submit(() -> Thread.currentThread().isVirtual()).get(5, TimeUnit.SECONDS))
          .isTrue();
    } finally {
      executor.shutdownNow();
    }
  }
}
