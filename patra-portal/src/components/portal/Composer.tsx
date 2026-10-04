"use client";

import { zodResolver } from "@hookform/resolvers/zod";
import { CornerDownLeft, Search } from "lucide-react";
import { useForm } from "react-hook-form";
import { z } from "zod";
import { Tabs, TabsIndicator, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { EXAMPLE_QUERIES } from "@/data/example-queries";
import { SEARCH_MODES } from "@/data/search-modes";
import { useScrollFade } from "@/lib/use-scroll-fade";
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
  /** 输入框 id：首页 Hero 用默认值（导航「快速检索」也可定位到它），对话框内另起一个避免重复 */
  inputId?: string;
}

export function Composer({ onSubmit, inputId = "hero-input" }: ComposerProps) {
  const form = useForm<ComposerFormValues>({
    resolver: zodResolver(composerSchema),
    defaultValues: { mode: "keyword", value: "" },
  });

  const mode = form.watch("mode");
  const examples = useScrollFade<HTMLDivElement>();

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
      className="relative overflow-hidden rounded-xl border border-ink-900 bg-bg-elevated shadow-composer transition-colors duration-300 focus-within:border-border-focus"
    >
      <Tabs value={mode} onValueChange={handleModeChange} className="flex-col!">
        <TabsList
          variant="line"
          // 上下留 4px：横向滚动容器会裁掉越界的焦点环，留白让 tab 的焦点环完整显示
          className="relative flex h-auto w-full items-center justify-start gap-0 overflow-x-auto rounded-none border-b border-border-subtle bg-transparent p-0 px-2.5 py-1 [scrollbar-width:none]"
        >
          {SEARCH_MODES.map((m) => (
            <TabsTrigger
              key={m.id}
              value={m.id}
              className="relative flex-none rounded-none border-0 bg-transparent px-3.5 py-2.5 text-sm font-medium text-fg-3 shadow-none transition-colors duration-200 after:hidden hover:text-ink-900 data-active:bg-transparent data-active:text-ink-900!"
            >
              {m.label}
              {m.id === "keyword" && (
                <span className="ml-1.5 font-mono text-3xs tracking-mono text-fg-3">默认</span>
              )}
            </TabsTrigger>
          ))}
          <TabsIndicator className="bottom-0 left-(--active-tab-left) h-0.5 w-(--active-tab-width) rounded-full bg-clay-500 motion-safe:transition-[left,width] duration-500 ease-out-expo" />
        </TabsList>
      </Tabs>

      <div className="flex items-center gap-3 py-2 pr-2 pl-5 max-sm:pl-3.5">
        <Search className="shrink-0 text-fg-3" size={20} strokeWidth={1.5} aria-hidden />
        <input
          id={inputId}
          {...form.register("value")}
          className={cn(
            // 行高固定 24px：等宽模式字号变小时输入框高度不跳（text-* 经 tailwind-merge 会挤掉在它之前的 leading-*）
            "min-w-0 flex-1 border-0 bg-transparent py-3.5 text-xl leading-6 text-ink-900 outline-none placeholder:text-fg-4 max-sm:py-3 max-sm:text-lg",
            current.mono && "font-mono text-lg leading-6 max-sm:text-md",
          )}
          placeholder={current.placeholder}
          autoComplete="off"
          spellCheck={false}
          aria-label={`按${current.label}搜索`}
        />
        <button
          type="submit"
          aria-label="搜索"
          className="group/submit inline-flex shrink-0 items-center gap-2 rounded-lg bg-action-primary px-5 py-3 text-sm font-semibold text-fg-on-clay transition duration-200 hover:bg-action-primary-hover active:bg-action-primary-press motion-safe:active:scale-[0.97] max-sm:px-3.5"
        >
          <span className="max-sm:hidden">搜索</span>
          <CornerDownLeft
            className="size-4 motion-safe:transition-transform duration-300 ease-out-expo motion-safe:group-hover/submit:-translate-x-0.5"
            strokeWidth={1.75}
            aria-hidden
          />
        </button>
      </div>

      <div
        ref={examples.ref}
        data-fade-end={examples.fadeEnd || undefined}
        className="flex flex-wrap items-center gap-2 border-t border-border-subtle bg-paper-50 px-4 py-3 text-xs text-fg-3 max-sm:flex-nowrap max-sm:overflow-x-auto max-sm:px-3 max-sm:[scrollbar-width:none] max-sm:data-fade-end:[mask-image:linear-gradient(to_right,black_82%,transparent)]"
      >
        <span className="mr-1 shrink-0 font-mono text-3xs whitespace-nowrap uppercase tracking-caps text-fg-3">
          试试
        </span>
        {EXAMPLE_QUERIES.map((ex) => {
          const m = SEARCH_MODES.find((s) => s.id === ex.mode);
          return (
            <button
              key={ex.text}
              type="button"
              onClick={() => applyExample(ex.mode, ex.text)}
              className={cn(
                "inline-flex items-center gap-1.5 whitespace-nowrap rounded-full border border-border-default bg-bg-elevated px-3 py-1 text-xs text-fg-2 transition duration-200 ease-out-expo hover:border-border-hover hover:text-ink-900 motion-safe:hover:-translate-y-px",
                m?.mono && "font-mono",
              )}
            >
              <span className="font-mono text-3xs uppercase tracking-mono text-fg-3">
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
