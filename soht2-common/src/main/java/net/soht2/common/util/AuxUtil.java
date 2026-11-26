/* SOHT2 © Licensed under MIT 2025. */
package net.soht2.common.util;

import com.fasterxml.uuid.Generators;
import com.fasterxml.uuid.impl.TimeBasedEpochGenerator;
import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.val;

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

  /**
   * Appends the bytes that are already available on the stream to a buffer that holds the result of
   * a first (possibly blocking) read, without blocking again. Reading stops when the buffer is
   * full, the stream reports no available bytes, or a read returns no data.
   *
   * @param in the stream to drain
   * @param buf the buffer whose first {@code firstRead} bytes are already filled
   * @param firstRead the number of bytes returned by the first read; values {@code <= 0} are
   *     returned as is, without reading
   * @return the total number of bytes in {@code buf}, or {@code firstRead} if it is not positive
   * @throws IOException if reading from the stream fails
   */
  public static int readAvailable(InputStream in, byte[] buf, int firstRead) throws IOException {
    if (firstRead <= 0) return firstRead;
    var n = firstRead;
    int available;
    while (n < buf.length && (available = in.available()) > 0) {
      val read = in.read(buf, n, Math.min(available, buf.length - n));
      if (read <= 0) break;
      n += read;
    }
    return n;
  }
}
