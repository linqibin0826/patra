import { CloudOff } from "lucide-react";
import Link from "next/link";
import { btnSecondary } from "@/lib/portal-ui";
import { cn } from "@/lib/utils";

interface InteractionUnavailableProps {
  title: string;
  body?: string;
  /// 客户端区块：重取查询
  onRetry?: () => void;
  /// 服务端区块：没有客户端缓存可重取，重试就是重新进入当前地址
  retryHref?: string;
  /// 紧凑版：页面里的一个区块（讨论区），上下留白 40px、标题小一号（简报 §3.21）
  compact?: boolean;
}

/// 互动区块的错误块（简报 §3.17）：不是整页错误，导航栏与页面标题照常。interaction 出问题时文献库、
/// 公开收藏夹页、主页收藏夹标签在自己的位置显示它
export function InteractionUnavailable({
  title,
  body,
  onRetry,
  retryHref,
  compact = false,
}: InteractionUnavailableProps) {
  return (
    <div
      className={cn(
        "flex flex-col items-center gap-3 px-6 text-center",
        compact ? "py-10" : "py-16",
      )}
    >
      <CloudOff
        className={cn("text-fg-3", compact ? "size-9" : "size-12")}
        strokeWidth={1.25}
        aria-hidden="true"
      />
      <h2 className={cn("font-serif font-medium text-fg-1", compact ? "text-lg" : "text-xl")}>
        {title}
      </h2>
      {body && <p className="text-sm text-fg-2">{body}</p>}
      {onRetry ? (
        <button type="button" onClick={onRetry} className={cn(btnSecondary, "mt-2")}>
          重试
        </button>
      ) : (
        retryHref && (
          <Link href={retryHref} className={cn(btnSecondary, "mt-2")}>
            重试
          </Link>
        )
      )}
    </div>
  );
}
