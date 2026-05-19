import type { Metadata, Viewport } from "next";
import { Geist } from "next/font/google";
import { cn } from "@/lib/utils";
import { QueryProvider } from "@/providers/query-provider";
import "./globals.css";

const geist = Geist({ subsets: ["latin"], variable: "--font-sans" });

export const metadata: Metadata = {
  title: "patra-portal",
  description: "Patra C-end portal — medical literature discovery",
};

export const viewport: Viewport = {
  themeColor: "#4338ca",
};

export default function RootLayout({ children }: { children: React.ReactNode }) {
  return (
    <html lang="zh-CN" className={cn("font-sans", geist.variable)}>
      <body>
        <QueryProvider>{children}</QueryProvider>
      </body>
    </html>
  );
}
