import type { Metadata } from "next";
import { AuthShell } from "@/components/auth/AuthShell";
import { RegisterForm } from "@/components/auth/RegisterForm";
import { readHumanVerification } from "@/lib/auth/human-verification";
import { safeNextPath } from "@/lib/auth/next-path";
import { readOAuthEnabled } from "@/lib/auth/oauth-host";
import { parseOAuthNotice } from "@/lib/auth/oauth-notice";

export const metadata: Metadata = { title: "注册 · Patra", robots: { index: false } };

/// 注册页。不读 Cookie，也不判断登录状态：已登录的人在 proxy 那一层就被送回 `next`。
/// `next` 出现多次时取第一个。第三方入口只在回调地址注册过的域名显示（决策 G）。
export default async function RegisterPage({
  searchParams,
}: {
  searchParams: Promise<Record<string, string | string[] | undefined>>;
}) {
  const params = await searchParams;
  const raw = params.next;
  const next = safeNextPath(Array.isArray(raw) ? raw[0] : raw);
  const [enabled, verification] = await Promise.all([readOAuthEnabled(), readHumanVerification()]);
  return (
    <AuthShell
      title="注册"
      description={
        enabled ? "用第三方账号注册，或用邮箱建一个 Patra 账号" : "用邮箱建一个 Patra 账号"
      }
      back={next}
    >
      <RegisterForm
        next={next}
        oauth={{ enabled, notice: parseOAuthNotice(params) }}
        verification={verification}
      />
    </AuthShell>
  );
}
