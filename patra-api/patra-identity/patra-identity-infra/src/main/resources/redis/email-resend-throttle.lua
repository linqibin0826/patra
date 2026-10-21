-- 重发验证邮件的限流：冷却 + 三个滑动窗口，一次判完一次计数。
-- KEYS[1] 冷却  KEYS[2] 账号小时窗口  KEYS[3] 账号天窗口  KEYS[4] IP 小时窗口
-- ARGV[1] now 毫秒  ARGV[2] 冷却毫秒  ARGV[3] 小时窗口毫秒  ARGV[4] 天窗口毫秒
-- ARGV[5] 小时上限  ARGV[6] 天上限  ARGV[7] IP 小时上限  ARGV[8] 1 判且计 / 0 只计
-- ARGV[9] 1 有 IP / 0 没有  ARGV[10] 成员后缀
-- 返回：0 放行并已计数；正数是还要等的毫秒。时间由 Java 传入，脚本不调 TIME。
local now = tonumber(ARGV[1])
local cooldown = tonumber(ARGV[2])
local windows = {
  { KEYS[2], tonumber(ARGV[3]), tonumber(ARGV[5]) },
  { KEYS[3], tonumber(ARGV[4]), tonumber(ARGV[6]) }
}
if ARGV[9] == '1' then
  windows[#windows + 1] = { KEYS[4], tonumber(ARGV[3]), tonumber(ARGV[7]) }
end
if ARGV[8] == '1' then
  local wait = 0
  local last = tonumber(redis.call('GET', KEYS[1]) or '0')
  if last > 0 and now - last < cooldown then
    wait = math.max(wait, last + cooldown - now)
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
redis.call('SET', KEYS[1], now, 'PX', cooldown)
local member = now .. ':' .. ARGV[10]
for _, w in ipairs(windows) do
  redis.call('ZADD', w[1], now, member)
  redis.call('PEXPIRE', w[1], w[2])
end
return 0
