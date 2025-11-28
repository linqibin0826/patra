package com.patra.starter.mybatis.injector;

import com.baomidou.mybatisplus.core.injector.AbstractMethod;
import com.baomidou.mybatisplus.core.injector.DefaultSqlInjector;
import com.baomidou.mybatisplus.core.metadata.TableInfo;
import com.baomidou.mybatisplus.extension.injector.methods.InsertBatchSomeColumn;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

/// Patra 自定义 SQL 注入器。
///
/// 扩展 MyBatis-Plus 默认注入器，添加 `InsertBatchSomeColumn` 批量插入方法。
/// 此注入器会自动将批量插入方法注入到所有继承 `PatraBaseMapper` 的 Mapper 中。
///
/// ## 注入的方法
///
/// - `insertBatchSomeColumn`：生成单条 INSERT 语句，多行 VALUES，性能优异
///
/// ## 字段排除规则
///
/// - 逻辑删除字段（`@TableLogic`）：由框架自动设置默认值
/// - 其他所有字段（包括审计字段）：正常插入
///
/// @author Patra Team
/// @since 0.1.0
@Slf4j
public class PatraSqlInjector extends DefaultSqlInjector {

  @Override
  public List<AbstractMethod> getMethodList(Class<?> mapperClass, TableInfo tableInfo) {
    List<AbstractMethod> methodList = super.getMethodList(mapperClass, tableInfo);

    // 添加批量插入方法，排除逻辑删除字段
    methodList.add(new InsertBatchSomeColumn(tableFieldInfo -> !tableFieldInfo.isLogicDelete()));

    log.debug("已注入 InsertBatchSomeColumn 方法到 Mapper: {}", mapperClass.getName());
    return methodList;
  }
}
