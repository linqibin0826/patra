"use client";

import { useQueryClient } from "@tanstack/react-query";
import { usePathname, useRouter } from "next/navigation";
import { type ReactNode, useRef, useState } from "react";
import { toast } from "sonner";
import { FormAlert } from "@/components/auth/FormAlert";
import { CheckBox } from "@/components/library/CheckBox";
import { CiteDialog } from "@/components/library/CiteDialog";
import { CollectionChecklist } from "@/components/library/CollectionChecklist";
import { CollectionHeader } from "@/components/library/CollectionHeader";
import { type DialogScope, ExportDialog } from "@/components/library/ExportDialog";
import { InteractionUnavailable } from "@/components/library/InteractionUnavailable";
import { LibraryEmpty } from "@/components/library/LibraryEmpty";
import { LibraryItem } from "@/components/library/LibraryItem";
import { LibraryItemMenu } from "@/components/library/LibraryItemMenu";
import { LibrarySelectionBar } from "@/components/library/LibrarySelectionBar";
import { LibraryListSkeleton } from "@/components/library/LibrarySkeleton";
import { SortSelect } from "@/components/library/SortSelect";
import { PaginationView } from "@/components/portal/browse/BrowsePagination";
import { Dialog } from "@/components/ui/dialog";
import { ResponsivePopover } from "@/components/ui/popover";
import { removeFromCollection } from "@/lib/interaction/collection-actions";
import type { LibraryEntry } from "@/lib/interaction/library";
import { collectPublication, removeItems } from "@/lib/interaction/library-actions";
import type { Collection } from "@/lib/interaction/schema";
import { dropEntries, invalidateLibrary, patchEntry, patchItemStatus } from "@/lib/library/cache";
import { removeConfirmText } from "@/lib/library/messages";
import { type LibrarySort, libraryHref } from "@/lib/library/params";
import { useLibraryItems, useLibraryOverview } from "@/lib/library/queries";
import { formatCount } from "@/lib/library/scope";
import { useLoginRedirect } from "@/lib/library/use-login-redirect";
import { useSelection } from "@/lib/library/use-selection";
import { orUnavailable } from "@/lib/or-unavailable";
import { btnDanger, btnSecondary, btnText } from "@/lib/portal-ui";
import { cn } from "@/lib/utils";

export interface LibraryViewProps {
  /// `all` / `unfiled` / 收藏夹 ID
  view: string;
  sort: LibrarySort;
  page: number;
  /// 收藏夹视图要的、只有服务端知道的东西（公开链接、主页可见性），由页面给
  collectionContext?: CollectionContext;
}

/// 收藏夹视图的服务端上下文（简报 §3.13 公开开关卡）
export interface CollectionContext {
  /// 公开链接（按门户公开地址拼好）
  publicLink: string;
  /// 本人主页现在对谁可见；读不到时为 null
  profileVisibility: "PUBLIC" | "SIGNED_IN" | "SELF" | null;
}

/// 视图头部的整体操作
export interface ViewActions {
  cite: () => void;
  exportAll: () => void;
}

/// 对话框的状态
type OpenDialog = { kind: "export" | "cite"; scope: DialogScope } | null;

