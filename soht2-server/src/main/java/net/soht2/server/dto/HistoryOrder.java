/* SOHT2 © Licensed under MIT 2025. */
package net.soht2.server.dto;

import lombok.Builder;

public final class HistoryOrder extends SortingOrder<HistorySorting> {

  @Builder
  HistoryOrder(HistorySorting field, SortingDir direction) {
    super(field, direction);
  }
}
