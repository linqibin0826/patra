"use client";

import { useQueryClient } from "@tanstack/react-query";
import { useRouter } from "next/navigation";
import { useState } from "react";
import { toast } from "sonner";
import { FormAlert } from "@/components/auth/FormAlert";
import { copyCollection } from "@/lib/interaction/collection-actions";
import type { CopyResult } from "@/lib/interaction/schema";
import { invalidateLibrary } from "@/lib/library/cache";
import { useLoginRedirect } from "@/lib/library/use-login-redirect";
import { orUnavailable } from "@/lib/or-unavailable";
import { btnPrimary } from "@/lib/portal-ui";

/// 复制失败的提示条（简报 §4.6）
const FAILURES = {
  "collection-limit": "你已经有 200 个收藏夹，先删除一些再复制。",
  "library-full": "文献库已满 20,000 篇，复制不了这个收藏夹。先移除一些文献再试。",
  "not-visible": "这个收藏夹已经不公开了。",
  unavailable: "没有复制成功，请稍后再试",
} as const;

/// 复制成功的 toast：实际名称（重名加序号）、新收进篇数，原本就在库里的篇数为 0 时省掉，已失效没复制的放末尾
export function copiedMessage(result: CopyResult): string {
  const parts = [`新收进 ${result.addedToLibrary} 篇`];
  if (result.alreadyInLibrary > 0) {
    parts.push(`${result.alreadyInLibrary} 篇原本就在文献库`);
  }
  if (result.skippedUnavailable > 0) {
    parts.push(`${result.skippedUnavailable} 篇已失效没有复制`);
  }
  return `已复制为「${result.name}」：${parts.join("，")}`;
}

/// 「复制为我的收藏夹」（简报 §3.14）：点了立即执行、不确认；整体成败。成功后按钮恢复，可以再复制一次
/// （得到「组会 (2)」）；toast 带「去看看」进新收藏夹
export function CopyCollectionButton({ collectionId }: { collectionId: string }) {
  const client = useQueryClient();
  const router = useRouter();
  const redirectToLogin = useLoginRedirect();
  const [copying, setCopying] = useState(false);
  const [failure, setFailure] = useState<keyof typeof FAILURES | null>(null);

  async function copy() {
    setCopying(true);
    setFailure(null);
    const result = await orUnavailable(copyCollection(collectionId));
    setCopying(false);
    if (result.ok) {
      void invalidateLibrary(client);
      toast.success(copiedMessage(result.result), {
        action: {
          label: "去看看",
          onClick: () => router.push(`/library/${result.result.collectionId}`),
        },
      });
      return;
    }
    if (result.reason === "signed-out") {
      redirectToLogin();
      return;
    }
    setFailure(result.reason);
  }

  return (
    <>
      <button
        type="button"
        aria-disabled={copying || undefined}
        onClick={() => !copying && void copy()}
        className={btnPrimary}
      >
        {copying ? "复制中…" : "复制为我的收藏夹"}
      </button>
      {failure && (
        <div className="basis-full">
          <FormAlert>{FAILURES[failure]}</FormAlert>
        </div>
      )}
    </>
  );
}
