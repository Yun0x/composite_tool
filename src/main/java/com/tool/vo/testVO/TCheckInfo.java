package com.tool.vo.testVO;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

/**
 * 用户表 t_user
 */
@Data
public class TCheckInfo implements Serializable {

    private static final long serialVersionUID = 1L;


    private Integer logId;

    private String machineNo;

    private String userNum;

    private String loginName;

    private String checkResultJson;

    private Date checkTime;
}