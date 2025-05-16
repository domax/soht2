/* SOHT2 © Licensed under MIT 2025. */
package net.soht2.common;

import org.springframework.util.Assert;

public record SOHT2User(String userName) {

  public void checkUserName() {
    Assert.hasLength(userName, "User name should be provided");
  }
}
