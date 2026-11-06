import "server-only";
import { z } from "zod";
import { logUnexpectedResponse } from "@/lib/auth/gateway-response";
import type { HumanVerificationConfig } from "@/lib/auth/types";
import { callIdentity } from "@/lib/identity/request";

const PATH = "/auth/human-verification";
/// 登录、注册、找回密码页的首屏都要等这个请求，identity 卡住时不能等满网关默认的 10 秒。
/// 超时按读不到配置处理：页面照常出来，要验证时提示不可用。
const READ_TIMEOUT_MS = 3_000;

const configSchema = z.object({
  provider: z.enum(["turnstile", "trustturn"]),
  siteKey: z.string().min(1),
});

/// 读当前人机验证厂商。厂商只配置在 identity，门户不另配一份，切换时不用重新部署。
/// 响应不对或网关不可用时返回 null，由页面显示不可用提示，不让页面出错。
export async function readHumanVerification(): Promise<HumanVerificationConfig | null> {
  const reply = await callIdentity(
    PATH,
    { method: "GET", timeoutMs: READ_TIMEOUT_MS },
    { auth: "optional" },
  );
  if (reply.kind !== "ok") {
    return null;
  }
  const parsed = configSchema.safeParse(reply.body);
  if (!parsed.success) {
    logUnexpectedResponse(`/patra-identity${PATH}`, reply.res.status, reply.body);
    return null;
  }
  return parsed.data;
}
