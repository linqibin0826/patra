// patra-learn/src/components/code-ref.tsx
// 代码引用：钉住的链到 GitHub 上那次提交的永久地址；pending 只显示路径
import { codeRefUrl, parseCodeRefAt } from "@/lib/content/code-refs";

export function CodeRef({ at, lines }: { at: string; lines?: string }) {
  const ref = parseCodeRefAt(at);
  const label = `${ref?.path ?? at}${lines ? `:${lines}` : ""}`;
  const url = ref ? codeRefUrl(ref, lines) : null;
  return (
    <p className="font-mono text-sm">
      {url ? <a href={url}>{label}</a> : <span>{label}（待钉到提交）</span>}
    </p>
  );
}
