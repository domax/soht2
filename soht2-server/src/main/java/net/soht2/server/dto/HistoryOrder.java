/* SOHT2 © Licensed under MIT 2025. */
package net.soht2.server.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;

public final class HistoryOrder extends SortingOrder<HistorySorting> {

  @Builder
  @JsonCreator
  HistoryOrder(
      @JsonProperty("field") HistorySorting field,
      @JsonProperty("direction") SortingDir direction) {
    super(field, direction);
  }
}
