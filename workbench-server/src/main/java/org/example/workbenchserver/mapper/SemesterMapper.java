package org.example.workbenchserver.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.example.workbenchserver.entity.Semester;

/**
 * 学期 Mapper。
 *
 * <p>没有自定义方法：列表、增删改查都能用 {@code BaseMapper} 现成的。
 * 学期是低频操作，一个用户的学期总共也就几条到几十条，不分页。
 */
@Mapper
public interface SemesterMapper extends BaseMapper<Semester> {

}
