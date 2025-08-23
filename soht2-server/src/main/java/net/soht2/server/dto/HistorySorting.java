/* SOHT2 © Licensed under MIT 2025. */
package net.soht2.server.dto;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Accessors;

@SuppressWarnings("java:S115")
@Getter
@Accessors(fluent = true)
@RequiredArgsConstructor
public enum HistorySorting implements SortingField {
  userName("user_name"),
  connectionId("connection_id"),
  clientHost("client_host"),
  targetHost("target_host"),
  targetPort("target_port"),
  openedAt("opened_at"),
  closedAt("closed_at"),
  bytesRead("bytes_read"),
  bytesWritten("bytes_written");

  private final String field;
}
