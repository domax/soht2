/* SOHT2 © Licensed under MIT 2025. */
package net.soht2.server.dto;

import static org.springframework.data.domain.Sort.Direction.ASC;
import static org.springframework.data.domain.Sort.Direction.DESC;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.experimental.Accessors;
import org.springframework.data.domain.Sort;

@Data
@Accessors(fluent = true)
public sealed class SortingOrder<T extends SortingField> permits HistoryOrder {

  @NotNull @JsonProperty(required = true)
  private final T field;

  @JsonProperty private final SortingDir direction;

  @JsonCreator
  protected SortingOrder(
      @NotNull @JsonProperty(value = "field", required = true) T field,
      @JsonProperty("direction") SortingDir direction) {
    this.field = field;
    this.direction = direction;
  }

  public Sort.Order toSortOrder(boolean isNative) {
    return new Sort.Order(
        direction == SortingDir.DESC ? DESC : ASC, isNative ? field.field() : field.name());
  }
}
