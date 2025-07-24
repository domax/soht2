/* SOHT2 © Licensed under MIT 2025. */
package net.soht2.common.dto;

import java.time.LocalDateTime;
import lombok.Builder;

@Builder
public record Soht2User(
    String username, String role, LocalDateTime createdAt, LocalDateTime updatedAt) {}
