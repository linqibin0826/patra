import "server-only";
import type { z } from "zod";
import { isSessionRejected } from "@/lib/identity/problem";
import {
  type MyProfile,
  myProfileSchema,
  type PrivacySettings,
  privacySettingsSchema,
} from "@/lib/identity/profile-schema";
import { callIdentity } from "@/lib/identity/request";

/// RSC 里读本人的一项设置：200 按 schema 解析；没有会话或会话失效是 `signed-out`（页面跳登录页，RSC 里
/// 不能清 Cookie）；其余（网关出错、形状不对、请求抛错）抛错交给错误页。
async function readMine<T>(path: string, schema: z.ZodType<T>): Promise<T | "signed-out"> {
  const reply = await callIdentity(path);
  if (
    reply.kind === "no-session" ||
    (reply.kind === "problem" && isSessionRejected(reply.problem))
  ) {
    return "signed-out";
  }
  if (reply.kind === "ok" && reply.res.status === 200) {
    const parsed = schema.safeParse(reply.body);
    if (parsed.success) {
      return parsed.data;
    }
  }
  throw new Error(`读取 ${path} 失败`);
}

/// 本人资料原值（`GET /me/profile`）
export function fetchMyProfile(): Promise<MyProfile | "signed-out"> {
  return readMine("/me/profile", myProfileSchema);
}

/// 本人隐私设置（`GET /me/privacy`）
export function fetchMyPrivacy(): Promise<PrivacySettings | "signed-out"> {
  return readMine("/me/privacy", privacySettingsSchema);
}
