package com.tool.vo;
import lombok.Data;

import java.util.Date;

@Data
public class TSimcardInfo implements java.io.Serializable {

    private Integer simcardId;
    private String msisdn;
    private String iccid;
    private String imsi;
    private String spCode;
    private String carrier;
    private String dataPlan;
    private String dataUsage;
    private String accountStatus;
    private String active;
    private String testUsedDataUsage;
    private String dataBalance;
    private String supportSms;
    private Date expiryDate;
    private Date testValidDate;
    private Date silentValidDate;
    private Date activeDate;
    private Date outboundDate;
    private Date dataRefreshTime;
    private String flag1;
    private String flag2;
    private String flag3;

}


