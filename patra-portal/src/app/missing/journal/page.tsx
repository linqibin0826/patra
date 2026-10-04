import { notFound } from "next/navigation";

/// 中间层（src/proxy.ts）把直接打开的不存在期刊详情改写到这里：此处没有加载骨架，
/// notFound() 在任何内容流出之前发生，响应是真正的 HTTP 404。
export default function MissingJournal() {
  notFound();
}
