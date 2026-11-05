import type { Metadata } from "next";
import { AuthShell } from "@/components/auth/AuthShell";
import { LoginForm } from "@/components/auth/LoginForm";
import { safeNextPath } from "@/lib/auth/next-path";
import { readOAuthEnabled } from "@/lib/auth/oauth-host";
import { parseOAuthNotice } from "@/lib/auth/oauth-notice";

export const metadata: Metadata = { title: "登录 · Patra", robots: { index: false } };

/// 登录页。不读 Cookie，也不判断登录状态：已登录的人在 proxy 那一层就被送回 `next`。
/// `next` 出现多次时取第一个。第三方入口只在回调地址注册过的域名显示（决策 G）。
export default async function LoginPage({
  searchParams,
}: {
  searchParams: Promise<Record<string, string | string[] | undefined>>;
}) {
  const params = await searchParams;
  const raw = params.next;
  const next = safeNextPath(Array.isArray(raw) ? raw[0] : raw);
  const enabled = await readOAuthEnabled();
  return (
    <AuthShell
      title="登录"
      description={enabled ? "用第三方账号，或邮箱和密码登录 Patra" : "用邮箱和密码登录 Patra"}
      back={next}
    >
      <LoginForm next={next} oauth={{ enabled, notice: parseOAuthNotice(params) }} />
    </AuthShell>
  );
}
