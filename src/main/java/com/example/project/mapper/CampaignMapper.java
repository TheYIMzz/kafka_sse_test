// src/main/java/com/example/project/mapper/CampaignMapper.java
package com.example.project.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import com.example.project.domain.Campaign;

@Mapper
public interface CampaignMapper {
    List<Campaign> selectByStatus(@Param("status") String status);
    int updateStatus(@Param("id") Long id, @Param("status") String status);

    List<Campaign> selectByStatusForUpdate(String published);
}
