/* SOHT2 © Licensed under MIT 2026. */
package net.soht2.client.config;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.*;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;

@Slf4j
class TimeoutAwareExecutorService implements ExecutorService, AutoCloseable {

  private final ExecutorService delegate;

  TimeoutAwareExecutorService(ExecutorService delegate) {
    log.info("constructor: delegate.class={}", delegate.getClass().getName());
    this.delegate = delegate;
  }

  @Override
  @SneakyThrows
  public void close() {
    log.info("close: shutting down {} service", delegate.getClass().getSimpleName());
    delegate.shutdown();
    if (!delegate.awaitTermination(5, TimeUnit.SECONDS)) {
      log.warn("close: enforce halting {} service", delegate.getClass().getSimpleName());
      delegate.shutdownNow();
      if (!delegate.awaitTermination(5, TimeUnit.SECONDS))
        log.error("Failed to terminate {} service", delegate.getClass().getSimpleName());
    }
  }

  @Override
  public void execute(@NonNull Runnable command) {
    delegate.execute(command);
  }

  @Override
  public void shutdown() {
    delegate.shutdown();
  }

  @Override
  public @NonNull List<Runnable> shutdownNow() {
    return delegate.shutdownNow();
  }

  @Override
  public boolean isShutdown() {
    return delegate.isShutdown();
  }

  @Override
  public boolean isTerminated() {
    return delegate.isTerminated();
  }

  @Override
  public boolean awaitTermination(long timeout, @NonNull TimeUnit unit)
      throws InterruptedException {
    return delegate.awaitTermination(timeout, unit);
  }

  @Override
  public @NonNull <T> Future<T> submit(@NonNull Callable<T> task) {
    return delegate.submit(task);
  }

  @Override
  public @NonNull <T> Future<T> submit(@NonNull Runnable task, T result) {
    return delegate.submit(task, result);
  }

  @Override
  public @NonNull Future<?> submit(@NonNull Runnable task) {
    return delegate.submit(task);
  }

  @Override
  public <T> @NonNull List<Future<T>> invokeAll(@NonNull Collection<? extends Callable<T>> tasks)
      throws InterruptedException {
    return delegate.invokeAll(tasks);
  }

  @Override
  public <T> @NonNull List<Future<T>> invokeAll(
      @NonNull Collection<? extends Callable<T>> tasks, long timeout, @NonNull TimeUnit unit)
      throws InterruptedException {
    return delegate.invokeAll(tasks, timeout, unit);
  }

  @Override
  public @NonNull <T> T invokeAny(@NonNull Collection<? extends Callable<T>> tasks)
      throws InterruptedException, ExecutionException {
    return delegate.invokeAny(tasks);
  }

  @Override
  public <T> T invokeAny(
      @NonNull Collection<? extends Callable<T>> tasks, long timeout, @NonNull TimeUnit unit)
      throws InterruptedException, ExecutionException, TimeoutException {
    return delegate.invokeAny(tasks, timeout, unit);
  }
}
