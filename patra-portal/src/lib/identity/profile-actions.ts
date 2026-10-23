"use server";

import { z } from "zod";
import { logUnexpectedResponse } from "@/lib/auth/gateway-response";
import { clearSessionCookie } from "@/lib/auth/session-cookie";
import type { FieldError } from "@/lib/auth/types";
import { OCCUPATIONS } from "@/lib/identity/occupation";
import { isSessionRejected } from "@/lib/identity/problem";
import { type ProfileFormValues, toUpdateRequest } from "@/lib/identity/profile-form";
import { type MyProfile, myProfileSchema } from "@/lib/identity/profile-schema";
import { callIdentity, type IdentityReply } from "@/lib/identity/request";

const PROFILE_PATH = "/me/profile";

/// 保存资料的结果。不带文案，表单按 `reason` 与字段原因码选文案。
export type SaveProfileResult =
  | { ok: true; profile: MyProfile }
  | { ok: false; reason: "invalid-fields"; fieldErrors: FieldError[] }
  | {
      ok: false;
      reason:
        | "catalog-unavailable"
        | "conflict"
        | "pending-deletion"
        | "signed-out"
        | "unavailable";
    };

/// 入参只校验形状（规则由后端判，失败时回 422），多余字段剥掉。身份不认识时也按形状不对处理
const formValuesSchema = z.object({
  displayName: z.string(),
  bio: z.string(),
  links: z.array(z.object({ title: z.string(), url: z.string() })),
  occupation: z.union([z.enum(OCCUPATIONS), z.literal("")]),
  jobTitle: z.string(),
  organization: z
    .union([
      z.object({
        kind: z.literal("ror"),
        rorId: z.string(),
        name: z.string(),
        detail: z.string().nullable(),
      }),
      z.object({ kind: z.literal("free"), name: z.string() }),
    ])
    .nullable(),
  department: z.string(),
  researchInterests: z.array(z.object({ ui: z.string(), name: z.string() })),
});

/// 只有 `CONFLICT`、不是冷静期的 409：后端没有乐观锁，这只是「沿用的名称快照被另一次并发保存删掉」，
/// 原样再交一次就会重新向 catalog 取名（spec §9.3）
const isTransientConflict = (reply: IdentityReply) =>
  reply.kind === "problem" &&
  reply.problem.status === 409 &&
  !reply.problem.traits.has("PENDING_DELETION");

/// 保存资料（`PUT /me/profile`，整份替换）。并发快照冲突自动重试一次，仍冲突才回 `conflict`。
export async function saveProfile(values: ProfileFormValues): Promise<SaveProfileResult> {
  const input = formValuesSchema.safeParse(values);
  if (!input.success) {
    return { ok: false, reason: "unavailable" };
  }
  const request = { method: "PUT", json: toUpdateRequest(input.data) } as const;
  let reply = await callIdentity(PROFILE_PATH, request);
  if (isTransientConflict(reply)) {
    reply = await callIdentity(PROFILE_PATH, request);
  }
  switch (reply.kind) {
    case "no-session":
      return { ok: false, reason: "signed-out" };
    case "error":
      return { ok: false, reason: "unavailable" };
    case "ok": {
      const parsed = myProfileSchema.safeParse(reply.body);
      if (reply.res.status === 200 && parsed.success) {
        return { ok: true, profile: parsed.data };
      }
      logUnexpectedResponse(`/patra-identity${PROFILE_PATH}`, reply.res.status, reply.body);
      return { ok: false, reason: "unavailable" };
    }
    case "problem": {
      const { problem } = reply;
      if (isSessionRejected(problem)) {
        await clearSessionCookie();
        return { ok: false, reason: "signed-out" };
      }
      if (problem.status === 422 && problem.errors.length > 0) {
        return { ok: false, reason: "invalid-fields", fieldErrors: problem.errors };
      }
      if (problem.status === 503) {
        return { ok: false, reason: "catalog-unavailable" };
      }
      if (problem.status === 409) {
        return problem.traits.has("PENDING_DELETION")
          ? { ok: false, reason: "pending-deletion" }
          : { ok: false, reason: "conflict" };
      }
      logUnexpectedResponse(`/patra-identity${PROFILE_PATH}`, problem.status, {
        code: problem.code,
      });
      return { ok: false, reason: "unavailable" };
    }
  }
}
