"use client";

import { toast } from "sonner";
import { Hero } from "@/components/portal/Hero";

export function HeroWithToast() {
  return (
    <Hero
      onComposerSubmit={({ value }) => {
        if (!value.trim()) return;
        toast("功能即将上线", {
          description: "搜索接入留待 v0.5 BE 联调。",
        });
      }}
    />
  );
}
