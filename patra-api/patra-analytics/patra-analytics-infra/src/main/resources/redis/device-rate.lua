-- 爬虫频率规则的滑动窗口：同一设备 5 分钟内的文献浏览条数（埋点方案 §6.7）。
-- KEYS[1] 窗口键 ana:bot-rate:device:{device_id}
-- ARGV[1] now 毫秒  ARGV[2] 窗口毫秒  ARGV[3] 成员（event_id，重发只占一个位置）  ARGV[4] 1 计入 / 0 只读
-- 返回窗口内的条数。时间由 Java 传入，脚本不调 TIME。
local now = tonumber(ARGV[1])
local window = tonumber(ARGV[2])
redis.call('ZREMRANGEBYSCORE', KEYS[1], '-inf', now - window)
if ARGV[4] == '1' then
  redis.call('ZADD', KEYS[1], now, ARGV[3])
  redis.call('PEXPIRE', KEYS[1], window)
end
return redis.call('ZCARD', KEYS[1])
