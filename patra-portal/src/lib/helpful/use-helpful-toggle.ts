import { useQueryClient } from "@tanstack/react-query";
import { useEffect, useRef, useState } from "react";
import { useCurrentUser } from "@/lib/auth/current-user-query";
import { markHelpfulStale, patchHelpful } from "@/lib/helpful/cache";
import { helpfulFailureMessage } from "@/lib/helpful/messages";
import { setHelpful } from "@/lib/interaction/helpful-actions";
import type { HelpfulTarget } from "@/lib/interaction/helpful-schema";
import { useLoginRedirect } from "@/lib/library/use-login-redirect";
import { orUnavailable } from "@/lib/or-unavailable";
import { queryKeys } from "@/lib/query-keys";

/// 行内提示显示多久（简报 v0.15 §3.2）
export const HELPFUL_HINT_MS = 4000;

/// 按钮旁的行内提示：`verify` 是邮箱未验证（琥珀色，带重新发送），其余是失败（锈红色）
export type HelpfulHint = { text: string; tone: "error" | "verify"; gone: boolean };

/// 按钮显示的值
export interface HelpfulValue {
  helpful: boolean;
  count: number;
}

const sameValue = (a: HelpfulValue, b: HelpfulValue) =>
  a.helpful === b.helpful && a.count === b.count;

/// 当前用户缓存里的用户 ID：未登录、还没取回都是 null
function viewerIdOf(data: unknown): string | null {
  return data !== null && typeof data === "object" && "userId" in data
    ? String((data as { userId: unknown }).userId)
    : null;
}

/// 有帮助的点击流程与乐观状态（工程 spec 5.2）
export function useHelpfulToggle({
  target,
  helpful,
  count,
  onGone,
}: {
  target: HelpfulTarget;
  helpful: boolean | null;
  count: number;
  onGone?: () => void;
}): {
  shown: HelpfulValue;
  busy: boolean;
  /// 登录状态已确定（未登录或已登录），点击才会响应
  ready: boolean;
  hint: HelpfulHint | null;
  toggle: () => void;
  /// 指针或焦点停在提示里：暂停计时
  holdHint: (held: boolean) => void;
} {
  const client = useQueryClient();
  const current = useCurrentUser();
  const redirectToLogin = useLoginRedirect();
  const [hint, setHint] = useState<HelpfulHint | null>(null);
  const [held, setHeld] = useState(false);
  /// 请求中的乐观值
  const [pending, setPending] = useState<HelpfulValue | null>(null);
  /// 上一次成功的返回值：props 自点击以来没变过（经验页的 props 来自 RSC、不会变）且还是同一位访客，就显示返回值；
  /// props 一变（缓存已写回、重取）或换了访客，以 props 为准
  const [settled, setSettled] = useState<{
    base: HelpfulValue;
    value: HelpfulValue;
    viewer: string | null;
  } | null>(null);
  /// 同一轮渲染里连点两下也只发一次
  const inFlight = useRef(false);
  const server: HelpfulValue = { helpful: helpful === true, count };
  const viewer = current.status === "authenticated" ? current.user.userId : null;
  // props 变过就丢掉上一次的返回值：只比值的话，props 变了又碰巧回到点击时的值会误显示旧结果
  const [lastServer, setLastServer] = useState(server);
  if (!sameValue(lastServer, server)) {
    setLastServer(server);
    setSettled(null);
  }
  const shown: HelpfulValue =
    pending ??
    (settled && settled.viewer === viewer && sameValue(settled.base, server)
      ? settled.value
      : server);

  /// 父组件每次渲染都可能传新的回调，放进 ref：换回调不重新计时
  const goneHandler = useRef(onGone);
  goneHandler.current = onGone;

  useEffect(() => {
    if (!hint || held) {
      return;
    }
    const timer = setTimeout(() => {
      setHint(null);
      if (hint.gone) {
        goneHandler.current?.();
      }
    }, HELPFUL_HINT_MS);
    return () => clearTimeout(timer);
  }, [hint, held]);

  const toggle = () => {
    // 登录状态还没回来时不响应：未登录、邮箱未验证都还判断不了，而且点击时记不下是谁点的，请求成功后状态一到会被
    // 当成换了访客、退回未点（生产构建上手机尺寸一打开页面就点会遇到）
    if (current.status === "pending") {
      return;
    }
    if (helpful === null || current.status === "anonymous") {
      redirectToLogin();
      return;
    }
    if (current.status === "authenticated" && !current.user.emailVerified) {
      setHint({
        text: helpfulFailureMessage("email-unverified", { on: true, type: target.type }),
        tone: "verify",
        gone: false,
      });
      return;
    }
    if (inFlight.current) {
      return;
    }
    const on = !shown.helpful;
    const base = server;
    const clickedBy = viewer;
    inFlight.current = true;
    setHint(null);
    setPending({ helpful: on, count: Math.max(0, shown.count + (on ? 1 : -1)) });
    void orUnavailable(setHelpful(target, on)).then((result) => {
      inFlight.current = false;
      setPending(null);
      // 请求在途时换了访客（另一个标签页退出、换号）：结果属于上一位，不写缓存、不提示
      if (
        clickedBy !== null &&
        viewerIdOf(client.getQueryData(queryKeys.currentUser)) !== clickedBy
      ) {
        return;
      }
      if (result.ok) {
        const { helpful: marked, helpfulCount } = result.result;
        setSettled({ base, value: { helpful: marked, count: helpfulCount }, viewer: clickedBy });
        patchHelpful(client, target, result.result);
        markHelpfulStale(client, target);
        return;
      }
      if (result.reason === "signed-out") {
        redirectToLogin();
        return;
      }
      setHint({
        text: helpfulFailureMessage(result.reason, { on, type: target.type }),
        tone: result.reason === "email-unverified" ? "verify" : "error",
        gone: result.reason === "gone",
      });
    });
  };

  return {
    shown,
    busy: pending !== null,
    ready: current.status !== "pending",
    hint,
    toggle,
    holdHint: setHeld,
  };
}
