package com.tool.vo;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class DrumInfo {

    private BigDecimal beginTime;

    private BigDecimal intervalTime;

    private Integer key;

    private BigDecimal endTime;
}
