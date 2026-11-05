import type { Provider } from "@/lib/auth/oauth-providers";
import { cn } from "@/lib/utils";

const MARKS: Record<Provider, { src: string; fit: string }> = {
  /// 官方文件是 40px 的白底描边按钮，G 在正中占 20px，文件不改：不缩放，按原始大小居中裁出中间的 G
  google: { src: "/oauth/google.svg", fit: "object-none" },
  github: { src: "/oauth/github.svg", fit: "object-contain" },
  orcid: { src: "/oauth/orcid.svg", fit: "object-contain" },
};

/// 平台的官方标识，20px，不改色（简报 §5）。按钮上的文字已经说了是哪家，这里只是装饰
export function ProviderMark({ provider, className }: { provider: Provider; className?: string }) {
  const { src, fit } = MARKS[provider];
  return (
    // biome-ignore lint/performance/noImgElement: 20px 的本地小图标，不需要 next/image 的优化
    <img src={src} alt="" width={20} height={20} className={cn("block size-5", fit, className)} />
  );
}
