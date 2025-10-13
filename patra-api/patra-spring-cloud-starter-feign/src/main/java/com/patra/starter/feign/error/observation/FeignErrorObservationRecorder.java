package com.patra.starter.feign.error.observation;

/** Abstraction for recording observations while decoding Feign error responses. */
public interface FeignErrorObservationRecorder {

  /** No-op implementation used when observation is disabled. */
  FeignErrorObservationRecorder NO_OP =
      new FeignErrorObservationRecorder() {
        @Override
        public void recordProblemDetailParsing(
            String methodKey, int status, long durationMs, boolean success) {
          // no-op
        }

        @Override
        public void recordDecodingOutcome(
            String methodKey, int status, boolean success, boolean tolerantMode) {
          // no-op
        }

        @Override
        public void recordResponseBodyRead(
            String methodKey, int bodySize, long durationMs, boolean truncated) {
          // no-op
        }

        @Override
        public void recordTraceIdExtraction(String methodKey, boolean found, String headerName) {
          // no-op
        }
      };

  /** Record the outcome and latency of parsing the downstream {@code ProblemDetail}. */
  void recordProblemDetailParsing(String methodKey, int status, long durationMs, boolean success);

  /** Record whether decoding succeeded and whether tolerant mode was used. */
  void recordDecodingOutcome(String methodKey, int status, boolean success, boolean tolerantMode);

  /** Record how long the response body took to read and whether it was truncated. */
  void recordResponseBodyRead(String methodKey, int bodySize, long durationMs, boolean truncated);

  /** Record whether the trace identifier was present in the downstream response headers. */
  void recordTraceIdExtraction(String methodKey, boolean found, String headerName);
}
