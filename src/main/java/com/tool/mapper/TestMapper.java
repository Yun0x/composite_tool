package com.tool.mapper;

import com.tool.vo.testVO.TCheckInfo;
import com.tool.vo.testVO.TMachineCarInfo;
import com.tool.vo.testVO.TOrderCarInfo;
import com.tool.vo.testVO.TUser;
import org.apache.ibatis.annotations.Param;

import java.util.Date;
import java.util.List;

public interface TestMapper {
    List<String> getAllIccid();

    void updateMessageLog(String bizId, String errMsg, int sendStatus, String sendTime);

    TMachineCarInfo getMachineInfoByNo(String machineNo);

    TMachineCarInfo getMachineInfoByNoOnAlcohol(String machineNo);

    Integer getMinOrderId();

    Integer getMaxOrderId();

    Integer getMinOrderIdOnAlcohol();

    Integer getMaxOrderIdOnAlcohol();

    Integer checkMachineOrderExist(@Param("machineNo") String machineNo,
                                   @Param("startOrderId") Integer startOrderId,
                                   @Param("endOrderId") Integer endOrderId);

    Integer checkMachineOrderExistInAlcohol(@Param("machineNo") String machineNo,
                                            @Param("startOrderId") Integer startOrderId,
                                            @Param("endOrderId") Integer endOrderId);

    Integer checkMachineOrderExistInDrum(@Param("machineNo") String machineNo,
                                         @Param("startOrderId") Integer startOrderId,
                                         @Param("endOrderId") Integer endOrderId);

    List<TOrderCarInfo> getFirstOrderByMachineNoInRange(@Param("machineNo") String machineNo,
                                                        @Param("startOrderId") Integer startOrderId,
                                                        @Param("endOrderId") Integer endOrderId);

    List<TOrderCarInfo> getFirstOrderByMachineNoInAlcRange(@Param("machineNo") String machineNo,
                                                           @Param("startOrderId") Integer startOrderId,
                                                           @Param("endOrderId") Integer endOrderId);

    List<TOrderCarInfo> getFirstOrderByMachineNoInDrumRange(@Param("machineNo") String machineNo,
                                                            @Param("startOrderId") Integer startOrderId,
                                                            @Param("endOrderId") Integer endOrderId);

    List<TUser> getUserInfo(String userNum, String agentUserNum, String parentUserNum);

    Date getOrderTime(Integer midOrderId);

    Date getOrderTimeOnAlcohol(Integer midOrderId);

    int batchInsertUsers(@Param("list") List<TUser> list);

    int batchInsertMachines(@Param("list") List<TMachineCarInfo> list);

    int batchInsertOrders(@Param("list") List<TOrderCarInfo> list);

    TCheckInfo getGetCheckInfoByMachine(String machineNo);

    TMachineCarInfo getMachineInfoByNoOnDrum(String machineNo);

    Integer getMinOrderIdOnDrum();

    Integer getMaxOrderIdOnDrum();

    Date getOrderTimeOnDrum(Integer mid);

    String checkToolLogin(@Param("username") String username, @Param("password") String password);

}
