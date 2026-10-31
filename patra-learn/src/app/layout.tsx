import type { Metadata } from "next";
import "@fontsource-variable/jetbrains-mono";
import "@fontsource-variable/source-serif-4";
import "./globals.css";
import { SiteNav } from "@/components/site-nav";

export const metadata: Metadata = {
  title: { default: "Patra 学习站", template: "%s · Patra 学习站" },
  description: "借 Patra 这个真实项目，从入门学到进阶",
};

export default function RootLayout({ children }: { children: React.ReactNode }) {
  return (
    <html lang="zh-CN">
      <body className="min-h-screen bg-ground font-body text-ink-2 antialiased">
        <SiteNav />
        {children}
      </body>
    </html>
  );
}
