"use client";

import { Clock, Quote } from "lucide-react";
import { useRouter } from "next/navigation";
import { Tabs, TabsIndicator, TabsList, TabsTrigger } from "@/components/ui/tabs";
import type { FeedTab } from "@/types/portal";

const TABS: { value: FeedTab; label: string; Icon: typeof Clock }[] = [
  { value: "recent", label: "最近更新", Icon: Clock },
  { value: "cited", label: "高被引", Icon: Quote },
];

/// 文献流 tab：药丸分段控件，墨色滑块随选中项滑动（URL ?tab= 为唯一状态来源）。
export function ExploreFeedTabs({ currentTab }: { currentTab: FeedTab }) {
  const router = useRouter();
  return (
    <Tabs
      value={currentTab}
      onValueChange={(v) => {
        const tab = v as FeedTab;
        router.push(`/?tab=${tab}`, { scroll: false });
      }}
      className="flex-col!"
    >
      <TabsList
        variant="line"
        className="relative h-auto gap-0 rounded-full border border-border-default bg-paper-50 p-1"
      >
        <TabsIndicator className="top-(--active-tab-top) left-(--active-tab-left) h-(--active-tab-height) w-(--active-tab-width) rounded-full bg-ink-900 transition-[left,width] duration-500 ease-out-expo" />
        {TABS.map(({ value, label, Icon }) => (
          <TabsTrigger
            key={value}
            value={value}
            className="relative z-10 flex-none rounded-full border-0 px-4 py-2 text-sm font-medium text-fg-2 shadow-none transition-colors duration-300 after:hidden hover:text-ink-900 data-active:text-paper-50! data-active:hover:text-paper-50"
          >
            <Icon className="size-3.5" strokeWidth={1.5} aria-hidden />
            {label}
          </TabsTrigger>
        ))}
      </TabsList>
    </Tabs>
  );
}
