/* SOHT2 © Licensed under MIT 2026. */
package net.soht2.common.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import lombok.val;
import org.jspecify.annotations.NonNull;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class AuxUtilReadAvailableTest {

  @SuppressWarnings("SameParameterValue")
  private static byte[] seq(int from, int len) {
    val bytes = new byte[len];
    for (int i = 0; i < len; i++) bytes[i] = (byte) (from + i);
    return bytes;
  }

  @Test
  void drainsAvailableBytesInOrder() throws IOException {
    val in = new ByteArrayInputStream(seq(0, 100));
    val buf = new byte[200];
    val first = in.read(buf, 0, 10);

    val n = AuxUtil.readAvailable(in, buf, first);

    assertThat(n).isEqualTo(100);
    assertThat(Arrays.copyOf(buf, n)).isEqualTo(seq(0, 100));
  }

  @Test
  void drainsUpToBufferLength() throws IOException {
    val in = new ByteArrayInputStream(seq(0, 100));
    val buf = new byte[30];
    val first = in.read(buf, 0, 10);

    val n = AuxUtil.readAvailable(in, buf, first);

    assertThat(n).isEqualTo(30);
    assertThat(buf).isEqualTo(seq(0, 30));
    assertThat(in.available()).isEqualTo(70);
  }

  @Test
  void neverBlocksWhenNothingIsAvailable() throws IOException {
    val in =
        new InputStream() {
          @Override
          public int read() {
            throw new AssertionError("must not read when nothing is available");
          }

          @Override
          public int available() {
            return 0;
          }
        };
    val buf = new byte[] {1, 2, 3, 0};

    assertThat(AuxUtil.readAvailable(in, buf, 3)).isEqualTo(3);
    assertThat(buf).containsExactly(1, 2, 3, 0);
  }

  @Test
  void readsOnlyWhatIsReportedAvailable() throws IOException {
    // a stream reporting chunks as available one by one, like a socket receiving segments
    val data = seq(0, 30);
    val in =
        new InputStream() {
          int pos = 10;

          @Override
          public int read() {
            throw new UnsupportedOperationException();
          }

          @Override
          public int read(byte @NonNull [] b, int off, int len) {
            assertThat(len).isLessThanOrEqualTo(available());
            System.arraycopy(data, pos, b, off, len);
            pos += len;
            return len;
          }

          @Override
          public int available() {
            return Math.min(7, data.length - pos);
          }
        };
    val buf = new byte[64];
    System.arraycopy(data, 0, buf, 0, 10);

    val n = AuxUtil.readAvailable(in, buf, 10);

    assertThat(n).isEqualTo(30);
    assertThat(Arrays.copyOf(buf, n)).isEqualTo(data);
  }

  @ParameterizedTest
  @ValueSource(ints = {0, -1})
  void returnsNonPositiveFirstReadAsIs(int firstRead) throws IOException {
    val in = new ByteArrayInputStream(seq(0, 10));
    assertThat(AuxUtil.readAvailable(in, new byte[16], firstRead)).isEqualTo(firstRead);
    assertThat(in.available()).isEqualTo(10);
  }

  @SuppressWarnings("resource")
  @Test
  void propagatesReadFailure() {
    val in =
        new InputStream() {
          @Override
          public int read() throws IOException {
            throw new IOException("boom");
          }

          @Override
          public int available() {
            return 5;
          }
        };
    assertThatThrownBy(() -> AuxUtil.readAvailable(in, new byte[16], 1))
        .isInstanceOf(IOException.class)
        .hasMessage("boom");
  }
}
