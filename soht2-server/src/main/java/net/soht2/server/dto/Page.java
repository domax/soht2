/* SOHT2 © Licensed under MIT 2025. */
package net.soht2.server.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import java.util.List;
import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(fluent = true)
@JsonPropertyOrder(alphabetic = true)
public sealed class Page<T, S extends SortingField> permits HistoryPage {

  @JsonProperty private final Paging<S> paging;
  @JsonProperty private final Long totalItems;
  @JsonProperty private final List<T> data;

  @JsonCreator
  protected Page(
      @JsonProperty("paging") Paging<S> paging,
      @JsonProperty("totalItems") Long totalItems,
      @JsonProperty("data") List<T> data) {
    this.paging = paging;
    this.totalItems = totalItems;
    this.data = data;
  }

  @JsonProperty(value = "totalPages", access = JsonProperty.Access.READ_ONLY)
  public Integer totalPages() {
    return totalItems != null && paging != null && paging.pageSize() > 0
        ? (int) Math.ceil((double) totalItems / paging.pageSize())
        : null;
  }
}
