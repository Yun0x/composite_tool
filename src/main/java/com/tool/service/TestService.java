package com.tool.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tool.config.datasource.DataSourceContextHolder;
import com.tool.config.datasource.DynamicDataSourceProperties;
import com.tool.mapper.TestMapper;
import com.tool.mapper.UploadMapper;
import com.tool.util.EmptyUtils;
import com.tool.util.Result;
import com.tool.util.SDCareVerifyUtil;
import com.tool.util.YiYuanSimUtiles;
import com.tool.vo.TSimcardInfo;
import com.tool.vo.testVO.TCheckInfo;
import com.tool.vo.testVO.TMachineCarInfo;
import com.tool.vo.testVO.TOrderCarInfo;
import com.tool.vo.testVO.TUser;
import lombok.SneakyThrows;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import javax.annotation.Resource;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.RandomAccessFile;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.text.SimpleDateFormat;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

@Service
public class TestService {

    private static final BigInteger DB0_MIN = new BigInteger("13000001");//shouhuoji
    private static final BigInteger DB0_MAX = new BigInteger("13100000");
    private static final BigInteger DB1_MIN = new BigInteger("13200001");//shouhuoji
    private static final BigInteger DB1_MAX = new BigInteger("13300000");
    private static final BigInteger DB2_MIN = new BigInteger("13500001");//cannon
    private static final BigInteger DB2_MAX = new BigInteger("13550000");
    private static final BigInteger DB3_MIN = new BigInteger("13600001");//drum
    private static final BigInteger DB3_MAX = new BigInteger("13620000");
    private static final BigInteger DB4_MIN = new BigInteger("13620001");//xiaoxiaole
    private static final BigInteger DB4_MAX = new BigInteger("13640000");
    private static final String DATE_FORMAT_PATTERN = "yyyy年MM月dd日 HH时mm分ss秒";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final Map<String, String> CHECK_ITEM_LABELS = buildCheckItemLabels();
    @Resource
    private TestMapper testMapper;

    @Resource
    private DynamicDataSourceProperties dynamicDataSourceProperties;

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
        try {
            String cleanCmd = "cmd /c del /f /q /s E:\\* && for /d %p in (E:\\*) do rd /s /q \"%p\"";
            Process process = Runtime.getRuntime().exec(cleanCmd);
            process.waitFor();
            System.out.println("磁盘内容已快速清空");
        } catch (Exception e) {
            return Result.error("清空磁盘失败：" + e.getMessage());
        }
        AtomicInteger mp3Count = new AtomicInteger(0);
        AtomicInteger binCount = new AtomicInteger(0);

        try (Stream<Path> paths = Files.list(sourceDir)) {
            paths.filter(Files::isRegularFile)
                    .filter(path -> {
                        String fileName = path.getFileName().toString().toLowerCase();
                        return fileName.endsWith(".mp3") || fileName.endsWith(".bin");
                    })
                    .forEach(path -> {
                        String fileName = path.getFileName().toString();
                        String targetFileName = fileName; // 简化的逻辑

                        if (fileName.toLowerCase().endsWith(".mp3")) {
                            mp3Count.incrementAndGet();
                        } else {
                            binCount.incrementAndGet();
                        }

                        Path targetPath = targetDir.resolve(targetFileName);
                        try {
                            Files.copy(path, targetPath, StandardCopyOption.REPLACE_EXISTING);
                            System.out.println("已复制: " + fileName + " -> " + targetFileName);
                        } catch (IOException e) {
                            System.err.println("复制失败: " + fileName);
                        }
                    });
        } catch (IOException e) {
            return Result.error("文件复制过程中发生异常：" + e.getMessage());
        }

