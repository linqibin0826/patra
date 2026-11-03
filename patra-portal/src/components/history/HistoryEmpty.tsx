import { History } from "lucide-react";
import Link from "next/link";
import type { ReactNode, Ref } from "react";
import { btnSecondary } from "@/lib/portal-ui";
import { cn } from "@/lib/utils";

/// 阅读历史的空状态与取不到时的版式（简报 v0.18 §3.7 第 8、9 条）：历史图标、衬线标题、说明、一个次按钮
export function HistoryEmpty({
  title,
  description,
  action,
}: {
  title: string;
  description: string;
  action: ReactNode;
}) {
  return (
    <div className="flex flex-col items-center gap-2.5 px-6 py-18 text-center">
      <History className="mb-1.5 size-12 text-fg-3" strokeWidth={1.25} aria-hidden="true" />
      <h2 className="font-serif text-2xl font-medium text-fg-1">{title}</h2>
      <p className="max-w-[34em] text-base leading-[22px] text-fg-2">{description}</p>
      <div className="mt-1.5">{action}</div>
    </div>
  );
}

/// 空状态里的次按钮链接
export function EmptyLink({
  href,
  children,
  linkRef,
}: {
  href: string;
  children: ReactNode;
  /// 删掉最后一条、清空之后焦点落在这里（「清空历史」那时不可用）
  linkRef?: Ref<HTMLAnchorElement>;
}) {
  return (
    <Link ref={linkRef} href={href} className={cn(btnSecondary, "px-4 py-2 text-sm")}>
      {children}
    </Link>
  );
}
