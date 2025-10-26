package com.patra.starter.objectstorage.domain;

import java.util.Collections;
import java.util.Map;
import lombok.Builder;
import lombok.Getter;

/** Basic metadata describing the object that will be uploaded. */
@Getter
@Builder
public class ObjectMetadata {

  private final long contentLength;
  @Builder.Default private final String contentType = "application/octet-stream";
  @Builder.Default private final Map<String, String> userMetadata = Collections.emptyMap();
}
