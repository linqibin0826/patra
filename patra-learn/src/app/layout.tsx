import type { Metadata } from "next";
import "@fontsource-variable/jetbrains-mono";
import "./globals.css";

export const metadata: Metadata = {
  title: "Patra 学习站",
  description: "借 Patra 这个真实项目，从入门学到进阶",
};

export default function RootLayout({ children }: { children: React.ReactNode }) {
  return (
    <html lang="zh-CN">
      <body className="bg-bg font-sans text-ink antialiased">{children}</body>
    </html>
  );
}
