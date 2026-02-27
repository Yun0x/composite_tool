package com.tool.util;

import javafx.embed.swing.JFXPanel;
import javafx.scene.media.AudioClip;
import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.*;

public class SimpleDtxPlayer {

    private static final Map<String, AudioClip> AUDIO_MAP = new HashMap<>();
    private static final String DRUM_PATH = "C:\\Users\\Admin\\Desktop\\鼓声\\fold2\\";

    static {
        new JFXPanel(); // 初始化 JavaFX
        // 初始化音效路径
        AUDIO_MAP.put("SD", loadClip(DRUM_PATH + "Snare Drum.wav"));
        AUDIO_MAP.put("HH", loadClip(DRUM_PATH + "Hi-Hat Open.wav"));
        AUDIO_MAP.put("CY", loadClip(DRUM_PATH + "Clash2.wav"));
        AUDIO_MAP.put("FT", loadClip(DRUM_PATH + "Floor Tom.wav"));
        AUDIO_MAP.put("LT", loadClip(DRUM_PATH + "tom2_MidTom.wav"));
        AUDIO_MAP.put("HT", loadClip(DRUM_PATH + "tom1_HighTom.wav"));
        AUDIO_MAP.put("LP", loadClip(DRUM_PATH + "Crash1 Cymbal.wav"));
        AUDIO_MAP.put("LC", loadClip(DRUM_PATH + "Ride Cymbal.wav"));
        AUDIO_MAP.put("ED", loadClip(DRUM_PATH + "Kick Drum.wav"));
        AUDIO_MAP.put("FI", loadClip(DRUM_PATH + "Hi-Hat Closed.wav"));
    }

    private static AudioClip loadClip(String path) {
        File file = new File(path);
        if (!file.exists()) {
            System.err.println("音效文件不存在: " + path);
            return null;
        }
        return new AudioClip(file.toURI().toString());
    }

    public static void main(String[] args) throws Exception {
        String dtxFile = "D:\\Document\\测试.dtx";
        String mp3File = "D:\\Document\\drumFiles\\正式歌曲\\result\\张灯结彩\\孤勇者.mp3";

        List<String> dtxLines = readLines(dtxFile);

        // 动态读取 BPM，默认 120
        double bpm = extractBPM(dtxLines);
        System.out.println("BPM: " + bpm);

        // 每格时间 = 60 / BPM / 4 (16分音符假设4格/拍)
        BigDecimal perGridSec = BigDecimal.valueOf(60.0 / bpm / 4.0);

        // 构建小节 -> 通道 -> 数据映射，并排序小节
        TreeMap<Integer, Map<String, String>> sectionMap = new TreeMap<>();
        for (String line : dtxLines) {
            line = line.trim();
            if (!line.startsWith("#") || !line.contains(":")) continue;

            String[] parts = line.split(":", 2);
            if (parts.length < 2) continue;

            String code = parts[0].substring(1).trim(); // 去掉 #
            String data = parts[1].trim();

            // 只处理数字开头的小节行
            if (!Character.isDigit(code.charAt(0))) continue;
            if (code.length() < 4) continue;

            int section = Integer.parseInt(code.substring(0, 3));
            String channel = code.substring(3);

            sectionMap.computeIfAbsent(section, k -> new HashMap<>()).put(channel, data);
        }

        // 填充缺失小节
        int minSection = sectionMap.firstKey();
        int maxSection = sectionMap.lastKey();
        for (int s = minSection; s <= maxSection; s++) {
            sectionMap.computeIfAbsent(s, k -> new HashMap<>()); // 空小节
        }

        // 构建时间线
        TreeMap<Double, List<String>> timeMap = new TreeMap<>();
        for (Map.Entry<Integer, Map<String, String>> secEntry : sectionMap.entrySet()) {
            int section = secEntry.getKey();
            Map<String, String> channelMap = secEntry.getValue();
            for (Map.Entry<String, String> chEntry : channelMap.entrySet()) {
                String channel = chEntry.getKey();
                String data = chEntry.getValue();
                if (data == null || data.isEmpty()) {
                    StringBuilder sb = new StringBuilder();
                    for (int j = 0; j < 16; j++) sb.append("02");
                    data = sb.toString();
                }
                for (int i = 0; i < data.length(); i += 2) {
                    if (i + 2 > data.length()) break;
                    String val = data.substring(i, i + 2);
                    if (!val.equals("02")) {
                        double tSec = sectionIndexToTime(section, i / 2, perGridSec);
                        String instr = channelToInstrument(channel);
                        if (instr != null) {
                            timeMap.computeIfAbsent(tSec, k -> new ArrayList<>()).add(instr);
                        }
                    }
                }
            }
        }

        // 播放 MP3
        MediaPlayer mp3Player = new MediaPlayer(new Media(new File(mp3File).toURI().toString()));
        mp3Player.setVolume(1);

        final boolean[] ready = {false};
        mp3Player.setOnReady(() -> {
            ready[0] = true;
            synchronized (ready) { ready.notifyAll(); }
        });

        synchronized (ready) {
            if (!ready[0]) ready.wait(5000);
        }

        // 鼓播放线程
        Thread drumThread = new Thread(() -> {
                long startTime = System.currentTimeMillis();
            int lastSection = -1;
            for (Map.Entry<Double, List<String>> entry : timeMap.entrySet()) {
                double tSec = entry.getKey();
                long target = startTime + (long)(tSec * 1000);
                long delay = target - System.currentTimeMillis();
                if (delay > 0) try { Thread.sleep(delay); } catch(Exception e){}

                int currentSection = sectionOfTime(tSec, perGridSec);
                if (currentSection != lastSection) {
                    System.out.println("当前小节: " + currentSection);
                    lastSection = currentSection;
                }

                for (String instr : entry.getValue()) {
                    AudioClip clip = AUDIO_MAP.get(instr);
                    if (clip != null) clip.play();
                }
            }
        });

        mp3Player.play();
        drumThread.start();
        drumThread.join();

        mp3Player.stop();
        mp3Player.dispose();
    }

    private static List<String> readLines(String path) throws IOException {
        BufferedReader br = new BufferedReader(new FileReader(path));
        List<String> lines = new ArrayList<>();
        String line;
        while((line = br.readLine()) != null) lines.add(line);
        br.close();
        return lines;
    }

    private static double extractBPM(List<String> lines) {
        for (String line : lines) {
            line = line.trim().toUpperCase();
            if (line.startsWith("#BPM:")) {
                try {
                    return Double.parseDouble(line.substring(5).trim());
                } catch(Exception e) {}
            }
        }
        System.out.println("未找到 BPM，使用默认 120");
        return 120.0;
    }

    private static double sectionIndexToTime(int section, int grid, BigDecimal perGridSec) {
        return (section - 22) * 16 * perGridSec.doubleValue() + grid * perGridSec.doubleValue();
    }

    private static int sectionOfTime(double timeSec, BigDecimal perGridSec) {
        int totalGrids = (int)(timeSec / perGridSec.doubleValue());
        int sectionIndex = totalGrids / 16;
        return sectionIndex + 22;
    }

    private static String channelToInstrument(String channel) {
        switch(channel.toUpperCase()) {
            case "22": return "SD";
            case "1B": return "HH";
            case "26": return "CY";
            case "21": return "FT";
            case "1F": return "LT";
            case "24": return "HT";
            case "25": return "LP";
            case "2A": return "LC";
            case "1D": return "ED";
            case "5D": return "FI";
            default: return null;
        }
    }
}
