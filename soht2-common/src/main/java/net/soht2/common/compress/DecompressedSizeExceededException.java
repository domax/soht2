/* SOHT2 © Licensed under MIT 2026. */
package net.soht2.common.compress;

import lombok.Getter;

/** Thrown when decompressed data would exceed the allowed maximum size. */
@Getter
public class DecompressedSizeExceededException extends RuntimeException {

  /** The maximum allowed size of the decompressed data, in bytes. */
  private final long maxBytes;

  public DecompressedSizeExceededException(long maxBytes) {
    super("Decompressed data exceeds the limit of " + maxBytes + " bytes");
    this.maxBytes = maxBytes;
  }
}
