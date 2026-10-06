-- 结算一次登录尝试（在校验密码之后，必定执行一次）。
-- KEYS[1] 失败计数  KEYS[2] 在途登记  KEYS[3] 锁
-- ARGV[1] 在途登记 ID  ARGV[2] 结果：SUCCESS / FAILURE / CANCEL
-- ARGV[3] 失败次数上限  ARGV[4] 计数窗口毫秒  ARGV[5] 锁定毫秒
-- 返回：0 没有锁；正数是锁的剩余毫秒
redis.call('ZREM', KEYS[2], ARGV[1])
local outcome = ARGV[2]
if outcome == 'SUCCESS' then
  redis.call('DEL', KEYS[1])
  return 0
end
if outcome ~= 'FAILURE' then
  return 0
end
local lockTtl = redis.call('PTTL', KEYS[3])
if lockTtl > 0 then
  return lockTtl
end
local failures = redis.call('INCR', KEYS[1])
if failures == 1 then
  redis.call('PEXPIRE', KEYS[1], ARGV[4])
end
if failures >= tonumber(ARGV[3]) then
  redis.call('SET', KEYS[3], '1', 'PX', ARGV[5])
  redis.call('DEL', KEYS[1])
  return tonumber(ARGV[5])
end
return 0
