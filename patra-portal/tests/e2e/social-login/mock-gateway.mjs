// 第三方登录端到端测试用的模拟网关和模拟平台（决策 H：测试不连真实平台）。
// 按 identity 设计第 2 节的契约回应，并记下收到的请求，测试据此断言门户转发了什么。
// state 和 ticket 都严格核对：只认自己发过的值，注入测试才有意义。

import { randomBytes } from "node:crypto";
import { createServer } from "node:http";

const PORT = Number(process.env.MOCK_GATEWAY_PORT ?? 4031);
const PORTAL = process.env.MOCK_PORTAL_ORIGIN ?? "http://localhost:4030";
const SELF = `http://localhost:${PORT}`;
const IDENTITY = "/patra-identity";

const ACCOUNTS = {
  google: { label: "chen.yu@example.com", username: null, email: "chen.yu@example.com" },
  github: { label: "chenyu-med", username: "chenyu-med", email: "chen.yu@example.com" },
  orcid: { label: "0000-0002-1825-0097", username: null, email: null },
};

/// 已验证的正确验证码；`EXPIRED_CODE` 模拟过期或错满 5 次
const RIGHT_CODE = "483920";
const EXPIRED_CODE = "000000";
const TAKEN_EMAIL = "lin.zhiyuan@example.com";

function freshState() {
  return {
    /// 回调怎么答：new 新用户、existing 老用户、deleting 冷静期、banned 封禁、link 绑定、taken 已绑给别人
    callback: "new",
    /// 没配置的平台：发起入口回 404
    unconfigured: [],
    hasPassword: true,
    emailVerified: true,
    linked: [],
    /// 再认证窗口开着
    reauthed: false,
    /// 就地绑定提交验证码时的冲突：taken 第三方账号已绑给别人、already 目标账号已绑这一家
    linkConflict: null,
    states: new Set(),
    tickets: new Map(),
    log: [],
  };
}

let state = freshState();

function user() {
  return {
    userId: "319041872872550658",
    email: "chen.yu@example.com",
    emailVerified: state.emailVerified,
    username: "chen-yu",
    displayName: "陈宇",
    avatarUrl: null,
    avatarPalette: 1,
    status: state.callback === "deleting" ? "PENDING_DELETION" : "ACTIVE",
    deletionDueAt: state.callback === "deleting" ? "2099-01-01T00:00:00Z" : null,
    nextUsernameChangeAt: null,
    hasPassword: state.hasPassword,
    accountType: "user",
  };
}

function json(res, status, body) {
  res.writeHead(status, { "content-type": "application/json" });
  res.end(JSON.stringify(body));
}

function problem(res, status, extra = {}) {
  res.writeHead(status, { "content-type": "application/problem+json" });
  res.end(JSON.stringify({ code: `IDN-0${status}`, traits: [], ...extra }));
}

/// 从请求头取 ticket 对应的待补填记录；不合格时回 422 并返回 null
function pendingFrom(req, res) {
  const ticket = req.headers["x-patra-registration-ticket"];
  const pending = state.tickets.get(ticket);
  if (!pending) {
    problem(res, 422, { errors: [{ field: "ticket", code: ticket ? "INVALID" : "REQUIRED" }] });
    return null;
  }
  return pending;
}

