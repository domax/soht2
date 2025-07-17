/* SOHT2 © Licensed under MIT 2025. */
package net.soht2.common.compress;

import io.vavr.CheckedFunction1;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import lombok.val;

@Slf4j
abstract class BaseIOStreamCompressor implements Compressor {

  @SneakyThrows
  byte[] compress(byte[] data, CheckedFunction1<ByteArrayOutputStream, OutputStream> osFunction) {
    if (data == null || data.length == 0) return data;
    val byteStream = new ByteArrayOutputStream();
    try (val outputStream = osFunction.apply(byteStream)) {
      outputStream.write(data);
    }
    log.trace("compress: data.length: {}, compressed.length={}", data.length, byteStream.size());
    return byteStream.toByteArray();
  }

  @SneakyThrows
  byte[] decompress(byte[] data, CheckedFunction1<ByteArrayInputStream, InputStream> isFunction) {
    if (data == null || data.length == 0) return data;
    try (val inputStream = isFunction.apply(new ByteArrayInputStream(data))) {
      val decompressedData = inputStream.readAllBytes();
      log.trace(
          "decompress: data.length: {}, decompressed.length={}",
          data.length,
          decompressedData.length);
      return decompressedData;
    }
  }
}
