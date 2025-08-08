/* SOHT2 © Licensed under MIT 2025. */
package net.soht2.server.dto;

public sealed interface SortingField permits HistorySorting {

  String name();

  String field();
}