/// 文献库的一个视图（简报 §3.10）：头部 → 工具条（排序、篇数）→ 条目 → 分页。交互只改查询缓存、失效重取，
/// 不刷新页面（决策 H）。排序、翻页换地址（等于换查询键）
export function LibraryView({ view, sort, page, collectionContext }: LibraryViewProps) {
  const client = useQueryClient();
  const router = useRouter();
  const pathname = usePathname();
  const redirectToLogin = useLoginRedirect();
  const items = useLibraryItems(view, sort, page);
  const overview = useLibraryOverview();
  const [dialog, setDialog] = useState<OpenDialog>(null);
  const [editing, setEditing] = useState<string | null>(null);
  const [adding, setAdding] = useState<{ entry: LibraryEntry; anchor: HTMLElement } | null>(null);
  const [confirming, setConfirming] = useState<LibraryEntry | null>(null);
  const [failure, setFailure] = useState<string | null>(null);
  const addAnchor = useRef<HTMLElement | null>(null);
  const [selecting, setSelecting] = useState(false);
  const [batchAdding, setBatchAdding] = useState(false);
  const [batchConfirm, setBatchConfirm] = useState(false);
  const [busy, setBusy] = useState(false);
  const [notice, setNotice] = useState<string | null>(null);
  const batchAddRef = useRef<HTMLButtonElement>(null);
  const pageEntries = items.data?.items ?? [];
  const selection = useSelection(pageEntries.map((entry) => entry.item.itemId));
  const selected = pageEntries.filter((entry) => selection.selected.has(entry.item.itemId));
  const selectedIds = selected.map((entry) => entry.item.itemId);

  const collections = overview.data?.collections ?? [];
  const isCollection = view !== "all" && view !== "unfiled";
  const collection = isCollection ? collections.find((c) => c.collectionId === view) : undefined;
  const hrefOf = (next: { sort?: LibrarySort; page?: number }) =>
    libraryHref(pathname, next.sort ?? sort, next.page ?? 1);

  const wholeScope = (): DialogScope | null => {
    if (view === "all" || view === "unfiled") {
      const count = view === "all" ? overview.data?.total : overview.data?.unfiled;
      return {
        kind: "library",
        view,
        sort,
        label:
          view === "all"
            ? `整个文献库 · ${formatCount(count ?? 0)} 篇`
            : `未分类 · ${formatCount(count ?? 0)} 篇`,
        name: view === "all" ? "library" : "未分类",
        count: count ?? 0,
      };
    }
    return collection
      ? {
          kind: "collection",
          id: collection.collectionId,
          sort,
          label: `收藏夹「${collection.name}」· ${formatCount(collection.itemCount)} 篇`,
          name: collection.name,
          count: collection.itemCount,
        }
      : null;
  };
  const viewActions: ViewActions = {
    cite: () => {
      const scope = wholeScope();
      if (scope) setDialog({ kind: "cite", scope });
    },
    exportAll: () => {
      const scope = wholeScope();
      if (scope) setDialog({ kind: "export", scope });
    },
  };
  const singleScope = (entry: LibraryEntry): DialogScope => {
    const title = entry.paper?.title ?? entry.item.title ?? "";
    const short = Array.from(title).slice(0, 30).join("");
    return {
      kind: "items",
      items: [{ key: entry.item.key, state: entry.paper ? "ACTIVE" : "UNAVAILABLE" }],
      label: `这一篇：${short}${Array.from(title).length > 30 ? "…" : ""}`,
      name: entry.paper?.pmid ?? entry.item.key ?? "export",
    };
  };

  /// 条目在缓存里的当前样子（浮层开着时条目可能已被改过，不能用打开那一刻的快照）
  const currentOf = (entry: LibraryEntry) =>
    pageEntries.find((other) => other.item.itemId === entry.item.itemId) ?? entry;

  /// 条目所在收藏夹变了：条目页与「我的状态」两边都改（别的屏的收藏浮层读后者）
  function syncCollections(entry: LibraryEntry, collectionIds: string[]) {
    patchEntry(client, entry.item.itemId, { collectionIds });
    if (entry.item.key) {
      patchItemStatus(client, entry.item.key, { collectionIds });
    }
  }

  /// 这一页去掉了几条之后：整页空了且不是第一页，回到上一页
  function afterDrop(count: number) {
    if (page > 1 && count >= pageEntries.length) {
      router.push(hrefOf({ page: page - 1 }));
    }
  }

  /// 选中的几篇（批量导出 / 复制引用的范围）
  const selectionScope = (): DialogScope => ({
    kind: "items",
    items: selected.map((entry) => ({
      key: entry.item.key,
      state: entry.paper ? "ACTIVE" : "UNAVAILABLE",
    })),
    label: `选中的 ${selected.length} 篇`,
    name: "selection",
  });

  async function batchRemove() {
    setBusy(true);
    setFailure(null);
    const result = await orUnavailable(removeItems(selectedIds));
    setBusy(false);
    setBatchConfirm(false);
    if (!result.ok) {
      if (result.reason === "signed-out") {
        redirectToLogin();
        return;
      }
      setFailure("没有移除成功，请稍后再试");
      return;
    }
    dropEntries(client, selectedIds);
    for (const entry of selected) {
      if (entry.item.key) {
        patchItemStatus(client, entry.item.key, {
          collected: false,
          itemId: null,
          collectedAt: null,
          collectionIds: [],
          hasNote: false,
        });
      }
    }
    void invalidateLibrary(client);
    toast.success(`已从文献库移除 ${selectedIds.length} 篇`);
    selection.clear();
    afterDrop(selectedIds.length);
  }

  async function batchRemoveFromCurrent() {
    if (!collection) {
      return;
    }
    setBusy(true);
    setFailure(null);
    const result = await orUnavailable(removeFromCollection(collection.collectionId, selectedIds));
    setBusy(false);
    if (!result.ok) {
      if (result.reason === "signed-out") {
        redirectToLogin();
        return;
      }
      setFailure("没有保存成功，请稍后再试");
      return;
    }
    dropEntries(client, selectedIds, view);
    for (const entry of selected) {
      syncCollections(
        entry,
        entry.item.collectionIds.filter((id) => id !== collection.collectionId),
      );
    }
    void invalidateLibrary(client);
    toast.success(`已从「${collection.name}」移出 ${selectedIds.length} 篇`);
    selection.clear();
    afterDrop(selectedIds.length);
  }

  function settleRemoved(entry: LibraryEntry, confirmed: boolean) {
    dropEntries(client, [entry.item.itemId]);
    afterDrop(1);
    if (entry.item.key) {
      patchItemStatus(client, entry.item.key, {
        collected: false,
        itemId: null,
        collectedAt: null,
        collectionIds: [],
        hasNote: false,
      });
    }
    void invalidateLibrary(client);
    const key = entry.item.key;
    if (confirmed || !key) {
      toast.success("已从文献库移除");
      return;
    }
    toast.success("已从文献库移除", {
      action: {
        label: "撤销",
        onClick: () => {
          void orUnavailable(collectPublication(key)).then((again) => {
            if (again.ok) {
              patchItemStatus(client, key, {
                collected: true,
                itemId: again.item.itemId,
                collectedAt: again.item.collectedAt,
                collectionIds: again.item.collectionIds,
                hasNote: again.item.note !== null,
              });
            }
            return invalidateLibrary(client);
          });
        },
      },
    });
  }

  async function remove(entry: LibraryEntry, confirmed: boolean) {
    setFailure(null);
    const result = await orUnavailable(removeItems([entry.item.itemId]));
    setConfirming(null);
    if (result.ok) {
      settleRemoved(entry, confirmed);
      return;
    }
    if (result.reason === "signed-out") {
      redirectToLogin();
      return;
    }
    setFailure("没有移除成功，请稍后再试");
  }

  async function removeFromCurrent(entry: LibraryEntry) {
    if (!collection) {
      return;
    }
    setFailure(null);
    dropEntries(client, [entry.item.itemId], view);
    const result = await orUnavailable(
      removeFromCollection(collection.collectionId, [entry.item.itemId]),
    );
    if (!result.ok && result.reason === "signed-out") {
      redirectToLogin();
      return;
    }
    if (!result.ok) {
      setFailure("没有保存成功，请稍后再试");
    } else {
      syncCollections(
        entry,
        entry.item.collectionIds.filter((id) => id !== collection.collectionId),
      );
      afterDrop(1);
    }
    void invalidateLibrary(client);
  }

  const data = items.data;
  let body: ReactNode;
  if (data === undefined) {
    body = items.isError ? (
      <InteractionUnavailable
        title="文献库暂时打不开"
        body="请稍后再试，你收藏的文献都还在。"
        onRetry={() => {
          void items.refetch();
          void overview.refetch();
        }}
      />
    ) : (
      <LibraryListSkeleton />
    );
  } else if (data.items.length === 0 && data.total === 0) {
    body = (
      <LibraryEmpty kind={isCollection ? "collection" : view === "unfiled" ? "unfiled" : "all"} />
    );
  } else {
    body = (
      <div className="flex flex-col gap-6">
        <ul aria-label="文献库条目" className="flex flex-col">
          {data.items.map((entry) => (
            <li key={entry.item.itemId}>
              <EntryRow
                entry={entry}
                select={
                  selecting ? (
                    <CheckBox
                      aria-label={`选择「${Array.from(entry.paper?.title ?? entry.item.title ?? "")
                        .slice(0, 40)
                        .join("")}」`}
                      checked={selection.selected.has(entry.item.itemId)}
                      onChange={() => selection.toggle(entry.item.itemId)}
                    />
                  ) : undefined
                }
                collections={collections}
                currentCollection={collection ?? null}
                editingNote={editing === entry.item.itemId}
                onEditNote={() => setEditing(entry.item.itemId)}
                onNoteDone={() => setEditing(null)}
                onNoteSaved={(item) => {
                  patchEntry(client, item.itemId, { note: item.note });
                  if (item.key) {
                    patchItemStatus(client, item.key, { hasNote: item.note !== null });
                  }
                }}
                onAddToCollections={(anchor) => {
                  addAnchor.current = anchor;
                  setAdding({ entry, anchor });
                }}
                onCite={() => setDialog({ kind: "cite", scope: singleScope(entry) })}
                onExport={() => setDialog({ kind: "export", scope: singleScope(entry) })}
                onRemoveFromCollection={() => void removeFromCurrent(entry)}
                onRemove={() => {
                  if (entry.item.note || entry.item.collectionIds.length > 0) {
                    setConfirming(entry);
                  } else {
                    void remove(entry, false);
                  }
                }}
              />
            </li>
          ))}
        </ul>
        <PaginationView
          page={page}
          total={data.total}
          pageSize={data.pageSize}
          unit="篇"
          hrefOf={(next) => hrefOf({ page: next })}
        />
      </div>
    );
  }

  return (
    <div className="flex flex-col gap-5">
      {isCollection ? (
        collection && (
          <CollectionHeader
            collection={collection}
            context={collectionContext}
            onCite={viewActions.cite}
            onExport={viewActions.exportAll}
          />
        )
      ) : (
        <ViewHeader
          title={view === "unfiled" ? "未分类" : "全部文献"}
          description={view === "unfiled" ? "不在任何收藏夹里的文献。" : "收藏的每一篇都在这里。"}
          actions={viewActions}
        />
      )}
      {data !== undefined && data.total > 0 && (
        <div className="flex min-h-10 items-center justify-between gap-4 border-b border-border-default pb-3">
          {selecting ? (
            <>
              <div className="flex items-center gap-2.5 text-sm text-fg-1">
                <CheckBox
                  aria-label="全选本页"
                  checked={selection.allSelected}
                  onChange={selection.togglePage}
                />
                <span aria-hidden="true">全选本页</span>
              </div>
              <span className="font-mono text-xs tabular-nums text-fg-2">
                已选 {selection.selected.size} 篇
              </span>
              <button
                type="button"
                className={cn(btnText, "text-sm")}
                onClick={() => {
                  setSelecting(false);
                  selection.clear();
                }}
              >
                完成
              </button>
            </>
          ) : (
            <>
              <div className="flex items-center gap-4">
                <button
                  type="button"
                  className={cn(btnText, "text-sm")}
                  onClick={() => setSelecting(true)}
                >
                  选择
                </button>
                <SortSelect sort={sort} />
              </div>
              <span className="font-mono text-xs tabular-nums text-fg-3">
                {formatCount(data.total)} 篇
              </span>
            </>
          )}
        </div>
      )}
      <span className="sr-only" aria-live="polite">
        {selecting && selection.selected.size > 0 ? `已选 ${selection.selected.size} 篇` : ""}
      </span>
      {notice && <FormAlert tone="warning">{notice}</FormAlert>}
      {failure && <FormAlert>{failure}</FormAlert>}
      {body}

      {selecting && selected.length > 0 && (
        <LibrarySelectionBar
          count={selected.length}
          currentCollectionName={collection?.name ?? null}
          addRef={batchAddRef}
          busy={busy}
          onAdd={() => setBatchAdding(true)}
          onRemoveFromCollection={() => !busy && void batchRemoveFromCurrent()}
          onCite={() => setDialog({ kind: "cite", scope: selectionScope() })}
          onExport={() => setDialog({ kind: "export", scope: selectionScope() })}
          onRemove={() => setBatchConfirm(true)}
        />
      )}
      {batchAdding && (
        <ResponsivePopover
          open
          onOpenChange={(open) => !open && setBatchAdding(false)}
          anchor={batchAddRef}
          title="放进收藏夹"
        >
          <div className="px-4 py-3.5">
            <CollectionChecklist
              itemIds={selectedIds}
              memberOf={(id) =>
                selected.filter((entry) => entry.item.collectionIds.includes(id)).length
              }
              onChanged={(id, added, result) => {
                const name = collections.find((c) => c.collectionId === id)?.name ?? "";
                for (const entry of selected) {
                  const ids = entry.item.collectionIds;
                  syncCollections(
                    entry,
                    added ? [...new Set([...ids, id])] : ids.filter((other) => other !== id),
                  );
                }
                if (added) {
                  toast.success(`已放进「${name}」${result?.added ?? selectedIds.length} 篇`);
                  setNotice(
                    result && result.skippedFull > 0
                      ? `${result.skippedFull} 篇没有放进「${name}」：收藏夹已满 5,000 篇。`
                      : null,
                  );
                } else {
                  toast.success(`已从「${name}」移出 ${selectedIds.length} 篇`);
                }
              }}
            />
          </div>
        </ResponsivePopover>
      )}
      {batchConfirm && (
        <Dialog
          open
          onOpenChange={(open) => !open && setBatchConfirm(false)}
          title={`从文献库移除 ${selectedIds.length} 篇？`}
          description="这些文献的备注会删除，也会移出所有收藏夹。"
          size="sm"
        >
          <div className="flex justify-end gap-4">
            <button
              type="button"
              className={cn(btnText, "text-sm")}
              onClick={() => setBatchConfirm(false)}
            >
              取消
            </button>
            <button
              type="button"
              aria-disabled={busy || undefined}
              className={btnDanger}
              onClick={() => !busy && void batchRemove()}
            >
              移除 {selectedIds.length} 篇
            </button>
          </div>
        </Dialog>
      )}
      {adding && (
        <ResponsivePopover
          open
          onOpenChange={(open) => !open && setAdding(null)}
          anchor={addAnchor}
          title="放进收藏夹"
          align="end"
        >
          <div className="px-4 py-3.5">
            <CollectionChecklist
              itemIds={[adding.entry.item.itemId]}
              memberOf={(id) => (currentOf(adding.entry).item.collectionIds.includes(id) ? 1 : 0)}
              onChanged={(id, added) => {
                const current = currentOf(adding.entry);
                const ids = current.item.collectionIds;
                syncCollections(
                  current,
                  added ? [...new Set([...ids, id])] : ids.filter((other) => other !== id),
                );
              }}
            />
          </div>
        </ResponsivePopover>
      )}
      {confirming && (
        <Dialog
          open
          onOpenChange={(open) => !open && setConfirming(null)}
          title="从文献库移除？"
          description={removeConfirmText(
            confirming.item.note !== null,
            confirming.item.collectionIds.length,
          )}
          size="sm"
        >
          <div className="flex justify-end gap-4">
            <button
              type="button"
              className={cn(btnText, "text-sm")}
              onClick={() => setConfirming(null)}
            >
              取消
            </button>
            <button
              type="button"
              className={btnDanger}
              onClick={() => void remove(confirming, true)}
            >
              移除
            </button>
          </div>
        </Dialog>
      )}
      {dialog?.kind === "export" && (
        <ExportDialog open onOpenChange={(open) => !open && setDialog(null)} scope={dialog.scope} />
      )}
      {dialog?.kind === "cite" && (
        <CiteDialog
          open
          onOpenChange={(open) => !open && setDialog(null)}
          scope={dialog.scope}
          onExport={() => setDialog({ kind: "export", scope: dialog.scope })}
        />
      )}
    </div>
  );
}

