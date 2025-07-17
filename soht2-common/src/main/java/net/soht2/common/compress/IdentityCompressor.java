/* SOHT2 © Licensed under MIT 2025. */
package net.soht2.common.compress;

/**
 * IdentityCompressor is a no-op compressor that does not modify the data. It is used when no
 * compression is desired or when the data is already in an uncompressed format.
 */
public class IdentityCompressor implements Compressor {

  @Override
  public String getAcceptEncoding() {
    return "identity";
  }

  /**
   * Returns null for content encoding, as this compressor does not apply any encoding. This is
   * consistent with the identity compression method.
   *
   * @return {@code null}
   */
  @Override
  public String getContentEncoding() {
    return null;
  }

  @Override
  public byte[] compress(byte[] data) {
    return data;
  }

  @Override
  public byte[] decompress(byte[] data) {
    return data;
  }
}
