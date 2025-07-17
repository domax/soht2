/* SOHT2 © Licensed under MIT 2025. */
package net.soht2.common.compress;

import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * GZIPCompressor implements the {@link Compressor} interface to provide GZIP compression and
 * decompression. It uses {@link GZIPOutputStream} for compression and {@link GZIPInputStream} for
 * decompression.
 */
public class GZIPCompressor extends BaseIOStreamCompressor {

  @Override
  public String getAcceptEncoding() {
    return "gzip";
  }

  @Override
  public byte[] compress(byte[] data) {
    return compress(data, GZIPOutputStream::new);
  }

  @Override
  public byte[] decompress(byte[] data) {
    return decompress(data, GZIPInputStream::new);
  }
}