/// 全部 / 未分类视图的头部：h2 + 说明，右端复制引用、导出（范围是整个视图）
function ViewHeader({
  title,
  description,
  actions,
}: {
  title: string;
  description: string;
  actions: ViewActions;
}) {
  return (
    <div className="flex items-start justify-between gap-4">
      <div className="min-w-0">
        <h2 className="font-serif text-2xl font-medium text-fg-1">{title}</h2>
        <p className="mt-1 text-sm text-fg-3">{description}</p>
      </div>
      <div className="flex shrink-0 gap-2">
        <button
          type="button"
          className={cn(btnSecondary, "px-3.5 py-1.5 text-sm")}
          onClick={actions.cite}
        >
          复制引用
        </button>
        <button
          type="button"
          className={cn(btnSecondary, "px-3.5 py-1.5 text-sm")}
          onClick={actions.exportAll}
        >
          导出
        </button>
      </div>
    </div>
  );
}

interface EntryRowProps {
  entry: LibraryEntry;
  select?: ReactNode;
  collections: Collection[];
  currentCollection: Collection | null;
  editingNote: boolean;
  onEditNote: () => void;
  onNoteDone: () => void;
  onNoteSaved: Parameters<typeof LibraryItem>[0]["onNoteSaved"];
  onAddToCollections: (anchor: HTMLElement) => void;
  onCite: () => void;
  onExport: () => void;
  onRemoveFromCollection: () => void;
  onRemove: () => void;
}

/// 一条条目 + 它的菜单（菜单按钮是「放进收藏夹…」浮层的锚点）
function EntryRow(props: EntryRowProps) {
  const trigger = useRef<HTMLButtonElement>(null);
  const { entry } = props;
  return (
    <LibraryItem
      entry={entry}
      select={props.select}
      collections={props.collections}
      currentCollectionId={props.currentCollection?.collectionId ?? null}
      editingNote={props.editingNote}
      onNoteSaved={props.onNoteSaved}
      onNoteDone={props.onNoteDone}
      menu={
        <LibraryItemMenu
          triggerRef={trigger}
          unavailable={entry.paper === null}
          currentCollectionName={props.currentCollection?.name ?? null}
          onAddToCollections={() => trigger.current && props.onAddToCollections(trigger.current)}
          onEditNote={props.onEditNote}
          onCite={props.onCite}
          onExport={props.onExport}
          onRemoveFromCollection={props.onRemoveFromCollection}
          onRemove={props.onRemove}
        />
      }
    />
  );
}
