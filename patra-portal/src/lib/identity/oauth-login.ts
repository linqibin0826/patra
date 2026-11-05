import "server-only";
import { z } from "zod";
import { logRequestError, logUnexpectedResponse, readJson } from "@/lib/auth/gateway-response";
import { authPageHref, deletionPendingHref } from "@/lib/auth/next-path";
import { readOAuthEnabled } from "@/lib/auth/oauth-host";
import { type OAuthNoticeKind, oauthNoticeHref } from "@/lib/auth/oauth-notice";
import type { Provider } from "@/lib/auth/oauth-providers";
import {
  clearOAuthFlowCookies,
  clearSessionCookie,
  ensureDeviceId,
  type OAuthReturn,
  readOAuthFlow,
  readSessionToken,
  writeOAuthFlowCookies,
  writeSessionCookie,
  writeSignedInFlag,
  writeTicketCookie,
} from "@/lib/auth/session-cookie";
import { readClientOrigin } from "@/lib/client-origin";
import { canSendToken, gatewayFetch } from "@/lib/gateway";
import { type Problem, parseProblem } from "@/lib/identity/problem";
import { userSchema } from "@/lib/identity/user-schema";

/// 回调的超时：identity 要依次请求平台的令牌、公钥、资料接口（每个连接 3 秒、读 5 秒）
const CALLBACK_TIMEOUT_MS = 20_000;
const DEVICE_ID_HEADER = "X-Patra-Device-Id";

/// 回调成功的三种结果（identity 设计第 2.2 节）
const callbackSchema = z.discriminatedUnion("result", [
  z.object({
    result: z.literal("AUTHENTICATED"),
    sessionToken: z.string().min(1),
    user: userSchema,
  }),
  z.object({ result: z.literal("REGISTRATION_REQUIRED"), ticket: z.string().min(1) }),
  z.object({ result: z.literal("LINKED") }),
]);

/// 发起页的地址，失败时回到这里
function originHref({ from, next }: OAuthReturn): string {
  return from === "settings" ? "/settings/security" : authPageHref(`/${from}`, next);
}

/// 从平台的授权地址取 `state`，写进 Cookie（决策 B）。取不到时返回 false，不写 Cookie。绑定也经这里
export async function rememberOAuthFlow(
  authorizationUrl: string,
  ret: OAuthReturn,
): Promise<boolean> {
  let state: string | null;
  try {
    state = new URL(authorizationUrl).searchParams.get("state");
  } catch {
    return false;
  }
  if (!state) {
    return false;
  }
  await writeOAuthFlowCookies(state, ret);
  return true;
}

/// 发起登录：问 identity 要平台的授权地址，返回要跳去的地址。当前域名走不通第三方时回发起页，不请求网关。
export async function startProviderLogin(provider: Provider, ret: OAuthReturn): Promise<string> {
  if (!(await readOAuthEnabled())) {
    return originHref(ret);
  }
  const endpoint = `/patra-identity/oauth2/authorization/${provider}`;
  try {
    const res = await gatewayFetch(endpoint, { client: await readClientOrigin() });
    const location = res.headers.get("location");
    if (res.status === 302 && location && (await rememberOAuthFlow(location, ret))) {
      return location;
    }
    logUnexpectedResponse(endpoint, res.status, undefined);
  } catch (error) {
    logRequestError(endpoint, error);
  }
  return oauthNoticeHref(originHref(ret), { kind: "unavailable", provider });
}

/// 失败的响应 → 发起页显示哪种结果（identity 设计第 2.2 节）。不认识的返回 null
function failureKind(problem: Problem): OAuthNoticeKind | null {
  const { status, traits } = problem;
  if (traits.has("OAUTH_CANCELLED")) return "cancelled";
  if (traits.has("OAUTH_REJECTED") || traits.has("LINK_SESSION_MISMATCH")) return "rejected";
  if (status === 503) return "unreachable";
  if (traits.has("PROVIDER_ACCOUNT_TAKEN")) return "taken";
  if (traits.has("PROVIDER_ALREADY_LINKED")) return "already-linked";
  if (status === 403) return "banned";
  return null;
}

/// 处理平台的回调，返回要跳去的地址。查询串里的 `state` 必须恰好一个、且等于 Cookie 的值，否则不转发：Redis 里的
/// 记录证明 state 是我们发的，Cookie 证明是这个浏览器发起的，两者都对才防得住登录 CSRF 和绑定 CSRF。日志只记常量
/// 接口名：`search` 里有授权码。
export async function finishProviderCallback(provider: Provider, search: string): Promise<string> {
  const { state, ret } = await readOAuthFlow();
  await clearOAuthFlowCookies();
  const back = originHref(ret);
  const notice = (kind: OAuthNoticeKind) => oauthNoticeHref(back, { kind, provider });
  const given = new URLSearchParams(search).getAll("state");
  if (!state || given.length !== 1 || given[0] !== state) {
    return notice("rejected");
  }
  const endpoint = `/patra-identity/login/oauth2/code/${provider}`;
  let res: Response;
  let body: unknown;
  try {
    const stored = await readSessionToken();
    res = await gatewayFetch(`${endpoint}${search}`, {
      ...(stored && canSendToken(stored) ? { token: stored } : {}),
      client: await readClientOrigin(),
      headers: { [DEVICE_ID_HEADER]: await ensureDeviceId() },
      timeoutMs: CALLBACK_TIMEOUT_MS,
    });
    body = await readJson(res);
  } catch (error) {
    logRequestError(endpoint, error);
    return notice("unavailable");
  }
  if (res.status !== 200) {
    if (res.status === 401 && ret.from === "settings") {
      await clearSessionCookie();
      return authPageHref("/login", ret.next);
    }
    const kind = failureKind(parseProblem(res, body));
    if (kind === null) {
      logUnexpectedResponse(endpoint, res.status, body);
    }
    return notice(kind ?? "unavailable");
  }
  const parsed = callbackSchema.safeParse(body);
  if (!parsed.success) {
    logUnexpectedResponse(endpoint, res.status, body);
    return notice("unavailable");
  }
  switch (parsed.data.result) {
    case "AUTHENTICATED": {
      await writeSessionCookie(parsed.data.sessionToken);
      if (parsed.data.user.status === "PENDING_DELETION") {
        return deletionPendingHref(ret.next);
      }
      await writeSignedInFlag();
      return ret.next;
    }
    case "REGISTRATION_REQUIRED": {
      await writeTicketCookie(parsed.data.ticket);
      const query = ret.next === "/" ? "" : `?${new URLSearchParams({ next: ret.next })}`;
      return `/register/complete${query}`;
    }
    case "LINKED":
      return oauthNoticeHref("/settings/security", { kind: "linked", provider });
  }
}
