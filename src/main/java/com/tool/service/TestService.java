package com.tool.service;

import com.tool.mapper.TestMapper;
import com.tool.mapper.UploadMapper;
import com.tool.util.SDCareVerifyUtil;
import com.tool.util.YiYuanSimUtiles;
import com.tool.vo.TSimcardInfo;
import lombok.SneakyThrows;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import javax.annotation.Resource;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

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
    public SseEmitter startSdCardTest(
            String drivePath,
            int rounds,
            boolean randomPattern
    ) {

        SseEmitter emitter = new SseEmitter(0L); // 永不超时

        new Thread(() -> {
            try {
                SDCareVerifyUtil.verify(
                        drivePath,
                        rounds,
                        randomPattern,
                        progress -> {
                            try {
                                emitter.send(buildProgressLine(progress));
                            } catch (Exception e) {
                                emitter.completeWithError(e);
                            }
                        }
                );

                emitter.send(">> 校验完成");
                emitter.complete();

            } catch (Exception e) {
                try {
                    emitter.send(">> 异常终止: " + e.getMessage());
                } catch (Exception ignored) {}
                emitter.completeWithError(e);
            }
        }).start();

        return emitter;
    }

    private String buildProgressLine(SDCareVerifyUtil.VerifyProgress p) {
        StringBuilder bar = new StringBuilder("[");
        int progress = (int) (p.percent / 5); // 20格进度条
        for (int i = 0; i < 20; i++) {
            bar.append(i < progress ? "=" : " ");
        }
        bar.append("]");

        // 计算速度 (MB/s)
        long now = System.currentTimeMillis();
        if (lastTime == 0) lastTime = now; // 初始化
        long deltaTimeMs = now - lastTime;
        long deltaBytes = p.verifiedBytes - lastVerified;
        double speed = deltaTimeMs > 0 ? deltaBytes / 1024.0 / 1024.0 / (deltaTimeMs / 1000.0) : 0.0;

        lastTime = now;
        lastVerified = p.verifiedBytes;

        return String.format(
                "%s %.2f%% 轮次：%d  已校验：%s  错误块数：%d  速度：%.2f MB/s",
                bar,
                p.percent,
                p.round,
                formatSize(p.verifiedBytes),
                p.errorCount,
                speed
        );
    }

    // 在类里定义这两个字段用于速度计算
    private long lastTime = 0;
    private long lastVerified = 0;

    // formatSize 方法保持原来的
    private String formatSize(long s) {
        if (s < 1024) return s + "B";
        int z = (63 - Long.numberOfLeadingZeros(s)) / 10;
        return String.format("%.2f%sB",
                (double) s / (1L << (z * 10)),
                " KMGTPE".charAt(z)
        );
    }

}
