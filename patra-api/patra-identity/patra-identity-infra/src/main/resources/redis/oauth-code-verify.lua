-- 校验注册验证码。对了立刻删（只能用一次）；错满次数也删（作废）。原子：并发猜码不会多算机会。
-- KEYS[1] 验证码的键（HASH）
-- ARGV[1] 提交的验证码的哈希  ARGV[2] 最多错几次
-- 返回：{'NONE'} 没有可用的码；{'MISMATCH'} 不对；{'EXHAUSTED'} 这次错满了；{'MATCH', 用户名, 邮箱}
local stored = redis.call('HMGET', KEYS[1], 'code_hash', 'username', 'email')
if not stored[1] then
  return {'NONE'}
end
if stored[1] == ARGV[1] then
  redis.call('DEL', KEYS[1])
  return {'MATCH', stored[2], stored[3]}
end
if redis.call('HINCRBY', KEYS[1], 'attempts', 1) >= tonumber(ARGV[2]) then
  redis.call('DEL', KEYS[1])
  return {'EXHAUSTED'}
end
return {'MISMATCH'}
