/* SOHT2 © Licensed under MIT 2025. */
package net.soht2.common.dto;

import lombok.Builder;

@Builder
public record Soht2User(String username, String password) {}
