import type { Metadata } from "next";
import { AuthShell } from "@/components/auth/AuthShell";
import { LoginForm } from "@/components/auth/LoginForm";
import { safeNextPath } from "@/lib/auth/next-path";

export const metadata: Metadata = { title: "登录 · Patra", robots: { index: false } };

/// 登录页。不读 Cookie，也不判断登录状态：已登录的人在 proxy 那一层就被送回 `next`。
/// `next` 出现多次时取第一个。
export default async function LoginPage({
  searchParams,
}: {
  searchParams: Promise<Record<string, string | string[] | undefined>>;
}) {
  const raw = (await searchParams).next;
  const next = safeNextPath(Array.isArray(raw) ? raw[0] : raw);
  return (
    <AuthShell title="登录" description="用邮箱和密码登录 Patra" back={next}>
      <LoginForm next={next} />
    </AuthShell>
  );
}
