"use client";

import { useQueryClient } from "@tanstack/react-query";
import { Globe, Loader2, Lock } from "lucide-react";
import { type FormEvent, useEffect, useId, useMemo, useRef, useState } from "react";
import { FIELD_BOX, FIELD_INPUT } from "@/components/auth/FormField";
import { graphemeCount } from "@/lib/grapheme";
import {
  addToCollection,
  createCollection,
  removeFromCollection,
} from "@/lib/interaction/collection-actions";
import type { AddToCollectionResult, Collection } from "@/lib/interaction/schema";
import { invalidateLibrary } from "@/lib/library/cache";
import { LIMITS, NAME_ERRORS } from "@/lib/library/messages";
import { useLibraryOverview } from "@/lib/library/queries";
import { useLoginRedirect } from "@/lib/library/use-login-redirect";
import { orUnavailable } from "@/lib/or-unavailable";
import { btnPrimary, btnText, CHECK_INPUT, CHECK_MARK } from "@/lib/portal-ui";
import { cn } from "@/lib/utils";

/// 收藏夹 ≥ 9 个时出现本地筛选框（简报 §3.2）
const FILTER_FROM = 9;

interface CollectionChecklistProps {
  /// 要放进 / 移出的条目：收藏浮层是一条，批量栏是选中的几条
  itemIds: string[];
  /// 这个收藏夹里已有这批条目中的几条：等于 `itemIds.length` 为全在，0 为都不在，其余为部分在（半选）
  memberOf: (collectionId: string) => number;
  /// 放进（`added=true`，带服务端结果）或移出成功之后
  onChanged: (collectionId: string, added: boolean, result?: AddToCollectionResult) => void;
}

/// 一行的本地状态：进行中（乐观值）或出错
type RowState = { pending: true; target: boolean } | { pending: false; error: string };

