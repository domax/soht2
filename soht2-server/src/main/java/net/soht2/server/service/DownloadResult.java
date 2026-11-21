/* SOHT2 © Licensed under MIT 2026. */
package net.soht2.server.service;

import java.util.Arrays;
import java.util.Objects;
import lombok.val;
import org.jspecify.annotations.Nullable;

/**
 * The outcome of a duplex download request: target data, an empty result when the hold time elapsed
 * without data, or the target end-of-stream marker.
 *
 * @param data the bytes read from the target; empty for the empty and end-of-stream results
 * @param eos whether the target has closed its connection
 */
public record DownloadResult(byte[] data, boolean eos) {

  private static final byte[] NO_DATA = new byte[0];

  /** The result of a download that was held until the hold timeout without target data. */
  public static final DownloadResult EMPTY = new DownloadResult(NO_DATA, false);

  /** The result of a download that observed the target end-of-stream. */
  @SuppressWarnings("java:S1845")
  public static final DownloadResult EOS = new DownloadResult(NO_DATA, true);

  /**
   * Creates a result carrying target data.
   *
   * @param data the bytes read from the target
   * @return a data result, or {@link #EMPTY} when {@code data} is empty
   */
  public static DownloadResult of(byte[] data) {
    return data.length == 0 ? EMPTY : new DownloadResult(data, false);
  }

  /** Returns {@code true} when this result carries neither data nor end-of-stream. */
  public boolean isEmpty() {
    return !eos && data.length == 0;
  }

  @Override
  public boolean equals(@Nullable Object o) {
    if (o == null || getClass() != o.getClass()) return false;
    val that = (DownloadResult) o;
    return eos == that.eos && Objects.deepEquals(data, that.data);
  }

  @Override
  public int hashCode() {
    return Objects.hash(Arrays.hashCode(data), eos);
  }

  @Override
  public String toString() {
    return "DownloadResult{"
        + "data("
        + data.length
        + ")="
        + (data.length <= 10
            ? Arrays.toString(data)
            : Arrays.toString(Arrays.copyOfRange(data, 0, 10)).replace("]", "...]"))
        + ", eos="
        + eos
        + '}';
  }
}
