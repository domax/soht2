/* SOHT2 © Licensed under MIT 2025. */
package net.soht2.common.compress;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

@Slf4j
class CompressorTest {

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
        Arguments.of(Compressor.compressorCache.apply("gzip")),
        Arguments.of(Compressor.compressorCache.apply("deflate")),
        Arguments.of(Compressor.compressorCache.apply("identity")));
  }
}