/// 放进收藏夹的勾选列表（简报 §3.2 第 2、3 条）：勾上 / 取消立即生效，乐观更新、行内转圈、失败回滚并就地报错；
/// 列表下方行内新建（新建的一律私有，建好后排第一行并自动放进去）。列表顺序在第一次拿到数据时定下：
/// 之后概览重取（收藏夹的更新时间变了）不重新排，免得行在鼠标下面跳。
export function CollectionChecklist({ itemIds, memberOf, onChanged }: CollectionChecklistProps) {
  const client = useQueryClient();
  const overview = useLibraryOverview();
  const redirectToLogin = useLoginRedirect();
  const [rows, setRows] = useState<Record<string, RowState>>({});
  const [created, setCreated] = useState<Collection[]>([]);
  const [joined, setJoined] = useState<Record<string, boolean>>({});
  const [order, setOrder] = useState<string[] | null>(null);
  const [filter, setFilter] = useState("");

  const fetched = overview.data?.collections;
  useEffect(() => {
    if (fetched && order === null) {
      setOrder(fetched.map((collection) => collection.collectionId));
    }
  }, [fetched, order]);

  const collections = useMemo(() => {
    const byId = new Map((fetched ?? []).map((c) => [c.collectionId, c]));
    for (const c of created) {
      byId.set(c.collectionId, byId.get(c.collectionId) ?? c);
    }
    const ids = [
      ...created.map((c) => c.collectionId),
      ...(order ?? []).filter((id) => !created.some((c) => c.collectionId === id)),
      ...[...byId.keys()].filter(
        (id) => !(order ?? []).includes(id) && !created.some((c) => c.collectionId === id),
      ),
    ];
    return ids.flatMap((id) => {
      const collection = byId.get(id);
      return collection ? [collection] : [];
    });
  }, [fetched, created, order]);

  if (overview.isPending) {
    return (
      <div aria-hidden="true" className="flex flex-col gap-2 py-1">
        <div className="shimmer h-5 w-2/3 rounded-sm" />
        <div className="shimmer h-5 w-1/2 rounded-sm" />
      </div>
    );
  }
  if (overview.isError && overview.data === undefined) {
    return <p className="text-sm text-fg-3">收藏夹暂时打不开</p>;
  }

  const total = itemIds.length;
  const count = (id: string) => (joined[id] === undefined ? memberOf(id) : joined[id] ? total : 0);

  async function toggle(collection: Collection) {
    const id = collection.collectionId;
    const current = count(id);
    const target = current < total;
    setRows((prev) => ({ ...prev, [id]: { pending: true, target } }));
    if (target) {
      const reply = await orUnavailable(addToCollection(id, itemIds));
      if (reply.ok && !(total === 1 && reply.result.skippedFull > 0)) {
        settle(id, true);
        onChanged(id, true, reply.result);
        return;
      }
      fail(id, reply, "这个收藏夹已满 5,000 篇");
    } else {
      const reply = await orUnavailable(removeFromCollection(id, itemIds));
      if (reply.ok) {
        settle(id, false);
        onChanged(id, false);
        return;
      }
      fail(id, reply, null);
    }
  }

  function settle(id: string, added: boolean) {
    setJoined((prev) => ({ ...prev, [id]: added }));
    setRows(({ [id]: _, ...rest }) => rest);
    void invalidateLibrary(client);
  }

  function fail(id: string, reply: { ok: boolean; reason?: string }, fullText: string | null) {
    if (!reply.ok && reply.reason === "signed-out") {
      redirectToLogin();
      return;
    }
    const error = reply.ok ? (fullText ?? "没有保存成功") : "没有保存成功";
    setRows((prev) => ({ ...prev, [id]: { pending: false, error } }));
  }

  const visible = filter.trim()
    ? collections.filter((c) => c.name.toLowerCase().includes(filter.trim().toLowerCase()))
    : collections;

  return (
    <div className="flex flex-col gap-2">
      {collections.length >= FILTER_FROM && (
        <input
          type="search"
          aria-label="筛选收藏夹"
          placeholder="筛选收藏夹"
          value={filter}
          onChange={(event) => setFilter(event.target.value)}
          className="h-9 rounded-md border border-border-default bg-bg-elevated px-3 text-sm outline-none focus:border-border-focus"
        />
      )}
      {collections.length === 0 ? (
        <p className="text-sm text-fg-3">还没有收藏夹，新建一个来整理文献。</p>
      ) : visible.length === 0 ? (
        <p className="text-sm text-fg-3">没有匹配的收藏夹</p>
      ) : (
        <ul className="-mx-1 flex max-h-54 flex-col overflow-y-auto">
          {visible.map((collection) => {
            const id = collection.collectionId;
            const row = rows[id];
            const n = row?.pending ? (row.target ? total : 0) : count(id);
            const full = collection.itemCount >= LIMITS.collectionItems && n === 0;
            return (
              <ChecklistRow
                key={id}
                collection={collection}
                checked={n === total}
                indeterminate={n > 0 && n < total}
                disabled={full || row?.pending === true}
                full={full}
                pending={row?.pending === true}
                error={row && !row.pending ? row.error : null}
                onToggle={() => toggle(collection)}
              />
            );
          })}
        </ul>
      )}
      <NewCollectionRow
        disabled={collections.length >= LIMITS.collections}
        onCreated={(collection) => {
          setCreated((prev) => [collection, ...prev]);
          void toggleNew(collection);
        }}
      />
    </div>
  );

  async function toggleNew(collection: Collection) {
    const id = collection.collectionId;
    setRows((prev) => ({ ...prev, [id]: { pending: true, target: true } }));
    const reply = await orUnavailable(addToCollection(id, itemIds));
    if (reply.ok) {
      settle(id, true);
      onChanged(id, true, reply.result);
    } else {
      fail(id, reply, null);
    }
  }
}

interface ChecklistRowProps {
  collection: Collection;
  checked: boolean;
  indeterminate: boolean;
  disabled: boolean;
  full: boolean;
  pending: boolean;
  error: string | null;
  onToggle: () => void;
}

