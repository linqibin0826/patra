Purpose and Responsibilities
- Feign enhancements: unified error decoding, trace propagation, and runtime properties.

Key Components
- ProblemDetailErrorDecoder maps remote RFC7807 payloads to exceptions.
- TraceIdRequestInterceptor and PatraFeignRequestInterceptor propagate headers.
- Auto-config: FeignErrorAutoConfiguration and PatraFeignAutoConfiguration.
- Observation: MicrometerFeignErrorObservationRecorder for metrics.

Configuration Properties
- `patra.feign.error.enabled` (decoder on/off)
- `patra.feign.error.include-remote-trace` (propagate remote error trace fields)
- `patra.feign.request-interceptor.enabled` (include cross-service headers)
