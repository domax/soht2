/* SOHT2 © Licensed under MIT 2025. */
package net.soht2.common;

import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.util.StdDateFormat;
import io.vavr.control.Option;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class UTHelper {

  public static ObjectMapper getObjectMapper() {
    return Option.of(new ObjectMapper())
        .peek(ObjectMapper::findAndRegisterModules)
        .peek(om -> om.setDateFormat(StdDateFormat.instance))
        .peek(om -> om.setDefaultPropertyInclusion(Include.NON_NULL))
        .peek(om -> om.configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false))
        .peek(om -> om.configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false))
        .peek(om -> om.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false))
        .get();
  }
}
