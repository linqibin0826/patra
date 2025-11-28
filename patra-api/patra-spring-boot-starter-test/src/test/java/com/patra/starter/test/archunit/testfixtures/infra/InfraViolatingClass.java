package com.patra.starter.test.archunit.testfixtures.infra;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;

/// 测试夹具：infra 包下继承 ServiceImpl 的违规类。
@SuppressWarnings("unused")
public class InfraViolatingClass extends ServiceImpl<InfraViolatingClass.TestMapper, Object> {

  interface TestMapper extends BaseMapper<Object> {}
}
