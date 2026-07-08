package com.tool.vo.testVO;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

/**
 * 订单信息表 t_order_car_info
 */
@Data
public class TOrderCarInfo implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 主键ID
     */
    private Integer orderId;

    /**
     * 订单编号
     */
    private String orderNum;

    /**
     * 交易号，拉卡拉下单时返回的订单号
     */
    private String transactionNo;

    /**
     * 微信openId
     */
    private String openId;

    /**
     * 退款交易号，多笔退款逗号拼接
     */
    private String refundNo;

    /**
     * 收款账号
     */
    private String accountNum;

    /**
     * 交易创建时间
     */
    private Date tradCreateTime;

    /**
     * 交易成功时间
     */
    private Date tradSuccessTime;

    /**
     * 设备返还完成时间
     */
    private Date tradFailTime;

    /**
     * 退款完成时间
     */
    private Date tradCancelTime;

    /**
     * 出货状态 0未出货 1已出货
     */
    private Integer shipmentStatus;

    /**
     * 订单状态 0等待支付 1支付成功 2订单关闭 -1支付失败
     */
    private Integer orderStatus;

    /**
     * 结算状态 0等待结算 1结算成功 2已退款
     */
    private Integer settlementStatus;


    /**
     * 0正常订单 1押金订单
     */
    private Integer payMethod;

    /**
     * 机器号
     */
    private String machineNo;

    /**
     * 制造商编号
     */
    private String parentUserNum;

    /**
     * 代理商编号
     */
    private String agentUserNum;

    /**
     * 产品名称
     */
    private String goodsName;

    /**
     * 吐泡时长
     */
    private Integer channelId;

    /**
     * 普通订单对应订单金额，押金订单对应消费金额
     */
    private BigDecimal totalPrice;

    /**
     * 用户编号
     */
    private String userNum;


    /**
     * 1代收 2自收
     */
    private Integer accountType;

    /**
     * 商品编号
     */
    private String goodsNum;

    /**
     * 商品的其他信息
     */
    private String goodsRemark;

    /**
     * 拉卡拉回调时返回的订单号
     */
    private String tradeNo;

    /**
     * 退还金额
     */
    private BigDecimal returnPrice;

    /**
     * 使用时长，分钟为单位
     */
    private Integer totalTime;

    /**
     * 场地
     */
    private String site;

    /**
     * 子账号
     */
    private String childUserNums;


}