function handleCallback(res, provider, query) {
  const given = query.get("state");
  if (!state.states.delete(given)) {
    return problem(res, 422, { traits: ["RULE_VIOLATION", "OAUTH_REJECTED"] });
  }
  if (query.get("error") === "access_denied") {
    return problem(res, 422, { traits: ["RULE_VIOLATION", "OAUTH_CANCELLED"] });
  }
  const account = ACCOUNTS[provider];
  switch (state.callback) {
    case "banned":
      return problem(res, 403, { traits: ["FORBIDDEN"] });
    case "taken":
      return problem(res, 409, { traits: ["CONFLICT", "PROVIDER_ACCOUNT_TAKEN"] });
    case "unreachable":
      return problem(res, 503, { traits: ["DEP_UNAVAILABLE"] });
    case "existing":
    case "deleting":
      return json(res, 200, {
        result: "AUTHENTICATED",
        sessionToken: "patra_user_chen_yu",
        user: user(),
      });
    case "link":
      state.linked.push({
        provider,
        accountLabel: account.label,
        linkedAt: "2026-11-04T01:00:00Z",
        lastLoginAt: null,
      });
      return json(res, 200, { result: "LINKED", provider, accountLabel: account.label });
    default: {
      const ticket = randomBytes(32).toString("base64url");
      state.tickets.set(ticket, { provider, account, pendingCode: null });
      return json(res, 200, {
        result: "REGISTRATION_REQUIRED",
        ticket,
        provider,
        accountLabel: account.label,
        suggestedUsername: account.username,
        suggestedEmail: account.email,
        expiresAt: "2099-01-01T00:30:00Z",
      });
    }
  }
}

function handleRegistration(req, res, path, body) {
  const pending = pendingFrom(req, res);
  if (!pending) return;
  if (path === "") {
    return json(res, 200, {
      provider: pending.provider,
      accountLabel: pending.account.label,
      suggestedUsername: pending.account.username,
      suggestedEmail: pending.account.email,
      expiresAt: "2099-01-01T00:30:00Z",
      pendingCode: pending.pendingCode,
    });
  }
  if (path === "/code") {
    if (body.email === TAKEN_EMAIL) {
      return problem(res, 409, { errors: [{ field: "email", code: "ALREADY_REGISTERED" }] });
    }
    return sendCode(res, pending, "REGISTER", body.username, body.email);
  }
  if (path === "/link-code") {
    // 邮箱未注册、未验证、封禁时响应也一样，只是不发信
    return sendCode(res, pending, "LINK", null, body.email);
  }
  if (body.code === EXPIRED_CODE) {
    return problem(res, 422, { errors: [{ field: "code", code: "EXPIRED" }] });
  }
  if (body.code !== RIGHT_CODE) {
    return problem(res, 422, { errors: [{ field: "code", code: "INVALID" }] });
  }
  if (path === "/link") {
    if (state.linkConflict === "taken") {
      return problem(res, 409, { traits: ["CONFLICT", "PROVIDER_ACCOUNT_TAKEN"] });
    }
    if (state.linkConflict === "already") {
      return problem(res, 409, { traits: ["CONFLICT", "PROVIDER_ALREADY_LINKED"] });
    }
    // 凭证用过就作废，重放回 422 ticket
    state.tickets.delete(req.headers["x-patra-registration-ticket"]);
    return json(res, 200, { sessionToken: "patra_user_chen_yu", user: user() });
  }
  state.hasPassword = false;
  return json(res, 201, { sessionToken: "patra_user_chen_yu", user: user() });
}

/// 发码，记下用途：刷新页面时靠它回到对应的步骤
function sendCode(res, pending, purpose, username, email) {
  pending.pendingCode = {
    purpose,
    username,
    email,
    codeExpiresAt: "2099-01-01T00:10:00Z",
    nextResendAt: new Date(Date.now() + 60_000).toISOString(),
  };
  const { purpose: _purpose, username: _username, ...sent } = pending.pendingCode;
  return json(res, 202, sent);
}

function handleLinkMethods(req, res, path) {
  if (!req.headers.authorization) return problem(res, 401);
  if (path === "/me/login-methods") {
    return json(res, 200, { hasPassword: state.hasPassword, linked: state.linked });
  }
  const [, , , provider, action] = path.split("/");
  if (req.method === "POST" && action === "link") {
    if (state.unconfigured.includes(provider)) return problem(res, 404);
    if (!state.reauthed) return problem(res, 403, { traits: ["FORBIDDEN", "REAUTH_REQUIRED"] });
    const issued = randomBytes(8).toString("hex");
    state.states.add(issued);
    return json(res, 200, {
      authorizationUrl: `${SELF}/platform/authorize?provider=${provider}&state=${issued}`,
    });
  }
  if (req.method === "DELETE") {
    if (!state.hasPassword && state.linked.length === 1) {
      return problem(res, 409, { traits: ["CONFLICT", "LAST_LOGIN_METHOD"] });
    }
    if (!state.reauthed) return problem(res, 403, { traits: ["FORBIDDEN", "REAUTH_REQUIRED"] });
    state.linked = state.linked.filter((item) => item.provider !== provider);
    res.writeHead(204);
    return res.end();
  }
  return problem(res, 404);
}

