package com.tool.mapper;


import com.tool.vo.TSimcardInfo;
import org.apache.ibatis.annotations.Param;

import java.util.Collection;
import java.util.List;

public interface UploadMapper {


    int batchInsert(List<String> lines);

    void batchUpdate(List<TSimcardInfo> tSimcardInfos);

    List<TSimcardInfo> selectNewInfo(@Param("list") List<TSimcardInfo> batchLineList);
}
