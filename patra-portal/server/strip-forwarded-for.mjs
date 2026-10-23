// 生产入口的预加载模块：`node --import ./strip-forwarded-for.mjs server.js`。
// Next 16 对入站请求做 `req.headers['x-forwarded-for'] ??= socket.remoteAddress`：浏览器自己带了这个头，
// Next 就原样保留，Server Action、Route Handler、proxy 读到的都是浏览器可控的值。这里在 Next 处理请求
// 之前删掉它，Next 填进去的就只会是连接上的真实地址（工程 spec 2026-10-23-portal-user-center-profile §4.1）。
import { Server } from "node:http";

const INSTALLED = Symbol.for("patra.stripForwardedFor");

/// 给 `http.Server` 的 `request` 事件套一层：先删掉 `x-forwarded-for`，再交给原来的监听器。重复调用只包一层。
export function install() {
  if (Server.prototype[INSTALLED]) {
    return;
  }
  const emit = Server.prototype.emit;
  Server.prototype.emit = function emitWithoutForwardedFor(event, ...args) {
    if (event === "request") {
      delete args[0].headers["x-forwarded-for"];
    }
    return emit.call(this, event, ...args);
  };
  Server.prototype[INSTALLED] = true;
}

install();
