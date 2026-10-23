// @vitest-environment node
import { createServer, request, Server } from "node:http";
import type { AddressInfo } from "node:net";
import { install } from "@server/strip-forwarded-for.mjs";
import { describe, expect, it } from "vitest";

/// 起一个只记下请求头的服务，发一次带指定头的请求，返回 listener 看到的请求头。
async function headersSeenByListener(
  headers: Record<string, string>,
): Promise<Record<string, string | string[] | undefined>> {
  let seen: Record<string, string | string[] | undefined> = {};
  const server = createServer((req, res) => {
    seen = { ...req.headers };
    res.end();
  });
  await new Promise<void>((resolve) => server.listen(0, "127.0.0.1", resolve));
  const { port } = server.address() as AddressInfo;
  await new Promise<void>((resolve, reject) => {
    const req = request({ host: "127.0.0.1", port, path: "/", headers }, (res) => {
      res.resume();
      res.on("end", resolve);
    });
    req.on("error", reject);
    req.end();
  });
  await new Promise<void>((resolve) => server.close(() => resolve()));
  return seen;
}

describe("strip-forwarded-for", () => {
  it("removes_inbound_x_forwarded_for_before_listeners_run", async () => {
    install();
    const seen = await headersSeenByListener({
      "X-Forwarded-For": "203.0.113.9",
      "X-Custom": "keep",
    });
    expect(seen["x-forwarded-for"]).toBeUndefined();
    expect(seen["x-custom"]).toBe("keep");
  });

  it("is_idempotent", () => {
    install();
    const wrapped = Server.prototype.emit;
    install();
    expect(Server.prototype.emit).toBe(wrapped);
  });
});
