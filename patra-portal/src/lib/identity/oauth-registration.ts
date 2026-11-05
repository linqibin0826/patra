import "server-only";
import { z } from "zod";
import { logRequestError, logUnexpectedResponse, readJson } from "@/lib/auth/gateway-response";
import { PROVIDERS } from "@/lib/auth/oauth-providers";
import type {
  CompleteResult,
  LinkResult,
  PendingRegistration,
  SendCodeResult,
  SendLinkCodeResult,
} from "@/lib/auth/registration-types";
import {
  clearTicketCookie,
  ensureDeviceId,
  readTicket,
  writeSessionCookie,
} from "@/lib/auth/session-cookie";
import { readClientOrigin } from "@/lib/client-origin";
import { type GatewayRequestInit, gatewayFetch, isSendableHeaderValue } from "@/lib/gateway";
import { type Problem, parseProblem } from "@/lib/identity/problem";
import { userSchema } from "@/lib/identity/user-schema";

const ENDPOINT = "/patra-identity/auth/oauth/registration";
const CODE_ENDPOINT = `${ENDPOINT}/code`;
const COMPLETE_ENDPOINT = `${ENDPOINT}/complete`;
const LINK_CODE_ENDPOINT = `${ENDPOINT}/link-code`;
const LINK_ENDPOINT = `${ENDPOINT}/link`;
const TICKET_HEADER = "X-Patra-Registration-Ticket";

const EXPIRED = { ok: false, reason: "expired" } as const;
const UNAVAILABLE = { ok: false, reason: "unavailable" } as const;

/// 422 里给用户看的字段
const USER_FIELDS = new Set(["username", "email"]);

const pendingCodeFields = {
  email: z.string().min(1),
  codeExpiresAt: z.string().min(1),
  nextResendAt: z.string().min(1),
};

const pendingSchema = z.object({
  provider: z.enum(PROVIDERS),
  accountLabel: z.string().min(1),
  suggestedUsername: z.string().nullable(),
  suggestedEmail: z.string().nullable(),
  expiresAt: z.string().min(1),
  pendingCode: z
    .discriminatedUnion("purpose", [
      z.object({
        ...pendingCodeFields,
        purpose: z.literal("REGISTER"),
        username: z.string().min(1),
      }),
      z.object({ ...pendingCodeFields, purpose: z.literal("LINK"), username: z.null() }),
    ])
    .nullable(),
});

const sentSchema = z.object({
  email: z.string().min(1),
  codeExpiresAt: z.string().min(1),
  nextResendAt: z.string().min(1),
});

const signedInSchema = z.object({ sessionToken: z.string().min(1), user: userSchema });

/// 两种验证码步共同的结果
type LinkOrCompleteBase = Exclude<LinkResult, { reason: "already-linked" }>;

/// Cookie 里能放进请求头的 `ticket`；没有或被改坏时为 null（等同失效，不请求网关）
async function sendableTicket(): Promise<string | null> {
  const ticket = await readTicket();
  return ticket && isSendableHeaderValue(ticket) ? ticket : null;
}

/// 带 `ticket` 头请求 identity；请求抛错时记日志（只记常量接口名）返回 null
async function callRegistration(
  endpoint: string,
  ticket: string,
  init: Pick<GatewayRequestInit, "method" | "json"> = {},
): Promise<{ res: Response; body: unknown } | null> {
  try {
    const res = await gatewayFetch(endpoint, {
      ...init,
      client: await readClientOrigin(),
      headers: { [TICKET_HEADER]: ticket },
    });
    return { res, body: await readJson(res) };
  } catch (error) {
    logRequestError(endpoint, error);
    return null;
  }
}

/// `errors[]` 里 `ticket` 出错：记录过期或已用完，要回登录页重走
function isTicketRejected(problem: Problem): boolean {
  return problem.status === 422 && problem.errors.some((error) => error.field === "ticket");
}

/// 409 `errors[]`：邮箱、用户名被占用；一项都认不出时为 null
function takenFrom(problem: Problem) {
  const email = problem.errors.some(
    (error) => error.field === "email" && error.code === "ALREADY_REGISTERED",
  );
  const username = problem.errors.some(
    (error) => error.field === "username" && error.code === "ALREADY_TAKEN",
  );
  return email || username ? ({ ok: false, reason: "taken", email, username } as const) : null;
}

/// 补填页的内容（`GET /auth/oauth/registration`）。`ticket` 不合格时是 `expired`，其余失败抛错交给错误页。
/// 在 RSC 里调用，不写 Cookie。
export async function fetchPendingRegistration(): Promise<PendingRegistration | "expired"> {
  const ticket = await sendableTicket();
  if (!ticket) {
    return "expired";
  }
  const reply = await callRegistration(ENDPOINT, ticket);
  if (reply) {
    const { res, body } = reply;
    if (res.status === 200) {
      const parsed = pendingSchema.safeParse(body);
      if (parsed.success) {
        return parsed.data;
      }
    } else if (isTicketRejected(parseProblem(res, body))) {
      return "expired";
    }
    logUnexpectedResponse(ENDPOINT, res.status, body);
  }
  throw new Error("补填内容暂时不可用");
}

