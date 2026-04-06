package com.tool.util;

import com.tool.vo.DrumInfo;
import org.springframework.web.multipart.MultipartFile;
import org.w3c.dom.*;
import org.xml.sax.InputSource;

import javax.xml.parsers.*;
import java.io.*;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

public class MusicXmlUtil {

    // 1. 定义打击乐特殊映射表 (位置 + 符头 -> MIDI)
    private static final Map<String, Integer> DRUM_MAPPING = new HashMap<>();

    static {
        DRUM_MAPPING.put("G5-normal", 1);   // HH(开镲)
        DRUM_MAPPING.put("A5-x", 1);        // CY(Crash Cymabl)
        DRUM_MAPPING.put("C5-normal", 4);   // SD(Snare Drum)
        DRUM_MAPPING.put("C5-x", 4);        // SD(Snare Drum)
        DRUM_MAPPING.put("B4-normal", 4);   // SD(Snare Drum)
        DRUM_MAPPING.put("E5-normal", 8);   // HT(High Tom/Tom1)
        DRUM_MAPPING.put("F5-x", 2);       // LC(Ride Cymbal)
        DRUM_MAPPING.put("B5-x", 16);       // LP(Crash1)
        DRUM_MAPPING.put("D5-normal", 64);  // LT(Low Tom/Tom2)
        DRUM_MAPPING.put("A4-normal", 128); // FT(Floor Tom/Tom3)
        DRUM_MAPPING.put("D4-x", 256);      // ED(Bass Drum)
        DRUM_MAPPING.put("F4-normal", 512); // ED2(Bass Drum)
        DRUM_MAPPING.put("G5-x", 257);         //闭镲（HH+FI)
        DRUM_MAPPING.put("E4-normal", 512); // 闭镲（HH+FI)
        DRUM_MAPPING.put("C6-x", 16);       // 水镲，但是算做LC
        DRUM_MAPPING.put("B5-normal", 32);  // China Cymbal 算作LP
//        DRUM_MAPPING.put("A5-x", 1);  // HH(开镲)


//        DRUM_MAPPING.put("G5-normal", 1);   // HH(开镲)
//        DRUM_MAPPING.put("A5-x", 2);        // CY(Crash Cymabl)
//        DRUM_MAPPING.put("C5-normal", 4);   // SD(Snare Drum)
//        DRUM_MAPPING.put("E5-normal", 8);   // HT(High Tom/Tom1)
//        DRUM_MAPPING.put("F5-x", 16);       // LC(Ride Cymbal)
//        DRUM_MAPPING.put("B5-x", 32);       // LP(Crash1)
//        DRUM_MAPPING.put("D5-normal", 64);  // LT(Low Tom/Tom2)
//        DRUM_MAPPING.put("A4-normal", 128); // FT(Floor Tom/Tom3)
//        DRUM_MAPPING.put("G5-x", 256);      // FI(Closed Hi-Hat)
//        DRUM_MAPPING.put("D4-x", 512);      // ED(Bass Drum)
//        DRUM_MAPPING.put("F4-normal", 512); // ED2(Bass Drum)
//        DRUM_MAPPING.put("E4-normal", 257); // 闭镲（HH+FI)
//        DRUM_MAPPING.put("C6-x", 16);       // 水镲，但是算做LC
//        DRUM_MAPPING.put("B5-normal", 32);  // China Cymbal 算作LP
    }

    // 内部类：记录变速点
    private static class TempoChange {
        double offset; // 拍数偏移
        double bpm;

        TempoChange(double offset, double bpm) {
            this.offset = offset;
            this.bpm = bpm;
        }
    }

    // 内部类：记录解析到的音符事件
    private static class NoteEvent {
        double offset; // 拍数偏移
        int midiVal;

        NoteEvent(double offset, int midiVal) {
            this.offset = offset;
            this.midiVal = midiVal;
        }
    }

