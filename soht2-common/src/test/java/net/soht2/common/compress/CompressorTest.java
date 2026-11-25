/* SOHT2 © Licensed under MIT 2025. */
package net.soht2.common.compress;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.io.ByteArrayOutputStream;
import java.util.stream.Stream;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.GZIPOutputStream;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

@Slf4j
class CompressorTest {

  @SuppressWarnings("SpellCheckingInspection")
  static final String DATA =
      """
      Lorem ipsum dolor sit amet, consectetur adipiscing elit. \
      Gubergren ad adipisici iure odio wisi pariatur consectetuer luptatum duis sadipscing fugiat \
      veniam molestie. Amet diam adipisici id sint. Aliquyam consectetur option.

      Et aliquip lorem quis sint diam sanctus augue. Nisl hendrerit consectetur justo minim augue \
      augue. Diam facer consetetur eiusmod. Ea accumsan nobis.

      Exerci at mazim stet lobortis consequat cum elitr wisi placerat takimata eleifend vel \
      eleifend. Exerci consequat nonumy facilisis, luptatum rebum anim mollit pariatur autem liber \
      te aute eleifend laoreet vero nonummy eiusmod duo laboris reprehenderit kasd, kasd sed non \
      delenit lobortis mollit augue cillum tempor nobis ex sadipscing illum nam voluptate. Enim no \
      commodo eros gubergren obcaecat consectetuer at, aliquip wisi accusam sea tincidunt \
      adipisici aliqua nisi magna feugait rebum eu voluptate delenit possim laborum iusto obcaecat \
      dolore option. Velit lorem clita exercitation amet no wisi commodo iure nonumy imperdiet \
      zzril. Vulputate luptatum iusto.

      Iriure imperdiet tincidunt vel clita officia deserunt officia vero ullamco veniam nisi eros \
      lorem tincidunt. Voluptate et est ipsum amet. Sea sadipscing magna. Euismod nostrud \
      imperdiet dolore hendrerit eos, volutpat facer adipiscing facilisi voluptate reprehenderit \
      option facilisis autem consequat iriure euismod in delenit stet. Sea aute elitr.
      """;

  @ParameterizedTest
  @MethodSource("compressor_OK_Args")
  void compressor_OK(Compressor compressor) {
    val compressedData = compressor.compress(DATA.getBytes());
    val decompressedData = compressor.decompress(compressedData);
    assertThat(new String(decompressedData)).isEqualTo(DATA);
    assertThat(compressedData.length).isLessThanOrEqualTo(decompressedData.length);
    log.info(
        "{} decompressed size: {}, compressed size: {}",
        compressor.getAcceptEncoding(),
        decompressedData.length,
        compressedData.length);

    assertThat(compressor.compress(null)).isNull();
    assertThat(compressor.decompress(null)).isNull();
    assertThat(compressor.compress(new byte[0])).isEmpty();
    assertThat(compressor.decompress(new byte[0])).isEmpty();
  }

  static Stream<Arguments> compressor_OK_Args() {
    return Stream.of(
        Arguments.of(Compressor.compressorCache.apply("GZip")),
        Arguments.of(Compressor.compressorCache.apply("Deflate")),
        Arguments.of(Compressor.compressorCache.apply("Identity")),
        Arguments.of(Compressor.compressorCache.apply("None")));
  }

  @ParameterizedTest
  @ValueSource(strings = {"gzip", "deflate", "identity"})
  void decompress_withinLimit_OK(String encoding) {
    val compressor = Compressor.compressorCache.apply(encoding);
    val bytes = DATA.getBytes();
    val compressed = compressor.compress(bytes);
    assertThat(compressor.decompress(compressed, bytes.length)).isEqualTo(bytes);
    assertThat(compressor.decompress(null, 0)).isNull();
    assertThat(compressor.decompress(new byte[0], 0)).isEmpty();
  }

  @ParameterizedTest
  @ValueSource(strings = {"gzip", "deflate", "identity"})
  void decompress_aboveLimit_FAIL(String encoding) {
    val compressor = Compressor.compressorCache.apply(encoding);
    val bytes = DATA.getBytes();
    val compressed = compressor.compress(bytes);
    assertThatExceptionOfType(DecompressedSizeExceededException.class)
        .isThrownBy(() -> compressor.decompress(compressed, bytes.length - 1L))
        .satisfies(e -> assertThat(e.getMaxBytes()).isEqualTo(bytes.length - 1L));
  }

  @ParameterizedTest
  @ValueSource(strings = {"gzip", "deflate"})
  void decompress_compressionBomb_FAIL(String encoding) throws Exception {
    val bomb = compressedZeros(encoding, 100L * 1024 * 1024);
    assertThat(bomb.length).isLessThan(1024 * 1024);
    val compressor = Compressor.compressorCache.apply(encoding);
    val runtime = Runtime.getRuntime();
    runtime.gc();
    val usedBefore = runtime.totalMemory() - runtime.freeMemory();
    assertThatExceptionOfType(DecompressedSizeExceededException.class)
        .isThrownBy(() -> compressor.decompress(bomb, 4L * 1024 * 1024));
    val usedAfter = runtime.totalMemory() - runtime.freeMemory();
    log.info(
        "{} bomb: compressed={}, heap delta={}", encoding, bomb.length, usedAfter - usedBefore);
    assertThat(usedAfter - usedBefore).isLessThan(64L * 1024 * 1024);
  }

  @SuppressWarnings("SameParameterValue")
  private static byte[] compressedZeros(String encoding, long size) throws Exception {
    val bytes = new ByteArrayOutputStream();
    try (val os =
        "gzip".equals(encoding) ? new GZIPOutputStream(bytes) : new DeflaterOutputStream(bytes)) {
      val chunk = new byte[64 * 1024];
      for (long written = 0; written < size; written += chunk.length) os.write(chunk);
    }
    return bytes.toByteArray();
  }
}
