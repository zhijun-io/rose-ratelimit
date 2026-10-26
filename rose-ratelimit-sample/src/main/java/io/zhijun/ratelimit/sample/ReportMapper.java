package io.zhijun.ratelimit.sample;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface ReportMapper {
    @Select("SELECT COUNT(*) FROM sample_report")
    long countRows();
}
