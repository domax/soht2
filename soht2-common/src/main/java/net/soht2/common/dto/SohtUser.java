/* SOHT2 © Licensed under MIT 2025. */
package net.soht2.common.dto;

import lombok.Builder;

@Builder
public record SohtUser(String username, String password) {}