function ChecklistRow({
  collection,
  checked,
  indeterminate,
  disabled,
  full,
  pending,
  error,
  onToggle,
}: ChecklistRowProps) {
  const box = useRef<HTMLInputElement>(null);
  const errorId = useId();
  useEffect(() => {
    if (box.current) {
      box.current.indeterminate = indeterminate;
    }
  }, [indeterminate]);
  const visibility = collection.visibility === "PUBLIC" ? "公开" : "私有";
  const Icon = collection.visibility === "PUBLIC" ? Globe : Lock;
  return (
    <li className="flex flex-col">
      <label
        className={cn(
          "flex min-h-9 items-center gap-2.5 rounded-sm px-1 text-sm text-fg-1",
          !disabled && "cursor-pointer hover:bg-paper-200",
        )}
      >
        <span className="relative inline-flex">
          <input
            ref={box}
            type="checkbox"
            className={cn(
              CHECK_INPUT,
              "indeterminate:border-action-primary indeterminate:bg-action-primary",
            )}
            checked={checked}
            disabled={disabled}
            aria-label={`${collection.name} ${visibility}`}
            aria-describedby={error ? errorId : undefined}
            onChange={onToggle}
          />
          {indeterminate ? (
            <svg
              aria-hidden="true"
              viewBox="0 0 16 16"
              className="pointer-events-none absolute inset-0 size-4 fill-none stroke-paper-50 [stroke-linecap:round] [stroke-width:2]"
            >
              <path d="M4.5 8h7" />
            </svg>
          ) : (
            <svg aria-hidden="true" viewBox="0 0 16 16" className={cn(CHECK_MARK, "left-0")}>
              <path d="M3.6 8.4l2.9 2.9 5.9-6.6" pathLength={1} />
            </svg>
          )}
        </span>
        <span title={collection.name} className="min-w-0 flex-1 truncate">
          {collection.name}
          {full && <span className="ml-1.5 text-xs text-fg-3">已满</span>}
        </span>
        {pending && <Loader2 className="size-3 animate-spin text-fg-3" aria-hidden="true" />}
        <Icon className="size-3.5 shrink-0 text-fg-3" aria-hidden="true" />
      </label>
      {error && (
        <p id={errorId} className="pl-7.5 text-xs text-status-danger">
          {error}
        </p>
      )}
    </li>
  );
}

/// 列表下方的行内新建：点开变输入框（自动聚焦），回车或「创建」提交，`Esc` 收起
function NewCollectionRow({
  disabled,
  onCreated,
}: {
  disabled: boolean;
  onCreated: (collection: Collection) => void;
}) {
  const [open, setOpen] = useState(false);
  const [name, setName] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);
  const field = useRef<HTMLInputElement>(null);
  const errorId = useId();
  const redirectToLogin = useLoginRedirect();

  useEffect(() => {
    if (open) {
      field.current?.focus();
    }
  }, [open]);

  if (!open) {
    return (
      <div className="flex items-center gap-2">
        <button
          type="button"
          aria-disabled={disabled || undefined}
          title={disabled ? "最多 200 个收藏夹" : undefined}
          onClick={() => !disabled && setOpen(true)}
          className={cn(btnText, "self-start text-sm")}
        >
          + 新建收藏夹
        </button>
        {disabled && <span className="text-xs text-fg-3">最多 200 个收藏夹</span>}
      </div>
    );
  }

  async function submit(event: FormEvent) {
    event.preventDefault();
    const trimmed = name.trim();
    if (trimmed === "") {
      setError(NAME_ERRORS.required);
      return;
    }
    if (graphemeCount(trimmed) > LIMITS.name) {
      setError(NAME_ERRORS.tooLong);
      return;
    }
    setSaving(true);
    setError(null);
    const result = await orUnavailable(createCollection({ name: trimmed, description: "" }));
    setSaving(false);
    if (result.ok) {
      onCreated(result.collection);
      setName("");
      setOpen(false);
      return;
    }
    if (result.reason === "signed-out") {
      redirectToLogin();
      return;
    }
    setError(
      result.reason === "name-taken"
        ? NAME_ERRORS.taken
        : result.reason === "collection-limit"
          ? "最多 200 个收藏夹"
          : result.reason === "invalid-fields"
            ? NAME_ERRORS.tooLong
            : "没有创建成功，请稍后再试",
    );
  }

  return (
    <form onSubmit={submit} className="flex flex-col gap-1.5">
      <div className="flex items-center gap-2">
        <div className={cn(FIELD_BOX, "h-9 flex-1 rounded-md px-3")}>
          <input
            ref={field}
            aria-label="收藏夹名称"
            placeholder="收藏夹名称"
            aria-invalid={error ? true : undefined}
            aria-describedby={error ? errorId : undefined}
            value={name}
            readOnly={saving}
            onChange={(event) => setName(event.target.value)}
            onKeyDown={(event) => {
              if (event.key === "Escape") {
                event.stopPropagation();
                setOpen(false);
                setError(null);
              }
            }}
            className={cn(FIELD_INPUT, "text-sm")}
          />
        </div>
        <button
          type="submit"
          aria-disabled={saving || undefined}
          className={cn(btnPrimary, "px-3.5 py-1.5 text-sm")}
        >
          创建
        </button>
      </div>
      {error && (
        <p id={errorId} className="text-xs text-status-danger">
          {error}
        </p>
      )}
    </form>
  );
}
