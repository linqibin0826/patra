"use client";

import { ArrowUpRight, Menu, Search } from "lucide-react";
import dynamic from "next/dynamic";
import Link from "next/link";
import { usePathname } from "next/navigation";
import { useEffect, useState } from "react";
import { BrandMark } from "@/components/portal/BrandMark";
import { Venation } from "@/components/portal/Venation";
import { buttonVariants } from "@/components/ui/button";
import { Sheet, SheetContent, SheetHeader, SheetTitle, SheetTrigger } from "@/components/ui/sheet";
import { REPO_URL } from "@/lib/site";
import { cn } from "@/lib/utils";

/// 快速检索对话框（含表单与校验依赖）按需加载：首次唤起时才拉取，之后常驻以保留退出动画
const loadQuickSearch = () => import("@/components/portal/QuickSearch");
const QuickSearch = dynamic(() => loadQuickSearch().then((m) => m.QuickSearch), { ssr: false });

/// 指针移入 / 键盘聚焦「快速检索」时预取对话框代码，首次打开不再等网络
const preloadQuickSearch = () => {
  void loadQuickSearch();
};

const NAV_ITEMS = [
  { label: "首页", href: "/" },
  { label: "文献", href: "/papers" },
  { label: "期刊", href: "/journals" },
] as const;

/// 移动菜单逐项入场的错峰（静态类名，Tailwind 才能生成）
const STAGGER = ["[--delay:60ms]", "[--delay:120ms]", "[--delay:180ms]", "[--delay:240ms]"];

/// 当前路径是否命中导航项。首页要求精确匹配，其余按前缀匹配以覆盖 `/papers/[id]`、`/journals/[id]` 等子路由。
function isNavActive(pathname: string, href: string): boolean {
  if (href === "/") return pathname === "/";
  return pathname === href || pathname.startsWith(`${href}/`);
}

