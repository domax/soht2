/* SOHT2 © Licensed under MIT 2025. */
package net.soht2.common;

import static org.assertj.core.api.Assertions.assertThatCode;

import lombok.val;
import org.junit.jupiter.api.Test;

class SOHT2UserTest {

  @Test
  void checkUserName_OK() {
    val user = new SOHT2User("user");
    assertThatCode(user::checkUserName).doesNotThrowAnyException();
  }

  @Test
  void checkUserName_isEmpty() {
    val user = new SOHT2User("");
    assertThatCode(user::checkUserName).hasMessage("User name should be provided");
  }
}
