"use server";

import { z } from "zod";
import { logUnexpectedResponse } from "@/lib/auth/gateway-response";
import { readOAuthEnabled } from "@/lib/auth/oauth-host";
import { isProvider } from "@/lib/auth/oauth-providers";
import {
  type CommonFailure,
  isReauthRequired,
  REAUTH_REQUIRED,
  type ReauthRequired,
  settleFailure,
  unexpectedProblem,
} from "@/lib/identity/mutation-failure";
import { rememberOAuthFlow } from "@/lib/identity/oauth-login";
import { callIdentity } from "@/lib/identity/request";

const UNAVAILABLE = { ok: false, reason: "unavailable" } as const;
const SECURITY_PAGE = "/settings/security";

/// 发起绑定的结果。不带文案，卡片按 `reason` 选文案。
export type LinkResult =
  | { ok: true; authorizationUrl: string }
  | { ok: false; reason: "email-not-verified" | "already-linked" }
  | ReauthRequired
  | CommonFailure;

const linkSchema = z.object({ authorizationUrl: z.string().min(1) });

/// 发起绑定（`POST /me/login-methods/{provider}/link`，无请求体，要求再认证）：从授权地址取 `state` 写 Cookie，把地址
/// 还给浏览器去跳转。回调地址只注册在公网域名上，其他地址发起没有意义，不请求。`provider` 只收三个值，防止拼出别的路径。
export async function startProviderLink(provider: string): Promise<LinkResult> {
  if (!isProvider(provider) || !(await readOAuthEnabled())) {
    return UNAVAILABLE;
  }
  const path = `/me/login-methods/${provider}/link`;
  const endpoint = `/patra-identity${path}`;
  const reply = await callIdentity(path, { method: "POST" });
  if (reply.kind === "ok") {
    const parsed = reply.res.status === 200 ? linkSchema.safeParse(reply.body) : undefined;
    if (
      parsed?.success &&
      (await rememberOAuthFlow(parsed.data.authorizationUrl, {
        from: "settings",
        next: SECURITY_PAGE,
      }))
    ) {
      return { ok: true, authorizationUrl: parsed.data.authorizationUrl };
    }
    logUnexpectedResponse(endpoint, reply.res.status, undefined);
    return UNAVAILABLE;
  }
  const settled = await settleFailure(reply);
  if ("failure" in settled) {
    return settled.failure;
  }
  const { problem } = settled;
  if (isReauthRequired(problem)) {
    return REAUTH_REQUIRED;
  }
  if (problem.traits.has("EMAIL_NOT_VERIFIED")) {
    return { ok: false, reason: "email-not-verified" };
  }
  if (problem.traits.has("PROVIDER_ALREADY_LINKED")) {
    return { ok: false, reason: "already-linked" };
  }
  return unexpectedProblem(endpoint, problem);
}

/// 解绑的结果。不带文案。
export type UnlinkResult =
  | { ok: true }
  | { ok: false; reason: "last-method" }
  | ReauthRequired
  | CommonFailure;

/// 解绑（`DELETE /me/login-methods/{provider}`，要求再认证；本来就没绑也是 204）。tailnet 上照常：不经过第三方。
export async function unlinkProvider(provider: string): Promise<UnlinkResult> {
  if (!isProvider(provider)) {
    return UNAVAILABLE;
  }
  const path = `/me/login-methods/${provider}`;
  const endpoint = `/patra-identity${path}`;
  const reply = await callIdentity(path, { method: "DELETE" });
  if (reply.kind === "ok") {
    if (reply.res.status === 204) {
      return { ok: true };
    }
    logUnexpectedResponse(endpoint, reply.res.status, undefined);
    return UNAVAILABLE;
  }
  const settled = await settleFailure(reply);
  if ("failure" in settled) {
    return settled.failure;
  }
  const { problem } = settled;
  if (isReauthRequired(problem)) {
    return REAUTH_REQUIRED;
  }
  if (problem.traits.has("LAST_LOGIN_METHOD")) {
    return { ok: false, reason: "last-method" };
  }
  return unexpectedProblem(endpoint, problem);
}
