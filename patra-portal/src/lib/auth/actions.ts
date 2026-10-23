"use server";

import { z } from "zod";
import {
  isRecord,
  logRequestError,
  logUnexpectedResponse,
  readJson,
} from "@/lib/auth/gateway-response";
import {
  clearSessionCookie,
  readSessionToken,
  writeSessionCookie,
} from "@/lib/auth/session-cookie";
import type {
  Credentials,
  CurrentUser,
  FieldError,
  LoginResult,
  RegisterResult,
} from "@/lib/auth/types";
import { canSendToken, gatewayFetch } from "@/lib/gateway";
import { userSchema } from "@/lib/identity/user-schema";

const REGISTER_ENDPOINT = "/patra-identity/auth/register";
const LOGIN_ENDPOINT = "/patra-identity/auth/login";
const LOGOUT_ENDPOINT = "/patra-identity/auth/logout";

/// 登出的超时：删后端会话只是尽力而为，不必让用户等满默认的 10 秒
const LOGOUT_TIMEOUT_MS = 5_000;

/// 入参只校验形状，多余的字段被剥掉；长度和格式由后端校验，失败时回 422 并带原因码
const credentialsSchema = z.object({ email: z.string(), password: z.string() });

/// 注册、登录成功时的响应体：`user` 与 `/auth/me` 同构
const signedInSchema = z.object({ sessionToken: z.string().min(1), user: userSchema });

/// 422 响应体 `errors[]` 里的一项：`field` 必须是字符串，`code` 不是字符串时记为 null，其余字段丢掉
const fieldErrorSchema = z.object({ field: z.string(), code: z.string().nullable().catch(null) });

/// 429 响应体里的等待秒数
const retryAfterSchema = z.object({ retryAfterSeconds: z.number().int().nonnegative() });

const UNAVAILABLE = { ok: false, reason: "unavailable" } as const;

/// 网关的响应和读出的响应体
interface GatewayReply {
  res: Response;
  body: unknown;
}

/// 把 `email`、`password` 发给网关，不带令牌。入参形状不对时返回 null，不请求网关；
/// 请求抛错时记日志，返回 null。
async function submitCredentials(
  endpoint: string,
  input: Credentials,
): Promise<GatewayReply | null> {
  const credentials = credentialsSchema.safeParse(input);
  if (!credentials.success) {
    return null;
  }
  try {
    const res = await gatewayFetch(endpoint, { method: "POST", json: credentials.data });
    return { res, body: await readJson(res) };
  } catch (error) {
    logRequestError(endpoint, error);
    return null;
  }
}

/// 成功响应：响应体对就写入 Cookie、返回用户；不对就记日志、返回 null，不写 Cookie。
async function signIn(endpoint: string, reply: GatewayReply): Promise<CurrentUser | null> {
  const parsed = signedInSchema.safeParse(reply.body);
  if (!parsed.success) {
    logUnexpectedResponse(endpoint, reply.res.status, reply.body);
    return null;
  }
  await writeSessionCookie(parsed.data.sessionToken);
  return parsed.data.user;
}

/// 422：取到字段错误时返回 `invalid-fields`；一项都取不到时记日志，返回 `unavailable`。
function invalidFields(endpoint: string, reply: GatewayReply) {
  const errors: unknown[] =
    isRecord(reply.body) && Array.isArray(reply.body.errors) ? reply.body.errors : [];
  const fieldErrors: FieldError[] = errors.flatMap((item) => {
    const parsed = fieldErrorSchema.safeParse(item);
    return parsed.success ? [parsed.data] : [];
  });
  if (fieldErrors.length === 0) {
    logUnexpectedResponse(endpoint, reply.res.status, reply.body);
    return UNAVAILABLE;
  }
  return { ok: false, reason: "invalid-fields", fieldErrors } as const;
}

/// 429 的等待秒数：先取响应体的 `retryAfterSeconds`，再取 `Retry-After` 头，都要是非负整数；
/// 都没有时为 null。
function retryAfterSeconds(reply: GatewayReply): number | null {
  const fromBody = retryAfterSchema.safeParse(reply.body);
  if (fromBody.success) {
    return fromBody.data.retryAfterSeconds;
  }
  const fromHeader = reply.res.headers.get("Retry-After");
  return fromHeader !== null && /^\d+$/.test(fromHeader) ? Number(fromHeader) : null;
}

/// 注册：成功（201）时写入 Cookie、返回用户。422 返回字段错误，409 是邮箱已被注册；
/// 其余状态码、超时和网络错误都是 `unavailable`。
export async function register(input: Credentials): Promise<RegisterResult> {
  const reply = await submitCredentials(REGISTER_ENDPOINT, input);
  if (!reply) {
    return UNAVAILABLE;
  }
  const { status } = reply.res;
  if (status === 201) {
    const user = await signIn(REGISTER_ENDPOINT, reply);
    return user ? { ok: true, user } : UNAVAILABLE;
  }
  if (status === 422) {
    return invalidFields(REGISTER_ENDPOINT, reply);
  }
  if (status === 409) {
    return { ok: false, reason: "email-taken" };
  }
  logUnexpectedResponse(REGISTER_ENDPOINT, status, reply.body);
  return UNAVAILABLE;
}

/// 登录：成功（200）时写入 Cookie、返回用户。422 返回字段错误，401 是邮箱或密码不对，
/// 403 是账号被封禁，429 是尝试次数过多；其余状态码、超时和网络错误都是 `unavailable`。
export async function login(input: Credentials): Promise<LoginResult> {
  const reply = await submitCredentials(LOGIN_ENDPOINT, input);
  if (!reply) {
    return UNAVAILABLE;
  }
  const { status } = reply.res;
  if (status === 200) {
    const user = await signIn(LOGIN_ENDPOINT, reply);
    return user ? { ok: true, user } : UNAVAILABLE;
  }
  if (status === 422) {
    return invalidFields(LOGIN_ENDPOINT, reply);
  }
  if (status === 401) {
    return { ok: false, reason: "bad-credentials" };
  }
  if (status === 403) {
    return { ok: false, reason: "banned" };
  }
  if (status === 429) {
    return { ok: false, reason: "throttled", retryAfterSeconds: retryAfterSeconds(reply) };
  }
  logUnexpectedResponse(LOGIN_ENDPOINT, status, reply.body);
  return UNAVAILABLE;
}

/// 登出：有令牌、且令牌放得进请求头时，带着它请求网关删除会话，非 204 的响应和抛出的错误只记日志、
/// 不往外抛；不论结果如何，最后都清除 Cookie，所以登出在用户看来总是成功的。
export async function logout(): Promise<void> {
  try {
    const token = await readSessionToken();
    if (token && canSendToken(token)) {
      const res = await gatewayFetch(LOGOUT_ENDPOINT, {
        method: "POST",
        token,
        timeoutMs: LOGOUT_TIMEOUT_MS,
      });
      if (res.status !== 204) {
        logUnexpectedResponse(LOGOUT_ENDPOINT, res.status, await readJson(res));
      }
    }
  } catch (error) {
    logRequestError(LOGOUT_ENDPOINT, error);
  } finally {
    await clearSessionCookie();
  }
}
