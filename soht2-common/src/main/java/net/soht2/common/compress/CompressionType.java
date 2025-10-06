/* SOHT2 © Licensed under MIT 2025. */
package net.soht2.common.compress;

/**
 * Defines the type of compression applied to requests.
 *
 * <p>This enum specifies the different types of compression that can be applied to requests sent to
 * the SOHT2 server.
 */
public enum CompressionType {
  /** No compression applied to requests. */
  NONE,
  /** GZIP compression applied to requests. */
  GZIP,
  /** DEFLATE compression applied to requests. */
  DEFLATE
}