    public static List<Map<String, Object>> parseMusicWithVariableTempo(String filePath, String jsonOutput) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setValidating(false);
        factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
        factory.setFeature("http://xml.org/sax/features/validation", false);
        DocumentBuilder builder = factory.newDocumentBuilder();
        builder.setEntityResolver((publicId, systemId) -> new InputSource(new StringReader("")));
        Document doc = builder.parse(new File(filePath));

        List<TempoChange> tempoChanges = new ArrayList<>();
        List<NoteEvent> noteEvents = new ArrayList<>();

        NodeList parts = doc.getElementsByTagName("part");

        // ================= 阶段 1：解析拍数偏移 (Offset) =================
        for (int p = 0; p < parts.getLength(); p++) {
            Element part = (Element) parts.item(p);
            double globalOffset = 0.0; // 当前声部的时间轴指针
            int divisions = 1;

            NodeList measures = part.getElementsByTagName("measure");
            for (int m = 0; m < measures.getLength(); m++) {
                Element measure = (Element) measures.item(m);
                double maxMeasureOffset = globalOffset; // 用于记录本小节推进的最远距离（处理多声部 backup 的关键）
                double lastNoteDuration = 0.0; // 供和弦(chord)回退使用

                NodeList children = measure.getChildNodes();
                for (int i = 0; i < children.getLength(); i++) {
                    Node node = children.item(i);
                    if (node.getNodeType() != Node.ELEMENT_NODE) continue;
                    Element el = (Element) node;
                    String tagName = el.getTagName();

                    // 1. 更新 divisions (四分音符的单位)
                    if ("attributes".equals(tagName)) {
                        NodeList divNodes = el.getElementsByTagName("divisions");
                        if (divNodes.getLength() > 0) {
                            divisions = Integer.parseInt(divNodes.item(0).getTextContent());
                        }
                    }
                    // 2. 处理变速标记
                    else if ("direction".equals(tagName)) {
                        NodeList metro = el.getElementsByTagName("metronome");
                        if (metro.getLength() > 0) {
                            NodeList bpmNode = el.getElementsByTagName("per-minute");
                            if (bpmNode.getLength() > 0) {
                                double bpm = Double.parseDouble(bpmNode.item(0).getTextContent());
                                tempoChanges.add(new TempoChange(globalOffset, bpm));
                            }
                        } else {
                            NodeList sound = el.getElementsByTagName("sound");
                            if (sound.getLength() > 0 && sound.item(0).getAttributes().getNamedItem("tempo") != null) {
                                double bpm = Double.parseDouble(sound.item(0).getAttributes().getNamedItem("tempo").getNodeValue());
                                tempoChanges.add(new TempoChange(globalOffset, bpm));
                            }
                        }
                    }
                    // 3. 处理音符
                    else if ("note".equals(tagName)) {
                        boolean isChord = el.getElementsByTagName("chord").getLength() > 0;
                        boolean isRest = el.getElementsByTagName("rest").getLength() > 0;

                        double durationBeats = 0;
                        NodeList durNodes = el.getElementsByTagName("duration");
                        if (durNodes.getLength() > 0) {
                            durationBeats = Double.parseDouble(durNodes.item(0).getTextContent()) / divisions;
                        }

                        // 如果是和弦，起始时间要回退到上一个音符的起始时间
                        if (isChord) {
                            globalOffset -= lastNoteDuration;
                        }

                        double noteStartOffset = globalOffset;

                        // 游标前进
                        globalOffset += durationBeats;
                        lastNoteDuration = durationBeats;
                        maxMeasureOffset = Math.max(maxMeasureOffset, globalOffset); // 更新本小节最远到达点

                        // 如果不是休止符，提取并记录音高
                        if (!isRest) {
                            NodeList pitchNodes = el.getElementsByTagName("pitch");
                            if (pitchNodes.getLength() > 0) {
                                Element pitchEl = (Element) pitchNodes.item(0);
                                String step = pitchEl.getElementsByTagName("step").item(0).getTextContent();
                                int octave = Integer.parseInt(pitchEl.getElementsByTagName("octave").item(0).getTextContent());
                                int alter = 0;
                                NodeList alterNodes = pitchEl.getElementsByTagName("alter");
                                if (alterNodes.getLength() > 0) {
                                    alter = (int) Double.parseDouble(alterNodes.item(0).getTextContent());
                                }
                                String pos = step + octave;
                                String head = "normal";
                                NodeList headNodes = el.getElementsByTagName("notehead");
                                if (headNodes.getLength() > 0) {
                                    head = headNodes.item(0).getTextContent();
                                }

                                String mapKey = pos + "-" + head;
                                Integer midiVal = DRUM_MAPPING.get(mapKey);
                                if (midiVal != null) {
                                    noteEvents.add(new NoteEvent(noteStartOffset, midiVal));
                                }
                            }
                        }
                    }
                    // 4. 处理 <backup> (多声部时间回溯)
                    else if ("backup".equals(tagName)) {
                        NodeList durNodes = el.getElementsByTagName("duration");
                        if (durNodes.getLength() > 0) {
                            globalOffset -= Double.parseDouble(durNodes.item(0).getTextContent()) / divisions;
                        }
                    }
                    // 5. 处理 <forward> (跳过时间)
                    else if ("forward".equals(tagName)) {
                        NodeList durNodes = el.getElementsByTagName("duration");
                        if (durNodes.getLength() > 0) {
                            globalOffset += Double.parseDouble(durNodes.item(0).getTextContent()) / divisions;
                            maxMeasureOffset = Math.max(maxMeasureOffset, globalOffset);
                        }
                    }
                }
                // 关键修正：一个小节结束后，游标必须对齐到该小节最长的声部末尾，避免时间轴缩短
                globalOffset = maxMeasureOffset;
            }
        }

