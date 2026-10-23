"use client";

import { useRouter } from "next/navigation";
import { useEffect, useState } from "react";
import { Dialog } from "@/components/ui/dialog";
import { btnPrimary, btnSecondary } from "@/lib/portal-ui";

/// 这次点击要不要拦：左键、无修饰键、同窗口打开的站内链接，且去的不是当前页（本页锚点不拦）
function interceptedHref(event: MouseEvent): string | null {
  if (event.defaultPrevented || event.button !== 0) {
    return null;
  }
  if (event.metaKey || event.ctrlKey || event.shiftKey || event.altKey) {
    return null;
  }
  const anchor = (event.target as Element | null)?.closest?.("a[href]");
  if (!(anchor instanceof HTMLAnchorElement) || (anchor.target && anchor.target !== "_self")) {
    return null;
  }
  if (anchor.hasAttribute("download")) {
    return null;
  }
  const url = new URL(anchor.href, window.location.href);
  if (url.origin !== window.location.origin || url.pathname === window.location.pathname) {
    return null;
  }
  return `${url.pathname}${url.search}${url.hash}`;
}

/// 有未保存的修改时离开前提醒（简报 §3.6）。App Router 没有拦截路由的接口，这里做两件事：
/// 1. `beforeunload`：关标签页、刷新、在地址栏输入时用浏览器原生提醒。
/// 2. 在捕获阶段拦站内链接的点击（分区导航、导航栏、页脚），弹确认框，选「离开」再跳。
/// 浏览器的后退 / 前进不拦（`popstate` 无法取消，spec §9.3）。
export function LeaveGuard({ when }: { when: boolean }) {
  const router = useRouter();
  const [pending, setPending] = useState<string | null>(null);

  useEffect(() => {
    if (!when) {
      return;
    }
    const onBeforeUnload = (event: BeforeUnloadEvent) => {
      event.preventDefault();
    };
    const onClick = (event: MouseEvent) => {
      const href = interceptedHref(event);
      if (href !== null) {
        event.preventDefault();
        event.stopPropagation();
        setPending(href);
      }
    };
    window.addEventListener("beforeunload", onBeforeUnload);
    document.addEventListener("click", onClick, true);
    return () => {
      window.removeEventListener("beforeunload", onBeforeUnload);
      document.removeEventListener("click", onClick, true);
    };
  }, [when]);

  return (
    <Dialog
      open={pending !== null}
      onOpenChange={(open) => {
        if (!open) setPending(null);
      }}
      title="有未保存的修改"
      description="确定离开吗？离开后这次的修改不会保存。"
      size="sm"
    >
      <div className="flex justify-end gap-3 max-[880px]:flex-col-reverse">
        <button type="button" className={btnSecondary} onClick={() => setPending(null)}>
          继续编辑
        </button>
        <button
          type="button"
          className={btnPrimary}
          onClick={() => {
            if (pending !== null) {
              router.push(pending);
            }
            setPending(null);
          }}
        >
          离开
        </button>
      </div>
    </Dialog>
  );
}
