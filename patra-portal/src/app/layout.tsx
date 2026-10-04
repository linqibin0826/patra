import type { Metadata, Viewport } from "next";
import { IBM_Plex_Mono, Inter, Newsreader, Noto_Sans_SC, Noto_Serif_SC } from "next/font/google";
import { Toaster } from "@/components/ui/sonner";
import { cn } from "@/lib/utils";
import { QueryProvider } from "@/providers/query-provider";
import "./globals.css";

const inter = Inter({
  subsets: ["latin"],
  variable: "--font-inter",
  display: "swap",
  axes: ["opsz"],
});
// opsz 轴随字号自动切换光学尺寸：海报级标题锋利、正文段落稳健
const newsreader = Newsreader({
  subsets: ["latin"],
  variable: "--font-newsreader",
  display: "swap",
  style: ["normal", "italic"],
  axes: ["opsz"],
});
const plexMono = IBM_Plex_Mono({
  subsets: ["latin"],
  variable: "--font-plex-mono",
  display: "swap",
  weight: ["400", "500", "600"],
});
const notoSansSC = Noto_Sans_SC({
  subsets: ["latin"],
  variable: "--font-noto-sans-sc",
  display: "swap",
  weight: ["400", "500", "600", "700"],
});
const notoSerifSC = Noto_Serif_SC({
  subsets: ["latin"],
  variable: "--font-noto-serif-sc",
  display: "swap",
  weight: ["400", "500", "600", "700"],
});

export const metadata: Metadata = {
  title: "Patra · 医学文献门户",
  description:
    "Patra 汇集 PubMed、Europe PMC、Crossref 等 10 个来源的学术文献，提供统一检索、出处追溯与 AI 辅助速读。",
};

export const viewport: Viewport = {
  themeColor: "#f7f2e8",
};

export default function RootLayout({ children }: { children: React.ReactNode }) {
  return (
    <html
      lang="zh-CN"
      // globals.css 给 html 开了平滑滚动；声明后 Next 在换页回顶时临时关掉它，换页瞬间回顶
      data-scroll-behavior="smooth"
      className={cn(
        inter.variable,
        newsreader.variable,
        plexMono.variable,
        notoSansSC.variable,
        notoSerifSC.variable,
      )}
    >
      <body>
        {/* 「回到页首」锚点：放在文档最顶、不吸顶的元素上（挂在吸顶导航上时浏览器按其吸住的位置计算，只滚动一小段） */}
        <div id="top" aria-hidden />
        <QueryProvider>
          {children}
          <Toaster richColors position="bottom-right" />
        </QueryProvider>
      </body>
    </html>
  );
}
