/* SOHT2 © Licensed under MIT 2025. */
package net.soht2.client.test;

import java.util.random.RandomGenerator;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import lombok.val;

@Slf4j
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class UTHelper {

  public static byte[] createBinData(int dataSize) {
    val binData = new byte[dataSize];
    RandomGenerator.getDefault().nextBytes(binData);
    return binData;
  }
}
