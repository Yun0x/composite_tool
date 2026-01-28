package com.tool.service;

import com.tool.mapper.TestMapper;
import com.tool.mapper.UploadMapper;
import com.tool.util.YiYuanSimUtiles;
import com.tool.vo.TSimcardInfo;
import lombok.SneakyThrows;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class TestService {

    @Resource
    private TestMapper testMapper;

    public List<String> getAllIccid() {
       return testMapper.getAllIccid();
    }

    public void updateMessageLog(String bizId, String errMsg, int sendStatus, String sendTime) {
        testMapper.updateMessageLog(bizId, errMsg, sendStatus, sendTime);
    }
}
