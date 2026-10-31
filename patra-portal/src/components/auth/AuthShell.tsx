import { ArrowLeft } from "lucide-react";
import Link from "next/link";
import type { ReactNode } from "react";
import { BrandMark } from "@/components/portal/BrandMark";
import { Venation } from "@/components/portal/Venation";

interface AuthShellProps {
  /// 不传时不出标题与副文案：退订确认页读取中的骨架连标题一起画（PAP-148 spec 第 7.2 节）
  title?: string;
  /// 副文案；可以带强调（找回密码已发送态里的邮箱）
  description?: ReactNode;
  /// 「返回」指向的站内路径，已经过 `safeNextPath`；null 时不显示「返回」（取消注销页不能被绕开，简报 §3.16）
  back: string | null;
  /// 标题上方的状态图标（落地页结果、取消注销页，见 `StateIcon`）
  icon?: ReactNode;
  children: ReactNode;
}

/// 登录页、注册页的外壳，照高保真稿 Main / Register-Default（桌面）、Login-M-Default /
/// Register-M-Default（移动）。左栏是品牌标识、「返回」和表单区；右栏是墨色品牌面板，宽度
/// ≤ 880px 时不显示。两栏各占一半，用 `basis-1/2`：`flex-1` 平分的是扣掉内边距之后的宽度，
/// 左栏内边距大，会更宽。左栏的左内边距和 TopNav 的品牌左缘对齐。表单区上下两个占位按
/// 2 : 3 分剩余高度，视觉上略偏上；移动端改为顶部对齐。
/// h1 和定位语的类名直接写字符串，不经过 `cn()`：tailwind-merge 会把 `text-display-3`
/// 当成文字颜色，和真正的颜色类互相覆盖。
export function AuthShell({ title, description, back, icon, children }: AuthShellProps) {
  return (
    <div className="flex min-h-screen">
      <div className="flex min-w-0 grow basis-1/2 flex-col pl-[max(var(--gutter),calc((100vw-var(--page-max))/2+var(--gutter)))] pr-gutter max-[880px]:pb-10">
        <header className="flex h-14 shrink-0 items-center justify-between gap-6 max-[880px]:gap-4">
          <Link
            href="/"
            aria-label="Patra 首页"
            className="inline-flex items-center gap-2.5 text-ink-900"
          >
            <BrandMark width={9} />
            <span className="font-serif text-2xl leading-none font-medium tracking-tight">
              Patra
            </span>
            <span className="whitespace-nowrap border-l border-border-strong pl-2.5 font-mono text-3xs tracking-caps text-fg-3 max-[880px]:hidden">
              医学文献门户
            </span>
          </Link>
          {/* 下划线只在悬停时画出，键盘聚焦时只有焦点环（高保真稿 Spec-Form 05） */}
          {back !== null && (
            <Link
              replace
              href={back}
              className="group/back inline-flex items-center gap-1.5 rounded-sm py-2 text-sm leading-5 font-medium text-fg-2 transition-colors hover:text-ink-900 max-[880px]:py-3"
            >
              <ArrowLeft className="size-3.5 shrink-0" strokeWidth={1.75} aria-hidden="true" />
              <span className="link-draw pb-px group-hover/back:[background-size:100%_1px]">
                返回
              </span>
            </Link>
          )}
        </header>
        <main className="flex flex-1 flex-col py-6 max-[880px]:pt-10 max-[880px]:pb-0">
          <div aria-hidden="true" className="flex-2 max-[880px]:hidden" />
          <div className="w-full max-w-100">
            {icon}
            {title !== undefined && (
              <h1 className="font-serif text-display-3 leading-heading font-medium tracking-tight text-fg-1">
                {title}
              </h1>
            )}
            {description !== undefined && (
              <p className="mt-3 text-md leading-relaxed text-fg-2">{description}</p>
            )}
            {children}
          </div>
          <div aria-hidden="true" className="flex-3 max-[880px]:hidden" />
        </main>
      </div>
      <aside className="relative flex min-w-0 grow basis-1/2 flex-col justify-end overflow-hidden bg-bg-inverse p-16 text-fg-inverse-2 max-[880px]:hidden">
        <Venation
          tone="inverse"
          className="pointer-events-none absolute top-[7vh] right-[13%] h-[66vh] w-auto -rotate-16"
        />
        <div className="relative border-t border-border-inverse-strong pt-5">
          <p className="font-mono text-2xs leading-relaxed tracking-mono text-fg-inverse-3">
            <span className="text-accent-on-inverse">图 1</span>　叶脉
          </p>
          <p className="mt-3 font-serif text-display-3 leading-heading font-medium tracking-tight whitespace-nowrap text-fg-inverse-1">
            十个来源，
            <br />
            汇入同一根<span className="text-accent-on-inverse">主脉</span>。
          </p>
        </div>
      </aside>
    </div>
  );
}
