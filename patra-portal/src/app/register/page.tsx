import type { Metadata } from "next";
import { AuthShell } from "@/components/auth/AuthShell";
import { RegisterForm } from "@/components/auth/RegisterForm";
import { safeNextPath } from "@/lib/auth/next-path";

export const metadata: Metadata = { title: "注册 · Patra", robots: { index: false } };

/// 注册页。不读 Cookie，也不判断登录状态：已登录的人在 proxy 那一层就被送回 `next`。
/// `next` 出现多次时取第一个。
export default async function RegisterPage({
  searchParams,
}: {
  searchParams: Promise<Record<string, string | string[] | undefined>>;
}) {
  const raw = (await searchParams).next;
  const next = safeNextPath(Array.isArray(raw) ? raw[0] : raw);
  return (
    <AuthShell title="注册" description="用邮箱建一个 Patra 账号" back={next}>
      <RegisterForm next={next} />
    </AuthShell>
  );
}
