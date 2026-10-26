import { useQuery } from "@tanstack/react-query";
import type { ItemState } from "@/lib/interaction/schema";
import type { ExportScope } from "@/lib/interaction/scope";

/// 导出 / 复制引用对话框的范围（简报 §3.5、§3.6）：
/// - `items`：单篇、选中的几篇，条目里已有 key 与状态，不经 interaction
/// - 其余：整个文献库、未分类、一个收藏夹（自己的 / 别人公开的），打开对话框时向 interaction 取 key
export type DialogScope =
  | {
      kind: "items";
      items: { key: string | null; state: ItemState }[];
      label: string;
      name: string;
    }
  | (ExportScope & { label: string; name: string; count: number });

/// 范围里的文献 ID：`ready` 带 key 与跳过的已失效篇数；`too-large` 超出这次能处理的上限
export type ScopeKeys =
  | { kind: "loading" }
  | { kind: "ready"; keys: string[]; skipped: number }
  | { kind: "too-large" }
  | { kind: "error" };

/// 范围的篇数（`items` 为条数，其余为视图 / 收藏夹的篇数）
export function scopeCount(scope: DialogScope): number {
  return scope.kind === "items" ? scope.items.length : scope.count;
}

/// 取范围里的文献 ID。`limit` 是这次的上限（导出 5,000、复制引用 100）：篇数已超出时不请求
export function useScopeKeys(
  scope: DialogScope,
  limit: number,
  enabled: boolean,
): {
  keys: ScopeKeys;
  retry: () => void;
} {
  const whole = scope.kind !== "items";
  const over = scopeCount(scope) > limit;
  const query = useQuery({
    queryKey: ["scope-keys", whole ? scopeParams(scope) : null],
    queryFn: async () => {
      const res = await fetch("/api/library/keys", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ scope: whole ? scopeParams(scope) : null }),
      });
      if (!res.ok) {
        throw new Error(`范围取不到：${res.status}`);
      }
      return (await res.json()) as
        | { keys: string[]; skippedUnavailable: number }
        | { tooLarge: true };
    },
    enabled: enabled && whole && !over,
    retry: false,
    staleTime: 0,
    gcTime: 0,
  });
  const retry = () => void query.refetch();
  if (over) {
    return { keys: { kind: "too-large" }, retry };
  }
  if (scope.kind === "items") {
    const keys = scope.items.flatMap((item) =>
      item.state === "ACTIVE" && item.key ? [item.key] : [],
    );
    return { keys: { kind: "ready", keys, skipped: scope.items.length - keys.length }, retry };
  }
  if (query.isError) {
    return { keys: { kind: "error" }, retry };
  }
  if (!query.data) {
    return { keys: { kind: "loading" }, retry };
  }
  if ("tooLarge" in query.data || query.data.keys.length > limit) {
    return { keys: { kind: "too-large" }, retry };
  }
  return {
    keys: { kind: "ready", keys: query.data.keys, skipped: query.data.skippedUnavailable },
    retry,
  };
}

/// 交给 `/api/library/keys` 的范围（去掉对话框自己用的字段）
function scopeParams(scope: Exclude<DialogScope, { kind: "items" }>): ExportScope {
  switch (scope.kind) {
    case "library":
      return { kind: "library", view: scope.view, sort: scope.sort };
    case "collection":
      return { kind: "collection", id: scope.id, sort: scope.sort };
    case "public-collection":
      return { kind: "public-collection", id: scope.id, sort: scope.sort };
  }
}

/// `1,284`
export function formatCount(n: number): string {
  return n.toLocaleString("en-US");
}
