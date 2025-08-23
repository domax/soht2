/* SOHT2 © Licensed under MIT 2025. */
package net.soht2.common.dto;

import static net.soht2.common.UTHelper.getObjectMapper;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.Set;
import lombok.val;
import org.junit.jupiter.api.Test;

class Soht2ConnectionTest {

  @Test
  void serialization() throws Exception {
    val timestamp = LocalDateTime.parse("2025-01-01T01:23:45");
    val object =
        Soht2Connection.builder()
            .targetHost("a")
            .targetPort(1)
            .clientHost("b")
            .user(
                Soht2User.builder()
                    .username("c")
                    .role("d")
                    .createdAt(timestamp)
                    .updatedAt(timestamp.plusDays(1))
                    .allowedTargets(Set.of("*:*"))
                    .build())
            .openedAt(timestamp.plusDays(2))
            .closedAt(timestamp.plusDays(3))
            .bytesRead(100L)
            .bytesWritten(200L)
            .build();
    assertThat(object.id()).isNotNull();
    assertThat(object.targetHost()).isEqualTo("a");
    assertThat(object.targetPort()).isEqualTo(1);
    assertThat(object.clientHost()).isEqualTo("b");
    assertThat(object.user().username()).isEqualTo("c");
    assertThat(object.user().role()).isEqualTo("d");
    assertThat(object.user().createdAt()).isEqualTo(timestamp);
    assertThat(object.user().updatedAt()).isEqualTo(timestamp.plusDays(1));
    assertThat(object.user().allowedTargets()).containsExactly("*:*");
    assertThat(object.openedAt()).isEqualTo(timestamp.plusDays(2));
    assertThat(object.closedAt()).isEqualTo(timestamp.plusDays(3));
    assertThat(object.bytesRead()).isEqualTo(100L);
    assertThat(object.bytesWritten()).isEqualTo(200L);

    val objectMapper = getObjectMapper();
    val json = objectMapper.writeValueAsString(object);
    val deserialized = objectMapper.readValue(json, Soht2Connection.class);

    assertThat(deserialized).isEqualTo(object);
  }
}
