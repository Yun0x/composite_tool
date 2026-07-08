package com.tool.vo.testVO;

import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * 机器信息表 t_machine_car_info
 */
@Data
public class TMachineCarInfo implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 主键ID
     */
    private Integer machineId;

    /**
     * 机器编号
     */
    private String machineNum;

    /**
     * machine_no，8位数字且在所有机器表中唯一
     */
    private String machineNo;

    /**
     * 机器状态 0未配置 1已配置 2启用 3停用 4未分配
     */
    private Integer machineStatus;

    /**
     * 芯片ID 0~0xFFFFFFFF
     */
    private String chipId;

    /**
     * 机器类型 0蓝牙 1：2G 2：4G
     */
    private Integer machineType;

    /**
     * 硬件类型 9：泡泡车，所有设备表统一编码
     */
    private Integer hardwareType;

    /**
     * 信号强度
     */
    private Integer signalValue;

    /**
     * 机器位置：经度,纬度，例如 99.030499,31.363785
     */
    private String location;

    /**
     * 移动imsi卡的串号
     */
    private String imsi;

    /**
     * 标签
     */
    private String machineLabel;

    /**
     * 备注
     */
    private String remark;

    /**
     * 创建时间
     */
    private Date createTime;

    /**
     * 更新时间
     */
    private Date updateTime;

    /**
     * 到期时间-蓝牙版
     */
    private Date expireTime;

    /**
     * 子账号编号组串用于分成
     */
    private String childUserNums;

    /**
     * 用户编号
     */
    private String userNum;

    /**
     * 代理商编号
     */
    private String agentUserNum;

    /**
     * 制造商编号
     */
    private String parentUserNum;

    /**
     * 二维码链接地址
     */
    private String qrcodeUrl;

    /**
     * 分组编号
     */
    private String newGroupNum;

    /**
     * 开机状态 0关机状态 1开机状态
     */
    private Integer poweronOrNot;

    /**
     * 是否停售 0没停售 1已停售
     */
    private Integer saleOrNot;

    /**
     * 0没损坏 1损坏
     */
    private Integer romDamage;

    /**
     * 电池电量百分比
     */
    private Integer powerLevel;

    /**
     * 机器软件版本号
     */
    private Integer softVersion;

    /**
     * 泡泡水液位 1有 0无
     */
    private Integer waterLevel;

    /**
     * 扩展字段1
     */
    private String flag1;

    /**
     * 扩展字段2
     */
    private String flag2;

    /**
     * 扩展字段3
     */
    private String flag3;

    /**
     * 场地
     */
    private String site;

    /**
     * 泡泡机自动吐泡时间json
     */
    private String paoPaoJson;

    /**
     * 套餐id，多个逗号拼接
     */
    private String goodsNums;

    /**
     * 绑定的围栏num
     */
    private String fenceNum;

    /**
     * 是否配置了围栏，主板状态上报 1已配置 0未配置
     */
    private Integer isFenceConfigured;

    /**
     * 是否在围栏内，主板状态上报 1在围栏内 0不在围栏内
     */
    private Integer isInFence;

    /**
     * 围栏配置时间
     */
    private Date fenceConfigTime;

    /**
     * 押金配置，格式：押金金额,免费时长,单价,时间
     * 例如：50,5,10,5 表示押金50元，5分钟内免费，10元/5分钟
     */
    private String depositConfig;


    /**
     * 到期时间
     */
    private Date exTime;
}