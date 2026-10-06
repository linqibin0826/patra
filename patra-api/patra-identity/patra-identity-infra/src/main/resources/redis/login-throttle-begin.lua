-- 开始一次登录尝试（在校验密码之前）。
-- KEYS[1] 失败计数  KEYS[2] 在途登记（有序集合，分数是过期时间）  KEYS[3] 锁
-- ARGV[1] 失败次数上限  ARGV[2] 在途登记 ID  ARGV[3] 在途登记的过期毫秒
-- 返回：0 放行；-1 失败数加在途数已到上限；正数是锁的剩余毫秒
local lockTtl = redis.call('PTTL', KEYS[3])
if lockTtl > 0 then
  return lockTtl
end
local time = redis.call('TIME')
local now = tonumber(time[1]) * 1000 + math.floor(tonumber(time[2]) / 1000)
redis.call('ZREMRANGEBYSCORE', KEYS[2], '-inf', now)
local failures = tonumber(redis.call('GET', KEYS[1]) or '0')
local inflight = redis.call('ZCARD', KEYS[2])
if failures + inflight >= tonumber(ARGV[1]) then
  return -1
end
local ttl = tonumber(ARGV[3])
redis.call('ZADD', KEYS[2], now + ttl, ARGV[2])
redis.call('PEXPIRE', KEYS[2], ttl)
return 0
