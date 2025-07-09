package net.soht2.server.test;

import io.vavr.control.Try;
import java.util.List;
import java.util.random.RandomGenerator;
import java.util.stream.Stream;
import lombok.*;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class UTHelper {

  public static byte[] createBinData(int dataSize) {
    val binData = new byte[dataSize];
    RandomGenerator.getDefault().nextBytes(binData);
    return binData;
  }

  @SuppressWarnings("java:S2925")
  public static List<EchoData> echo(
      Stream<byte[]> binDataStream, int portNumber, int socketTimeout, int bufferSize) {
    try (val server =
            EchoServer.builder()
                .portNumber(portNumber)
                .socketTimeout(socketTimeout)
                .bufferSize(bufferSize)
                .build();
        val client = new EchoClient(server)) {
      return binDataStream
          .peek(v -> Try.run(() -> Thread.sleep(500)).get())
          .map(bin -> EchoData.builder().original(bin).echoed(client.shout(bin)).build())
          .toList();
    }
  }

  @Builder
  public record EchoData(byte[] original, byte[] echoed) {}
}
