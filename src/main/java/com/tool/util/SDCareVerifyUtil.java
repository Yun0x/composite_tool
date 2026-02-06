package com.tool.util;

import java.io.File;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.security.SecureRandom;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/**
 * SD卡整盘顺序写校验工具
 *
 * 特性：
 * 1. 支持随机/非随机 Pattern
 * 2. 支持多轮覆盖
 * 3. 使用临时文件，自动获取盘符可用空间
 * 4. 支持进度回调，百分比 / 已校验字节 / 错误数量
 */
public class SDCareVerifyUtil {

    // ====== 可调参数 ======
    private static final int BLOCK_SIZE = 1024 * 1024; // 1MB
    private static final long RESERVED_BYTES = 64L * 1024 * 1024; // 预留 64MB
    // =====================

    // ========= 校验结果 =========
    public static class VerifyResult {
        public long totalBytes;
        public long verifiedBytes;
        public long errorCount;
        public int rounds;
        public boolean success;
    }

    // ========= 校验进度 =========
    public static class VerifyProgress {
        public int round;
        public double percent;
        public long verifiedBytes;
        public long errorCount;
    }

    /**
     * 整盘顺序写校验
     *
     * @param drivePath        盘符路径，例如 "E:\\"
     * @param rounds           覆盖轮数
     * @param randomPattern    是否使用随机 Pattern
     * @param progressCallback 进度回调，每写完 1MB 调一次
     * @return 校验结果
     * @throws Exception
     */
    /**
     * 修复后的 verify 方法
     */
    public static VerifyResult verify(
            String drivePath,
            int rounds,
            boolean randomPattern,
            Consumer<VerifyProgress> progressCallback
    ) throws Exception {

        VerifyResult result = new VerifyResult();
        AtomicLong verified = new AtomicLong();
        AtomicLong errors = new AtomicLong();

        // 1. 路径处理
        if (!drivePath.endsWith("\\") && !drivePath.endsWith("/")) {
            drivePath += File.separator;
        }
        String testFilePath = drivePath + "sdcard_test.bin";
        File file = new File(testFilePath);
        File parent = file.getParentFile();

        if (!parent.exists()) {
            throw new IllegalStateException("盘符路径不存在: " + drivePath);
        }

        // 2.如果存在旧文件，先删除，确保获取真实的剩余空间
        if (file.exists()) {
            // 尝试删除，如果删除失败可能是被占用，但这步很重要
            try {
                file.delete();
            } catch (Exception e) {
                // ignore
            }
        }

        // 3. 获取可用空间
        long diskSize = parent.getUsableSpace();
        long usable = diskSize - RESERVED_BYTES;
        if (usable <= 0) {
            throw new IllegalStateException("磁盘空间不足: " + diskSize + "B");
        }

        System.out.println("检测到可用空间: " + (diskSize / 1024 / 1024) + "MB, 计划写入: " + (usable / 1024 / 1024) + "MB");

        result.totalBytes = usable;
        result.rounds = rounds;

        try (RandomAccessFile raf = new RandomAccessFile(file, "rw");
             FileChannel channel = raf.getChannel()) {
            ByteBuffer writeBuf = ByteBuffer.allocateDirect(BLOCK_SIZE);
            ByteBuffer readBuf = ByteBuffer.allocateDirect(BLOCK_SIZE);
            SecureRandom random = randomPattern ? SecureRandom.getInstanceStrong() : null;
            long startTime = System.currentTimeMillis();
            for (int round = 1; round <= rounds; round++) {
                verified.set(0);
                // 循环写入
                for (long offset = 0; offset < usable; offset += BLOCK_SIZE) {
                    // 剩余不足一个 Block 的情况处理
                    long remaining = usable - offset;
                    int currentBlockSize = (int) Math.min(BLOCK_SIZE, remaining);
                    // 调整 Buffer 大小限制
                    writeBuf.clear();
                    writeBuf.limit(currentBlockSize);
                    readBuf.clear();
                    readBuf.limit(currentBlockSize);

                    fillPattern(writeBuf, offset, round, randomPattern, random);
                    writeBuf.flip();
                    try {
                        channel.position(offset);
                        int w = channel.write(writeBuf);
                        // 强制刷盘，确保校验真实性（会降低速度，可视情况开启/关闭）
                        // channel.force(false);
                    } catch (java.io.IOException e) {
                        // 5. 捕获写满异常
                        if (e.getMessage().contains("空间不足") || e.getMessage().contains("Space") || e.getMessage().contains("There is not enough space")) {
                            System.err.println("磁盘已写满（符合预期），停止写入。");
                            break; // 退出当前轮次写入
                        } else {
                            throw e; // 其他 IO 异常则抛出
                        }
                    }

                    // 校验逻辑
                    writeBuf.flip(); // 重置 position 用于比较
                    channel.position(offset);
                    int r = channel.read(readBuf);
                    readBuf.flip();

                    if (!bufferEquals(writeBuf, readBuf)) {
                        errors.incrementAndGet();
                    }

                    verified.addAndGet(currentBlockSize);

                    // 进度回调 (略微优化防止除以0)
                    if (progressCallback != null) {
                        VerifyProgress p = new VerifyProgress();
                        p.round = round;
                        p.verifiedBytes = verified.get();
                        p.errorCount = errors.get();
                        p.percent = (verified.get() * 100.0) / usable;
                        progressCallback.accept(p);
                    }
                }
            }
        } finally {
            // file.delete();
        }

        result.verifiedBytes = verified.get();
        result.errorCount = errors.get();
        result.success = errors.get() == 0;

        return result;
    }

    private static void fillPattern(
            ByteBuffer buf,
            long offset,
            int round,
            boolean random,
            SecureRandom rnd
    ) {
        if (random) {
            byte[] tmp = new byte[buf.capacity()];
            rnd.setSeed(offset ^ ((long) round << 32));
            rnd.nextBytes(tmp);
            buf.put(tmp);
        } else {
            long seed = offset ^ ((long) round << 32);
            while (buf.remaining() >= 8) {
                buf.putLong(seed);
                seed = seed * 31 + 7;
            }
            while (buf.hasRemaining()) {
                buf.put((byte) seed);
                seed++;
            }
        }
    }

    // ====== 校验两个 ByteBuffer 是否相等 ======
    private static boolean bufferEquals(ByteBuffer a, ByteBuffer b) {
        if (a.remaining() != b.remaining()) return false;
        for (int i = 0; i < a.remaining(); i++) {
            if (a.get(i) != b.get(i)) return false;
        }
        return true;
    }
}
