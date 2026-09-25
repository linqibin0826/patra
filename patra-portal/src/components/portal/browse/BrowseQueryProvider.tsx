"use client";

import { useRouter } from "next/navigation";
import {
  createContext,
  type ReactNode,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useRef,
  useState,
  useTransition,
} from "react";

/** 浏览页查询的最小约束：都有 1-based 页码。 */
export interface BrowseQueryBase {
  page: number;
}

export interface NavigateOptions {
  /** 用 router.replace（不产生浏览历史），默认 push */
  replace?: boolean;
}

export interface BrowseQueryContextValue<Q extends BrowseQueryBase> {
  /** 乐观查询：导航进行中为用户最新操作后的状态，完成后等于服务端下发的查询 */
  query: Q;
  /** 是否有导航在进行（结果区变淡、分页禁用） */
  isPending: boolean;
  /** 查询 → 站内链接（basePath + querystring） */
  hrefFor: (query: Q) => string;
  /** 跳转到下一个查询；函数形式基于当前乐观值计算，连点可叠加 */
  navigate: (next: Q | ((current: Q) => Q), options?: NavigateOptions) => void;
}

export const BrowseQueryContext = createContext<BrowseQueryContextValue<BrowseQueryBase> | null>(
  null,
);

interface ProviderProps<Q extends BrowseQueryBase> {
  query: Q;
  basePath: string;
  serialize: (query: Q) => string;
  children: ReactNode;
}

function isUpdater<Q>(next: Q | ((current: Q) => Q)): next is (current: Q) => Q {
  return typeof next === "function";
}

/**
 * 浏览页地址栏导航 Provider：地址栏是唯一状态来源，本组件只补"即时反馈"。
 * - 乐观值：navigate 后立即生效；服务端查询一变（导航完成 / 后退 / 前进）即清空，
 *   所以 A → B → 后退 A 不会复活 B。
 * - 最新值引用：navigate 引用稳定，函数形式总基于"此刻最新"的查询计算——快速连点、
 *   防抖定时器等持有旧 navigate 引用的延迟回调都不会覆盖别的操作。
 */
export function BrowseQueryProvider<Q extends BrowseQueryBase>({
  query,
  basePath,
  serialize,
  children,
}: ProviderProps<Q>) {
  const router = useRouter();
  const [isPending, startTransition] = useTransition();
  const serverKey = serialize(query);
  const [optimistic, setOptimistic] = useState<Q | null>(null);
  const [seenServerKey, setSeenServerKey] = useState(serverKey);
  // 服务端查询变化：渲染期清空乐观值（派生状态写法，本次渲染即用服务端查询）
  if (serverKey !== seenServerKey) {
    setSeenServerKey(serverKey);
    setOptimistic(null);
  }
  const current = optimistic !== null && serverKey === seenServerKey ? optimistic : query;

  const latestRef = useRef(current);
  useEffect(() => {
    latestRef.current = current;
  }, [current]);

  const hrefFor = useCallback(
    (q: Q) => {
      const qs = serialize(q);
      return qs ? `${basePath}?${qs}` : basePath;
    },
    [basePath, serialize],
  );

  const navigate = useCallback(
    (next: Q | ((c: Q) => Q), options?: NavigateOptions) => {
      const target = isUpdater(next) ? next(latestRef.current) : next;
      // 同一 tick 内的连续调用也要看到本次结果
      latestRef.current = target;
      setOptimistic(target);
      const href = hrefFor(target);
      startTransition(() => {
        if (options?.replace) {
          router.replace(href);
        } else {
          router.push(href);
        }
      });
    },
    [hrefFor, router],
  );

  const value = useMemo(
    () => ({ query: current, isPending, hrefFor, navigate }),
    [current, isPending, hrefFor, navigate],
  );

  return (
    <BrowseQueryContext.Provider
      value={value as unknown as BrowseQueryContextValue<BrowseQueryBase>}
    >
      {children}
    </BrowseQueryContext.Provider>
  );
}

/** 读取浏览页查询上下文；必须在 BrowseQueryProvider（或其页面包装）内使用。 */
export function useBrowseQuery<Q extends BrowseQueryBase>(): BrowseQueryContextValue<Q> {
  const ctx = useContext(BrowseQueryContext);
  if (ctx === null) {
    throw new Error("useBrowseQuery 必须在 BrowseQueryProvider 内使用");
  }
  return ctx as unknown as BrowseQueryContextValue<Q>;
}
