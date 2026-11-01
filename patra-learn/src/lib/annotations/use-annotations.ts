// patra-learn/src/lib/annotations/use-annotations.ts
// 页面读写批注的钩子：做法同 use-progress（useSyncExternalStore 订阅 localStorage）。服务端与水合时是空列表，
// 其他标签页改了批注经 storage 事件同步；写失败时退到内存，本页照常生效
"use client";

import { useCallback, useSyncExternalStore } from "react";
import {
  ANNOTATIONS_KEY,
  type Annotation,
  type AnnotationKind,
  type AnnotationStorage,
  type Annotations,
  parseAnnotations,
  saveAnnotations,
  withAdded,
  withAnchorResult,
  withEdited,
  withExported,
  withRemoved,
} from "./annotations";

const EMPTY: Annotations = [];
const listeners = new Set<() => void>();
/// 存储写不进去时，本页的批注原文放在这里（undefined 表示没在用内存）
let memory: string | undefined;
let cachedRaw: string | null | undefined;
let cachedValue: Annotations = EMPTY;

function storage(): AnnotationStorage | undefined {
  try {
    return window.localStorage;
  } catch {
    return undefined;
  }
}

function readRaw(): string | null {
  if (memory !== undefined) return memory;
  try {
    return storage()?.getItem(ANNOTATIONS_KEY) ?? null;
  } catch {
    return null;
  }
}

/// 同样的原文返回同一个列表，useSyncExternalStore 要求快照稳定
function getSnapshot(): Annotations {
  const raw = readRaw();
  if (raw !== cachedRaw) {
    cachedRaw = raw;
    cachedValue = parseAnnotations(raw);
  }
  return cachedValue;
}

const getServerSnapshot = () => EMPTY;

function subscribe(listener: () => void) {
  listeners.add(listener);
  const onStorage = (event: StorageEvent) => {
    if (event.key === ANNOTATIONS_KEY || event.key === null) listener();
  };
  window.addEventListener("storage", onStorage);
  return () => {
    listeners.delete(listener);
    window.removeEventListener("storage", onStorage);
  };
}

/// 列表没变（同一个对象）时不写
function write(next: Annotations) {
  if (next === getSnapshot()) return;
  memory = saveAnnotations(storage(), next) ? undefined : JSON.stringify(next);
  for (const listener of listeners) listener();
}

function newId() {
  return typeof crypto !== "undefined" && "randomUUID" in crypto
    ? crypto.randomUUID()
    : `${Date.now().toString(36)}-${Math.random().toString(36).slice(2, 10)}`;
}

export function useAnnotations() {
  const annotations = useSyncExternalStore(subscribe, getSnapshot, getServerSnapshot);
  const add = useCallback((draft: Omit<Annotation, "id" | "createdAt">) => {
    write(withAdded(getSnapshot(), { ...draft, id: newId(), createdAt: Date.now() }));
  }, []);
  const edit = useCallback((id: string, change: { kind: AnnotationKind; text: string }) => {
    write(withEdited(getSnapshot(), id, change, Date.now()));
  }, []);
  const remove = useCallback((id: string) => {
    write(withRemoved(getSnapshot(), id));
  }, []);
  const applyAnchorResult = useCallback((slug: string, found: ReadonlySet<string>) => {
    write(withAnchorResult(getSnapshot(), slug, found));
  }, []);
  const markExported = useCallback((ids: readonly string[]) => {
    write(withExported(getSnapshot(), ids, Date.now()));
  }, []);
  return { annotations, add, edit, remove, applyAnchorResult, markExported };
}
