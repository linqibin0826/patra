import type { Metadata } from "next";
import { ProviderRegistrationFlow } from "@/components/auth/ProviderRegistrationFlow";
import { safeNextPath } from "@/lib/auth/next-path";
import { fetchPendingRegistration } from "@/lib/identity/oauth-registration";

export const metadata: Metadata = { title: "完成注册 · Patra", robots: { index: false } };

/// 第三方首次登录的补填页（决策 D）：用户名、邮箱，再填邮件里的验证码，账号才建好。凭据在 Cookie 里，不进地址栏；
/// 取不到内容时抛错交给错误页，凭据失效时流程显示过期态。已登录的人在 proxy 那一层就被送回 `next`。
export default async function RegisterCompletePage({
  searchParams,
}: {
  searchParams: Promise<Record<string, string | string[] | undefined>>;
}) {
  const raw = (await searchParams).next;
  const next = safeNextPath(Array.isArray(raw) ? raw[0] : raw);
  return <ProviderRegistrationFlow pending={await fetchPendingRegistration()} next={next} />;
}
