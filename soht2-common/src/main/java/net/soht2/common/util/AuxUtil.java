package net.soht2.common.util;

import java.util.Objects;
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
}
