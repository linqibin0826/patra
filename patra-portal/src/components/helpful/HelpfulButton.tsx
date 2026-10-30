"use client";

import { ThumbsUp } from "lucide-react";
import { ResendVerificationButton } from "@/components/portal/ResendVerificationButton";
import { formatCount } from "@/lib/format-count";
import { useHelpfulToggle } from "@/lib/helpful/use-helpful-toggle";
import type { HelpfulTarget } from "@/lib/interaction/helpful-schema";
import { cn } from "@/lib/utils";

export interface HelpfulButtonProps {
  target: HelpfulTarget;
  /// 服务端的「我点过没有」；null = 这次请求没有会话
  helpful: boolean | null;
  /// 服务端的有帮助数
  count: number;
  /// 自己的内容（含匿名经验的作者、作者在自己匿名经验下的评论）：只有数字，没有按钮
  own: boolean;
  /// `text`：评论操作行、经验行；`pill`：经验页正文下方
  variant: "text" | "pill";
  /// 点的时候目标已被删除：提示结束后调用，由所在位置对账
  onGone?: () => void;
}

/// 「有帮助」（简报 v0.15 §3.1–§3.2）：文字形 / 胶囊形 / 自己的内容只读数字三种渲染
export function HelpfulButton(props: HelpfulButtonProps) {
  return props.own ? (
    <OwnHelpful count={props.count} pill={props.variant === "pill"} />
  ) : (
    <Toggle {...props} />
  );
}

/// 自己的内容：一句话写出人数，不是按钮；为 0 时什么都不显示
function OwnHelpful({ count, pill }: { count: number; pill: boolean }) {
  return count > 0 ? (
    <span
      className={cn(
        "inline-flex items-center gap-1.5 text-fg-3",
        pill ? "text-sm" : "text-xs max-[880px]:min-h-8",
      )}
    >
      <ThumbsUp size={pill ? 16 : 14} strokeWidth={1.75} aria-hidden />
      {formatCount(count)} 人觉得有帮助
    </span>
  ) : null;
}

/// 别人的内容：可点的按钮 + 行内提示
function Toggle({ target, helpful, count: serverCount, variant, onGone }: HelpfulButtonProps) {
  const pill = variant === "pill";
  const { shown, busy, ready, hint, toggle, holdHint } = useHelpfulToggle({
    target,
    helpful,
    count: serverCount,
    onGone,
  });
  const on = shown.helpful;
  const count = shown.count;
  return (
    <span className="inline-flex flex-wrap items-center gap-y-1">
      <button
        type="button"
        aria-pressed={on}
        aria-busy={busy || undefined}
        // 登录状态还没回来时点了不响应，标出来（与收藏按钮的 pending 一致）
        aria-disabled={!ready || undefined}
        // 读屏名写死：拼接可见文字与隐藏文字时 Chromium 会在中间多算一个空格（「有帮助 ，3 人…」）
        aria-label={count > 0 ? `有帮助，${formatCount(count)} 人觉得有帮助` : "有帮助"}
        onClick={toggle}
        className={cn(
          "inline-flex items-center gap-1.5 transition-colors",
          pill
            ? "h-9 rounded-full border px-4 text-sm max-[880px]:h-11"
            : "font-medium text-xs max-[880px]:min-h-8",
          pill &&
            (on
              ? "border-clay-200 bg-clay-50 text-clay-700"
              : "border-border-default bg-paper-50 text-fg-2 hover:text-fg-1"),
          !pill && (on ? "text-clay-700" : "text-fg-3 hover:text-fg-1"),
        )}
      >
        <ThumbsUp
          size={pill ? 16 : 14}
          strokeWidth={1.75}
          aria-hidden
          className={cn(
            "duration-(--dur-normal) ease-out-expo motion-safe:transition-transform",
            on && "fill-clay-500 text-clay-600 motion-safe:scale-110",
          )}
        />
        有帮助
        {count > 0 && <span className="font-mono text-2xs tabular-nums">{formatCount(count)}</span>}
      </button>
      {/* 常驻、不隐藏的播报区（提示要作为已有播报区的内容变化才会被读出）；提示出现时才挂上 status：一页里有
          很多按钮，空的 status 会和区块自己的播报混在一起。间距放在提示上，空的播报区不占位 */}
      <span aria-live="polite">
        {hint && (
          <span
            role="status"
            onPointerEnter={() => holdHint(true)}
            onPointerLeave={() => holdHint(false)}
            onFocus={() => holdHint(true)}
            onBlur={() => holdHint(false)}
            className={cn(
              "ml-2.5 inline-flex flex-wrap items-center gap-x-2 text-xs",
              hint.tone === "verify" ? "text-amber-700" : "text-rust-500",
            )}
          >
            {hint.text}
            {hint.tone === "verify" && <ResendVerificationButton label="重新发送验证邮件" />}
          </span>
        )}
      </span>
    </span>
  );
}
