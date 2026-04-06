package com.tool.util;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;

import javax.sound.sampled.*;
import java.io.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;

public class JsonToMp3 {

    private static final int SAMPLE_RATE = 44100;
    private static final boolean TIME_IS_MILLIS = true;

    // 鼓声采样文件路径映射 (1-10)
    private static final String[] DRUM_SAMPLES = {
            "C:\\Users\\Admin\\Desktop\\鼓声\\fold2\\drum\\Hi-Hat Open.wav",    // 1
            "C:\\Users\\Admin\\Desktop\\鼓声\\fold2\\drum\\Clash2.wav",         // 2
            "C:\\Users\\Admin\\Desktop\\鼓声\\fold2\\drum\\Snare Drum.wav",    // 3
            "C:\\Users\\Admin\\Desktop\\鼓声\\fold2\\drum\\tom1_HighTom.wav",  // 4
            "C:\\Users\\Admin\\Desktop\\鼓声\\fold2\\drum\\Ride Cymbal.wav",   // 5
            "C:\\Users\\Admin\\Desktop\\鼓声\\fold2\\drum\\Crash1 Cymbal.wav", // 6
            "C:\\Users\\Admin\\Desktop\\鼓声\\fold2\\drum\\tom2_MidTom.wav",   // 7
            "C:\\Users\\Admin\\Desktop\\鼓声\\fold2\\drum\\Floor Tom.wav",     // 8
            "C:\\Users\\Admin\\Desktop\\鼓声\\fold2\\drum\\Hi-Hat Closed.wav", // 9
            "C:\\Users\\Admin\\Desktop\\鼓声\\fold2\\drum\\Kick Drum.wav"      // 10
    };

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class DrumNote {
        public double beginTime;
        public int key;
    }

    public static void main(String[] args) {
        String filePath = "D:\\Downloads\\S00023_test\\S00023\\S00023\\S00023_level2反编译.json";
        String outputPath = "D:\\Downloads\\S00023_test\\S00023\\S00023\\bin.wav";

        try {
            System.out.println("正在读取 JSON...");
            String json = new String(Files.readAllBytes(Paths.get(filePath)), "UTF-8");
            ObjectMapper mapper = new ObjectMapper();
            DrumNote[] notes = mapper.readValue(json, DrumNote[].class);

            if (notes == null || notes.length == 0) return;

            // 1. 预加载真实鼓声采样到缓存
            System.out.println("正在加载真实鼓声采样...");
            short[][] toneCache = new short[DRUM_SAMPLES.length][];
            for (int i = 0; i < DRUM_SAMPLES.length; i++) {
                toneCache[i] = loadWavSample(DRUM_SAMPLES[i]);
            }

            // 2. 计算音频总长度
            double maxEndTime = 0;
            for (DrumNote note : notes) {
                double timeSec = TIME_IS_MILLIS ? note.beginTime / 1000.0 : note.beginTime;
                // 注意：这里需要考虑每个采样本身的长度
                maxEndTime = Math.max(maxEndTime, timeSec + 2.0); // 暂定预留2秒冗余
            }

            int totalSamples = (int) ((maxEndTime + 1.0) * SAMPLE_RATE);
            short[] mixBuffer = new short[totalSamples];

            System.out.println("开始混音处理，总采样数: " + totalSamples);

            // 3. 核心混音逻辑
            for (DrumNote note : notes) {
                double timeSec = TIME_IS_MILLIS ? note.beginTime / 1000.0 : note.beginTime;
                int startSampleIndex = (int) (timeSec * SAMPLE_RATE);

                List<Integer> activeKeys = parseKeys(note.key);

                for (int keyIndex : activeKeys) {
                    if (keyIndex >= toneCache.length || toneCache[keyIndex] == null) continue;

                    short[] toneSamples = toneCache[keyIndex];

                    for (int i = 0; i < toneSamples.length; i++) {
                        int targetIndex = startSampleIndex + i;
                        if (targetIndex >= mixBuffer.length) break;

                        // 叠加并限幅
                        int mixed = mixBuffer[targetIndex] + toneSamples[i];
                        if (mixed > Short.MAX_VALUE) mixed = Short.MAX_VALUE;
                        else if (mixed < Short.MIN_VALUE) mixed = Short.MIN_VALUE;

                        mixBuffer[targetIndex] = (short) mixed;
                    }
                }
            }

            // 4. 导出 WAV
            System.out.println("正在导出音频文件...");
            writeWavFile(outputPath, mixBuffer);
            System.out.println("生成完成: " + outputPath);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * 读取 WAV 文件并转换为 short[] 采样数据
     */
    private static short[] loadWavSample(String path) {
        File file = new File(path);
        if (!file.exists()) {
            System.err.println("警告：找不到采样文件 " + path);
            return null;
        }
        try (AudioInputStream ais = AudioSystem.getAudioInputStream(file)) {
            AudioFormat targetFormat = new AudioFormat(SAMPLE_RATE, 16, 1, true, false);
            try (AudioInputStream convertedAis = AudioSystem.getAudioInputStream(targetFormat, ais)) {

                // --- Java 8 兼容写法开始 ---
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                byte[] buffer = new byte[4096];
                int n;
                while ((n = convertedAis.read(buffer)) != -1) {
                    baos.write(buffer, 0, n);
                }
                byte[] bytes = baos.toByteArray();
                // --- Java 8 兼容写法结束 ---

                short[] samples = new short[bytes.length / 2];
                ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(samples);
                return samples;
            }
        } catch (Exception e) {
            System.err.println("读取采样失败: " + path);
            e.printStackTrace();
            return null;
        }
    }

    private static List<Integer> parseKeys(int key) {
        List<Integer> result = new ArrayList<>();
        // 注意：原 key 掩码如果是从 1 开始的，i 应该对应索引 (i-1)
        for (int i = 0; i < 10; i++) {
            if ((key & (1 << i)) != 0) {
                result.add(i); // 这里 i 0-9 对应 DRUM_SAMPLES 的 0-9
            }
        }
        return result;
    }

    private static void writeWavFile(String path, short[] audioData) throws IOException {
        byte[] byteBuffer = new byte[audioData.length * 2];
        ByteBuffer.wrap(byteBuffer).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().put(audioData);

        AudioFormat format = new AudioFormat(SAMPLE_RATE, 16, 1, true, false);
        try (ByteArrayInputStream bais = new ByteArrayInputStream(byteBuffer);
             AudioInputStream ais = new AudioInputStream(bais, format, audioData.length)) {
            AudioSystem.write(ais, AudioFileFormat.Type.WAVE, new File(path));
        }
    }
}