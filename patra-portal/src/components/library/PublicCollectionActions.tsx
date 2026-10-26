"use client";

import { useRef, useState } from "react";
import { CiteDialog } from "@/components/library/CiteDialog";
import { CopyLinkField } from "@/components/library/CopyLinkField";
import { type DialogScope, ExportDialog } from "@/components/library/ExportDialog";
import { ResponsivePopover } from "@/components/ui/popover";
import type { LibrarySort } from "@/lib/library/params";
import { formatCount } from "@/lib/library/scope";
import { btnSecondary } from "@/lib/portal-ui";
import { cn } from "@/lib/utils";

interface PublicCollectionActionsProps {
  collectionId: string;
  name: string;
  /// 所有者昵称（范围行「陈宇的收藏夹「组会」· 23 篇」）；已注销或取不到时为 null
  ownerName: string | null;
  count: number;
  sort: LibrarySort;
  /// 规范地址（按门户公开地址拼好）
  link: string;
}

/// 公开收藏夹页头部的导出、复制引用、分享（访客也能用，2026-10-24 用户定）。分享浮层本版只有「链接」一块
export function PublicCollectionActions({
  collectionId,
  name,
  ownerName,
  count,
  sort,
  link,
}: PublicCollectionActionsProps) {
  const [dialog, setDialog] = useState<"export" | "cite" | null>(null);
  const [sharing, setSharing] = useState(false);
  const shareRef = useRef<HTMLButtonElement>(null);
  const scope: DialogScope = {
    kind: "public-collection",
    id: collectionId,
    sort,
    label: `${ownerName ? `${ownerName}的收藏夹` : "收藏夹"}「${name}」· ${formatCount(count)} 篇`,
    name,
    count,
  };
  const action = cn(btnSecondary, "max-[880px]:flex-1");
  return (
    <>
      <button type="button" className={action} onClick={() => setDialog("export")}>
        导出
      </button>
      <button type="button" className={action} onClick={() => setDialog("cite")}>
        复制引用
      </button>
      <button ref={shareRef} type="button" className={action} onClick={() => setSharing(true)}>
        分享
      </button>
      <ResponsivePopover
        open={sharing}
        onOpenChange={setSharing}
        anchor={shareRef}
        title="分享"
        align="end"
        className="w-100"
      >
        <div className="flex flex-col gap-2 px-4 py-4">
          <h3 className="font-mono text-2xs tracking-caps text-fg-3 uppercase">链接</h3>
          <CopyLinkField url={link} />
        </div>
      </ResponsivePopover>
      {dialog === "export" && (
        <ExportDialog open onOpenChange={(open) => !open && setDialog(null)} scope={scope} />
      )}
      {dialog === "cite" && (
        <CiteDialog
          open
          onOpenChange={(open) => !open && setDialog(null)}
          scope={scope}
          onExport={() => setDialog("export")}
        />
      )}
    </>
  );
}
