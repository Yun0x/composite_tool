package com.tool.vo.testVO;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

/**
 * 用户表 t_user
 */
@Data
public class TUser implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 用户id--主键
     */
    private Integer userId;

    /**
     * 用户编号即商户编号
     */
    private String userNum;

    /**
     * 上级num
     */
    private String parentNum;

    /**
     * 小程序num即路人num
     */
    private String memberNum;

    /**
     * 用户登录名称
     */
    private String loginName;

    /**
     * 用户真实姓名
     */
    private String userName;

    /**
     * 登录密码
     */
    private String loginPass;

    /**
     * 支付密码
     */
    private String payPass;

    /**
     * 单位名称
     */
    private String orgName;

    /**
     * 所属机构的id
     */
    private Integer orgId;

    /**
     * 用户性别 1/2 男/女
     */
    private Integer userSex;

    /**
     * 电话号码
     */
    private String linkTel;

    /**
     * 0系统用户 1代理商 2商家用户 3商户子账号
     */
    private Integer userType;

    /**
     * 地址
     */
    private String address;

    /**
     * 创建人id
     */
    private Integer createCode;

    /**
     * 创建时间
     */
    private Date createDate;

    /**
     * 用户状态 0:删除 1：正常 2：禁用
     */
    private Integer userStatus;

    /**
     * 用户头像
     */
    private String userPhoto;

    /**
     * 0未绑定 1银行卡 2微信零钱包
     */
    private Integer bindStatus;

    /**
     * 余额
     */
    private BigDecimal balance;

    /**
     * 商户可售额度
     */
    private BigDecimal moneyLimit;

    /**
     * 商户下每台机器每年的服务费（SIM流量费+服务费）
     */
    private BigDecimal serviceFee;

    /**
     * 微信二维码
     */
    private String weixinTicket;

    /**
     * 是否自动制卡
     */
    private Integer isAutoCard;

    /**
     * 微信 AppId
     */
    private String weixinAppId;

    /**
     * 微信开放平台密钥
     */
    private String weixinSecret;

    /**
     * 微信商户密钥
     */
    private String weixinAppSecret;

    /**
     * 微信商户号
     */
    private String weixinMchid;

    /**
     * 是否配置支付 1已配置 2未配置
     */
    private Integer isConfigure;

    /**
     * 支付宝公钥
     */
    private String alipayPublicKey;

    /**
     * 支付宝 partner
     */
    private String alipayPartner;

    /**
     * 支付宝应用私钥
     */
    private String alipayAppPrivateKey;

    /**
     * 支付宝 appId
     */
    private String alipayAppId;

    /**
     * 微信 openId
     */
    private String wxopenId;

    /**
     * 小程序 appid
     */
    private String xiaochengxuAppid;

    /**
     * 是否发送短信，1发送，2不发送
     */
    private Integer isSms;

    /**
     * 是否允许退款申请，0：不允许，1：微信，2：支付宝，4：银行卡转账，组合退款途径相加即可
     */
    private Integer isRefund;

    /**
     * 父级制造商所占分成比例
     */
    private BigDecimal parentProportion;

    /**
     * 商户或子账号所占分成比例
     */
    private BigDecimal proportion;

    /**
     * 允许商户小程序上展示的设备类型，bit0~bit30依次代表：云投币、售水机、水控器、售液机 ...
     */
    private Integer devType;

    /**
     * 泡泡车分成比例
     */
    private BigDecimal ppcProportion;

    /**
     * 泡泡车制造商的分成比例，只有类型为代理商时才会有值
     */
    private BigDecimal parentPpcProportion;

    /**
     * 省份代码
     */
    private String provinceCode;

    /**
     * 城市代码
     */
    private String cityCode;

    /**
     * 区域代码
     */
    private String areaCode;

    /**
     * 统一公众号 openId
     */
    private String unionOpenid;

    /**
     * 统一分账账户编号
     */
    private String receiverNo;

    /**
     * 1:需要验证（默认） 0：不需要
     */
    private Integer verifyPhone;

    /**
     * 1:允许提现（默认） 0：不允许
     */
    private Integer allowWithdraw;
}