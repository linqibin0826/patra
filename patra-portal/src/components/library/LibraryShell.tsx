"use client";

import { ChevronDown } from "lucide-react";
import { usePathname } from "next/navigation";
import { type ReactNode, useRef, useState } from "react";
import { CollectionDialog } from "@/components/library/CollectionDialog";
import { LibrarySidebar } from "@/components/library/LibrarySidebar";
import { ResponsivePopover } from "@/components/ui/popover";
import { useLibraryOverview } from "@/lib/library/queries";
import { formatCount } from "@/lib/library/scope";
import { btnSecondary } from "@/lib/portal-ui";
import { cn } from "@/lib/utils";

interface LibraryShellProps {
  children: ReactNode;
}

/// 文献库外壳（简报 §3.9）：h1「我的文献库」；> 880px 两栏（左侧栏 240px 吸顶 + 内容列），≤ 880px h1 下是整宽的
/// 视图切换按钮，点开底部抽屉列出与侧栏相同的内容。侧栏「+」与抽屉里的新建打开新建收藏夹对话框
export function LibraryShell({ children }: LibraryShellProps) {
  const [creating, setCreating] = useState(false);
  const onCreate = () => setCreating(true);
  return (
    <div className="mx-auto max-w-page px-gutter pt-10 pb-24 max-[880px]:pt-6">
      <h1 className="font-serif text-display-3 leading-heading font-medium tracking-tight text-fg-1">
        我的文献库
      </h1>
      <div className="mt-4 hidden max-[880px]:block">
        <ViewSwitcher onCreate={onCreate} />
      </div>
      <div className="mt-10 grid grid-cols-[240px_minmax(0,1fr)] items-start gap-12 max-[880px]:mt-5 max-[880px]:grid-cols-1">
        <aside className="sticky top-20 max-h-[calc(100dvh-6.5rem)] overflow-y-auto max-[880px]:hidden">
          <LibrarySidebar onCreate={onCreate} />
        </aside>
        <div className="min-w-0">{children}</div>
      </div>
      <CollectionDialog mode="create" open={creating} onOpenChange={setCreating} />
    </div>
  );
}

/// 移动端的视图切换：「全部 · 1,284 ▾」，点开底部抽屉，选中即关闭
function ViewSwitcher({ onCreate }: { onCreate: () => void }) {
  const pathname = usePathname();
  const overview = useLibraryOverview();
  const anchor = useRef<HTMLButtonElement>(null);
  const [open, setOpen] = useState(false);
  const data = overview.data;
  const id = pathname.split("/")[2];
  const current =
    data === undefined
      ? null
      : pathname === "/library"
        ? { name: "全部", count: data.total }
        : pathname === "/library/unfiled"
          ? { name: "未分类", count: data.unfiled }
          : (() => {
              const collection = data.collections.find((c) => c.collectionId === id);
              return collection ? { name: collection.name, count: collection.itemCount } : null;
            })();
  return (
    <>
      <button
        ref={anchor}
        type="button"
        onClick={() => setOpen(true)}
        className={cn(btnSecondary, "h-12 w-full justify-between py-0 font-medium")}
      >
        <span className="min-w-0 truncate">
          {current ? `${current.name} · ${formatCount(current.count)}` : "文献库视图"}
        </span>
        <ChevronDown className="size-4 shrink-0" aria-hidden="true" />
      </button>
      <ResponsivePopover open={open} onOpenChange={setOpen} anchor={anchor} title="文献库视图">
        <LibrarySidebar
          visibilityText
          onNavigate={() => setOpen(false)}
          onCreate={() => {
            setOpen(false);
            onCreate();
          }}
        />
      </ResponsivePopover>
    </>
  );
}
