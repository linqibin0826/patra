import type { ReactNode } from "react";

interface UnavailableEntryProps {
  /// 收藏时记下的标题（登记表快照）；没有时写「标题暂不可用」
  title: string | null;
  /// 标题行右端的操作（文献库是条目菜单，公开收藏夹页没有）
  action?: ReactNode;
  children?: ReactNode;
}

/// 已失效的条目（简报 §3.10 `item.unavailable`）：文献在 catalog 下线了，标题用快照、不是链接，标「已失效」；
/// 文献恢复后对账会让它自动回到正常外观
export function UnavailableEntry({ title, action, children }: UnavailableEntryProps) {
  return (
    <article className="flex flex-col gap-2 border-b border-border-subtle py-6">
      <div className="grid grid-cols-[minmax(0,1fr)_32px] items-start gap-4">
        <h3 className="flex flex-wrap items-baseline gap-x-2.5 gap-y-1 font-serif text-xl leading-snug font-medium tracking-tight text-fg-3">
          <span className="line-clamp-2">{title ?? "标题暂不可用"}</span>
          <span className="rounded-full border border-ink-200 px-2 py-px font-mono text-3xs tracking-mono text-fg-3">
            已失效
          </span>
        </h3>
        {action}
      </div>
      <p className="text-xs text-fg-3">这篇文献已从数据源下线，恢复后会自动显示。</p>
      {children}
    </article>
  );
}