/// 吸顶导航：内容区恒高 56px，另有 1px 下边框（详情页面包屑 / 侧栏的吸顶偏移依赖它），滚动离开顶部后才出现纸色毛玻璃与发丝线。
/// ⌘K / Ctrl+K 与「快速检索」按钮在任何页面唤起检索对话框。
export function TopNav() {
  const [menuOpen, setMenuOpen] = useState(false);
  const [searchOpen, setSearchOpen] = useState(false);
  const [searchLoaded, setSearchLoaded] = useState(false);
  const [scrolled, setScrolled] = useState(false);
  // 快捷键提示：服务端与首帧写 ⌘K，挂载后在非 Apple 平台改写 Ctrl K（避免水合不一致）
  const [shortcut, setShortcut] = useState("⌘K");
  const pathname = usePathname();

  useEffect(() => {
    if (!/Mac|iPhone|iPad|iPod/.test(navigator.platform || navigator.userAgent)) {
      setShortcut("Ctrl K");
    }
  }, []);

  useEffect(() => {
    const onKeyDown = (e: KeyboardEvent) => {
      if ((e.metaKey || e.ctrlKey) && e.key.toLowerCase() === "k") {
        e.preventDefault();
        setSearchLoaded(true);
        setSearchOpen((open) => !open);
      }
    };
    window.addEventListener("keydown", onKeyDown);
    return () => window.removeEventListener("keydown", onKeyDown);
  }, []);

  useEffect(() => {
    const onScroll = () => setScrolled(window.scrollY > 4);
    onScroll();
    window.addEventListener("scroll", onScroll, { passive: true });
    return () => window.removeEventListener("scroll", onScroll);
  }, []);

  const openSearch = () => {
    setMenuOpen(false);
    setSearchLoaded(true);
    setSearchOpen(true);
  };

  return (
    <header
      data-section="topnav"
      data-scrolled={scrolled}
      className="sticky top-0 z-40 border-b border-transparent transition-colors duration-300 data-[scrolled=true]:border-border-default data-[scrolled=true]:bg-bg-sticky data-[scrolled=true]:backdrop-blur-md"
    >
      <div className="mx-auto grid h-14 max-w-page grid-cols-[1fr_auto_1fr] items-center gap-6 px-gutter max-[880px]:grid-cols-[1fr_auto]">
        <Link href="/" className="inline-flex items-center gap-2.5 justify-self-start text-ink-900">
          <BrandMark width={9} />
          <span className="font-serif text-2xl leading-none font-medium tracking-tight">Patra</span>
          <span className="border-l border-border-strong pl-2.5 font-mono text-3xs tracking-caps text-fg-3 max-sm:hidden">
            医学文献门户
          </span>
        </Link>

        <nav className="max-[880px]:hidden" aria-label="主导航">
          <ul className="flex items-center gap-1">
            {NAV_ITEMS.map((item) => {
              const active = isNavActive(pathname, item.href);
              return (
                <li key={item.label}>
                  <Link
                    href={item.href}
                    aria-current={active ? "page" : undefined}
                    className="group/nav relative inline-flex items-center rounded-full px-3.5 py-2 text-sm font-medium text-fg-2 transition-colors hover:text-ink-900 aria-[current=page]:text-ink-900"
                  >
                    <span className="link-draw pb-px">{item.label}</span>
                    <span
                      aria-hidden
                      className="absolute -bottom-0.5 left-1/2 size-1 -translate-x-1/2 scale-0 rounded-full bg-clay-500 motion-safe:transition-transform duration-300 ease-out-expo group-aria-[current=page]/nav:scale-100"
                    />
                  </Link>
                </li>
              );
            })}
          </ul>
        </nav>

        <div className="flex items-center gap-1.5 justify-self-end">
          <button
            type="button"
            onClick={openSearch}
            onPointerEnter={preloadQuickSearch}
            onFocus={preloadQuickSearch}
            className="group/qs inline-flex h-9 items-center gap-2 rounded-full border border-border-default bg-paper-50 pr-1.5 pl-3 text-sm text-fg-2 transition hover:border-border-hover hover:text-ink-900 max-[880px]:size-9 max-[880px]:justify-center max-[880px]:p-0"
          >
            <Search className="size-3.5 shrink-0" strokeWidth={1.75} aria-hidden />
            <span className="max-[880px]:sr-only">快速检索</span>
            <kbd
              aria-hidden
              className="rounded-full border border-border-subtle bg-paper-100 px-2 py-0.5 font-mono text-3xs text-fg-3 transition-colors group-hover/qs:border-border-default max-[880px]:hidden"
            >
              {shortcut}
            </kbd>
          </button>
          <a
            href="#about"
            className="rounded-full px-3 py-2 text-sm font-medium text-fg-2 transition-colors hover:text-ink-900 max-[880px]:hidden"
          >
            <span className="link-draw pb-px">关于</span>
          </a>
          <Sheet open={menuOpen} onOpenChange={setMenuOpen}>
            <SheetTrigger
              aria-label="打开菜单"
              className={cn(
                buttonVariants({ variant: "ghost", size: "icon" }),
                "hidden size-9 rounded-full max-[880px]:inline-flex",
              )}
            >
              <Menu className="size-5" strokeWidth={1.5} />
            </SheetTrigger>
            <SheetContent
              side="top"
              className="isolate gap-0 overflow-hidden bg-bg-canvas px-gutter pt-0 pb-10 data-[side=top]:h-dvh"
            >
              <Venation
                id="menu-leaf"
                className="pointer-events-none absolute right-[-14%] bottom-[-10%] -z-10 h-[62%] w-auto rotate-[24deg] opacity-[0.18]"
              />
              <SheetHeader className="h-14 justify-center p-0">
                <SheetTitle className="font-mono text-2xs font-normal tracking-caps text-fg-3">
                  导航菜单
                </SheetTitle>
              </SheetHeader>
              <ul className="mt-4 flex flex-col border-t border-border-default">
                {NAV_ITEMS.map((item, i) => {
                  const active = isNavActive(pathname, item.href);
                  return (
                    <li key={item.label} className={cn("anim-rise", STAGGER[i])}>
                      <Link
                        href={item.href}
                        aria-current={active ? "page" : undefined}
                        onClick={() => setMenuOpen(false)}
                        className="group/m flex items-baseline gap-5 border-b border-border-default py-5"
                      >
                        <span aria-hidden className="w-6 font-mono text-xs text-fg-3">
                          {String(i + 1).padStart(2, "0")}
                        </span>
                        <span className="font-serif text-display-3 leading-heading text-ink-900 group-aria-[current=page]/m:text-clay-700">
                          {item.label}
                        </span>
                      </Link>
                    </li>
                  );
                })}
              </ul>
              <div className={cn("anim-rise mt-8 flex flex-col gap-4", STAGGER[3])}>
                <button
                  type="button"
                  onClick={openSearch}
                  onPointerEnter={preloadQuickSearch}
                  onFocus={preloadQuickSearch}
                  className="flex h-12 items-center gap-3 rounded-full border border-border-default bg-paper-50 px-5 text-base text-fg-2"
                >
                  <Search className="size-4" strokeWidth={1.75} aria-hidden />
                  检索文献、PMID、DOI 或作者
                </button>
                <div className="flex items-center gap-6 px-1 text-sm text-fg-2">
                  <Link href="#about" onClick={() => setMenuOpen(false)}>
                    关于 Patra
                  </Link>
                  <a
                    href={REPO_URL}
                    target="_blank"
                    rel="noreferrer"
                    className="inline-flex items-center gap-1"
                  >
                    GitHub <ArrowUpRight className="size-3.5" strokeWidth={1.5} aria-hidden />
                  </a>
                </div>
              </div>
            </SheetContent>
          </Sheet>
        </div>
      </div>
      {searchLoaded && <QuickSearch open={searchOpen} onOpenChange={setSearchOpen} />}
    </header>
  );
}
