/* SOHT2 © Licensed under MIT 2025. */
package net.soht2.common.compress;

import static java.util.Optional.ofNullable;

import io.vavr.Function1;
import io.vavr.control.Try;

/**
 * Compressor interface defines methods for compressing and decompressing byte arrays.
 * Implementations should provide specific compression algorithms.
 */
public interface Compressor {

  /**
   * A cache for compressor instances based on the encoding type. This avoids creating multiple
   * instances of the same compressor for the same encoding type.
   */
  Function1<String, Compressor> compressorCache =
      Function1.<String, Compressor>of(
              encoding ->
                  switch (ofNullable(encoding)
                      .map(String::toUpperCase)
                      .map(v -> Try.of(() -> CompressionType.valueOf(v)).getOrNull())
                      .orElse(CompressionType.NONE)) {
                    case GZIP -> new GZIPCompressor();
                    case DEFLATE -> new DeflateCompressor();
                    default -> new IdentityCompressor();
                  })
          .memoized();

  /**
   * Returns the encoding type accepted by this compressor. This is typically used in HTTP headers
   * to indicate the compression method.
   *
   * @return the "Accept-Encoding" type as a String
   */
  String getAcceptEncoding();

  /**
   * Returns the content encoding type used by this compressor. By default, it returns the same
   * value as {@link #getAcceptEncoding()}.
   *
   * @return the "Content-Encoding" type as a String
   */
  default String getContentEncoding() {
    return getAcceptEncoding();
  }

  /**
   * Compresses the given byte array.
   *
   * @param data the byte array to compress
   * @return a compressed byte array
   */
  byte[] compress(byte[] data);

  /**
   * Decompresses the given byte array.
   *
   * @param data the byte array to decompress
   * @return a decompressed byte array
   */
  byte[] decompress(byte[] data);
}
