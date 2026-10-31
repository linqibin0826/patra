// 代码引用（画板 l-codebox 的头部）：钉住的链到 GitHub 上那次提交的永久地址；pending 只显示路径
import { codeRefUrl, parseCodeRefAt } from "@/lib/content/code-refs";

export function CodeRef({ at, lines }: { at: string; lines?: string }) {
  const ref = parseCodeRefAt(at);
  const label = `${ref?.path ?? at}${lines ? `:${lines}` : ""}`;
  const url = ref ? codeRefUrl(ref, lines) : null;
  return (
    <div className="my-4 flex flex-wrap items-center justify-between gap-x-4 gap-y-1 rounded-xl bg-ink px-4 py-2.5 font-mono text-code-dim text-xs">
      <span className="min-w-0 break-all">{label}</span>
      {url && ref ? (
        <a href={url} className="whitespace-nowrap text-code-link hover:text-code-ink">
          在 GitHub 看 @ {ref.sha.slice(0, 7)}
        </a>
      ) : (
        <span className="whitespace-nowrap">待钉到提交</span>
      )}
    </div>
  );
}
