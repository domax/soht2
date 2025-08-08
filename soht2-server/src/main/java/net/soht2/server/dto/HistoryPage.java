/* SOHT2 © Licensed under MIT 2025. */
package net.soht2.server.dto;

import static java.util.Optional.ofNullable;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import lombok.Builder;
import net.soht2.common.dto.Soht2Connection;

public final class HistoryPage extends Page<Soht2Connection, HistorySorting> {

  @Builder
  private HistoryPage(HistoryPaging paging, Long totalItems, List<Soht2Connection> data) {
    super(paging, totalItems, data);
  }

  @JsonProperty
  @Override
  public HistoryPaging paging() {
    return ofNullable(super.paging())
        .map(
            p ->
                p instanceof HistoryPaging historyPaging
                    ? historyPaging
                    : HistoryPaging.builder()
                        .pageNumber(p.pageNumber())
                        .pageSize(p.pageSize())
                        .sorting(HistoryPaging.sortingOrder(p.sorting()))
                        .build())
        .orElse(null);
  }
}
