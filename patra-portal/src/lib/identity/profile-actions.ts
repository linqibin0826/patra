"use server";

import { z } from "zod";
import { logUnexpectedResponse } from "@/lib/auth/gateway-response";
import type { FieldError } from "@/lib/auth/types";
import { settleFailure, unexpectedProblem } from "@/lib/identity/mutation-failure";
import { OCCUPATIONS } from "@/lib/identity/occupation";
import { type ProfileFormValues, toUpdateRequest } from "@/lib/identity/profile-form";
import {
  type MyProfile,
  myProfileSchema,
  type PrivacySettings,
  privacySettingsSchema,
} from "@/lib/identity/profile-schema";
import { callIdentity, type IdentityReply } from "@/lib/identity/request";

const PROFILE_PATH = "/me/profile";
const PROFILE_ENDPOINT = `/patra-identity${PROFILE_PATH}`;

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
  if (reply.kind === "ok") {
    const parsed = myProfileSchema.safeParse(reply.body);
    if (reply.res.status === 200 && parsed.success) {
      return { ok: true, profile: parsed.data };
    }
    logUnexpectedResponse(PROFILE_ENDPOINT, reply.res.status, reply.body);
    return { ok: false, reason: "unavailable" };
  }
  const settled = await settleFailure(reply);
  if ("failure" in settled) {
    return settled.failure;
  }
  const { problem } = settled;
  if (problem.status === 422 && problem.errors.length > 0) {
    return { ok: false, reason: "invalid-fields", fieldErrors: problem.errors };
  }
  // identity 自己回的 503（`IDN-0503`）才是 catalog 不可用；网关的 503 是 identity 连不上
  if (problem.status === 503 && problem.code === "IDN-0503") {
    return { ok: false, reason: "catalog-unavailable" };
  }
  if (problem.status === 409) {
    return { ok: false, reason: "conflict" };
  }
  return unexpectedProblem(PROFILE_ENDPOINT, problem);
}

const PRIVACY_PATH = "/me/privacy";
const PRIVACY_ENDPOINT = `/patra-identity${PRIVACY_PATH}`;

/// 保存隐私设置的结果
export type SavePrivacyResult =
  | { ok: true; settings: PrivacySettings }
  | { ok: false; reason: "pending-deletion" | "signed-out" | "unavailable" };

/// 保存隐私设置（`PUT /me/privacy`，三项必填）。会话失效时清掉会话 Cookie；422 是门户自己发错了值，
/// 按 `unavailable` 处理并记日志。
export async function savePrivacy(settings: PrivacySettings): Promise<SavePrivacyResult> {
  const input = privacySettingsSchema.safeParse(settings);
  if (!input.success) {
    return { ok: false, reason: "unavailable" };
  }
  const reply = await callIdentity(PRIVACY_PATH, { method: "PUT", json: input.data });
  if (reply.kind === "ok") {
    const parsed = privacySettingsSchema.safeParse(reply.body);
    if (reply.res.status === 200 && parsed.success) {
      return { ok: true, settings: parsed.data };
    }
    logUnexpectedResponse(PRIVACY_ENDPOINT, reply.res.status, reply.body);
    return { ok: false, reason: "unavailable" };
  }
  const settled = await settleFailure(reply);
  return "failure" in settled
    ? settled.failure
    : unexpectedProblem(PRIVACY_ENDPOINT, settled.problem);
}
