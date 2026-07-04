package com.tool.service;

import com.tool.mapper.TestMapper;
import com.tool.mapper.UploadMapper;
import com.tool.util.Result;
import com.tool.util.SDCareVerifyUtil;
import com.tool.util.YiYuanSimUtiles;
import com.tool.vo.TSimcardInfo;
import com.tool.vo.testVO.TMachineCarInfo;
import com.tool.vo.testVO.TOrderCarInfo;
import com.tool.vo.testVO.TUser;
import lombok.SneakyThrows;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import javax.annotation.Resource;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.RandomAccessFile;
import java.math.BigDecimal;
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


    public Map<String, Object> getFirstOrder(String machineNo) {
        Map<String, Object> result = new LinkedHashMap<>();
        Integer minOrderId = testMapper.getMinOrderId();
        Integer maxOrderId = testMapper.getMaxOrderId();
        if (minOrderId == null || maxOrderId == null) {
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
        TOrderCarInfo firstOrder = testMapper.getFirstOrderByMachineNoInRange(machineNo, left, right);
        //这时候订单找到了，firstOrder，然后用订单去找user的三级
        List<TUser> userList = testMapper.getUserInfo(firstOrder.getUserNum(),firstOrder.getAgentUserNum(),firstOrder.getParentUserNum());
        result.put("代理商信息", userList.get(0));
        result.put("合伙人信息", userList.get(1));
        result.put("品牌方信息", userList.get(2));
        result.put("第一次下单时间", firstOrder.getTradCreateTime());
        result.put("订单信息", firstOrder);

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
            throw new IllegalArgumentException("当前规则固定生成20万用户，total请传200000");
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
        order.setBuyerAccount("buyer_" + (index % 10000000));
        order.setPayMethod(0);

        order.setMachineNo(machineNo);
        order.setParentUserNum(ROOT_PARENT_NUM);
        order.setAgentUserNum(agentUserNum);
        order.setGoodsName("泡泡车套餐");
        order.setChannelId((int) (index % 10) + 1);
        order.setTotalCount(1);

        BigDecimal price = BigDecimal.valueOf(((index % 20) + 1)).setScale(2, BigDecimal.ROUND_HALF_UP);
        order.setTotalPrice(price);

        order.setUserNum(merchantUserNum);
        order.setErrorInfo(null);

        order.setFalg1(null);
        order.setFlag2(null);
        order.setFlag3(null);

        order.setAccountType(1);
        order.setGoodsNum("GOODS" + ((index % 100) + 1));
        order.setGoodsRemark("测试商品");
        order.setTradeNo("TRADE" + String.format("%012d", index));

        order.setIsMachineReturned(0);
        order.setReturnPrice(BigDecimal.ZERO);
        order.setTotalTime((int) ((index % 60) + 1));
        order.setDepositConfig("50,5,10,5");
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
