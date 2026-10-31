// patra-learn/src/lib/progress/use-progress.ts
// 页面读写进度的钩子：useSyncExternalStore 订阅 localStorage。服务端与水合时是空进度（ready 为 false），
// 页面因此先渲染「新读者」的样子；其他标签页改了进度经 storage 事件同步。写失败时退到内存，本页照常生效
"use client";

import { useCallback, useSyncExternalStore } from "react";
import {
  clearProgress,
  type Level,
  PROGRESS_KEY,
  type Progress,
  type ProgressStorage,
  parseProgress,
  saveProgress,
  withMastery,
  withVisit,
} from "./progress";

const EMPTY: Progress = {};
const listeners = new Set<() => void>();
/// 存储写不进去时，本页的进度原文放在这里（undefined 表示没在用内存）
let memory: string | null | undefined;
let cachedRaw: string | null | undefined;
let cachedValue: Progress = EMPTY;

function storage(): ProgressStorage | undefined {
  try {
    return window.localStorage;
  } catch {
    return undefined;
  }
}

function readRaw(): string | null {
  if (memory !== undefined) return memory;
  try {
    return storage()?.getItem(PROGRESS_KEY) ?? null;
  } catch {
    return null;
  }
}

/// 同样的原文返回同一个对象，useSyncExternalStore 要求快照稳定
function getSnapshot(): Progress {
  const raw = readRaw();
  if (raw !== cachedRaw) {
    cachedRaw = raw;
    cachedValue = parseProgress(raw);
  }
  return cachedValue;
}

const getServerSnapshot = () => EMPTY;

function notify() {
  for (const listener of listeners) listener();
}

function subscribe(listener: () => void) {
  listeners.add(listener);
  const onStorage = (event: StorageEvent) => {
    if (event.key === PROGRESS_KEY || event.key === null) listener();
  };
  window.addEventListener("storage", onStorage);
  return () => {
    listeners.delete(listener);
    window.removeEventListener("storage", onStorage);
  };
}

function write(next: Progress) {
  memory = saveProgress(storage(), next) ? undefined : JSON.stringify(next);
  notify();
}

const noop = () => () => {};

/// 水合完成后为 true；服务端与水合那一遍为 false
export function useHydrated() {
  return useSyncExternalStore(
    noop,
    () => true,
    () => false,
  );
}

export function useProgress() {
  const progress = useSyncExternalStore(subscribe, getSnapshot, getServerSnapshot);
  const ready = useHydrated();
  const setMastery = useCallback((slug: string, level: Level | 0) => {
    write(withMastery(getSnapshot(), slug, level, Date.now()));
  }, []);
  const visit = useCallback((slug: string) => {
    write(withVisit(getSnapshot(), slug, Date.now()));
  }, []);
  const clear = useCallback(() => {
    memory = clearProgress(storage()) ? undefined : null;
    notify();
  }, []);
  return { progress, ready, setMastery, visit, clear };
}
