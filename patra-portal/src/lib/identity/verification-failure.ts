import type { VerificationFailure } from "@/lib/auth/types";
import type { Problem } from "@/lib/identity/problem";

const FIELD = "verificationToken";

/// 从错误响应里认出人机验证的三种失败，不是的话返回 null。
export function verificationFailureOf(problem: Problem): VerificationFailure | null {
  const code = problem.errors.find((error) => error.field === FIELD)?.code;
  if (code === "VERIFICATION_REQUIRED") {
    return { ok: false, reason: "verification-required" };
  }
  if (code === "VERIFICATION_FAILED") {
    return { ok: false, reason: "verification-failed" };
  }
  if (problem.traits.has("VERIFICATION_UNAVAILABLE")) {
    return { ok: false, reason: "verification-unavailable" };
  }
  return null;
}
