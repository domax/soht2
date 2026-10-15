/* SOHT2 © Licensed under MIT 2025. */
package net.soht2.common;

import com.fasterxml.jackson.annotation.JsonInclude.Include;
import io.vavr.control.Option;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.cfg.DateTimeFeature;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.util.StdDateFormat;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class UTHelper {

  public static ObjectMapper getObjectMapper() {
    return Option.of(
            JsonMapper.builder()
                .findAndAddModules()
                .defaultDateFormat(StdDateFormat.instance)
                .changeDefaultPropertyInclusion(value -> value.withValueInclusion(Include.NON_NULL))
                .disable(SerializationFeature.FAIL_ON_EMPTY_BEANS)
                .disable(DateTimeFeature.WRITE_DATES_AS_TIMESTAMPS)
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .build())
        .get();
  }
}
