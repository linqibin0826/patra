"use client";

import Image from "next/image";
import { PaperCard } from "@/components/portal/PaperCard";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { FEED } from "@/data/feed";
import type { FeedTab } from "@/types/portal";

const TAB_LABELS: Record<FeedTab, string> = {
  trending: "热度",
  recent: "最新",
  cited: "被引",
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
          <Image src="/brand/patra-monogram.svg" alt="" aria-hidden width={14} height={14} />
          探索 · explore
        </span>
        <h2 className="mt-1 font-serif text-3xl font-medium leading-tight tracking-tight text-ink-900">
          探索文献流
        </h2>
      </div>

      <Tabs defaultValue="trending">
        <TabsList className="flex h-auto items-center gap-0 rounded-none border-b border-border-default bg-transparent p-0">
          {TAB_KEYS.map((k) => (
            <TabsTrigger
              key={k}
              value={k}
              className="rounded-none border-b-2 border-transparent bg-transparent px-4 py-2.5 text-sm font-medium text-fg-3 shadow-none hover:text-ink-900 data-[state=active]:border-teal-600 data-[state=active]:bg-transparent data-[state=active]:text-ink-900 data-[state=active]:shadow-none"
            >
              {TAB_LABELS[k]}
            </TabsTrigger>
          ))}
        </TabsList>
        {TAB_KEYS.map((k) => (
          <TabsContent key={k} value={k} className="pt-2">
            {FEED[k].map((p) => (
              <PaperCard key={p.id} paper={p} />
            ))}
          </TabsContent>
        ))}
      </Tabs>
    </section>
  );
}
