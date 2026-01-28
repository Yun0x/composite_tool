package com.tool.vo;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

@Data
public class DtxVO implements Serializable {
    private String createBy;

    private String title;
    private String level;
    private List<Double> BPMInfo;
    private List<DrumInfo> drumInfo;

}
