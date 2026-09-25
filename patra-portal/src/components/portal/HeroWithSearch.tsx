"use client";

import { useRouter } from "next/navigation";
import { toast } from "sonner";
import { Hero } from "@/components/portal/Hero";
import { composerTarget } from "@/lib/portal-api/paper-search";

/// 首页 Hero + 搜索框：按当前 tab 跳转 /papers（关键词 ?q= / 作者 ?author= / PMID ?pmid= / DOI ?doi=）。
/// 空内容不跳；PMID 非数字弹提示。PMID / DOI 命中直跳详情由 /papers 服务端统一处理。
export function HeroWithSearch() {
  const router = useRouter();
  return (
    <Hero
      onComposerSubmit={({ mode, value }) => {
        const target = composerTarget(mode, value);
        if (target.kind === "invalid") {
          toast(target.message);
          return;
        }
        if (target.kind === "ok") {
          router.push(target.href);
        }
      }}
    />
  );
}
