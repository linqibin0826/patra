"use server";

import { logUnexpectedResponse } from "@/lib/auth/gateway-response";
import type { CurrentUser } from "@/lib/auth/types";
import {
  type CommonFailure,
  settleFailure,
  unexpectedProblem,
} from "@/lib/identity/mutation-failure";
import { callIdentity } from "@/lib/identity/request";
import { userSchema } from "@/lib/identity/user-schema";

const DELETION_PATH = "/me/deletion";
const UNAVAILABLE = { ok: false, reason: "unavailable" } as const;

/// 取消注销（`DELETE /me/deletion`，不要求再认证）。200 带与 `/auth/me` 同构的用户（重复取消也 200）；
/// 会话失效清 Cookie 回 `signed-out`；其余都是 `unavailable`。
export async function cancelDeletion(): Promise<{ ok: true; user: CurrentUser } | CommonFailure> {
  const reply = await callIdentity(DELETION_PATH, { method: "DELETE" });
  if (reply.kind === "ok") {
    const parsed = reply.res.status === 200 ? userSchema.safeParse(reply.body) : undefined;
    if (!parsed?.success) {
      logUnexpectedResponse(`/patra-identity${DELETION_PATH}`, reply.res.status, reply.body);
      return UNAVAILABLE;
    }
    return { ok: true, user: parsed.data };
  }
  const settled = await settleFailure(reply);
  return "failure" in settled
    ? settled.failure
    : unexpectedProblem(`/patra-identity${DELETION_PATH}`, settled.problem);
}
