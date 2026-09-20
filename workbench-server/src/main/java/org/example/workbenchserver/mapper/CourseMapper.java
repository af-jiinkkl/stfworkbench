package org.example.workbenchserver.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.example.workbenchserver.entity.Course;

/**
 * 课程 Mapper。
 *
 * <p>没有自定义方法。查某学期的课是一句等值查询（{@code semester_id = ?}），
 * 用不着写 XML。
 *
 * <p>注意这里**没有任何"连带删除 / 连带统计"的 SQL**：学期模块要数
 * "这个学期下有几门课"时，走的是 {@code selectCount} 而不是一条手写的
 * {@code COUNT(*)} —— 手写就等于绕开了拦截器自动补 {@code user_id} 那条路，
 * 于是凭空多出一类"条件全靠拦截器补"的语句，而那类语句在本仓库
 * 一律要单独钉用例（见 {@code MemoServiceImpl#count} 的注释）。
 */
@Mapper
public interface CourseMapper extends BaseMapper<Course> {

}
