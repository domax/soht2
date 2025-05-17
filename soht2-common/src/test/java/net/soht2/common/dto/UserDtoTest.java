/* SOHT2 © Licensed under MIT 2025. */
package net.soht2.common.dto;

import static org.assertj.core.api.Assertions.assertThat;

import lombok.val;
import org.junit.jupiter.api.Test;

class UserDtoTest {

  @Test
  void checkUserName_OK() {
    val user = UserDto.builder().username("a").password("b").build();
    assertThat(user.username()).isEqualTo("a");
    assertThat(user.password()).isEqualTo("b");
  }
}
