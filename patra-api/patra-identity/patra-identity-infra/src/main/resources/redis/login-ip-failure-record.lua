-- 记一次按客户端网络的登录失败。窗口从第一次失败算起，后面的失败不续期。
-- KEYS[1] 计数键  ARGV[1] 窗口毫秒
-- 返回：记完之后的失败次数
local failures = redis.call('INCR', KEYS[1])
if failures == 1 then
  redis.call('PEXPIRE', KEYS[1], ARGV[1])
end
return failures
