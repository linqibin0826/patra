package com.patra.starter.web.req;

/**
 * Pagination contract implemented by request DTOs that accept page settings.
 *
 * @author linqibin
 * @since 0.1.0
 */
public interface Pageable {

  /** Page index (1-based). */
  Integer getPageNo();

  /** Page size requested by the client. */
  Integer getPageSize();
}
