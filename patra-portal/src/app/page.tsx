"use client";

import { toast } from "sonner";
import { ExploreFeed } from "@/components/portal/ExploreFeed";
import { Footer } from "@/components/portal/Footer";
import { Hero } from "@/components/portal/Hero";
import { Journals } from "@/components/portal/Journals";
import { TopicCloud } from "@/components/portal/TopicCloud";
import { TopNav } from "@/components/portal/TopNav";

export default function HomePage() {
  return (
    <>
      <TopNav />
      <main>
        <Hero
          onComposerSubmit={({ value }) => {
            if (!value.trim()) return;
            toast("功能即将上线", {
              description: "搜索接入留待 v0.5 BE 联调。",
            });
          }}
        />
        <TopicCloud />
        <Journals />
        <ExploreFeed />
      </main>
      <Footer />
    </>
  );
}