        //时间转换与汇总
        // 1. 整理全局 Tempo Map 并去重、排序
        if (tempoChanges.isEmpty()) {
            tempoChanges.add(new TempoChange(0.0, 120.0));
        }
        tempoChanges.sort(Comparator.comparingDouble(t -> t.offset));
        // 2. 将所有音符按毫秒分组聚合
        TreeMap<Integer, Set<Integer>> timeGroups = new TreeMap<>();
        for (NoteEvent event : noteEvents) {
            int msTime = getMsAtOffset(event.offset, tempoChanges);
            timeGroups.computeIfAbsent(msTime, k -> new HashSet<>()).add(event.midiVal);
        }
        // 3. 组装最终 JSON 格式
        List<Map<String, Object>> finalOutput = new ArrayList<>();
        for (Map.Entry<Integer, Set<Integer>> entry : timeGroups.entrySet()) {
            Map<String, Object> item = new HashMap<>();
            item.put("beginTime", entry.getKey());
            // 累加 unique_keys
            int keySum = entry.getValue().stream().mapToInt(Integer::intValue).sum();
            item.put("key", keySum);
            item.put("endTime", entry.getKey() + 100);
            finalOutput.add(item);
        }
        // 4. 导出 JSON
        if (jsonOutput != null && !jsonOutput.isEmpty()) {
            ObjectMapper mapper = new ObjectMapper();
            mapper.enable(SerializationFeature.INDENT_OUTPUT);
            mapper.writeValue(new File(jsonOutput), finalOutput);
        }
        return finalOutput;
    }

    public static List<DrumInfo> parseMusicWithVariableTempo(MultipartFile file) throws Exception {
        // 1. 初始化解析器并防止加载外部 DTD (安全配置)
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setValidating(false);
        factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
        factory.setFeature("http://xml.org/sax/features/validation", false);
        DocumentBuilder builder = factory.newDocumentBuilder();
        builder.setEntityResolver((publicId, systemId) -> new InputSource(new StringReader("")));

        Document doc;
        try (InputStream is = file.getInputStream()) {
            doc = builder.parse(is);
        }

        List<TempoChange> tempoChanges = new ArrayList<>();
        List<NoteEvent> noteEvents = new ArrayList<>();

        NodeList parts = doc.getElementsByTagName("part");

        for (int p = 0; p < parts.getLength(); p++) {
            Element part = (Element) parts.item(p);
            double globalOffset = 0.0; // 拍数偏移
            int divisions = 1;        // 每四分音符包含的单位数

            NodeList measures = part.getElementsByTagName("measure");
            for (int m = 0; m < measures.getLength(); m++) {
                Element measure = (Element) measures.item(m);
                double maxMeasureOffset = globalOffset;
                double lastNoteDuration = 0.0;

                NodeList children = measure.getChildNodes();
                for (int i = 0; i < children.getLength(); i++) {
                    Node node = children.item(i);
                    if (node.getNodeType() != Node.ELEMENT_NODE) continue;

                    Element el = (Element) node;
                    String tagName = el.getTagName();

                    // 处理 divisions (解析精度)
                    if ("attributes".equals(tagName)) {
                        NodeList divNodes = el.getElementsByTagName("divisions");
                        if (divNodes.getLength() > 0) {
                            divisions = Integer.parseInt(divNodes.item(0).getTextContent());
                        }
                    }
                    // 处理变速 (Tempo)
                    else if ("direction".equals(tagName)) {
                        NodeList metro = el.getElementsByTagName("metronome");
                        if (metro.getLength() > 0) {
                            NodeList bpmNode = el.getElementsByTagName("per-minute");
                            if (bpmNode.getLength() > 0) {
                                double bpm = Double.parseDouble(bpmNode.item(0).getTextContent());
                                tempoChanges.add(new TempoChange(globalOffset, bpm));
                            }
                        } else {
                            NodeList sound = el.getElementsByTagName("sound");
                            if (sound.getLength() > 0 && sound.item(0).getAttributes().getNamedItem("tempo") != null) {
                                double bpm = Double.parseDouble(sound.item(0).getAttributes().getNamedItem("tempo").getNodeValue());
                                tempoChanges.add(new TempoChange(globalOffset, bpm));
                            }
                        }
                    }
                    // 处理音符
                    else if ("note".equals(tagName)) {
                        boolean isChord = el.getElementsByTagName("chord").getLength() > 0;
                        boolean isRest = el.getElementsByTagName("rest").getLength() > 0;

                        // 计算当前音符占据的拍数
                        double durationBeats = 0;
                        NodeList durNodes = el.getElementsByTagName("duration");
                        if (durNodes.getLength() > 0) {
                            durationBeats = Double.parseDouble(durNodes.item(0).getTextContent()) / divisions;
                        }

                        // 如果是和音，回退到当前组的起始位置
                        if (isChord) {
                            globalOffset -= lastNoteDuration;
                        }

                        double noteStartOffset = globalOffset;
                        globalOffset += durationBeats;
                        lastNoteDuration = durationBeats;
                        maxMeasureOffset = Math.max(maxMeasureOffset, globalOffset);

                        // 如果不是休止符，则解析打击乐映射
                        if (!isRest) {
                            NodeList pitchNodes = el.getElementsByTagName("pitch");
                            if (pitchNodes.getLength() > 0) {
                                Element pitchEl = (Element) pitchNodes.item(0);
                                String step = pitchEl.getElementsByTagName("step").item(0).getTextContent();
                                int octave = Integer.parseInt(pitchEl.getElementsByTagName("octave").item(0).getTextContent());

                                // 获取音符头类型 (用于区分打击乐器，如 normal, x, cross)
                                String head = "normal";
                                NodeList headNodes = el.getElementsByTagName("notehead");
                                if (headNodes.getLength() > 0) {
                                    head = headNodes.item(0).getTextContent();
                                }

                                String mapKey = step + octave + "-" + head;
                                Integer midiVal = DRUM_MAPPING.get(mapKey);

                                // --- 核心修改：如果映射表里没有，直接忽略该音符 ---
                                if (midiVal != null) {
                                    noteEvents.add(new NoteEvent(noteStartOffset, midiVal));
                                }
                            }
                        }
                    }
                    // 处理回退 (多声部处理)
                    else if ("backup".equals(tagName)) {
                        NodeList durNodes = el.getElementsByTagName("duration");
                        if (durNodes.getLength() > 0) {
                            globalOffset -= Double.parseDouble(durNodes.item(0).getTextContent()) / divisions;
                        }
                    }
                    // 处理前进
                    else if ("forward".equals(tagName)) {
                        NodeList durNodes = el.getElementsByTagName("duration");
                        if (durNodes.getLength() > 0) {
                            globalOffset += Double.parseDouble(durNodes.item(0).getTextContent()) / divisions;
                            maxMeasureOffset = Math.max(maxMeasureOffset, globalOffset);
                        }
                    }
                }
                // 确保小节结束时位移同步
                globalOffset = maxMeasureOffset;
            }
        }

        // 3. 转换时间为毫秒
        if (tempoChanges.isEmpty()) {
            tempoChanges.add(new TempoChange(0.0, 120.0));
        }
        tempoChanges.sort(Comparator.comparingDouble(t -> t.offset));

        // 按毫秒时间戳分组音符（合并同一时间点的多个击打）
        TreeMap<Integer, Set<Integer>> timeGroups = new TreeMap<>();
        for (NoteEvent event : noteEvents) {
            int msTime = getMsAtOffset(event.offset, tempoChanges);
            timeGroups.computeIfAbsent(msTime, k -> new HashSet<>()).add(event.midiVal);
        }

        // 4. 生成结果列表
        List<DrumInfo> drumInfoArrayList = new ArrayList<>();
        for (Map.Entry<Integer, Set<Integer>> entry : timeGroups.entrySet()) {
            DrumInfo drum = new DrumInfo();
            // 计算同一时间点所有音符的 Key 总和（或根据业务逻辑修改）
            int keySum = entry.getValue().stream().mapToInt(Integer::intValue).sum();

            drum.setBeginTime(BigDecimal.valueOf(entry.getKey()));
            drum.setKey(keySum);
            drum.setEndTime(BigDecimal.valueOf(entry.getKey() + 100)); // 默认持续 100ms
            drumInfoArrayList.add(drum);
        }

        return drumInfoArrayList;
    }

    // 将拍数偏移量转换为绝对毫秒数
    private static int getMsAtOffset(double targetOffset, List<TempoChange> tempoChanges) {
        double totalMs = 0.0;
        double currentOffset = 0.0;
        double currentBpm = 120.0;

        for (TempoChange tc : tempoChanges) {
            if (targetOffset > tc.offset) {
                // 如果当前目标点大于这个变速点，说明度过了一段旧速度区间
                double durationBeats = tc.offset - currentOffset;
                totalMs += durationBeats * (60.0 / currentBpm) * 1000.0;
                currentOffset = tc.offset;
                currentBpm = tc.bpm;
            } else {
                // 目标点在当前速度区间内，停止遍历
                break;
            }
        }

        // 计算剩余部分的拍数并转为毫秒
        double remainingBeats = targetOffset - currentOffset;
        totalMs += remainingBeats * (60.0 / currentBpm) * 1000.0;

        return (int) Math.round(totalMs);
    }

    // 辅助方法：模拟 music21 的 pitch.midi 计算
    private static int calculateMidi(String step, int octave, int alter) {
        // C=0, D=2, E=4, F=5, G=7, A=9, B=11
        int[] stepVals = {9, 11, 0, 2, 4, 5, 7}; // 基于 'A' 的偏移下标
        int stepIdx = step.charAt(0) - 'A';
        return (octave + 1) * 12 + stepVals[stepIdx] + alter;
    }

    /**
     * 从 MultipartFile 中快速获取第一个出现的 BPM (per-minute) 值
     *
     * @param file 传入的 MusicXML 文件
     * @return 找到的第一个 BPM 整数值，若未找到则返回默认值 120
     */
    public static int getFirstBpmFromXml(File file) {
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            String soundTempo = null;
            while ((line = reader.readLine()) != null) {
                // 1. 优先找 <per-minute>
                Pattern bpmPattern = Pattern.compile("<per-minute>\\s*([0-9.]+)\\s*</per-minute>");
                Matcher bpmMatcher = bpmPattern.matcher(line);
                if (bpmMatcher.find()) {
                    return (int) Math.round(Double.parseDouble(bpmMatcher.group(1)));
                }
                // 2.找到的第一个 sound tempo 属性
                if (soundTempo == null) {
                    Pattern soundPattern = Pattern.compile("<sound[^>]*\\stempo=\"([0-9.]+)\"");
                    Matcher soundMatcher = soundPattern.matcher(line);
                    if (soundMatcher.find()) {
                        soundTempo = soundMatcher.group(1);
                    }
                }
            }
            // 如果循环结束了还没找到 per-minute，但找到了 sound tempo
            if (soundTempo != null) {
                return (int) Math.round(Double.parseDouble(soundTempo));
            }
        } catch (Exception e) {
            System.err.println("正则匹配BPM失败: " + e.getMessage());
        }
        // 默认返回值
        return 120;
    }

    public static int getDurationFromMultipartFile(File file) {
        return getMp3Duration(file);
    }

    public static int getMp3Duration(File file) {
        try {
            String[] command = {
                    "D:\\Code\\ffmpeg\\bin\\ffprobe.exe",
                    "-v", "error",
                    "-show_entries", "format=duration",
                    "-of", "default=noprint_wrappers=1:nokey=1",
                    file.getAbsolutePath()
            };
            Process process = Runtime.getRuntime().exec(command);
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line = reader.readLine();
            if (line != null) {
                double duration = Double.parseDouble(line);
                return (int) Math.round(duration); // 返回秒
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return 0;
    }

    public static void main(String[] args) {
        List<DrumInfo> drumInfoArrayList = new ArrayList<>();
        BigDecimal beginTime = new BigDecimal(62.2);
        BigDecimal IntervalTime = new BigDecimal(0.5);
        BigDecimal stepTime = new BigDecimal(1.4);
        for (int i = 0; i < 10; i++) {
            for (int j = 0; j < 4; j++) {
                DrumInfo drumInfo = new DrumInfo();
                drumInfo.setBeginTime(beginTime);
                drumInfo.setEndTime(beginTime.add(IntervalTime));
                drumInfo.setKey(1 << i);
                drumInfoArrayList.add(drumInfo);
                beginTime = beginTime.add(new BigDecimal(1));
            }
            beginTime = beginTime.add(stepTime);
        }
        byte[] bytes = new byte[drumInfoArrayList.size() * 6 + 5];
        int size = drumInfoArrayList.size();
        int realSize = size * 6;
        byte numHigh = (byte) ((realSize >> 8) & 0xFF);
        byte numLow = (byte) (realSize & 0xFF);
        bytes[0] = (byte) 0xAA;
        bytes[1] = numHigh;
        bytes[2] = numLow;
        int index = 3;
        for (DrumInfo info : drumInfoArrayList) {
            BigDecimal begin = info.getBeginTime().multiply(new BigDecimal(100)).setScale(0, RoundingMode.HALF_UP);
            BigDecimal end = info.getEndTime().multiply(new BigDecimal(100)).setScale(0, RoundingMode.HALF_UP);
            int key = info.getKey();
            int beginTimeValue = begin.intValue();
            int endTimeValue = end.intValue();
            byte beginHigh = (byte) ((beginTimeValue >> 8) & 0xFF);
            byte beginLow = (byte) (beginTimeValue & 0xFF);
            byte endHigh = (byte) ((endTimeValue >> 8) & 0xFF);
            byte endLow = (byte) (endTimeValue & 0xFF);
            byte keyHigh = (byte) ((key >> 8) & 0xFF);
            byte keyLow = (byte) (key & 0xFF);
            bytes[index++] = beginHigh;
            bytes[index++] = beginLow;
            bytes[index++] = endHigh;
            bytes[index++] = endLow;
            bytes[index++] = keyHigh;
            bytes[index++] = keyLow;
        }
        byte check = 0;
        for (int i = 0; i < bytes.length - 2; i++) {
            check = (byte) (check ^ bytes[i]);
        }
        bytes[index++] = check;
        bytes[index] = (byte) 0xEE;
        DtxUtils.saveFile("D:\\Downloads\\S20002_测试\\level1.bin", bytes);
    }
}