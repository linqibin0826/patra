import type {
  CompleteResult,
  LinkResult,
  SendCodeResult,
  SendLinkCodeResult,
} from "@/lib/auth/registration-types";

const UNAVAILABLE = { ok: false, reason: "unavailable" } as const;

/// JSON 请求
function jsonPost(body: unknown): RequestInit {
  return {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(body),
  };
}

/// 失败的状态码也读响应体；网络断开、响应体不是结果都是 `unavailable`
async function readResult<R>(request: Promise<Response>): Promise<R | typeof UNAVAILABLE> {
  try {
    const parsed: unknown = await (await request).json();
    return typeof parsed === "object" && parsed !== null && "ok" in parsed
      ? (parsed as R)
      : UNAVAILABLE;
  } catch {
    return UNAVAILABLE;
  }
}

/// 浏览器端发验证码，重发也调它
export function postRegistrationCode(input: {
  username: string;
  email: string;
}): Promise<SendCodeResult> {
  return readResult<SendCodeResult>(fetch("/api/auth/oauth/registration/code", jsonPost(input)));
}

/// 浏览器端提交验证码
export function postRegistrationComplete(input: { code: string }): Promise<CompleteResult> {
  return readResult<CompleteResult>(
    fetch("/api/auth/oauth/registration/complete", jsonPost(input)),
  );
}

/// 浏览器端发绑定用的验证码，重发也调它
export function postLinkCode(input: { email: string }): Promise<SendLinkCodeResult> {
  return readResult<SendLinkCodeResult>(
    fetch("/api/auth/oauth/registration/link-code", jsonPost(input)),
  );
}

/// 浏览器端提交绑定用的验证码
export function postLink(input: { code: string }): Promise<LinkResult> {
  return readResult<LinkResult>(fetch("/api/auth/oauth/registration/link", jsonPost(input)));
}
