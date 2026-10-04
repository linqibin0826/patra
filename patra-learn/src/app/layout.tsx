import type { Metadata } from "next";
import { TopBar } from "@/components/top-bar";
import "@fontsource-variable/jetbrains-mono";
import "./globals.css";

export const metadata: Metadata = {
  title: "Patra 学习站",
  description: "把你的系统，一条线一条线学明白",
};

export default function RootLayout({ children }: { children: React.ReactNode }) {
  return (
    <html lang="zh-CN">
      <body className="bg-bg font-sans text-ink antialiased">
        <TopBar />
        {children}
      </body>
    </html>
  );
}
