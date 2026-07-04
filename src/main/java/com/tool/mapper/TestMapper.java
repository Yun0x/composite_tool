package com.tool.mapper;

import com.tool.vo.testVO.TMachineCarInfo;
import com.tool.vo.testVO.TOrderCarInfo;
import com.tool.vo.testVO.TUser;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface TestMapper {
    List<String> getAllIccid();

    void updateMessageLog(String bizId, String errMsg, int sendStatus, String sendTime);

    TMachineCarInfo getMachineInfoByNo(String machineNo);

    Integer getMinOrderId();

    Integer getMaxOrderId();

    Integer checkMachineOrderExist(@Param("machineNo") String machineNo,
                                   @Param("startOrderId") Integer startOrderId,
                                   @Param("endOrderId") Integer endOrderId);

    TOrderCarInfo getFirstOrderByMachineNoInRange(@Param("machineNo") String machineNo,
                                                  @Param("startOrderId") Integer startOrderId,
                                                  @Param("endOrderId") Integer endOrderId);

    List<TUser> getUserInfo(String userNum, String agentUserNum, String parentUserNum);

    int batchInsertUsers(@Param("list") List<TUser> list);

    int batchInsertMachines(@Param("list") List<TMachineCarInfo> list);

    int batchInsertOrders(@Param("list") List<TOrderCarInfo> list);
}
