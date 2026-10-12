import Link from "next/link";
import type { ReactNode } from "react";
import { cn } from "@/lib/utils";

interface AuthLinkProps {
  href: string;
  /// 替换当前这条历史记录：登录页、注册页里的站内链接都替换（spec 第 6 节）
  replace?: boolean;
  /// 下划线常驻，不只在悬停、键盘聚焦时画出：错误文案里的「去登录」
  drawn?: boolean;
  /// 点击时先做的事：移动端全屏菜单里的「注册」先关闭菜单
  onClick?: () => void;
  children: ReactNode;
}

/// 登录、注册相关的文字链接：「注册」「登录」「去登录」，以及移动端账号块里的「注册」。
/// 悬停和键盘聚焦时下划线自左画出：下划线画在里层的 span 上，由外层链接的 hover / focus-visible
/// 触发。移动端上下各加 12px 点按区，又不撑高这一行。
export function AuthLink({ href, replace, drawn, onClick, children }: AuthLinkProps) {
  return (
    <Link
      href={href}
      replace={replace}
      onClick={onClick}
      className="group/link rounded-xs font-medium text-link hover:text-link-hover max-[880px]:-my-3 max-[880px]:inline-block max-[880px]:py-3"
    >
      <span
        className={cn(
          "link-draw pb-px group-hover/link:[background-size:100%_1px] group-focus-visible/link:[background-size:100%_1px]",
          drawn && "[background-size:100%_1px]",
        )}
      >
        {children}
      </span>
    </Link>
  );
}
