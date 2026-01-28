package com.tool.mapper;

import java.util.List;

public interface TestMapper {
    List<String> getAllIccid();

    void updateMessageLog(String bizId, String errMsg, int sendStatus, String sendTime);
}
