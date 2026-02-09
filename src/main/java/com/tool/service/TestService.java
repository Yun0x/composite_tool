package com.tool.service;

import com.tool.mapper.TestMapper;
import com.tool.mapper.UploadMapper;
import com.tool.util.Result;
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
import java.io.RandomAccessFile;
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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
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
                } catch (Exception ignored) {
                }
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

    private long lastTime = 0;
    private long lastVerified = 0;

    private String formatSize(long s) {
        if (s < 1024) return s + "B";
        int z = (63 - Long.numberOfLeadingZeros(s)) / 10;
        return String.format("%.2f%sB",
                (double) s / (1L << (z * 10)),
                " KMGTPE".charAt(z)
        );
    }

    public Result copyDrumFiles(String sourceDirPath) {
        Path sourceDir = Paths.get(sourceDirPath);
        Path targetDir = Paths.get("E:\\");
        if (!Files.exists(sourceDir) || !Files.isDirectory(sourceDir)) {
            return Result.error("源目录不存在或不是目录：" + sourceDir);
        }
        if (!Files.exists(targetDir) || !Files.isDirectory(targetDir)) {
            return Result.error("目标目录不存在或未挂载磁盘：E:\\");
        }
        AtomicInteger mp3Count = new AtomicInteger(0);
        AtomicInteger binCount = new AtomicInteger(0);

        try (Stream<Path> paths = Files.list(sourceDir)) { // 只扫描当前目录
            paths
                    .filter(Files::isRegularFile)
                    .filter(path -> {
                        String fileName = path.getFileName().toString().toLowerCase();
                        return fileName.endsWith(".mp3") || fileName.endsWith(".bin");
                    })
                    .forEach(path -> {
                        String fileName = path.getFileName().toString();
                        String lowerName = fileName.toLowerCase();
                        String targetFileName;

                        if (lowerName.endsWith(".mp3")) {
                            int dotIndex = fileName.lastIndexOf('.');
                            targetFileName =
                                    fileName.substring(0, dotIndex) + fileName.substring(dotIndex);
                            mp3Count.incrementAndGet();
                        } else {
                            targetFileName = fileName;
                            binCount.incrementAndGet();
                        }

                        Path targetPath = targetDir.resolve(targetFileName);
                        try {
                            Files.copy(
                                    path,
                                    targetPath,
                                    StandardCopyOption.REPLACE_EXISTING
                            );
                            System.out.println("已复制: " + fileName + " -> " + targetFileName);
                        } catch (IOException e) {
                            System.err.println("复制失败: " + fileName);
                            e.printStackTrace();
                        }
                    });
        } catch (IOException e) {
            e.printStackTrace();
            return Result.error("文件复制过程中发生异常：" + e.getMessage());
        }

        return Result.success(
                "已成功复制 mp3 文件 " + mp3Count.get() +
                        " 个，bin 文件 " + binCount.get() + " 个"
        );
    }

    public SseEmitter wipeSdCard(Integer type) {
        SseEmitter emitter = new SseEmitter(0L); // 不超时
        Path sdCardPath = Paths.get("E:\\");

        ExecutorService executor = Executors.newSingleThreadExecutor();

        executor.execute(() -> {
            try {
                if (!Files.exists(sdCardPath)) {
                    emitter.send("SD 卡路径不存在");
                    emitter.complete();
                    return;
                }

                emitter.send("开始擦除 SD 卡：" + sdCardPath);

                if (type == 1) {
                    emitter.send("模式：快速擦除");
                    quickDelete(sdCardPath, emitter);
                } else if (type == 2) {
                    emitter.send("模式：安全擦除");
                    secureDelete(sdCardPath, emitter);
                } else {
                    emitter.send("未知擦除类型");
                }

                emitter.send("擦除完成");
                emitter.complete();
            } catch (Exception e) {
                try {
                    emitter.send("发生异常：" + e.getMessage());
                } catch (IOException ignored) {
                }
                emitter.completeWithError(e);
            } finally {
                executor.shutdown();
            }
        });

        return emitter;
    }

    private void quickDelete(Path root, SseEmitter emitter) throws IOException {
        Files.walk(root)
                .sorted((a, b) -> b.compareTo(a)) // 先删文件，再删目录
                .forEach(path -> {
                    try {
                        if (!path.equals(root)) {
                            Files.deleteIfExists(path);
                            emitter.send("删除：" + path.toString());
                        }
                    } catch (Exception e) {
                        try {
                            emitter.send("删除失败：" + path + " - " + e.getMessage());
                        } catch (IOException ignored) {
                        }
                    }
                });
    }

    private void secureDelete(Path root, SseEmitter emitter) throws IOException {
        Files.walk(root)
                .filter(Files::isRegularFile)
                .forEach(file -> {
                    try {
                        overwriteFile(file);
                        Files.deleteIfExists(file);
                        emitter.send("安全删除文件：" + file);
                    } catch (Exception e) {
                        try {
                            emitter.send("安全删除失败：" + file + " - " + e.getMessage());
                        } catch (IOException ignored) {
                        }
                    }
                });

        // 再删除空目录
        Files.walk(root)
                .sorted((a, b) -> b.compareTo(a))
                .filter(Files::isDirectory)
                .forEach(dir -> {
                    try {
                        if (!dir.equals(root)) {
                            Files.deleteIfExists(dir);
                        }
                    } catch (IOException ignored) {
                    }
                });
    }

    private void overwriteFile(Path file) throws IOException {
        long size = Files.size(file);
        if (size <= 0) return;

        try (RandomAccessFile raf = new RandomAccessFile(file.toFile(), "rw")) {
            byte[] buffer = new byte[8192];
            for (int i = 0; i < buffer.length; i++) {
                buffer[i] = 0x00; // 覆盖为 0
            }

            long written = 0;
            while (written < size) {
                raf.write(buffer, 0, (int) Math.min(buffer.length, size - written));
                written += buffer.length;
            }
            raf.getFD().sync();
        }
    }

}
