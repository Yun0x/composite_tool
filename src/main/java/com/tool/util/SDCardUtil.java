package com.tool.util;

import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import java.security.SecureRandom;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.stream.Stream;

public final class SDCardUtil {

    /* ===================== 通用参数 ===================== */

    private static final int BLOCK_SIZE = 1024 * 1024; // 1MB
    private static final long RESERVED_BYTES = 64L * 1024 * 1024; // 64MB

    private SDCardUtil() {
    }

    /* ====================================================
     *                     擦除相关
     * ==================================================== */

    /** 快速擦除（直接删除） */
    public static void quickWipe(Path root, SseEmitter emitter) throws IOException {
        Files.walk(root)
                .sorted((a, b) -> b.compareTo(a))
                .forEach(path -> {
                    try {
                        if (!path.equals(root)) {
                            Files.deleteIfExists(path);
                            send(emitter, "删除：" + path);
                        }
                    } catch (Exception e) {
                        send(emitter, "删除失败：" + path + " - " + e.getMessage());
                    }
                });
    }

    /** 安全擦除（覆盖 + 删除） */
    public static void secureWipe(Path root, SseEmitter emitter) throws IOException {
        Files.walk(root)
                .filter(Files::isRegularFile)
                .forEach(file -> {
                    try {
                        overwriteFile(file);
                        Files.deleteIfExists(file);
                        send(emitter, "安全删除文件：" + file);
                    } catch (Exception e) {
                        send(emitter, "安全删除失败：" + file + " - " + e.getMessage());
                    }
                });

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

    private static void overwriteFile(Path file) throws IOException {
        long size = Files.size(file);
        if (size <= 0) return;

        try (RandomAccessFile raf = new RandomAccessFile(file.toFile(), "rw")) {
            byte[] buffer = new byte[8192];
            long written = 0;
            while (written < size) {
                int len = (int) Math.min(buffer.length, size - written);
                raf.write(buffer, 0, len);
                written += len;
            }
            raf.getFD().sync();
        }
    }

    /* ====================================================
     *                  整盘写校验
     * ==================================================== */

    public static class VerifyResult {
        public long totalBytes;
        public long verifiedBytes;
        public long errorCount;
        public int rounds;
        public boolean success;
    }

    public static class VerifyProgress {
        public int round;
        public double percent;
        public long verifiedBytes;
        public long errorCount;
    }

    public static VerifyResult verify(
            String drivePath,
            int rounds,
            boolean randomPattern,
            Consumer<VerifyProgress> progressCallback
    ) throws Exception {

        VerifyResult result = new VerifyResult();
        AtomicLong verified = new AtomicLong();
        AtomicLong errors = new AtomicLong();

        if (!drivePath.endsWith(File.separator)) {
            drivePath += File.separator;
        }

        File root = new File(drivePath);
        if (!root.exists()) {
            throw new IllegalStateException("盘符不存在: " + drivePath);
        }

        File testFile = new File(drivePath + "sdcard_test.bin");
        if (testFile.exists()) testFile.delete();

        long usable = root.getUsableSpace() - RESERVED_BYTES;
        if (usable <= 0) {
            throw new IllegalStateException("磁盘空间不足");
        }

        result.totalBytes = usable;
        result.rounds = rounds;

        try (RandomAccessFile raf = new RandomAccessFile(testFile, "rw");
             FileChannel channel = raf.getChannel()) {

            ByteBuffer writeBuf = ByteBuffer.allocateDirect(BLOCK_SIZE);
            ByteBuffer readBuf = ByteBuffer.allocateDirect(BLOCK_SIZE);
            SecureRandom random = randomPattern ? SecureRandom.getInstanceStrong() : null;

            for (int round = 1; round <= rounds; round++) {
                verified.set(0);

                for (long offset = 0; offset < usable; offset += BLOCK_SIZE) {
                    int size = (int) Math.min(BLOCK_SIZE, usable - offset);
                    writeBuf.clear().limit(size);
                    readBuf.clear().limit(size);

                    fillPattern(writeBuf, offset, round, randomPattern, random);
                    writeBuf.flip();

                    channel.position(offset);
                    channel.write(writeBuf);

                    writeBuf.flip();
                    channel.position(offset);
                    channel.read(readBuf);
                    readBuf.flip();

                    if (!bufferEquals(writeBuf, readBuf)) {
                        errors.incrementAndGet();
                    }

                    verified.addAndGet(size);

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
            byte[] tmp = new byte[buf.remaining()];
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
                buf.put((byte) seed++);
            }
        }
    }

    private static boolean bufferEquals(ByteBuffer a, ByteBuffer b) {
        if (a.remaining() != b.remaining()) return false;
        for (int i = 0; i < a.remaining(); i++) {
            if (a.get(i) != b.get(i)) return false;
        }
        return true;
    }

    /* ====================================================
     *                  批量文件复制
     * ==================================================== */

    public static void batchCopyDrumFiles(
            Path sourceDir,
            Path targetDir
    ) throws IOException {

        if (!Files.isDirectory(sourceDir)) {
            throw new IllegalArgumentException("源目录不存在: " + sourceDir);
        }

        try (Stream<Path> paths = Files.list(sourceDir)) {
            paths
                    .filter(Files::isRegularFile)
                    .filter(p -> {
                        String name = p.getFileName().toString().toLowerCase();
                        return name.endsWith(".mp3") || name.endsWith(".bin");
                    })
                    .forEach(p -> {
                        try {
                            String name = p.getFileName().toString();
                            String targetName = name.endsWith(".mp3")
                                    ? name.replace(".mp3", "_.mp3")
                                    : name;

                            Files.copy(
                                    p,
                                    targetDir.resolve(targetName),
                                    StandardCopyOption.REPLACE_EXISTING
                            );
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    });
        }
    }

    /* ================= SSE 工具 ================= */

    private static void send(SseEmitter emitter, String msg) {
        if (emitter == null) return;
        try {
            emitter.send(msg);
        } catch (IOException ignored) {
        }
    }
}