/// 模拟平台的授权页：真实平台由用户选择同意或拒绝
function platformPage(res, query) {
  const provider = query.get("provider");
  const issued = query.get("state");
  const back = `${PORTAL}/api/auth/oauth/callback/${provider}`;
  res.writeHead(200, { "content-type": "text/html; charset=utf-8" });
  res.end(`<!doctype html><title>模拟平台</title>
<h1>授权 Patra</h1>
<a href="${back}?code=platform-code&state=${issued}">同意</a>
<a href="${back}?error=access_denied&state=${issued}">拒绝</a>`);
}

function route(req, res, url, body) {
  const path = url.pathname;
  if (path === "/__reset") {
    state = { ...freshState(), ...body };
    return json(res, 200, {});
  }
  if (path === "/__update") {
    Object.assign(state, body);
    return json(res, 200, {});
  }
  if (path === "/__log") {
    return json(res, 200, state.log);
  }
  if (path === "/platform/authorize") {
    return platformPage(res, url.searchParams);
  }
  if (!path.startsWith(IDENTITY)) {
    return problem(res, 404);
  }
  const inner = path.slice(IDENTITY.length);
  state.log.push({
    method: req.method,
    path: inner,
    query: url.search,
    authorization: req.headers.authorization ?? null,
    deviceId: req.headers["x-patra-device-id"] ?? null,
    ticket: req.headers["x-patra-registration-ticket"] ?? null,
    body,
  });
  const start = inner.match(/^\/oauth2\/authorization\/(\w+)$/);
  if (start) {
    if (state.unconfigured.includes(start[1])) return problem(res, 404);
    const issued = randomBytes(8).toString("hex");
    state.states.add(issued);
    res.writeHead(302, {
      location: `${SELF}/platform/authorize?provider=${start[1]}&state=${issued}`,
    });
    return res.end();
  }
  const callback = inner.match(/^\/login\/oauth2\/code\/(\w+)$/);
  if (callback) return handleCallback(res, callback[1], url.searchParams);
  if (inner.startsWith("/auth/oauth/registration")) {
    return handleRegistration(req, res, inner.slice("/auth/oauth/registration".length), body);
  }
  if (inner.startsWith("/me/login-methods")) return handleLinkMethods(req, res, inner);
  if (inner === "/auth/me") {
    return req.headers.authorization ? json(res, 200, user()) : problem(res, 401);
  }
  if (inner === "/auth/reauth") {
    if (body.password !== "qingshan-2026") {
      return problem(res, 401, { traits: ["PASSWORD_MISMATCH"] });
    }
    state.reauthed = true;
    return json(res, 200, { sessionToken: null, user: user() });
  }
  if (inner === "/auth/password/forgot") {
    return json(res, 202, {
      email: body.email,
      nextRequestAt: new Date(Date.now() + 60_000).toISOString(),
    });
  }
  if (inner === "/me/sessions") return json(res, 200, { sessions: [] });
  return problem(res, 404);
}

createServer((req, res) => {
  const chunks = [];
  req.on("data", (chunk) => chunks.push(chunk));
  req.on("end", () => {
    const raw = Buffer.concat(chunks).toString();
    route(req, res, new URL(req.url, SELF), raw ? JSON.parse(raw) : {});
  });
}).listen(PORT, () => console.log(`mock gateway on ${PORT}`));
