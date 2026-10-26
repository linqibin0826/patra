"use client";

import { Globe, Lock, Plus } from "lucide-react";
import Link from "next/link";
import { usePathname } from "next/navigation";
import { LibrarySidebarSkeleton } from "@/components/library/LibrarySkeleton";
import { LIMITS } from "@/lib/library/messages";
import { useLibraryOverview } from "@/lib/library/queries";
import { formatCount } from "@/lib/library/scope";
import { btnText } from "@/lib/portal-ui";
import { cn } from "@/lib/utils";

const ROW =
  "flex items-center gap-2 border-l-2 border-transparent px-3.5 py-2 text-base leading-5 text-fg-2 transition-colors hover:bg-paper-200 hover:text-ink-900 aria-[current=page]:border-clay-500 aria-[current=page]:bg-paper-200 aria-[current=page]:font-medium aria-[current=page]:text-fg-1";
const COUNT = "ml-auto shrink-0 font-mono text-xs tabular-nums text-fg-3";

interface LibrarySidebarProps {
  onCreate: () => void;
  /// 点了某一项（移动端抽屉里用来关闭抽屉）
  onNavigate?: () => void;
  /// 可见性用文字「私有 / 公开」标在名称前（手机视图切换抽屉，简报 §10 第 20 条）
  visibilityText?: boolean;
}

/// 文献库侧栏（简报 §3.9）：全部 / 未分类，下面是收藏夹（可见性图标、名称、篇数，最近更新的在前）。
/// 样式同设置页分区导航；移动端同样的内容放进视图切换抽屉。概览取不到时这一块不出（列表位置有错误块）
export function LibrarySidebar({
  onCreate,
  onNavigate,
  visibilityText = false,
}: LibrarySidebarProps) {
  const pathname = usePathname();
  const overview = useLibraryOverview();
  if (overview.data === undefined) {
    return overview.isPending ? <LibrarySidebarSkeleton /> : null;
  }
  const { total, unfiled, collections } = overview.data;
  const current = (href: string) => (pathname === href ? "page" : undefined);
  const full = collections.length >= LIMITS.collections;
  return (
    <nav aria-label="文献库视图" className="flex flex-col gap-0.5">
      <Link href="/library" aria-current={current("/library")} className={ROW} onClick={onNavigate}>
        全部
        <span className={COUNT}>{formatCount(total)}</span>
      </Link>
      <Link
        href="/library/unfiled"
        aria-current={current("/library/unfiled")}
        className={ROW}
        onClick={onNavigate}
      >
        未分类
        <span className={COUNT}>{formatCount(unfiled)}</span>
      </Link>
      <div className="mt-4 flex items-center justify-between border-t border-border-subtle px-3.5 pt-4 pb-1.5">
        <h2 className="font-mono text-2xs tracking-caps text-fg-3 uppercase">收藏夹</h2>
        <button
          type="button"
          aria-label="新建收藏夹"
          aria-disabled={full || undefined}
          title={full ? "最多 200 个收藏夹" : "新建收藏夹"}
          onClick={() => !full && onCreate()}
          className="inline-flex size-7 items-center justify-center rounded-full text-fg-2 transition-colors not-aria-disabled:hover:bg-paper-200 not-aria-disabled:hover:text-ink-900 aria-disabled:cursor-not-allowed aria-disabled:opacity-55"
        >
          <Plus className="size-4" aria-hidden="true" />
        </button>
      </div>
      {collections.length === 0 ? (
        <div className="flex flex-col items-start gap-1.5 px-3.5 py-2 text-sm text-fg-3">
          还没有收藏夹
          <button type="button" className={cn(btnText, "text-sm")} onClick={onCreate}>
            新建收藏夹
          </button>
        </div>
      ) : (
        collections.map((collection) => {
          const href = `/library/${collection.collectionId}`;
          const Icon = collection.visibility === "PUBLIC" ? Globe : Lock;
          const label = collection.visibility === "PUBLIC" ? "公开" : "私有";
          return (
            <Link
              key={collection.collectionId}
              href={href}
              aria-current={current(href)}
              title={collection.name}
              className={ROW}
              onClick={onNavigate}
            >
              {visibilityText ? (
                <span className="shrink-0 text-xs text-fg-3">{label} </span>
              ) : (
                <Icon className="size-3.5 shrink-0 text-fg-3" aria-hidden="true" />
              )}
              <span className="min-w-0 truncate">{collection.name}</span>
              {!visibilityText && <span className="sr-only">{label}</span>}
              <span className={COUNT}>{formatCount(collection.itemCount)}</span>
            </Link>
          );
        })
      )}
    </nav>
  );
}
