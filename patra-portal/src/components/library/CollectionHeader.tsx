"use client";

import { Globe, Lock, MoreHorizontal } from "lucide-react";
import Link from "next/link";
import { useState } from "react";
import { toast } from "sonner";
import { CollectionDialog } from "@/components/library/CollectionDialog";
import { DeleteCollectionDialog } from "@/components/library/DeleteCollectionDialog";
import type { CollectionContext } from "@/components/library/LibraryView";
import { PublicToggleCard } from "@/components/library/PublicToggleCard";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuLinkItem,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";
import { useCurrentUser } from "@/lib/auth/current-user-query";
import { formatDate } from "@/lib/format-time";
import type { Collection } from "@/lib/interaction/schema";
import { formatCount } from "@/lib/library/scope";
import { btnSecondary, btnText } from "@/lib/portal-ui";
import { useCopy } from "@/lib/use-copy";
import { cn } from "@/lib/utils";

interface CollectionHeaderProps {
  collection: Collection;
  context: CollectionContext | undefined;
  onCite: () => void;
  onExport: () => void;
}

/// 收藏夹视图的头部（简报 §3.11）：名称、简介（4 行 + 展开）、可见性 · 篇数 · 更新时间；右端复制引用、导出与
/// `···`（编辑、复制公开链接、在阅读页查看、删除）；下面是公开开关卡。窄屏时复制引用、导出收进 `···`
export function CollectionHeader({ collection, context, onCite, onExport }: CollectionHeaderProps) {
  const user = useCurrentUser();
  const [editing, setEditing] = useState(false);
  const [deleting, setDeleting] = useState(false);
  const [expanded, setExpanded] = useState(false);
  const { copy } = useCopy();
  const isPublic = collection.visibility === "PUBLIC";
  const Icon = isPublic ? Globe : Lock;
  const longDescription = (collection.description ?? "").length > 160;
  const action = cn(btnSecondary, "px-3.5 py-1.5 text-sm");

  return (
    <div className="flex flex-col gap-4">
      <div className="flex items-start justify-between gap-4">
        <div className="flex min-w-0 flex-col gap-2">
          <h2
            title={collection.name}
            className="line-clamp-2 font-serif text-display-3 leading-heading font-medium tracking-tight break-words text-fg-1"
          >
            {collection.name}
          </h2>
          {collection.description && (
            <div className="flex flex-col items-start gap-1">
              <p
                className={cn(
                  "text-md leading-6 whitespace-pre-line text-fg-2",
                  !expanded && "line-clamp-4",
                )}
              >
                {collection.description}
              </p>
              {longDescription && !expanded && (
                <button
                  type="button"
                  className={cn(btnText, "text-sm")}
                  onClick={() => setExpanded(true)}
                >
                  展开
                </button>
              )}
            </div>
          )}
          <p className="flex items-center gap-1.5 text-sm text-fg-3">
            <Icon className="size-3.5" aria-hidden="true" />
            {isPublic ? "公开" : "私有"} · {formatCount(collection.itemCount)} 篇 · 更新于{" "}
            {formatDate(collection.updatedAt)}
          </p>
        </div>
        <div className="flex shrink-0 items-center gap-2">
          <button type="button" className={cn(action, "max-[880px]:hidden")} onClick={onCite}>
            复制引用
          </button>
          <button type="button" className={cn(action, "max-[880px]:hidden")} onClick={onExport}>
            导出
          </button>
          <DropdownMenu>
            <DropdownMenuTrigger
              aria-label="收藏夹操作"
              className="inline-flex size-9 items-center justify-center rounded-full border border-border-default bg-paper-50 text-fg-2 transition-colors hover:bg-paper-200 data-popup-open:bg-paper-200"
            >
              <MoreHorizontal className="size-4" aria-hidden="true" />
            </DropdownMenuTrigger>
            <DropdownMenuContent align="end" className="w-52">
              <DropdownMenuItem className="min-[881px]:hidden" onClick={onCite}>
                复制引用
              </DropdownMenuItem>
              <DropdownMenuItem className="min-[881px]:hidden" onClick={onExport}>
                导出
              </DropdownMenuItem>
              <DropdownMenuItem onClick={() => setEditing(true)}>编辑名称和简介</DropdownMenuItem>
              {isPublic && context && (
                <>
                  <DropdownMenuItem
                    onClick={async () => {
                      const result = await copy(context.publicLink, null);
                      if (result === "copied") {
                        toast.success("已复制公开链接");
                      }
                    }}
                  >
                    复制公开链接
                  </DropdownMenuItem>
                  <DropdownMenuLinkItem
                    render={<Link href={`/collections/${collection.collectionId}`} />}
                  >
                    在阅读页查看
                  </DropdownMenuLinkItem>
                </>
              )}
              <DropdownMenuSeparator />
              <DropdownMenuItem className="text-status-danger" onClick={() => setDeleting(true)}>
                删除收藏夹
              </DropdownMenuItem>
            </DropdownMenuContent>
          </DropdownMenu>
        </div>
      </div>
      {user.status === "authenticated" && context && (
        <PublicToggleCard
          key={`${collection.collectionId}-${collection.visibility}`}
          collection={collection}
          emailVerified={user.user.emailVerified}
          email={user.user.email}
          profileVisibility={context.profileVisibility}
          publicLink={context.publicLink}
        />
      )}
      <CollectionDialog
        mode="edit"
        collection={collection}
        open={editing}
        onOpenChange={setEditing}
      />
      <DeleteCollectionDialog collection={collection} open={deleting} onOpenChange={setDeleting} />
    </div>
  );
}
