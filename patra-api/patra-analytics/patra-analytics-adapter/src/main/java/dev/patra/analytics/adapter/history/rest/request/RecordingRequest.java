package dev.patra.analytics.adapter.history.rest.request;

import jakarta.validation.constraints.NotNull;

/// 改阅读记录开关。
///
/// @param enabled 是否记录；缺失时 422
public record RecordingRequest(@NotNull Boolean enabled) {}
