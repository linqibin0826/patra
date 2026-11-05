"use client";

import { createContext, type ReactNode, useContext, useMemo, useRef, useState } from "react";
import { newId } from "@/lib/analytics/ids";
import type { ListType } from "@/lib/analytics/types";

/// 一个列表实例：类型、ID、已曝光过的文献（同一实例里同一篇只算一次）
export interface TrackedListValue {
  listType: ListType;
  listId: string;
  impressed: Set<string>;
}

const TrackedListContext = createContext<TrackedListValue | null>(null);

/// 包住的卡片所在的列表实例；不在任何列表里为 null
export function useTrackedList(): TrackedListValue | null {
  return useContext(TrackedListContext);
}

/// 一个列表实例（埋点 spec §5.2）：给里面的 `TrackedArticle` 提供 `list_type`、`list_id` 与去重集合。`listId` 不传时
/// 首次挂载生成一个（检索结果传 `search_id`），重渲染不变
export function TrackedList({
  listType,
  listId,
  children,
}: {
  listType: ListType;
  listId?: string;
  children: ReactNode;
}) {
  const [generated] = useState(newId);
  const impressed = useRef(new Set<string>());
  const id = listId ?? generated;
  // 父组件重渲染时 value 不变：卡片的观察器与曝光计时依赖它，换新对象会被拆掉重建、计时清零
  const value = useMemo(
    () => ({ listType, listId: id, impressed: impressed.current }),
    [listType, id],
  );
  return <TrackedListContext value={value}>{children}</TrackedListContext>;
}
