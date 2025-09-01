/* SOHT2 © Licensed under MIT 2025. */
package net.soht2.common.util;

import com.fasterxml.uuid.Generators;
import com.fasterxml.uuid.impl.TimeBasedEpochGenerator;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * Utility class providing auxiliary methods.
 *
 * <p>This class is not meant to be instantiated and contains static utility methods.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class AuxUtil {

  private static final TimeBasedEpochGenerator UUID_V7 = Generators.timeBasedEpochGenerator();

  /**
   * Returns a unary operator that applies the given consumer to its input and returns the input
   * unchanged.
   *
   * @param consumer the consumer to apply
   * @param <T> the type of the input and output
   * @return a unary operator that applies the consumer and returns the input
   */
  public static <T> UnaryOperator<T> peek(Consumer<T> consumer) {
    Objects.requireNonNull(consumer, "Consumer must not be null");
    return v -> {
      consumer.accept(v);
      return v;
    };
  }

  /** Generates a UUID using version 7 (Unix Epoch time+random based). */
  public static UUID generateUUIDv7() {
    return UUID_V7.generate();
  }
}
