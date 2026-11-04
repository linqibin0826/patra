import type { Provider } from "@/lib/auth/oauth-providers";
import { cn } from "@/lib/utils";

const MARKS: Record<Provider, string> = {
  google: "/oauth/google.svg",
  github: "/oauth/github.svg",
  orcid: "/oauth/orcid.svg",
};

/// 平台的官方标识，20px，不改色（简报 §5）。按钮上的文字已经说了是哪家，这里只是装饰
export function ProviderMark({ provider, className }: { provider: Provider; className?: string }) {
  return (
    // biome-ignore lint/performance/noImgElement: 20px 的本地小图标，不需要 next/image 的优化
    <img
      src={MARKS[provider]}
      alt=""
      width={20}
      height={20}
      className={cn("block size-5 object-contain", className)}
    />
  );
}
