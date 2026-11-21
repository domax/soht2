/* SOHT2 © Licensed under MIT 2026. */
package net.soht2.common.dto;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * Protocol constants shared by the SOHT2 client and server for the duplex transport: header names,
 * header values, and the path suffixes of the upload and download lanes.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class Soht2Protocol {

  /** Defines how tunneled bytes travel between the client and the server. */
  public enum TransportType {
    /**
     * Independent upload and download requests per session; the server answers a held download as
     * soon as target data is available. Falls back to {@link #SIMPLE} when the server does not
     * advertise duplex support.
     */
    DUPLEX,
    /** A single request per round trip that carries both directions, with client-side polling. */
    SIMPLE
  }

  /** Response header of the open request that advertises the transports supported by a server. */
  public static final String HEADER_TRANSPORT = "X-SOHT2-Transport";

  /** The value of {@link #HEADER_TRANSPORT} for a duplex transport. */
  public static final String TRANSPORT_DUPLEX = "duplex";

  /** The value of {@link #HEADER_TRANSPORT} for a simple transport. */
  public static final String TRANSPORT_SIMPLE = "simple";

  /** Request header carrying the per-lane sequence number of an upload or download request. */
  public static final String HEADER_SEQ = "X-SOHT2-Seq";

  /** Response header that marks a {@code 410 Gone} download response as target end-of-stream. */
  public static final String HEADER_EOS = "X-SOHT2-EOS";

  /** The value of {@link #HEADER_EOS} for an end-of-stream response. */
  public static final String EOS_VALUE = "true";

  /** The path suffix of the upload lane, relative to {@code /api/connection/{id}}. */
  public static final String PATH_UPLOAD = "upload";

  /** The path suffix of the download lane, relative to {@code /api/connection/{id}}. */
  public static final String PATH_DOWNLOAD = "download";
}
