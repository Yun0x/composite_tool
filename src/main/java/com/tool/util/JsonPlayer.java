package com.tool.util;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import javafx.embed.swing.JFXPanel;
import javafx.scene.media.AudioClip;
import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;

import java.io.File;
import java.util.*;

public class JsonPlayer {

    private static final Map<String, AudioClip> AUDIO_MAP = new HashMap<>();
    private static final Map<String, Integer> MASK_MAP = new LinkedHashMap<>();
    private static final String DRUM_PATH = "C:\\Users\\Admin\\Desktop\\鼓声\\fold2\\";

    // 默认音量设置 (0.0 - 1.0)
    private double currentDrumVolume = 1.0;

    static {
        new JFXPanel(); // 初始化 JavaFX

        MASK_MAP.put("HH", 1);
        MASK_MAP.put("CY", 2);
        MASK_MAP.put("SD", 4);
        MASK_MAP.put("HT", 8);
        MASK_MAP.put("LC", 16);
        MASK_MAP.put("LP", 32);
        MASK_MAP.put("LT", 64);
        MASK_MAP.put("FT", 128);
        MASK_MAP.put("FI", 256);
        MASK_MAP.put("ED", 512);

        String[] keys = {"SD", "HH", "CY", "FT", "LT", "HT", "LP", "LC", "ED", "FI"};
        String[] files = {"Snare Drum.wav", "Hi-Hat Open.wav", "Clash2.wav", "Floor Tom.wav",
                "tom2_MidTom.wav", "tom1_HighTom.wav", "Crash1 Cymbal.wav",
                "Ride Cymbal.wav", "Kick Drum.wav", "Hi-Hat Closed.wav"};

        for (int i = 0; i < keys.length; i++) {
            AUDIO_MAP.put(keys[i], loadClip(DRUM_PATH + files[i]));
        }
    }
    private static AudioClip loadClip(String path) {
        File file = new File(path);
        return file.exists() ? new AudioClip(file.toURI().toString()) : null;
    }
    /**
     * 设置鼓声的统一音量
     * @param volume 0.0 到 1.0
     */
    public void setDrumVolume(double volume) {
        this.currentDrumVolume = volume;
        for (AudioClip clip : AUDIO_MAP.values()) {
            if (clip != null) clip.setVolume(volume);
        }
    }
    public void startSyncPlay(String jsonPath, String mp3Path, double drumVol, double musicVol) throws Exception {
        // 1. 检查并解析 JSON 文件
        File jFile = new File(jsonPath);
        if (jFile.isDirectory()) {
            throw new IllegalArgumentException("错误：jsonPath 指向的是文件夹，请指定具体的 .json 文件路径！");
        }
        ObjectMapper mapper = new ObjectMapper();
        List<Map<String, Object>> drumEvents = mapper.readValue(
                jFile,
                new TypeReference<List<Map<String, Object>>>() {}
        );
        drumEvents.sort(Comparator.comparingLong(m -> Long.parseLong(m.get("beginTime").toString())));
        // 2. 准备 MP3 播放器
        Media musicMedia = new Media(new File(mp3Path).toURI().toString());
        MediaPlayer musicPlayer = new MediaPlayer(musicMedia);

        // 设置背景音乐音量
        musicPlayer.setVolume(musicVol);
        // 设置当前鼓声音量
        setDrumVolume(drumVol);

        final Object lock = new Object();
        final boolean[] isReady = {false};

        musicPlayer.setOnReady(() -> {
            synchronized (lock) {
                isReady[0] = true;
                lock.notifyAll();
            }
        });

        System.out.println("正在缓冲音乐...");
        synchronized (lock) {
            if (!isReady[0]) lock.wait(10000);
        }

        System.out.println("开始播放！(背景音乐音量: " + musicVol + ", 鼓声音量: " + drumVol + ")");
        musicPlayer.play();
        long startTime = System.currentTimeMillis();

        for (Map<String, Object> event : drumEvents) {
            long targetMs = Long.parseLong(event.get("beginTime").toString());
            int keyMask = Integer.parseInt(event.get("key").toString());

            long currentTime = System.currentTimeMillis() - startTime;
            long sleepTime = targetMs - currentTime;

            if (sleepTime > 0) {
                Thread.sleep(sleepTime);
            }

            triggerByMask(keyMask);
        }

        System.out.println("鼓点播放完毕。");
    }

    private void triggerByMask(int keyMask) {
        MASK_MAP.forEach((name, mask) -> {
            if ((keyMask & mask) != 0) {
                AudioClip clip = AUDIO_MAP.get(name);
                if (clip != null) {
                    // 确保音量实时应用
                    clip.play();
                }
            }
        });
    }

    public static void main(String[] args) {
        try {
            String jsonFile = "D:\\Downloads\\离开地球表面测试json\\离开地球表面反编译.json";
            String mp3File = "D:\\Downloads\\离开地球表面测试json\\离开地球表面.mp3";
            JsonPlayer player = new JsonPlayer();
            player.startSyncPlay(jsonFile, mp3File, 1.0, 0.1);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}