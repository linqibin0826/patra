"use client";

import { ChevronDown, Clock, Quote, TrendingUp } from "lucide-react";
import Image from "next/image";
import type { ComponentType, SVGProps } from "react";
import { PaperCard } from "@/components/portal/PaperCard";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { FEED } from "@/data/feed";
import type { FeedTab } from "@/types/portal";

type IconComponent = ComponentType<SVGProps<SVGSVGElement>>;

interface TabMeta {
  label: string;
  Icon: IconComponent;
}

const TAB_META: Record<FeedTab, TabMeta> = {
  trending: { label: "今日热门", Icon: TrendingUp },
  recent: { label: "最近更新", Icon: Clock },
  cited: { label: "本周高引", Icon: Quote },
};

const TAB_KEYS: readonly FeedTab[] = ["trending", "recent", "cited"];

export function ExploreFeed() {
  return (
    <section
      data-section="explore-feed"
      className="container mx-auto max-w-[1200px] px-6 py-14 max-[880px]:py-10"
    >
      <div className="mb-6">
        <span className="inline-flex items-center gap-1.5 font-sans text-2xs font-semibold uppercase tracking-caps text-fg-3">
          <Image src="/brand/patra-mark.svg" alt="" aria-hidden width={4} height={14} />
          文献流
        </span>
        <h2 className="mt-1 font-serif text-3xl font-medium leading-tight tracking-tight text-ink-900">
          值得读一读的文献
        </h2>
        <p className="mt-1 text-sm text-fg-3">
          每篇都带一段 AI 速读 —— 由 Patra 在采集时生成，仅作为线索，不能替代阅读原文。
        </p>
      </div>

      <Tabs defaultValue="trending" className="!flex-col">
        <TabsList
          variant="line"
          className="flex h-auto w-full items-center justify-start gap-0 rounded-none border-b border-border-default bg-transparent p-0"
        >
          {TAB_KEYS.map((k) => {
            const { label, Icon } = TAB_META[k];
            return (
              <TabsTrigger
                key={k}
                value={k}
                className="group/feed-tab relative -mb-px flex-none rounded-none border-x-0 border-t-0 border-b-2 border-transparent px-4 py-2.5 text-sm font-medium text-fg-3 shadow-none transition-colors duration-150 hover:text-ink-900 data-active:border-clay-500! data-active:text-ink-900!"
              >
                <Icon className="h-3.5 w-3.5" strokeWidth={1.5} aria-hidden />
                {label}
                <span className="ml-1 rounded-sm bg-paper-200 px-1.5 py-px font-mono text-[10px] text-fg-3 transition-colors duration-150 group-data-active/feed-tab:bg-clay-100 group-data-active/feed-tab:text-clay-800">
                  {FEED[k].length}
                </span>
              </TabsTrigger>
            );
          })}
        </TabsList>
        {TAB_KEYS.map((k) => (
          <TabsContent key={k} value={k} className="pt-6">
            <div className="grid grid-cols-2 gap-5 max-[880px]:grid-cols-1">
              {FEED[k].map((p) => (
                <PaperCard key={p.id} paper={p} />
              ))}
            </div>
            <div className="mt-8 flex justify-center">
              <button
                type="button"
                disabled
                aria-disabled="true"
                title="功能即将上线"
                className="inline-flex items-center gap-1.5 rounded-md border border-border-default bg-paper-50 px-4 py-1.5 text-sm text-fg-2 hover:bg-paper-200 disabled:cursor-not-allowed disabled:opacity-60"
              >
                展开更多
                <ChevronDown className="h-3.5 w-3.5" strokeWidth={1.5} aria-hidden />
              </button>
            </div>
          </TabsContent>
        ))}
      </Tabs>
    </section>
  );
}