/// 发验证码的共同部分：202 带回时间；`ticket` 失效、字段错误、限流、撞车按结果分开
async function requestCode(endpoint: string, json: object): Promise<SendCodeResult> {
  const ticket = await sendableTicket();
  if (!ticket) {
    return EXPIRED;
  }
  const reply = await callRegistration(endpoint, ticket, { method: "POST", json });
  if (!reply) {
    return UNAVAILABLE;
  }
  const { res, body } = reply;
  if (res.status === 202) {
    const parsed = sentSchema.safeParse(body);
    if (parsed.success) {
      return { ok: true, ...parsed.data };
    }
  } else {
    const problem = parseProblem(res, body);
    if (isTicketRejected(problem)) {
      return EXPIRED;
    }
    if (res.status === 422) {
      const fieldErrors = problem.errors.filter((error) => USER_FIELDS.has(error.field));
      if (fieldErrors.length > 0) {
        return { ok: false, reason: "invalid-fields", fieldErrors };
      }
    }
    if (res.status === 429) {
      return { ok: false, reason: "throttled", retryAfterSeconds: problem.retryAfterSeconds };
    }
    const taken = res.status === 409 ? takenFrom(problem) : null;
    if (taken) {
      return taken;
    }
  }
  logUnexpectedResponse(endpoint, res.status, body);
  return UNAVAILABLE;
}

/// 提交用户名、邮箱并发验证码，重发也调它（`POST /auth/oauth/registration/code`）
export function sendRegistrationCode(input: {
  username: string;
  email: string;
}): Promise<SendCodeResult> {
  return requestCode(CODE_ENDPOINT, input);
}

/// 就地绑定：验证这个邮箱，重发也调它（`POST /auth/oauth/registration/link-code`）。凭证有效、格式合法时一律 202，
/// 邮箱未注册、未验证、账号封禁时只是不发信：成功不代表发了信，界面不能说「已发送」。
export async function sendLinkCode(email: string): Promise<SendLinkCodeResult> {
  const result = await requestCode(LINK_CODE_ENDPOINT, { email });
  if (!result.ok && result.reason === "taken") {
    logUnexpectedResponse(LINK_CODE_ENDPOINT, 409, undefined);
    return UNAVAILABLE;
  }
  return result;
}

/// 提交验证码的共同部分：成功时写会话 Cookie、清 `ticket`；`ticket` 失效（含重放）、验证码错误按结果分开，
/// 409 交给 `conflict` 按自己的接口判，认不出时返回 null
async function submitCode<F>(
  endpoint: string,
  code: string,
  okStatus: number,
  conflict: (problem: Problem) => F | null,
): Promise<LinkOrCompleteBase | F> {
  const ticket = await sendableTicket();
  if (!ticket) {
    return EXPIRED;
  }
  const reply = await callRegistration(endpoint, ticket, {
    method: "POST",
    json: { code, clientType: "web", deviceId: await ensureDeviceId() },
  });
  if (!reply) {
    return UNAVAILABLE;
  }
  const { res, body } = reply;
  if (res.status === okStatus) {
    const parsed = signedInSchema.safeParse(body);
    if (parsed.success) {
      await writeSessionCookie(parsed.data.sessionToken);
      await clearTicketCookie();
      return { ok: true, user: parsed.data.user };
    }
  } else if (res.status >= 400) {
    const problem = parseProblem(res, body);
    if (isTicketRejected(problem)) {
      return EXPIRED;
    }
    if (res.status === 422) {
      const codeError = problem.errors.find((error) => error.field === "code");
      if (codeError) {
        return {
          ok: false,
          reason: codeError.code === "EXPIRED" ? "code-expired" : "code-invalid",
        };
      }
    }
    if (res.status === 409) {
      if (problem.traits.has("PROVIDER_ACCOUNT_TAKEN")) {
        return { ok: false, reason: "provider-taken" };
      }
      const conflicted = conflict(problem);
      if (conflicted) {
        return conflicted;
      }
    }
  }
  logUnexpectedResponse(endpoint, res.status, body);
  return UNAVAILABLE;
}

/// 提交验证码，建账号（`POST /auth/oauth/registration/complete`，成功 201）
export function completeRegistration(code: string): Promise<CompleteResult> {
  return submitCode(COMPLETE_ENDPOINT, code, 201, takenFrom);
}

/// 提交验证码，绑定到已有账号并登录（`POST /auth/oauth/registration/link`，成功 200，和登录同构）
export function completeProviderLink(code: string): Promise<LinkResult> {
  return submitCode(LINK_ENDPOINT, code, 200, (problem) =>
    problem.traits.has("PROVIDER_ALREADY_LINKED")
      ? ({ ok: false, reason: "already-linked" } as const)
      : null,
  );
}
