import type { Metadata, Viewport } from "next";
import {
  Inter,
  JetBrains_Mono,
  Noto_Sans_SC,
  Noto_Serif_SC,
  Source_Serif_4,
} from "next/font/google";
import { Toaster } from "@/components/ui/sonner";
import { cn } from "@/lib/utils";
import { QueryProvider } from "@/providers/query-provider";
import "./globals.css";

const inter = Inter({
  subsets: ["latin"],
  variable: "--font-inter",
  display: "swap",
});
const sourceSerif = Source_Serif_4({
  subsets: ["latin"],
  variable: "--font-source-serif",
  display: "swap",
});
const jbMono = JetBrains_Mono({
  subsets: ["latin"],
  variable: "--font-jetbrains-mono",
  display: "swap",
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
  themeColor: "#176B62",
};

export default function RootLayout({ children }: { children: React.ReactNode }) {
  return (
    <html
      lang="zh-CN"
      className={cn(
        inter.variable,
        sourceSerif.variable,
        jbMono.variable,
        notoSansSC.variable,
        notoSerifSC.variable,
      )}
    >
      <body>
        <QueryProvider>
          {children}
          <Toaster richColors position="bottom-right" />
        </QueryProvider>
      </body>
    </html>
  );
}
