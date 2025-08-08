/* SOHT2 © Licensed under MIT 2025. */
package net.soht2.server.dto;

import static io.vavr.API.unchecked;
import static io.vavr.Predicates.not;
import static java.util.Optional.ofNullable;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import lombok.Data;
import lombok.SneakyThrows;
import lombok.experimental.Accessors;
import lombok.val;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

@Data
@Accessors(fluent = true)
public sealed class Paging<S extends SortingField> permits HistoryPaging {

  @NotNull @JsonProperty(required = true)
  private final int pageNumber;

  @NotNull @JsonProperty(required = true)
  private final int pageSize;

  @JsonProperty private final List<SortingOrder<S>> sorting;

  @JsonCreator
  protected Paging(
      @NotNull @JsonProperty(value = "pageNumber", required = true) int pageNumber,
      @NotNull @JsonProperty(value = "pageSize", required = true) int pageSize,
      @JsonProperty("sorting") List<SortingOrder<S>> sorting) {
    this.pageNumber = pageNumber;
    this.pageSize = pageSize;
    this.sorting = sorting;
  }

  public Pageable toPageable(boolean isNative) {
    return PageRequest.of(
        pageNumber,
        pageSize,
        ofNullable(sorting)
            .filter(not(List::isEmpty))
            .map(orders -> orders.stream().map(o -> o.toSortOrder(isNative)).toList())
            .map(Sort::by)
            .orElseGet(Sort::unsorted));
  }

  @SuppressWarnings("SameParameterValue")
  @SneakyThrows
  static <T extends Enum<T> & SortingField, O extends SortingOrder<T>, P extends Paging<T>>
      P fromRequest(
          int pageNumber,
          int pageSize,
          List<String> sortBy,
          Class<T> sortingClass,
          Class<O> orderClass,
          Class<P> pagingClass) {
    val sorting =
        ofNullable(sortBy).stream()
            .flatMap(Collection::stream)
            .filter(not(String::isBlank))
            .map(v -> v.split(":", 2))
            .map(
                unchecked(
                    v ->
                        orderClass
                            .getDeclaredConstructor(sortingClass, SortingDir.class)
                            .newInstance(
                                Arrays.stream(sortingClass.getEnumConstants())
                                    .filter(f -> f.name().equals(v[0]))
                                    .findFirst()
                                    .orElseThrow(
                                        () ->
                                            new IllegalArgumentException("Unknown field " + v[0])),
                                v.length > 1 ? SortingDir.valueOf(v[1].toUpperCase()) : null)))
            .toList();
    return pagingClass
        .getDeclaredConstructor(int.class, int.class, List.class)
        .newInstance(pageNumber, pageSize, sorting.isEmpty() ? null : sorting);
  }
}
