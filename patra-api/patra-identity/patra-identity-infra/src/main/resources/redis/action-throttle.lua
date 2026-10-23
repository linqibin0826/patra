-- 通用的操作限流：一个可选的冷却 + 任意个滑动窗口，一次判完一次计数。
-- 重发验证邮件、找回密码的申请与提交、改邮箱的申请共用。
-- KEYS[1] 冷却键（没有冷却时传占位的 -，不读不写）  KEYS[2..] 窗口键，按顺序
-- ARGV[1] now 毫秒  ARGV[2] 冷却毫秒（0 = 没有冷却）  ARGV[3] 1 判且计 / 0 只计  ARGV[4] 成员后缀
-- ARGV[5], ARGV[6] 第 1 个窗口的毫秒数、上限；ARGV[7], ARGV[8] 第 2 个窗口 ……（窗口数 = #KEYS - 1）
-- 返回：0 放行并已计数；正数是还要等的毫秒（各维度里最长的）。时间由 Java 传入，脚本不调 TIME。
local now = tonumber(ARGV[1])
local cooldown = tonumber(ARGV[2])
local windows = {}
for i = 2, #KEYS do
  local base = 5 + (i - 2) * 2
  windows[#windows + 1] = { KEYS[i], tonumber(ARGV[base]), tonumber(ARGV[base + 1]) }
end
if ARGV[3] == '1' then
  local wait = 0
  if cooldown > 0 then
    local last = tonumber(redis.call('GET', KEYS[1]) or '0')
    if last > 0 and now - last < cooldown then
      wait = math.max(wait, last + cooldown - now)
    end
  end
  for _, w in ipairs(windows) do
    redis.call('ZREMRANGEBYSCORE', w[1], '-inf', now - w[2])
    if redis.call('ZCARD', w[1]) >= w[3] then
      local oldest = redis.call('ZRANGE', w[1], 0, 0, 'WITHSCORES')
      wait = math.max(wait, tonumber(oldest[2]) + w[2] - now)
    end
  end
  if wait > 0 then
    return wait
  end
end
if cooldown > 0 then
  redis.call('SET', KEYS[1], now, 'PX', cooldown)
end
local member = now .. ':' .. ARGV[4]
for _, w in ipairs(windows) do
  redis.call('ZADD', w[1], now, member)
  redis.call('PEXPIRE', w[1], w[2])
end
return 0
