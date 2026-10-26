/// 从 `Content-Disposition` 取文件名：先取 RFC 5987 的 `filename*=UTF-8''…`（中文收藏夹名），再取 `filename="…"`
export function filenameFromDisposition(disposition: string | null): string {
  const extended = disposition ? /filename\*=UTF-8''([^;]+)/i.exec(disposition)?.[1] : undefined;
  if (extended) {
    try {
      return decodeURIComponent(extended.trim());
    } catch {
      // 编码坏了：退回普通文件名
    }
  }
  const plain = disposition ? /filename="?([^";]+)"?/i.exec(disposition)?.[1] : undefined;
  return plain?.trim() || "patra-export";
}

/// 把下载响应存成文件（浏览器的「下载」），返回文件名。文件不大（5,000 篇 RIS 约 5 MB），整体读成 blob
export async function saveResponseAsFile(res: Response): Promise<string> {
  const filename = filenameFromDisposition(res.headers.get("Content-Disposition"));
  const url = URL.createObjectURL(await res.blob());
  const link = document.createElement("a");
  link.href = url;
  link.download = filename;
  document.body.append(link);
  link.click();
  link.remove();
  setTimeout(() => URL.revokeObjectURL(url), 0);
  return filename;
}
