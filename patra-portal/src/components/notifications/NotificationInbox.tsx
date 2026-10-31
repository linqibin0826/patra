"use client";

import { useQuery } from "@tanstack/react-query";
import Link from "next/link";
import { usePathname, useRouter } from "next/navigation";
import { useEffect } from "react";
import { FormAlert } from "@/components/auth/FormAlert";
import { InteractionUnavailable } from "@/components/library/InteractionUnavailable";
import { NotificationEmpty } from "@/components/notifications/NotificationEmpty";
import { NotificationRow } from "@/components/notifications/NotificationRow";
import { NotificationSkeleton } from "@/components/notifications/NotificationSkeleton";
import { UnreadBadge } from "@/components/notifications/UnreadBadge";
import { fetchCurrentUser } from "@/lib/auth/current-user-query";
import { authPageHref } from "@/lib/auth/next-path";
import { useNow } from "@/lib/discussion/use-now";
import type { NotificationTab } from "@/lib/notification/inbox-schema";
import { flattenPages, groupByDay } from "@/lib/notifications/groups";
import { useMarkAllRead, useMarkRead } from "@/lib/notifications/mark-read";
import { useNotificationList, useUnreadCounts } from "@/lib/notifications/queries";
import { btnSecondary } from "@/lib/portal-ui";
import { queryKeys } from "@/lib/query-keys";
import { cn } from "@/lib/utils";

/// 标签样式同主页标签栏（`ProfileTabs`）
const TAB =
  "-mb-px flex h-11 items-center justify-center border-b-2 border-transparent px-1 text-base text-fg-2 transition-colors hover:text-fg-1 aria-[current=page]:border-clay-500 aria-[current=page]:font-medium aria-[current=page]:text-fg-1 max-[880px]:flex-1";

const TABS = [
  { tab: "replies", label: "回复我的", href: "/notifications" },
  { tab: "mentions", label: "@ 我的", href: "/notifications/mentions" },
  { tab: "helpful", label: "有帮助", href: "/notifications/helpful" },
] as const;

/// 提醒列表页（简报 §3.19；分组与版式见 spec 2026-10-28-notification-inbox-redesign 4.2；有帮助标签见 v0.16 简报
/// §3.1）：h1 + 「全部标为已读」（三个标签一起）、三个标签（各自的未读胶囊）、按今天 / 昨天 / 更早分组的列表（一页
/// 20 条，保持服务端顺序）与「加载更多提醒」。
/// 列表在客户端取（已读是乐观写、与铃铛共用缓存）；页面壳同步渲染，加载中只有列表区是骨架。会话过期时去登录页
export function NotificationInbox({ tab }: { tab: NotificationTab }) {
  const list = useNotificationList(tab);
  // 只认网关明确说「没有登录」（null）；当前用户一时取不到（出错）不算，不把人踢去登录页
  const signedOut =
    useQuery({ queryKey: queryKeys.currentUser, queryFn: fetchCurrentUser }).data === null;
  const pathname = usePathname();
  const router = useRouter();
  // 会话在页面开着时过期（任一提醒请求 401 让当前用户失效后取回未登录）：去登录页，回来还是这一页
  useEffect(() => {
    if (signedOut) {
      router.replace(authPageHref("/login", pathname));
    }
  }, [signedOut, pathname, router]);
  const { counts } = useUnreadCounts();
  const markRead = useMarkRead();
  const { markAll, pending, failed } = useMarkAllRead();
  const now = useNow(Date.now());
  const items = list.data ? flattenPages(list.data.pages) : [];
  const anyUnread = counts ? counts.replies + counts.mentions + counts.helpful > 0 : false;
  return (
    <div className="mx-auto max-w-page px-gutter pt-10 pb-24 max-[880px]:pt-6">
      <div className="flex max-w-190 flex-col gap-6">
        <div className="flex items-center justify-between gap-4">
          <h1 className="font-serif text-display-3 leading-heading font-medium tracking-tight text-fg-1">
            提醒
          </h1>
          {/* 窄屏缩成文字按钮「全部已读」（简报 §3.19 移动），读屏名称不变 */}
          <button
            type="button"
            aria-label="全部标为已读"
            disabled={!anyUnread || pending}
            onClick={markAll}
            className={cn(
              btnSecondary,
              "h-8 shrink-0 px-3 py-0 text-sm disabled:cursor-not-allowed disabled:opacity-50 max-[880px]:border-transparent max-[880px]:bg-transparent max-[880px]:px-0 max-[880px]:text-link",
            )}
          >
            <span className="max-[880px]:hidden">全部标为已读</span>
            <span className="hidden max-[880px]:inline">全部已读</span>
          </button>
        </div>
        {failed && <FormAlert>没有标记成功，请稍后再试</FormAlert>}
        <nav aria-label="提醒分类">
          <div className="flex gap-6 border-b border-border-default">
            {TABS.map((item) => {
              const unread = counts?.[item.tab] ?? 0;
              return (
                <Link
                  key={item.tab}
                  href={item.href}
                  aria-current={item.tab === tab ? "page" : undefined}
                  className={TAB}
                >
                  {item.label}
                  <UnreadBadge count={unread} className="ml-1.5 ring-0" />
                </Link>
              );
            })}
          </div>
        </nav>
        {list.isPending ? (
          <NotificationSkeleton />
        ) : list.isError && items.length === 0 ? (
          <InteractionUnavailable
            title="提醒暂时打不开"
            body="请稍后再试。"
            onRetry={() => void list.refetch()}
          />
        ) : items.length === 0 ? (
          <NotificationEmpty tab={tab} />
        ) : (
          <div className="flex flex-col gap-4">
            <div className="flex flex-col">
              {groupByDay(items, now).map((group, index) => (
                <section key={group.group} aria-labelledby={`notification-group-${group.group}`}>
                  <h2
                    id={`notification-group-${group.group}`}
                    className={cn(
                      "border-border-subtle border-b pb-2 font-medium text-fg-3 text-xs tracking-wide",
                      index === 0 ? "mt-5" : "mt-7",
                    )}
                  >
                    {group.label}
                  </h2>
                  <ol className="flex flex-col">
                    {group.items.map((notification) => (
                      <li key={notification.notificationId}>
                        <NotificationRow notification={notification} now={now} onOpen={markRead} />
                      </li>
                    ))}
                  </ol>
                </section>
              ))}
            </div>
            {list.hasNextPage ? (
              <button
                type="button"
                disabled={list.isFetchingNextPage}
                onClick={() => void list.fetchNextPage()}
                className={cn(btnSecondary, "self-center")}
              >
                加载更多提醒
              </button>
            ) : (
              <p className="text-xs text-fg-3">提醒保留 5 个月。</p>
            )}
          </div>
        )}
      </div>
    </div>
  );
}
