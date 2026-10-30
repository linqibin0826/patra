"use client";

import { Globe, Lock, NotebookPen } from "lucide-react";
import { useState } from "react";
import { NoteEditor } from "@/components/library/NoteEditor";
import { PaperStats } from "@/components/library/PaperStats";
import { UnavailableEntry } from "@/components/library/UnavailableEntry";
import { Pill } from "@/components/portal/Pill";
import { PaperListItem } from "@/components/portal/papers/PaperListItem";
import { formatMonthDay } from "@/lib/format-time";
import { graphemeCount } from "@/lib/grapheme";
import type { LibraryEntry } from "@/lib/interaction/library";
import type { Collection, LibraryItem as Item } from "@/lib/interaction/schema";
import { btnText } from "@/lib/portal-ui";
import { cn } from "@/lib/utils";

interface LibraryItemProps {
  entry: LibraryEntry;
  /// 本人全部收藏夹（归属胶囊要名称与可见性）
  collections: Collection[];
  /// 当前收藏夹视图的收藏夹 ID；全部 / 未分类为 null
  currentCollectionId: string | null;
  /// 标题行右端：条目菜单
  menu: React.ReactNode;
  /// 选择模式下的复选框（标题前）
  select?: React.ReactNode;
  /// 正在编辑备注（条目菜单「编辑备注」）
  editingNote: boolean;
  onNoteSaved: (item: Item) => void;
  onNoteDone: () => void;
}

/// 备注超过这些字或这些行时显示「展开」
const NOTE_FOLD_GRAPHEMES = 90;
const NOTE_FOLD_LINES = 3;

/// 文献库条目（简报 §3.10）：`PaperListItem` 的结构（不显示摘要与证据徽章，日期按精度）+ 收藏人数与讨论数 + 归属行
/// （所在收藏夹胶囊 · 收藏 / 加入时间）+ 备注。已失效的用快照标题，不是链接。
export function LibraryItem({
  entry,
  collections,
  currentCollectionId,
  menu,
  select,
  editingNote,
  onNoteSaved,
  onNoteDone,
}: LibraryItemProps) {
  const { item, paper, stats } = entry;
  const extra = (
    <>
      <Membership item={item} collections={collections} currentCollectionId={currentCollectionId} />
      {editingNote ? (
        <NoteEditor
          itemId={item.itemId}
          note={item.note}
          startEditing
          onSaved={onNoteSaved}
          onDone={onNoteDone}
        />
      ) : (
        item.note && <NoteBlock note={item.note} />
      )}
    </>
  );
  const body = paper ? (
    <PaperListItem
      paper={paper}
      action={menu}
      detailedDate
      summary={false}
      stats={<PaperStats paperKey={paper.id} stats={stats} />}
    >
      {extra}
    </PaperListItem>
  ) : (
    <UnavailableEntry title={item.title} action={menu}>
      {extra}
    </UnavailableEntry>
  );
  return select ? (
    <div className="grid grid-cols-[20px_minmax(0,1fr)] items-start gap-3">
      <div className="pt-7">{select}</div>
      {body}
    </div>
  ) : (
    body
  );
}

/// 归属行：所在收藏夹的胶囊（当前收藏夹视图里不显示当前夹）· 收藏时间 / 加入该夹的时间
function Membership({
  item,
  collections,
  currentCollectionId,
}: {
  item: Item;
  collections: Collection[];
  currentCollectionId: string | null;
}) {
  const shown = item.collectionIds.flatMap((id) => {
    const collection =
      id === currentCollectionId ? undefined : collections.find((c) => c.collectionId === id);
    return collection ? [collection] : [];
  });
  const when =
    currentCollectionId && item.addedAt
      ? `${formatMonthDay(item.addedAt)}加入`
      : `${formatMonthDay(item.collectedAt)}收藏`;
  return (
    <div className="flex flex-wrap items-center gap-1.5 text-xs">
      {shown.map((collection) => {
        const Icon = collection.visibility === "PUBLIC" ? Globe : Lock;
        return (
          <Pill
            key={collection.collectionId}
            size="sm"
            href={`/library/${collection.collectionId}`}
            title={collection.name}
            className="max-w-48"
          >
            <Icon className="size-3 shrink-0" aria-hidden="true" />
            <span className="truncate">{collection.name}</span>
          </Pill>
        );
      })}
      {shown.length > 0 && (
        <span aria-hidden="true" className="text-ink-300">
          ·
        </span>
      )}
      <span className="text-fg-3">{when}</span>
    </div>
  );
}

/// 备注：左侧 2px 陶土细线，最多 3 行，长了给「展开」
function NoteBlock({ note }: { note: string }) {
  const [open, setOpen] = useState(false);
  const long =
    graphemeCount(note) > NOTE_FOLD_GRAPHEMES || note.split("\n").length > NOTE_FOLD_LINES;
  return (
    <div className="flex max-w-[72ch] gap-2 border-l-2 border-clay-200 pl-3">
      <NotebookPen className="mt-1 size-3 shrink-0 text-fg-3" aria-hidden="true" />
      <div className="flex min-w-0 flex-col items-start gap-1">
        <p
          className={cn("text-sm leading-5 whitespace-pre-line text-fg-2", !open && "line-clamp-3")}
        >
          {note}
        </p>
        {long && !open && (
          <button type="button" className={cn(btnText, "text-sm")} onClick={() => setOpen(true)}>
            展开
          </button>
        )}
      </div>
    </div>
  );
}