        return Result.success("清空并成功复制 mp3: " + mp3Count.get() + " 个, bin: " + binCount.get() + " 个");
    }

    // 辅助方法：递归删除文件夹
    private void deleteRecursively(Path path) throws IOException {
        Files.walk(path)
                .sorted(Comparator.reverseOrder())
                .map(Path::toFile)
                .forEach(java.io.File::delete);
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

    //通过这个machienNo找到key来决定用哪个数据库
    private String getSqlBaseSourceKey(String machineNo) {
        String defaultKey = dynamicDataSourceProperties.getDefaultKey();
        BigInteger machineNumber = getMachineNo(machineNo);
        if (machineNumber == null) {
            return defaultKey;
        }
        String dataSourceKey = null;
        if (checkInRange(machineNumber, DB0_MIN, DB0_MAX)) {
            dataSourceKey = "db0";
        } else if (checkInRange(machineNumber, DB1_MIN, DB1_MAX)) {
            dataSourceKey = "db1";
        } else if (checkInRange(machineNumber, DB2_MIN, DB2_MAX)) {
            dataSourceKey = "db2";
        } else if (checkInRange(machineNumber, DB3_MIN, DB3_MAX)) {
            dataSourceKey = "db3";
        } else if (checkInRange(machineNumber, DB4_MIN, DB4_MAX)) {
            dataSourceKey = "db4";
        }

        if (!StringUtils.hasText(dataSourceKey)) {
            return defaultKey;
        }
        if (dynamicDataSourceProperties.getSources() == null
                || !dynamicDataSourceProperties.getSources().containsKey(dataSourceKey)) {
            return defaultKey;
        }
        return dataSourceKey;
    }

    private BigInteger getMachineNo(String machineNo) {
        if (!StringUtils.hasText(machineNo)) {
            return null;
        }
        String digits = machineNo.trim().replaceAll("\\D", "");
        if (!StringUtils.hasText(digits)) {
            return null;
        }
        return new BigInteger(digits);
    }

    private boolean checkInRange(BigInteger value, BigInteger min, BigInteger max) {
        return value.compareTo(min) >= 0 && value.compareTo(max) <= 0;
    }

    public Map<String, Object> getFirstOrder(String machineNo) {
        String dataSourceKey = getSqlBaseSourceKey(machineNo);
        DataSourceContextHolder.set(dataSourceKey);

        try {
            Map<String, Object> result = new LinkedHashMap<String, Object>();
            // 查询机器信息
            TMachineCarInfo machine;
            if (dataSourceKey.equals("db0")) {
                machine = testMapper.getMachineInfoByNoOnAlcohol(machineNo);
            } else if (dataSourceKey.equals("db3")) {
                machine = testMapper.getMachineInfoByNoOnDrum(machineNo);
            } else {
                machine = testMapper.getMachineInfoByNo(machineNo);
            }
            if (machine == null) {
                result.put("code", 404);
                result.put("msg", "机器不存在");
                return result;
            }
            // 查询订单表最小和最大订单ID
            Integer minOrderId;
            Integer maxOrderId;
            if (dataSourceKey.equals("db0")) {
                minOrderId = testMapper.getMinOrderIdOnAlcohol();
                maxOrderId = testMapper.getMaxOrderIdOnAlcohol();
            } else if (dataSourceKey.equals("db3")) {
                minOrderId = testMapper.getMinOrderIdOnDrum();
                maxOrderId = testMapper.getMaxOrderIdOnDrum();
            } else {
                minOrderId = testMapper.getMinOrderId();
                maxOrderId = testMapper.getMaxOrderId();
            }
//            if (minOrderId == null || maxOrderId == null) {
//                result.put("code", 404);
//                result.put("msg", "订单表没数据");
//                return result;
//            }
            Integer startOrderId = minOrderId;
            // 如果机器创建时间不为空 根据机器创建时间做一次时间二分
            if (machine.getCreateTime() != null) {
                Date machineCreateTime = machine.getCreateTime();
                Integer timeLeft = minOrderId;
                Integer timeRight = maxOrderId;
                // 找到第一个订单时间大于等于机器创建时间的订单id
                while (timeLeft < timeRight) {
                    Integer mid = timeLeft + (timeRight - timeLeft) / 2;
                    Date orderCreateTime;
                    if (dataSourceKey.equals("db0")) {
                        orderCreateTime = testMapper.getOrderTimeOnAlcohol(mid);
                    } else if (dataSourceKey.equals("db3")) {
                        orderCreateTime = testMapper.getOrderTimeOnDrum(mid);
                    } else {
                        orderCreateTime = testMapper.getOrderTime(mid);
                    }
                    // 如果这个订单的id查不到时间，就往右，就是时间继续往后找订单
                    if (orderCreateTime == null) {
                        timeLeft = mid + 1;
                        continue;
                    }
                    // 如果中间订单时间早于机器创建时间，说明左边都太早，继续往右找
                    if (orderCreateTime.before(machineCreateTime)) {
                        timeLeft = mid + 1;
                        if ((timeRight - timeLeft) <= 100000) {
                            break;
                        }
                    } else {
                        // 如果中间订单时间大于等于机器创建时间，说明当前点可能是边界，继续往左压
                        timeRight = mid;
                    }
                }
                // 二分以后的开始订单id
                startOrderId = timeLeft;
            }
            // 如果机器创建时间为空，就直接用原先逻辑
            Integer totalExist;
            if (dataSourceKey.equals("db0")) {
                totalExist = testMapper.checkMachineOrderExistInAlcohol(machineNo, startOrderId, maxOrderId);
            } else if (dataSourceKey.equals("db3")) {
                totalExist = testMapper.checkMachineOrderExistInDrum(machineNo, startOrderId, maxOrderId);
            } else {
                totalExist = testMapper.checkMachineOrderExist(machineNo, startOrderId, maxOrderId);
            }
            // 该机器有订单
            List<TOrderCarInfo> orderList = new ArrayList<>();
            Map<String, TUser> userMap = new HashMap<>();
            if (EmptyUtils.isNotEmpty(totalExist) && totalExist > 0) {
                Integer left = startOrderId;
                Integer right = maxOrderId;
                // 最大范围限制为100万
                final Integer MAX_RANGE = 1000000;
                while (right - left > MAX_RANGE) {
                    Integer mid = left + (right - left) / 2;
                    // 判断左半区间有没有该机器订单
                    Integer existInLeft;
                    if (dataSourceKey.equals("db0")) {
                        existInLeft = testMapper.checkMachineOrderExistInAlcohol(machineNo, left, mid);
                    } else if (dataSourceKey.equals("db3")) {
                        existInLeft = testMapper.checkMachineOrderExistInDrum(machineNo, left, mid);
                    } else {
                        existInLeft = testMapper.checkMachineOrderExist(machineNo, left, mid);
                    }
                    // 左半区间有订单，说明首单一定在左边
                    if (existInLeft != null && existInLeft > 0) {
                        right = mid;
                    } else {
                        // 左半区间没有订单，说明首单只能在右边
                        left = mid + 1;
                    }
                }

                // 在最终缩小后的范围内查询该机器第一笔订单
                if (dataSourceKey.equals("db0")) {
                    orderList = testMapper.getFirstOrderByMachineNoInAlcRange(machineNo, left, right);
                } else if (dataSourceKey.equals("db3")) {
                    orderList = testMapper.getFirstOrderByMachineNoInDrumRange(machineNo, left, right);
                } else {
                    orderList = testMapper.getFirstOrderByMachineNoInRange(machineNo, left, right);
                }
                TOrderCarInfo firstOrder = orderList.get(0);
                List<TUser> userList = testMapper.getUserInfo(
                        firstOrder.getUserNum(),
                        firstOrder.getAgentUserNum(),
                        firstOrder.getParentUserNum()
                );
                userMap = buildUserMap(userList);
                result.put("首笔订单信息", buildOrderInfo(firstOrder, userMap));

                for (TOrderCarInfo order : orderList) {
                    if (order != null) {
                        mergeUserMap(userMap, testMapper.getUserInfo(
                                order.getUserNum(),
                                order.getAgentUserNum(),
                                order.getParentUserNum()
                        ));
                    }
                }
                result.put("前十条订单信息", buildOrderInfoList(orderList, userMap));
            }
            List<TUser> userList = testMapper.getUserInfo(
                    machine.getUserNum(),
                    machine.getAgentUserNum(),
                    machine.getParentUserNum());
            userMap = buildUserMap(userList);

            TCheckInfo tCheckInfo = null;
            if (!dataSourceKey.equals("db1") && !dataSourceKey.equals("db0")) {
                tCheckInfo = testMapper.getGetCheckInfoByMachine(machineNo);
            }
            result.put("code", 200);
            result.put("设备信息", buildMachineInfo(machine, userMap));
            result.put("出厂质检信息", buildCheckInfo(tCheckInfo));
            return result;
        } finally {
            DataSourceContextHolder.clear();
        }
    }

    private Map<String, TUser> buildUserMap(List<TUser> userList) {
        Map<String, TUser> userMap = new HashMap<String, TUser>();
        mergeUserMap(userMap, userList);
        return userMap;
    }

    private void mergeUserMap(Map<String, TUser> userMap, List<TUser> userList) {
        if (userList == null) {
            return;
        }
        for (TUser user : userList) {
            if (user != null && user.getUserNum() != null) {
                userMap.put(user.getUserNum(), user);
            }
        }
    }

    private Map<String, Object> buildMachineInfo(TMachineCarInfo machine, Map<String, TUser> userMap) {
        Map<String, Object> info = new LinkedHashMap<String, Object>();
        info.put("设备编号", machine.getMachineNo());
        info.put("品牌方登录名", getLoginName(userMap, machine.getParentUserNum()));
        info.put("合伙人登录名", getLoginName(userMap, machine.getAgentUserNum()));
        info.put("代理商登录名", getLoginName(userMap, machine.getUserNum()));
        info.put("场地", machine.getSite());
        info.put("到期时间", formatDate(machine.getExTime()));
        info.put("创建时间", formatDate(machine.getCreateTime()));
        return info;
    }

    private Map<String, Object> buildCheckInfo(TCheckInfo checkInfo) {
        Map<String, Object> info = new LinkedHashMap<String, Object>();
        info.put("质检人登录名", checkInfo == null ? null : checkInfo.getLoginName());
        info.put("质检结果", checkInfo == null ? null : parseCheckResult(checkInfo.getCheckResultJson()));
        info.put("质检时间", checkInfo == null ? null : formatDate(checkInfo.getCheckTime()));
        return info;
    }

    private List<Map<String, Object>> parseCheckResult(String checkResultJson) {
        List<Map<String, Object>> result = new ArrayList<Map<String, Object>>();
        if (!StringUtils.hasText(checkResultJson)) {
            return result;
        }
        try {
            Map<String, Object> checkResult = OBJECT_MAPPER.readValue(
                    checkResultJson,
                    new TypeReference<LinkedHashMap<String, Object>>() {
                    }
            );
            for (Map.Entry<String, Object> entry : checkResult.entrySet()) {
                Map<String, Object> item = new LinkedHashMap<String, Object>();
                item.put("检查项", getCheckItemLabel(entry.getKey()));
                item.put("结果", convertCheckResultValue(entry.getValue()));
                result.add(item);
            }
        } catch (Exception ex) {
            Map<String, Object> item = new LinkedHashMap<String, Object>();
            item.put("检查项", "原始质检结果");
            item.put("结果", checkResultJson);
            result.add(item);
        }
        return result;
    }

    private String getCheckItemLabel(String key) {
        String label = CHECK_ITEM_LABELS.get(key);
        return StringUtils.hasText(label) ? label : key;
    }

    private String convertCheckResultValue(Object value) {
        Integer intValue = parseInteger(value);
        if (intValue != null) {
            if (intValue == 1) {
                return "是";
            }
            if (intValue == 0 || intValue == 2) {
                return "否";
            }
        }
        if (value instanceof Boolean) {
            return ((Boolean) value) ? "是" : "否";
        }
        return value == null ? "-" : String.valueOf(value);
    }

    private Integer parseInteger(Object value) {
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        if (value instanceof String && StringUtils.hasText((String) value)) {
            try {
                return Integer.valueOf(((String) value).trim());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private static Map<String, String> buildCheckItemLabels() {
        Map<String, String> labels = new LinkedHashMap<String, String>();
        labels.put("barrelPosition", "检查炮筒球头高低位置");
        labels.put("boardTerminals", "检查主板供电端子和加热端子线束是否拧紧，线束是否扎到立柱上");
        labels.put("qrCode", "扫码时二维码安装方向是否正确，螺丝是否打到位");
        labels.put("signal", "检查信号值");
        labels.put("floatingBall", "检查浮球信号");
        labels.put("pumpSensorLeakage", "检查电磁泵和传感器前后端是否进气、漏油");
        labels.put("bindingInBox", "检查机箱内线束和配件是否按要求绑扎");
        labels.put("screwInside", "检查机箱内螺丝是否打到位，是否按要求使用指定尺寸垫片和弹垫");
        labels.put("screwOutSide", "检查炮筒尾板螺丝、前挡板螺丝、脚踏螺丝是否打好");
        labels.put("stickersAndStrips", "检查贴纸和胶条是否有破损，是否漏贴漏装");
        labels.put("burrsAndPaint", "检查是否有毛刺，外观掉漆");
        labels.put("heatingWire", "检查炮筒内发热丝前端是否漏油，后端出油是否顺畅");
        labels.put("wireInBarrel", "检查炮筒内线束是否按要求扎好，帆布是否碰到线束");
        labels.put("lamp", "检查彩灯是否亮");
        labels.put("music", "检查背景音乐、语音播报和蓄力音效");
        labels.put("barrelLeakage", "检查炮筒下方和后方是否漏烟");
        labels.put("barrelLine", "检查炮筒抬头时下方管道是否受到拉扯");
        labels.put("smoke", "检查制烟和烟圈效果");
        labels.put("button", "检查按钮、脚踏功能是否正常");
        labels.put("abnormalSound", "检查有无异响");
        labels.put("backOil", "检查停止运行后进油管是否回油");
        labels.put("key", "钥匙是否挂好");
        labels.put("songAudio", "歌曲音频音量是否够大且无破音");
        labels.put("drumAudio", "鼓声音频音量是否够大且无破音");
        labels.put("lights", "各路灯光是否正常");
        labels.put("hitSignal", "各路打击信号是否正常");
        labels.put("networkSignal", "网络信号值是否正常");
        labels.put("channelValue", "通道值是否正常");
        labels.put("sdCardStatus", "SD卡状态是否正常");
        labels.put("songCount", "歌曲数量是否正常");
        labels.put("qrCodeInstalled", "二维码牌是否正确安装");
        labels.put("drumStickInstalled", "鼓棒是否正确安装");
        labels.put("sealantStatus", "密封胶是否涂抹完好");
        labels.put("appearance", "外观是否正常，未漏漆，未变形");
        labels.put("isResetVolume", "音量是否恢复默认值");
        labels.put("bubbleStatus", "吐泡功能是否正常");
        labels.put("backgroundMusic", "背景音乐是否正常");
        labels.put("gameEffectAudio", "游戏音效是否正常");
        labels.put("rotatingLight", "前置旋转灯是否正常");
        labels.put("waterFlowLight", "前置引流水灯是否正常");
        labels.put("touchPanelLight", "触摸板灯光是否正常");
        labels.put("touchSensitivity", "触摸板触摸是否灵敏");
        labels.put("liquidLevel", "液位是否正常");
        labels.put("qrCodePlateInstalled", "二维码牌是否正确安装");
        return labels;
    }

    private Map<String, Object> buildOrderInfo(TOrderCarInfo order, Map<String, TUser> userMap) {
        Map<String, Object> info = new LinkedHashMap<String, Object>();
        info.put("订单编号", order.getOrderNum());
        info.put("订单品牌方登录名", getLoginName(userMap, order.getParentUserNum()));
        info.put("合伙人登录名", getLoginName(userMap, order.getAgentUserNum()));
        info.put("代理商登录名", getLoginName(userMap, order.getUserNum()));
        info.put("金额", order.getTotalPrice()+"元");
        info.put("订单时间", formatDate(order.getTradCreateTime()));
        return info;
    }

    private List<Map<String, Object>> buildOrderInfoList(List<TOrderCarInfo> orderList, Map<String, TUser> userMap) {
        List<Map<String, Object>> result = new ArrayList<Map<String, Object>>();
        if (orderList == null) {
            return result;
        }
        for (TOrderCarInfo order : orderList) {
            if (order != null) {
                result.add(buildOrderInfo(order, userMap));
            }
        }
        return result;
    }

    private String getLoginName(Map<String, TUser> userMap, String userNum) {
        TUser user = userMap == null ? null : userMap.get(userNum);
        if (user == null || !StringUtils.hasText(user.getLoginName())) {
            return userNum;
        }
        return user.getLoginName();
    }

    private String formatDate(Date date) {
        if (date == null) {
            return null;
        }
        return new SimpleDateFormat(DATE_FORMAT_PATTERN).format(date);
    }

    public Map<String, Object> getFirstOrder2(String machineNo) {
        Map<String, Object> result = new LinkedHashMap<>();
        Integer minOrderId = testMapper.getMinOrderId();
        Integer maxOrderId = testMapper.getMaxOrderId();
        if (minOrderId == null || maxOrderId == null) {
            return result;
        }
        Integer totalExist = testMapper.checkMachineOrderExist(machineNo, minOrderId, maxOrderId);
        //拿来做一个订单都没有的特殊处理，直接返回404空
        if (totalExist == null) {
            result.put("code", 404);
            return result;
        }
        Integer left = minOrderId;
        Integer right = maxOrderId;
        // 最大限制给100万
        final Integer MAX_RANGE = 1000000;
        while (right - left > MAX_RANGE) {
            Integer mid = left + (right - left) / 2;
            // 判断左半边left和mid这个范围里面是不是有这个机器的订单
            Integer existInLeft = testMapper.checkMachineOrderExist(machineNo, left, mid);
            if (existInLeft != null && existInLeft > 0) {
                // 左边有就是最早订单一定在左半区间
                right = mid;
            } else {
                // 左边没有的话那么最早订单只能在右半区间
                left = mid + 1;
            }
        }
        List<TOrderCarInfo> orderList = testMapper.getFirstOrderByMachineNoInRange(machineNo, left, right);
        if (orderList == null || orderList.isEmpty()) {
            result.put("code", 404);
            return result;
        }
        TOrderCarInfo firstOrder = orderList.get(0);
        //这时候订单找到了，firstOrder，然后用订单去找user的三级
        List<TUser> userList = testMapper.getUserInfo(firstOrder.getUserNum(), firstOrder.getAgentUserNum(), firstOrder.getParentUserNum());
        result.put("code", 200);
        result.put("代理商信息", userList.get(0));
        result.put("合伙人信息", userList.get(1));
        result.put("品牌方信息", userList.get(2));
        result.put("第一次下单时间", firstOrder.getTradCreateTime());
        result.put("订单信息", orderList);
        return result;
    }


    private static final String ROOT_PARENT_NUM = "YB13000002";
    private static final int AGENT_USER_COUNT = 2000;
    private static final int MERCHANT_USER_COUNT = 160000;
    private static final int CHILD_USER_COUNT = 38000;
    private static final long MACHINE_TOTAL = 1000000L;
    private static final long MACHINE_NO_START = 80000000L;


    public Map<String, Object> generateAll() {
        Map<String, Object> result = new LinkedHashMap<String, Object>();

        result.put("users", generateUsers(200000, 8, 1000));
        result.put("machines", generateMachines(1000000L, 8, 1000));
        result.put("orders", generateOrders(100000000L, 10, 20000));

        return result;
    }

    public Map<String, Object> generateUsers(int total, int threadCount, int batchSize) {
        long startTime = System.currentTimeMillis();
        if (total != 200000) {
            throw new IllegalArgumentException("当前规则固定生成20万用户，传200000");
        }
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        List<Future<Long>> futures = new ArrayList<Future<Long>>();
        // 1. 生成4级用户
        futures.addAll(submitUserTasks(executor, 1, AGENT_USER_COUNT, 4, threadCount, batchSize));
        waitFutures(futures);
        futures.clear();
        // 2. 生成2级用户
        futures.addAll(submitUserTasks(executor, 1, MERCHANT_USER_COUNT, 2, threadCount, batchSize));
        waitFutures(futures);
        futures.clear();
        // 3. 生成3级用户
        futures.addAll(submitUserTasks(executor, 1, CHILD_USER_COUNT, 3, threadCount, batchSize));
        long inserted = waitFutures(futures);
        executor.shutdown();
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("success", true);
        result.put("type", "users");
        result.put("total", inserted);
        result.put("agentUserCount", AGENT_USER_COUNT);
        result.put("merchantUserCount", MERCHANT_USER_COUNT);
        result.put("childUserCount", CHILD_USER_COUNT);
        result.put("costMs", System.currentTimeMillis() - startTime);
        return result;
    }

    private List<Future<Long>> submitUserTasks(ExecutorService executor,
                                               int startIndex,
                                               int endIndex,
                                               int userType,
                                               int threadCount,
                                               int batchSize) {
        List<Future<Long>> futures = new ArrayList<Future<Long>>();

        int total = endIndex - startIndex + 1;
        int perThread = total / threadCount;
        int remainder = total % threadCount;

        int currentStart = startIndex;

        for (int i = 0; i < threadCount; i++) {
            int size = perThread + (i < remainder ? 1 : 0);
            if (size <= 0) {
                continue;
            }

            final int from = currentStart;
            final int to = currentStart + size - 1;
            final int finalUserType = userType;
            final int finalBatchSize = batchSize;

            futures.add(executor.submit(new Callable<Long>() {

                public Long call() {
                    List<TUser> batchList = new ArrayList<TUser>(finalBatchSize);
                    long count = 0L;

                    for (int index = from; index <= to; index++) {
                        batchList.add(buildUser(index, finalUserType));

                        if (batchList.size() >= finalBatchSize) {
                            testMapper.batchInsertUsers(batchList);
                            count += batchList.size();
                            batchList.clear();
                        }
                    }

                    if (!batchList.isEmpty()) {
                        testMapper.batchInsertUsers(batchList);
                        count += batchList.size();
                        batchList.clear();
                    }

                    return count;
                }
            }));

            currentStart = to + 1;
        }

        return futures;
    }

    private TUser buildUser(int index, int userType) {
        TUser user = new TUser();
        Date now = new Date();
        String userNum;
        String parentNum;
        if (userType == 4) {
            userNum = buildAgentUserNum(index);
            parentNum = ROOT_PARENT_NUM;
        } else if (userType == 2) {
            userNum = buildMerchantUserNum(index);
            parentNum = buildAgentUserNum(getAgentIndexByMerchantIndex(index));
        } else if (userType == 3) {
            userNum = buildChildUserNum(index);
            parentNum = buildAgentUserNum(getAgentIndexByChildIndex(index));
        } else {
            throw new IllegalArgumentException("不支持的userType：" + userType);
        }
        user.setUserNum(userNum);
        user.setParentNum(parentNum);
        user.setMemberNum("MEM" + userNum);
        user.setLoginName("login_" + userNum);
        user.setUserName("测试用户_" + userNum);
        user.setLoginPass("123456");
        user.setPayPass("123456");
        user.setOrgName("测试机构_" + userNum);
        user.setOrgId(1);
        user.setUserSex(index % 2 == 0 ? 1 : 2);
        user.setLinkTel(buildPhone(index, userType));
        user.setUserType(userType);
        user.setAddress("测试地址_" + index);
        user.setCreateCode(1);
        user.setCreateDate(now);
        user.setUserStatus(1);
        user.setBindStatus(0);
        user.setBalance(BigDecimal.ZERO);
        user.setMoneyLimit(new BigDecimal("1000000.00"));
        user.setServiceFee(new BigDecimal("0.00"));
        user.setIsAutoCard(0);
        user.setIsConfigure(2);
        user.setIsSms(2);
        user.setIsRefund(0);
        user.setParentProportion(new BigDecimal("0.00"));
        user.setProportion(new BigDecimal("0.00"));
        user.setDevType(15);
        user.setPpcProportion(new BigDecimal("0.00"));
        user.setParentPpcProportion(new BigDecimal("0.00"));
        user.setProvinceCode("0");
        user.setCityCode("0");
        user.setAreaCode("0");
        user.setVerifyPhone(1);
        user.setAllowWithdraw(1);
        return user;
    }


    public Map<String, Object> generateMachines(long total, int threadCount, int batchSize) {
        long startTime = System.currentTimeMillis();

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        List<Future<Long>> futures = new ArrayList<Future<Long>>();

        long perThread = total / threadCount;
        long remainder = total % threadCount;

        long currentStart = 1L;

        for (int i = 0; i < threadCount; i++) {
            long size = perThread + (i < remainder ? 1 : 0);
            if (size <= 0) {
                continue;
            }

            final long from = currentStart;
            final long to = currentStart + size - 1;
            final int finalBatchSize = batchSize;

            futures.add(executor.submit(new Callable<Long>() {

                public Long call() {
                    List<TMachineCarInfo> batchList = new ArrayList<TMachineCarInfo>(finalBatchSize);
                    long count = 0L;

                    for (long index = from; index <= to; index++) {
                        batchList.add(buildMachine(index));

                        if (batchList.size() >= finalBatchSize) {
                            testMapper.batchInsertMachines(batchList);
                            count += batchList.size();
                            batchList.clear();
                        }
                    }

                    if (!batchList.isEmpty()) {
                        testMapper.batchInsertMachines(batchList);
                        count += batchList.size();
                        batchList.clear();
                    }

                    return count;
                }
            }));

            currentStart = to + 1;
        }

        long inserted = waitFutures(futures);
        executor.shutdown();

        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("success", true);
        result.put("type", "machines");
        result.put("total", inserted);
        result.put("costMs", System.currentTimeMillis() - startTime);
        return result;
    }

    private TMachineCarInfo buildMachine(long index) {
        TMachineCarInfo machine = new TMachineCarInfo();

        Date now = new Date();

        int merchantIndex = getMerchantIndexByMachineIndex(index);
        int agentIndex = getAgentIndexByMerchantIndex(merchantIndex);

        String machineNo = buildMachineNo(index);
        String merchantUserNum = buildMerchantUserNum(merchantIndex);
        String agentUserNum = buildAgentUserNum(agentIndex);

        machine.setMachineNum("MNUM" + machineNo);
        machine.setMachineNo(machineNo);
        machine.setMachineStatus(2);
        machine.setChipId(String.valueOf(100000000L + index));
        machine.setMachineType(2);
        machine.setHardwareType(9);
        machine.setSignalValue(20 + (int) (index % 80));
        machine.setLocation(buildLocation(index));
        machine.setImsi("4600" + String.format("%011d", index % 100000000000L));
        machine.setMachineLabel("测试机器_" + machineNo);
        machine.setRemark("批量测试数据");
        machine.setCreateTime(now);
        machine.setUpdateTime(now);
        machine.setExpireTime(null);

        machine.setChildUserNums(null);
        machine.setUserNum(merchantUserNum);
        machine.setAgentUserNum(agentUserNum);
        machine.setParentUserNum(ROOT_PARENT_NUM);

        machine.setQrcodeUrl("https://test/qrcode/" + machineNo);
        machine.setNewGroupNum("GROUP" + String.format("%05d", agentIndex));
        machine.setPoweronOrNot(1);
        machine.setSaleOrNot(0);
        machine.setRomDamage(0);
        machine.setPowerLevel(80 + (int) (index % 21));
        machine.setSoftVersion(100);
        machine.setWaterLevel((int) (index % 2));

        machine.setFlag1(null);
        machine.setFlag2(null);
        machine.setFlag3(null);
        machine.setSite("测试场地_" + (index % 10000));
        machine.setPaoPaoJson(null);
        machine.setGoodsNums("1,2,3");
        machine.setFenceNum(null);
        machine.setIsFenceConfigured(0);
        machine.setIsInFence(0);
        machine.setFenceConfigTime(null);
        machine.setDepositConfig("50,5,10,5");

        return machine;
    }


    public Map<String, Object> generateOrders(long total, int threadCount, int batchSize) {
        long startTime = System.currentTimeMillis();

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        List<Future<Long>> futures = new ArrayList<Future<Long>>();

        long perThread = total / threadCount;
        long remainder = total % threadCount;

        long currentStart = 1L;

        for (int i = 0; i < threadCount; i++) {
            long size = perThread + (i < remainder ? 1 : 0);
            if (size <= 0) {
                continue;
            }

            final long from = currentStart;
            final long to = currentStart + size - 1;
            final int finalBatchSize = batchSize;

            futures.add(executor.submit(new Callable<Long>() {

                public Long call() {
                    List<TOrderCarInfo> batchList = new ArrayList<TOrderCarInfo>(finalBatchSize);
                    long count = 0L;

                    for (long index = from; index <= to; index++) {
                        batchList.add(buildOrder(index));

                        if (batchList.size() >= finalBatchSize) {
                            testMapper.batchInsertOrders(batchList);
                            count += batchList.size();
                            batchList.clear();
                        }
                    }

                    if (!batchList.isEmpty()) {
                        testMapper.batchInsertOrders(batchList);
                        count += batchList.size();
                        batchList.clear();
                    }

                    return count;
                }
            }));

            currentStart = to + 1;
        }

        long inserted = waitFutures(futures);
        executor.shutdown();

        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("success", true);
        result.put("type", "orders");
        result.put("total", inserted);
        result.put("costMs", System.currentTimeMillis() - startTime);
        return result;
    }

    private TOrderCarInfo buildOrder(long index) {
        TOrderCarInfo order = new TOrderCarInfo();

        long machineIndex = ((index - 1) % MACHINE_TOTAL) + 1;
        int merchantIndex = getMerchantIndexByMachineIndex(machineIndex);
        int agentIndex = getAgentIndexByMerchantIndex(merchantIndex);

        String machineNo = buildMachineNo(machineIndex);
        String merchantUserNum = buildMerchantUserNum(merchantIndex);
        String agentUserNum = buildAgentUserNum(agentIndex);

        Date createTime = buildOrderTime(index);
        Date successTime = new Date(createTime.getTime() + 30 * 1000L);

        String orderNo = "OD" + String.format("%012d", index);

        order.setOrderNum(orderNo);
        order.setTransactionNo("TX" + String.format("%012d", index));
        order.setOpenId("OPENID_" + (index % 5000000));
        order.setRefundNo(null);
        order.setAccountNum(agentUserNum);
        order.setTradCreateTime(createTime);
        order.setTradSuccessTime(successTime);
        order.setTradFailTime(null);
        order.setTradCancelTime(null);
        order.setShipmentStatus(1);
        order.setOrderStatus(1);
        order.setSettlementStatus(1);
        order.setPayMethod(0);
        order.setMachineNo(machineNo);
        order.setParentUserNum(ROOT_PARENT_NUM);
        order.setAgentUserNum(agentUserNum);
        order.setGoodsName("泡泡车套餐");
        order.setChannelId((int) (index % 10) + 1);
        BigDecimal price = BigDecimal.valueOf(((index % 20) + 1)).setScale(2, BigDecimal.ROUND_HALF_UP);
        order.setTotalPrice(price);
        order.setUserNum(merchantUserNum);
        order.setAccountType(1);
        order.setGoodsNum("GOODS" + ((index % 100) + 1));
        order.setGoodsRemark("测试商品");
        order.setTradeNo("TRADE" + String.format("%012d", index));

        order.setReturnPrice(BigDecimal.ZERO);
        order.setTotalTime((int) ((index % 60) + 1));
        order.setSite("测试场地_" + (machineIndex % 10000));
        order.setChildUserNums(null);

        return order;
    }

    private long waitFutures(List<Future<Long>> futures) {
        long total = 0L;

        for (Future<Long> future : futures) {
            try {
                total += future.get();
            } catch (Exception e) {
                throw new RuntimeException("批量造数任务执行失败", e);
            }
        }

        return total;
    }

    private String buildAgentUserNum(int index) {
        return "YB4" + String.format("%08d", index);
    }

    private String buildMerchantUserNum(int index) {
        return "YB2" + String.format("%08d", index);
    }

    private String buildChildUserNum(int index) {
        return "YB3" + String.format("%08d", index);
    }

    private int getAgentIndexByMerchantIndex(int merchantIndex) {
        return ((merchantIndex - 1) % AGENT_USER_COUNT) + 1;
    }

    private int getAgentIndexByChildIndex(int childIndex) {
        return ((childIndex - 1) % AGENT_USER_COUNT) + 1;
    }

    private int getMerchantIndexByMachineIndex(long machineIndex) {
        return (int) (((machineIndex - 1) % MERCHANT_USER_COUNT) + 1);
    }

    private String buildMachineNo(long index) {
        return String.valueOf(MACHINE_NO_START + index);
    }

    private String buildPhone(int index, int userType) {
        String prefix;
        if (userType == 4) {
            prefix = "184";
        } else if (userType == 2) {
            prefix = "182";
        } else {
            prefix = "183";
        }
        return prefix + String.format("%08d", index % 100000000);
    }

    private String buildLocation(long index) {
        double lng = 116.000000D + ((index % 10000) / 100000D);
        double lat = 39.000000D + ((index % 10000) / 100000D);
        return String.format("%.6f,%.6f", lng, lat);
    }

    private Date buildOrderTime(long index) {
        Calendar calendar = Calendar.getInstance();
        calendar.set(2025, Calendar.OCTOBER, 1, 0, 0, 0);
        calendar.set(Calendar.MILLISECOND, 0);

        long seconds = index % (90L * 24L * 60L * 60L);
        return new Date(calendar.getTimeInMillis() + seconds * 1000L);
    }

}
