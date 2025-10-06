/* SOHT2 © Licensed under MIT 2025. */
package net.soht2.common.compress;

import java.util.zip.DeflaterOutputStream;
import java.util.zip.InflaterInputStream;

/**
 * DeflateCompressor implements the {@link Compressor} interface to provide compression and
 * decompression using the Deflate algorithm. It uses {@link DeflaterOutputStream} for compression
 * and {@link InflaterInputStream} for decompression.
 */
public class DeflateCompressor extends BaseIOStreamCompressor {

  @Override
  public String getAcceptEncoding() {
    return CompressionType.DEFLATE.name().toLowerCase();
  }

  @Override
  public byte[] compress(byte[] data) {
    return compress(data, DeflaterOutputStream::new);
  }

  @Override
  public byte[] decompress(byte[] data) {
    return decompress(data, InflaterInputStream::new);
  }
}
