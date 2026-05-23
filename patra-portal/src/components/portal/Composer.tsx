"use client";

import { zodResolver } from "@hookform/resolvers/zod";
import { CornerDownLeft, Search } from "lucide-react";
import { useForm } from "react-hook-form";
import { z } from "zod";
import { Tabs, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { EXAMPLE_QUERIES } from "@/data/example-queries";
import { SEARCH_MODES } from "@/data/search-modes";
import { cn } from "@/lib/utils";
import type { ComposerMode } from "@/types/portal";

const composerSchema = z.object({
  mode: z.enum(["keyword", "pmid", "doi", "author"]),
  value: z.string(),
});

type ComposerFormValues = z.infer<typeof composerSchema>;

export interface ComposerSubmitEvent {
  mode: ComposerMode;
  value: string;
}

interface ComposerProps {
  onSubmit?: (event: ComposerSubmitEvent) => void;
}

export function Composer({ onSubmit }: ComposerProps) {
  const form = useForm<ComposerFormValues>({
    resolver: zodResolver(composerSchema),
    defaultValues: { mode: "keyword", value: "" },
  });

  const mode = form.watch("mode");

  const current = SEARCH_MODES.find((m) => m.id === mode) ?? SEARCH_MODES[0];
  if (!current) return null;

  const handleSubmit = form.handleSubmit((data) => {
    onSubmit?.({ mode: data.mode, value: data.value });
  });

  const handleModeChange = (next: string) => {
    form.setValue("mode", next as ComposerMode);
    form.setValue("value", "");
  };

  const applyExample = (exMode: ComposerMode, exText: string) => {
    form.setValue("mode", exMode);
    form.setValue("value", exText);
  };

  return (
    <form
      onSubmit={handleSubmit}
      className="overflow-hidden rounded-lg border border-ink-800 bg-white shadow-sm"
    >
      <Tabs value={mode} onValueChange={handleModeChange}>
        <TabsList className="flex h-auto items-center gap-0 rounded-none border-b border-border-default bg-paper-50 p-0 px-1.5">
          {SEARCH_MODES.map((m) => (
            <TabsTrigger
              key={m.id}
              value={m.id}
              className="rounded-none border-b-2 border-transparent bg-transparent px-3.5 py-2.5 text-sm font-medium text-fg-3 shadow-none hover:text-ink-900 data-[state=active]:border-teal-600 data-[state=active]:bg-transparent data-[state=active]:text-ink-900 data-[state=active]:shadow-none"
            >
              {m.label}
              {m.id === "keyword" && (
                <span className="ml-1.5 font-mono text-[10.5px] text-fg-4">默认</span>
              )}
            </TabsTrigger>
          ))}
        </TabsList>
      </Tabs>

      <div className="flex items-center gap-3 py-1.5 pl-[18px] pr-1.5 max-sm:pl-3">
        <Search className="shrink-0 text-fg-3" size={20} strokeWidth={1.5} />
        <input
          id="hero-input"
          {...form.register("value")}
          className={cn(
            "min-w-0 flex-1 border-0 bg-transparent py-3.5 text-lg leading-tight text-ink-900 outline-none placeholder:text-fg-4 max-sm:py-3 max-sm:text-base",
            current.mono && "font-mono text-base tracking-[0.01em] max-sm:text-sm",
          )}
          placeholder={current.placeholder}
          autoComplete="off"
          spellCheck={false}
          aria-label={`按${current.label}搜索`}
        />
        <button
          type="submit"
          aria-label="搜索"
          className="inline-flex shrink-0 items-center gap-1.5 rounded-md border border-teal-700 bg-teal-600 px-4 py-2.5 text-sm font-semibold text-fg-on-clay transition-colors hover:bg-teal-700 active:bg-teal-800 max-sm:px-3"
        >
          <span className="max-sm:hidden">搜索</span>
          <CornerDownLeft className="h-3.5 w-3.5" strokeWidth={1.5} aria-hidden />
        </button>
      </div>

      <div className="flex flex-wrap items-center gap-2 border-t border-border-subtle bg-paper-50 px-3.5 py-2.5 text-xs text-fg-3">
        <span className="mr-0.5 font-mono text-[10px] uppercase tracking-caps text-fg-3">试试</span>
        {EXAMPLE_QUERIES.map((ex) => {
          const m = SEARCH_MODES.find((s) => s.id === ex.mode);
          return (
            <button
              key={ex.text}
              type="button"
              onClick={() => applyExample(ex.mode, ex.text)}
              className={cn(
                "inline-flex items-center gap-1.5 whitespace-nowrap rounded-full border border-border-default bg-white px-2.5 py-0.5 text-xs text-fg-2 hover:border-ink-300 hover:bg-paper-200 hover:text-ink-900",
                m?.mono && "font-mono text-[11px]",
              )}
            >
              <span className="border-r border-border-default pr-1.5 font-mono text-[10px] uppercase tracking-caps text-fg-4">
                {m?.label}
              </span>
              {ex.text}
            </button>
          );
        })}
      </div>
    </form>
  );
}
