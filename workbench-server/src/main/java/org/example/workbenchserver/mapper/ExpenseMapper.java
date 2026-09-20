package org.example.workbenchserver.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.example.workbenchserver.entity.Expense;

/**
 * 消费记录 Mapper。
 *
 * <p>没有自定义方法：连两个汇总查询也是用 {@code QueryWrapper} 的
 * {@code select(...).groupBy(...)} 拼出来的，用不着写 XML
 * （CLAUDE.md 的分层约定是"复杂 SQL 写在 xml"，而这两条不算复杂）。
 *
 * <p>走 Wrapper 还有个实际好处：租户插件与逻辑删除的注入路径，
 * 跟普通的增删查改是**同一条**。要是改成手写 SQL，
 * 就凭空多出一类"条件全靠拦截器补"的语句 —— 那类语句在本仓库
 * 一律要单独钉用例（见 {@code MemoServiceImpl#count} 的注释），
 * 没必要自己给自己加。
 */
@Mapper
public interface ExpenseMapper extends BaseMapper<Expense> {

}
