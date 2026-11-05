import type { VerificationFailure } from "@/lib/auth/types";
import type { Problem } from "@/lib/identity/problem";

const FIELD = "verificationToken";

/// 带人机验证的提交等多久。后端校验令牌最坏要 10 秒（连接 2 秒 + 读 3 秒，重试一次），之后还要做哈希、写库。
/// 门户不能比它先放弃：否则用户看到“没有成功”，后端却可能已经把账号建好了，再试就撞上“已注册”。
export const VERIFIED_SUBMIT_TIMEOUT_MS = 15_000;

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
