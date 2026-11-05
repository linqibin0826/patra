import "server-only";
import { z } from "zod";
import { logUnexpectedResponse } from "@/lib/auth/gateway-response";
import type { HumanVerificationConfig } from "@/lib/auth/types";
import { callIdentity } from "@/lib/identity/request";

const PATH = "/auth/human-verification";

const configSchema = z.object({
  provider: z.enum(["turnstile", "trustturn"]),
  siteKey: z.string().min(1),
});

/// 读当前人机验证厂商。厂商只配置在 identity，门户不另配一份，切换时不用重新部署。
/// 响应不对或网关不可用时返回 null，由页面显示不可用提示，不让页面出错。
export async function readHumanVerification(): Promise<HumanVerificationConfig | null> {
  const reply = await callIdentity(PATH, { method: "GET" }, { auth: "optional" });
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
