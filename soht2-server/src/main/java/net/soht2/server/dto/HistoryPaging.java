/* SOHT2 © Licensed under MIT 2025. */
package net.soht2.server.dto;

import static java.util.Optional.ofNullable;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import lombok.Builder;

public final class HistoryPaging extends Paging<HistorySorting> {

  @Builder
  HistoryPaging(int pageNumber, int pageSize, List<HistoryOrder> sorting) {
    super(
        pageNumber,
        pageSize,
        ofNullable(sorting)
            .map(o -> o.stream().map(v -> (SortingOrder<HistorySorting>) v).toList())
            .orElse(null));
  }

  @JsonProperty("sorting")
  public List<HistoryOrder> sortingOrder() {
    return sortingOrder(sorting());
  }

  static List<HistoryOrder> sortingOrder(List<SortingOrder<HistorySorting>> sorting) {
    return ofNullable(sorting)
        .map(
            orders ->
                orders.stream()
                    .map(
                        order ->
                            order instanceof HistoryOrder historyOrder
                                ? historyOrder
                                : HistoryOrder.builder()
                                    .field(order.field())
                                    .direction(order.direction())
                                    .build())
                    .toList())
        .orElse(null);
  }

  public static HistoryPaging fromRequest(int pageNumber, int pageSize, List<String> sortBy) {
    return Paging.fromRequest(
        pageNumber,
        pageSize,
        sortBy,
        HistorySorting.class,
        HistoryOrder.class,
        HistoryPaging.class);
  }
}
