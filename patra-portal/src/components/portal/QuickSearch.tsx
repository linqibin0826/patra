"use client";

import { Dialog } from "@base-ui/react/dialog";
import { useRouter } from "next/navigation";
import { toast } from "sonner";
import { Composer } from "@/components/portal/Composer";
import { composerTarget } from "@/lib/portal-api/paper-search";

const INPUT_ID = "quick-search-input";

interface QuickSearchProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
}

/// ⌘K 快速检索：任何页面都可唤起的居中对话框，内嵌首页同款 Composer，提交复用 composerTarget 的跳转规则。
/// 遮罩用深墨色（70%）把注意力收拢到检索框，键位提示以纸色字写在遮罩上。
export function QuickSearch({ open, onOpenChange }: QuickSearchProps) {
  const router = useRouter();
  return (
    <Dialog.Root open={open} onOpenChange={onOpenChange}>
      <Dialog.Portal>
        <Dialog.Backdrop className="fixed inset-0 z-50 bg-ink-900/70 backdrop-blur-sm transition-opacity duration-300 data-ending-style:opacity-0 data-starting-style:opacity-0" />
        <Dialog.Popup
          initialFocus={() => document.getElementById(INPUT_ID)}
          className="fixed top-[16vh] left-1/2 z-50 w-[min(760px,calc(100vw-32px))] -translate-x-1/2 transition-[opacity,scale,translate] duration-300 ease-out-expo data-ending-style:-translate-y-1 data-ending-style:scale-[0.98] data-ending-style:opacity-0 data-starting-style:-translate-y-2 data-starting-style:scale-[0.98] data-starting-style:opacity-0 max-sm:top-4"
        >
          <Dialog.Title className="sr-only">快速检索</Dialog.Title>
          <Composer
            inputId={INPUT_ID}
            onSubmit={({ mode, value }) => {
              const target = composerTarget(mode, value);
              if (target.kind === "invalid") {
                toast(target.message);
                return;
              }
              if (target.kind === "ok") {
                onOpenChange(false);
                router.push(target.href);
              }
            }}
          />
          {/* 触屏没有 Esc：给一个看得见的关闭入口（点遮罩同样可关） */}
          <Dialog.Close className="mx-auto mt-4 flex h-10 items-center rounded-full px-5 text-sm font-medium text-fg-inverse-1 sm:hidden">
            取消
          </Dialog.Close>
          <p
            aria-hidden
            className="mt-4 flex justify-center gap-6 font-mono text-2xs tracking-mono text-fg-inverse-1 max-sm:hidden"
          >
            <span>↵ 检索</span>
            <span>Tab 切换检索方式</span>
            <span>Esc 关闭</span>
          </p>
        </Dialog.Popup>
      </Dialog.Portal>
    </Dialog.Root>
  );
}
