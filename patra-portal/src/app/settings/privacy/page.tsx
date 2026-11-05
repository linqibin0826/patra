import type { Metadata } from "next";
import { redirect } from "next/navigation";
import { Suspense } from "react";
import { PrivacyForm } from "@/components/settings/privacy/PrivacyForm";
import {
  ReadingRecordSection,
  ReadingRecordSkeleton,
} from "@/components/settings/privacy/ReadingRecordSection";
import { authPageHref } from "@/lib/auth/next-path";
import { requireUser } from "@/lib/auth/require-user";
import { fetchMyPrivacy } from "@/lib/identity/profile";

export const metadata: Metadata = { title: "隐私 · 设置 · Patra" };

const PAGE = "/settings/privacy";

/// 设置 · 隐私（简报 §3.12）：三个开关，改动即时保存；第四张卡片「阅读记录」在行为数据服务，在自己的 Suspense 里取、单独降级
/// （v0.18 简报 §3.8）
export default async function PrivacySettingsPage() {
  const user = await requireUser(PAGE);
  const privacy = await fetchMyPrivacy();
  if (privacy === "signed-out") {
    redirect(authPageHref("/login", PAGE));
  }
  return (
    <PrivacyForm
      initial={privacy}
      user={user}
      readingCard={
        <Suspense fallback={<ReadingRecordSkeleton />}>
          <ReadingRecordSection />
        </Suspense>
      }
    />
  );
}
