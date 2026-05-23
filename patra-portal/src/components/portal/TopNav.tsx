"use client";

import { ExternalLink, Menu, Search } from "lucide-react";
import Image from "next/image";
import { useState } from "react";
import { buttonVariants } from "@/components/ui/button";
import { Sheet, SheetContent, SheetHeader, SheetTitle, SheetTrigger } from "@/components/ui/sheet";
import { cn } from "@/lib/utils";

const NAV_ITEMS = [
  { label: "首页", href: "#top", active: true, disabled: false },
  { label: "文献", href: "#", active: false, disabled: true },
  { label: "期刊", href: "#", active: false, disabled: true },
  { label: "主题", href: "#", active: false, disabled: true },
] as const;

export function TopNav() {
  const [open, setOpen] = useState(false);

  return (
    <header
      data-section="topnav"
      className="sticky top-0 z-40 border-b border-border-default bg-paper-100/85 backdrop-blur supports-[backdrop-filter]:bg-paper-100/70"
    >
      <div className="container mx-auto grid h-14 max-w-[1200px] grid-cols-[auto_1fr_auto] items-center gap-6 px-6 max-[880px]:gap-3">
        <a href="#top" className="inline-flex items-center gap-2 text-ink-900" aria-label="Patra">
          <Image src="/brand/patra-mark.svg" alt="" aria-hidden width={28} height={28} />
          <span className="font-serif text-xl tracking-tight">Patra</span>
          <span className="ml-1 rounded-sm border border-clay-200 bg-clay-50 px-1.5 text-[10px] font-medium uppercase tracking-caps text-clay-700">
            门户
          </span>
        </a>

        <nav className="justify-self-center max-[880px]:hidden" aria-label="主导航">
          <ul className="flex items-center gap-1">
            {NAV_ITEMS.map((item) => (
              <li key={item.label}>
                <a
                  href={item.href}
                  aria-current={item.active ? "page" : undefined}
                  aria-disabled={item.disabled ? "true" : undefined}
                  className={
                    item.active
                      ? "inline-flex items-center gap-1.5 border-b-2 border-clay-500 px-3 py-1.5 text-sm font-medium text-ink-900"
                      : item.disabled
                        ? "inline-flex items-center gap-1.5 px-3 py-1.5 text-sm font-medium text-fg-4 cursor-not-allowed"
                        : "inline-flex items-center gap-1.5 rounded-sm px-3 py-1.5 text-sm font-medium text-fg-2 hover:bg-paper-200 hover:text-ink-900"
                  }
                  onClick={item.disabled ? (e) => e.preventDefault() : undefined}
                >
                  {item.label}
                  {item.disabled && (
                    <span className="rounded-sm border border-border-subtle bg-paper-100 px-1 font-mono text-[9.5px] uppercase tracking-caps text-fg-4">
                      soon
                    </span>
                  )}
                </a>
              </li>
            ))}
          </ul>
        </nav>

        <div className="justify-self-end flex items-center gap-2">
          <button
            type="button"
            className="hidden min-[881px]:inline-flex items-center gap-2 rounded-md border border-border-default bg-paper-50 px-2.5 py-1 text-sm text-fg-3 hover:bg-paper-200"
            onClick={() => document.getElementById("hero-input")?.focus()}
            aria-label="跳转到搜索框"
          >
            <Search className="h-3.5 w-3.5" strokeWidth={1.5} />
            搜索
            <kbd className="ml-1 rounded-sm border border-border-subtle bg-paper-100 px-1 font-mono text-[10px] text-fg-3">
              ⌘K
            </kbd>
          </button>
          <a
            href="#about"
            className="max-[880px]:hidden rounded-sm px-3 py-1.5 text-sm font-medium text-fg-2 hover:bg-paper-200 hover:text-ink-900"
          >
            关于
          </a>
          <Sheet open={open} onOpenChange={setOpen}>
            <SheetTrigger
              aria-label="打开菜单"
              className={cn(
                buttonVariants({ variant: "ghost", size: "icon" }),
                "hidden max-[880px]:inline-flex h-9 w-9",
              )}
            >
              <Menu className="h-5 w-5" strokeWidth={1.5} />
            </SheetTrigger>
            <SheetContent side="top" className="bg-paper-100">
              <SheetHeader>
                <SheetTitle>导航菜单</SheetTitle>
              </SheetHeader>
              <ul className="flex flex-col gap-1 py-4">
                {NAV_ITEMS.map((item) => (
                  <li key={item.label}>
                    <a
                      href={item.href}
                      aria-current={item.active ? "page" : undefined}
                      aria-disabled={item.disabled ? "true" : undefined}
                      onClick={(e) => {
                        if (item.disabled) e.preventDefault();
                        else setOpen(false);
                      }}
                      className="flex items-center justify-between border-b border-border-subtle px-2 py-3 text-base font-medium text-ink-900 data-[disabled]:text-fg-4"
                      data-disabled={item.disabled ? "" : undefined}
                    >
                      {item.label}
                      {item.disabled && (
                        <span className="rounded-sm border border-border-subtle bg-paper-50 px-1 font-mono text-[10px] uppercase tracking-caps">
                          soon
                        </span>
                      )}
                    </a>
                  </li>
                ))}
                <li className="pt-4">
                  <a
                    href="https://github.com"
                    target="_blank"
                    rel="noreferrer"
                    className="inline-flex items-center gap-1 text-sm text-fg-2"
                  >
                    GitHub <ExternalLink className="h-3.5 w-3.5" strokeWidth={1.5} />
                  </a>
                </li>
              </ul>
            </SheetContent>
          </Sheet>
        </div>
      </div>
    </header>
  );
}
