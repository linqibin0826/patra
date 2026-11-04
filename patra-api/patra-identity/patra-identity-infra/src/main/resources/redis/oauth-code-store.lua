-- 写一个注册验证码，覆盖旧的：旧码作废，错误次数重新算。
-- KEYS[1] 验证码的键（HASH）
-- ARGV[1] 用户名  ARGV[2] 邮箱  ARGV[3] 验证码哈希  ARGV[4] 过期时间（epoch 毫秒）
-- ARGV[5] 最早可以重发的时间（epoch 毫秒）  ARGV[6] 有效期毫秒
redis.call('DEL', KEYS[1])
redis.call('HSET', KEYS[1], 'username', ARGV[1], 'email', ARGV[2], 'code_hash', ARGV[3],
  'attempts', '0', 'expires_at', ARGV[4], 'next_resend_at', ARGV[5])
redis.call('PEXPIRE', KEYS[1], ARGV[6])
return 1
