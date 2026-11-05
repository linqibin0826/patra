import "server-only";
import { z } from "zod";
import { logRequestError, logUnexpectedResponse, readJson } from "@/lib/auth/gateway-response";
import { PROVIDERS } from "@/lib/auth/oauth-providers";
import type {
  CompleteResult,
  PendingRegistration,
  SendCodeResult,
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
const TICKET_HEADER = "X-Patra-Registration-Ticket";

const EXPIRED = { ok: false, reason: "expired" } as const;
const UNAVAILABLE = { ok: false, reason: "unavailable" } as const;

/// 422 里给用户看的字段
const USER_FIELDS = new Set(["username", "email"]);

const pendingSchema = z.object({
  provider: z.enum(PROVIDERS),
  accountLabel: z.string().min(1),
  suggestedUsername: z.string().nullable(),
  suggestedEmail: z.string().nullable(),
  expiresAt: z.string().min(1),
  pendingCode: z
    .object({
      email: z.string().min(1),
      username: z.string().min(1),
      codeExpiresAt: z.string().min(1),
      nextResendAt: z.string().min(1),
    })
    .nullable(),
});

const sentSchema = z.object({
  email: z.string().min(1),
  codeExpiresAt: z.string().min(1),
  nextResendAt: z.string().min(1),
});

const signedInSchema = z.object({ sessionToken: z.string().min(1), user: userSchema });

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

/// 提交用户名、邮箱并发验证码，重发也调它（`POST /auth/oauth/registration/code`）
export async function sendRegistrationCode(input: {
  username: string;
  email: string;
}): Promise<SendCodeResult> {
  const ticket = await sendableTicket();
  if (!ticket) {
    return EXPIRED;
  }
  const reply = await callRegistration(CODE_ENDPOINT, ticket, { method: "POST", json: input });
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
  logUnexpectedResponse(CODE_ENDPOINT, res.status, body);
  return UNAVAILABLE;
}

/// 提交验证码，建账号（`POST /auth/oauth/registration/complete`）：成功时写会话 Cookie、清 `ticket`
export async function completeRegistration(code: string): Promise<CompleteResult> {
  const ticket = await sendableTicket();
  if (!ticket) {
    return EXPIRED;
  }
  const reply = await callRegistration(COMPLETE_ENDPOINT, ticket, {
    method: "POST",
    json: { code, clientType: "web", deviceId: await ensureDeviceId() },
  });
  if (!reply) {
    return UNAVAILABLE;
  }
  const { res, body } = reply;
  if (res.status === 201) {
    const parsed = signedInSchema.safeParse(body);
    if (parsed.success) {
      await writeSessionCookie(parsed.data.sessionToken);
      await clearTicketCookie();
      return { ok: true, user: parsed.data.user };
    }
  } else {
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
      const taken = takenFrom(problem);
      if (taken) {
        return taken;
      }
    }
  }
  logUnexpectedResponse(COMPLETE_ENDPOINT, res.status, body);
  return UNAVAILABLE;
}
