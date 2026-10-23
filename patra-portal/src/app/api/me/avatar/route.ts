import { NextResponse } from "next/server";
import { logUnexpectedResponse } from "@/lib/auth/gateway-response";
import { clearSessionCookie, readSessionToken } from "@/lib/auth/session-cookie";
import type { AvatarMutationResult } from "@/lib/identity/avatar-upload";
import { isSessionRejected } from "@/lib/identity/problem";
import { callIdentity, type IdentityReply } from "@/lib/identity/request";
import { isSameOrigin } from "@/lib/same-origin";

export const dynamic = "force-dynamic";

/// 请求体上限：原图上限 5 MB 加上 multipart 的边界与字段头，超过就不读体（identity 那边是 6 MB）
const MAX_BODY_BYTES = 6 * 1024 * 1024;

const NO_STORE = { "Cache-Control": "private, no-store" };

/// JSON 结果 + 状态码，不缓存
function reply(status: number, body: AvatarMutationResult): NextResponse {
  return NextResponse.json(body, { status, headers: NO_STORE });
}

/// 网关的失败 → 门户的结果（spec §11）。会话失效时顺手清会话 Cookie。
async function failure(identity: IdentityReply, endpoint: string): Promise<NextResponse> {
  if (identity.kind === "no-session") {
    return reply(401, { ok: false, reason: "signed-out" });
  }
  if (identity.kind !== "problem") {
    return reply(503, { ok: false, reason: "unavailable" });
  }
  const { problem } = identity;
  if (isSessionRejected(problem)) {
    await clearSessionCookie();
    return reply(401, { ok: false, reason: "signed-out" });
  }
  if (problem.status === 413) {
    return reply(413, { ok: false, reason: "too-large" });
  }
  if (problem.status === 422) {
    return reply(422, {
      ok: false,
      reason: "rejected",
      code: problem.errors[0]?.code ?? "REQUIRED",
    });
  }
  if (problem.status === 409 && problem.traits.has("PENDING_DELETION")) {
    return reply(409, { ok: false, reason: "pending-deletion" });
  }
  logUnexpectedResponse(endpoint, problem.status, { code: problem.code });
  return reply(503, { ok: false, reason: "unavailable" });
}

/// 上传裁好的头像：同源校验 → 会话 → 大小 → 取 `file` → multipart 转给 identity `POST /me/avatar`。
export async function POST(request: Request): Promise<NextResponse> {
  if (!isSameOrigin(request)) {
    return new NextResponse(null, { status: 403 });
  }
  if (!(await readSessionToken())) {
    return reply(401, { ok: false, reason: "signed-out" });
  }
  if (Number(request.headers.get("content-length") ?? 0) > MAX_BODY_BYTES) {
    return reply(413, { ok: false, reason: "too-large" });
  }
  const file = await request
    .formData()
    .then((form) => form.get("file"))
    .catch(() => null);
  if (!(file instanceof File) || file.size === 0) {
    return reply(422, { ok: false, reason: "rejected", code: "REQUIRED" });
  }
  const body = new FormData();
  body.append("file", file, file.name);
  const identity = await callIdentity("/me/avatar", { method: "POST", body, timeoutMs: 30_000 });
  if (identity.kind === "ok") {
    const avatarUrl =
      typeof identity.body === "object" && identity.body !== null && "avatarUrl" in identity.body
        ? identity.body.avatarUrl
        : null;
    return typeof avatarUrl === "string"
      ? reply(200, { ok: true, avatarUrl })
      : reply(503, { ok: false, reason: "unavailable" });
  }
  return failure(identity, "/patra-identity/me/avatar");
}

/// 移除头像，回到默认头像：identity `DELETE /me/avatar` 回 204（没有头像也是 204）。
export async function DELETE(request: Request): Promise<NextResponse> {
  if (!isSameOrigin(request)) {
    return new NextResponse(null, { status: 403 });
  }
  const identity = await callIdentity("/me/avatar", { method: "DELETE" });
  if (identity.kind === "ok") {
    return reply(200, { ok: true, avatarUrl: null });
  }
  return failure(identity, "/patra-identity/me/avatar");
}
