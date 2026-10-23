import type { Metadata } from "next";
import { redirect } from "next/navigation";
import { PrivacyForm } from "@/components/settings/privacy/PrivacyForm";
import { authPageHref } from "@/lib/auth/next-path";
import { requireUser } from "@/lib/auth/require-user";
import { fetchMyPrivacy } from "@/lib/identity/profile";

export const metadata: Metadata = { title: "隐私 · 设置 · Patra" };

const PAGE = "/settings/privacy";

/// 设置 · 隐私（简报 §3.12）：三个开关，改动即时保存
export default async function PrivacySettingsPage() {
  const user = await requireUser(PAGE);
  const privacy = await fetchMyPrivacy();
  if (privacy === "signed-out") {
    redirect(authPageHref("/login", PAGE));
  }
  return <PrivacyForm initial={privacy} user={user} />;
}
