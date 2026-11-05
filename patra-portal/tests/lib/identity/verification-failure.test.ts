// @vitest-environment node
import { describe, expect, it } from "vitest";
import type { Problem } from "@/lib/identity/problem";
import { verificationFailureOf } from "@/lib/identity/verification-failure";

const problem = (extra: Partial<Problem>): Problem => ({
  status: 422,
  code: null,
  traits: new Set(),
  errors: [],
  retryAfterSeconds: null,
  existingPostId: null,
  ...extra,
});

describe("verificationFailureOf", () => {
  it("没带令牌", () => {
    const errors = [{ field: "verificationToken", code: "VERIFICATION_REQUIRED" }];

    expect(verificationFailureOf(problem({ errors }))).toEqual({
      ok: false,
      reason: "verification-required",
    });
  });

  it("令牌没通过", () => {
    const errors = [{ field: "verificationToken", code: "VERIFICATION_FAILED" }];

    expect(verificationFailureOf(problem({ errors }))).toEqual({
      ok: false,
      reason: "verification-failed",
    });
  });

  it("验证不了", () => {
    const failure = problem({ status: 503, traits: new Set(["VERIFICATION_UNAVAILABLE"]) });

    expect(verificationFailureOf(failure)).toEqual({
      ok: false,
      reason: "verification-unavailable",
    });
  });

  it("别的字段错误和别的 503 不算", () => {
    const errors = [{ field: "email", code: "INVALID_FORMAT" }];

    expect(verificationFailureOf(problem({ errors }))).toBeNull();
    expect(verificationFailureOf(problem({ status: 503 }))).toBeNull();
  });
});
