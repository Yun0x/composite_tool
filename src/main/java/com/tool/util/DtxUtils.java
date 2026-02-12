package com.tool.util;

import java.io.*;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.Inflater;
import java.util.zip.InflaterInputStream;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

/**
 * DTX 工具类
 * <p>
 * 用于解析 DTX 文件中的通道编号、乐器映射及节段编号。
 */
public class DtxUtils {

    /**
     * 通道号 -> 乐器名
     */
    public static final Map<String, String> CHANNEL_MAP;

    /**
     * 乐器名 -> 对应编号（1~10）
     */
    public static final Map<String, Integer> KEY_MAP;


    /**
     * 乐器名 -> 对应数值）
     */
    public static final Map<String, Integer> KEYVALUE_MAP;

    static {
        CHANNEL_MAP = Collections.unmodifiableMap(buildChannelMap());
        KEY_MAP = Collections.unmodifiableMap(buildKeyMap());
        KEYVALUE_MAP = Collections.unmodifiableMap(buildKeyValue());
    }

    public static int getNumberMap(String key) {
        if (key == null || key.length() != 2)
            throw new IllegalArgumentException("编号格式错误：" + key);

        int value = decode36(key.charAt(0)) * 36 + decode36(key.charAt(1));
        int base = decode36('0') * 36 + decode36('3');
        return value - base + 1;
    }

    public static String getNumberCode(int index) {
        if (index < 1)
            throw new IllegalArgumentException("index 必须 >= 1");

        int base = decode36('0') * 36 + decode36('3');
        int value = base + index - 1;
        int high = value / 36;
        int low = value % 36;
        return "" + encode36(high) + encode36(low);
    }

    public static String getSectionMap(int index) {
        if (index < 0 || index > 17)
            throw new IllegalArgumentException("index 超出范围 (0~17)");
        int base = 22 + index * 2;
        int value = (index % 2 == 0) ? base : base - 1;
        return String.format("%03d", value);
    }

    //小节号转实际序号
    public static int getSectionIndex(String code) {
        int value = Integer.parseInt(code);
        int base = 20;
        int diff = value - base;
        if (diff % 2 != 1) {
            diff = diff - 2;
        }
        return diff;
    }

    //实际序号转小节号
    public static String sectionIndexToCode(int index) {
        int base = 20;
        int value;
        if (index % 2 != 1) {
            value = base + index + 2;
        } else {
            value = base + index;
        }
        // 补齐为三位数
        return String.format("%03d", value);
    }


    private static int decode36(char c) {
        if (c >= '0' && c <= '9') return c - '0';
        if (c >= 'A' && c <= 'Z') return c - 'A' + 10;
        throw new IllegalArgumentException("非法字符: " + c);
    }

    private static char encode36(int n) {
        if (n >= 0 && n <= 9) return (char) ('0' + n);
        if (n >= 10 && n < 36) return (char) ('A' + (n - 10));
        throw new IllegalArgumentException("非法数值: " + n);
    }

    public static String getInstrumentByChannel(String channelCode) {
        if (channelCode == null) return null;
        return CHANNEL_MAP.get(channelCode.toUpperCase());
    }

    /**
     * 输入通道号直接返回对应鼓编号（1~10）
     *
     * @param channelCode 通道编号，如 "1B"
     * @return 对应的鼓编号（1~10），不存在返回 null
     */
    public static Integer getDrumNumberByChannel(String channelCode) {
        String instrument = getInstrumentByChannel(channelCode);
        if (instrument == null) return null;
        return KEYVALUE_MAP.get(instrument);
    }


    private static Map<String, String> buildChannelMap() {
        Map<String, String> reverse = new HashMap<>();
        reverse.put("18", "EPM");
        reverse.put("5C", "左右");
        reverse.put("57", "轻重");
        reverse.put("5A", "高连");
        reverse.put("55", "低连");
        reverse.put("FE", "符杆");
        reverse.put("2A", "LC");
        reverse.put("1B", "HH");
        reverse.put("25", "LP");
        reverse.put("22", "SD");
        reverse.put("1D", "ED");
        reverse.put("24", "HT");
        reverse.put("1F", "LT");
        reverse.put("21", "FT");
        reverse.put("26", "CY");
        reverse.put("5D", "FI");
        reverse.put("0B", "BGM");
        reverse.put("64", "AVI");
        reverse.put("14", "BG1");
        return reverse;
    }

    private static Map<String, Integer> buildKeyMap() {
        Map<String, Integer> map = new HashMap<>();
        map.put("HH", 1);
        map.put("CY", 2);
        map.put("SD", 3);
        map.put("HT", 4);
        map.put("LC", 5);
        map.put("LP", 6);
        map.put("LT", 7);
        map.put("FT", 8);
        map.put("FI", 9);
        map.put("ED", 10);
        return map;
    }

    private static Map<String, Integer> buildKeyValue() {
        Map<String, Integer> map = new HashMap<>();
        map.put("HH", 1);
        map.put("CY", 2);
        map.put("SD", 4);
        map.put("HT", 8);
        map.put("LC", 16);
        map.put("LP", 32);
        map.put("LT", 64);
        map.put("FT", 128);
        map.put("FI", 256);
        map.put("ED", 512);
        return map;
    }

    public static String extractTitle(String fileContent) {
        if (fileContent == null) return null;
        Pattern pattern = Pattern.compile("(?m)^#TITLE\\s*:\\s*(.+)$");
        Matcher matcher = pattern.matcher(fileContent);
        if (matcher.find()) {
            return matcher.group(1).trim();
        }
        return "null";
    }

    /**
     * 从 DTX 文件内容中提取所有 BPM 定义
     * 例如：
     * #BPM: 120
     * #BPM01: 187
     * #BPM02: 186
     * 返回：{"00"=120.0, "01"=187.0, "02"=186.0}
     */
    public static List<Double> extractBpmMap(String content) {
        List<Double> bpmList = new ArrayList<>();
        Pattern pattern = Pattern.compile("#BPM(?:[0-9A-Z]{0,2})\\s*:\\s*([0-9.]+)");
        Matcher matcher = pattern.matcher(content);

        while (matcher.find()) {
            double bpmValue = Double.parseDouble(matcher.group(1));
            bpmList.add(bpmValue);
        }

        return bpmList;
    }


    /**
     * 解析每一行的内容，例如：
     * "#0241D: 02030203020302020202020202020202"
     *
     * @param input DTX 行字符串
     * @return List，第一项为行号（去掉 #），后续为每两位分割的内容
     */
    public static List<String> splitDrumInfo(String input) {
        if (input == null || !input.contains(":")) {
            throw new IllegalArgumentException("输入格式不正确，必须包含 ':'");
        }

        String[] split = input.split(": ", 2); // 限制分割两部分，避免多余冒号影响
        String rowId = split[0].replace("#", "");
        String data = split[1];

        if (data.length() % 2 != 0) {
            throw new IllegalArgumentException("数据部分长度必须是2的倍数");
        }

        List<String> result = new ArrayList<>();
        result.add(rowId); // 第一项为行号

        for (int i = 0; i < data.length(); i += 2) {
            result.add(data.substring(i, i + 2));
        }

        return result;
    }


    /**
     * @Description:单条补齐 归一化后的同样格式字符串，保证鼓谱长度和结构满足特定规则
     * 分离 key 和 value 例如#0241B: 03020202020202020302020202020202
     * 拆分为 #0241B 以及 03020202020202020302020202020202
     * 先把鼓谱按 每 2 个字符切分 → chips 列表
     * 根据当前片段长度（1/2/4/8 个 chip）选择 补齐字符串 (pad)
     * 对每个 chip 拼接 pad，形成统一长度的节奏段
     * 低分辨率 (<36)：
     * 每个 chip 补齐 padding，输出固定长度
     * 高分辨率 (32/64)：
     * 每个 chip 压缩，只保留第一个有效事件
     * 去掉多余的 02，统一长度
     * 保证输出统一，便于 后续生成 DTX 指令或 bit 映射
     * 返回格式保持 DTX 标准：
     * @author Lachesism
     * @date 2025-12-29
     */

    public static String paddedDrumInfo(String input) {
        if (input == null || !input.contains(":")) {
            return input;
        }
        String[] split = input.split(": ", 2);
        String drumInfo = split[1];
        if (drumInfo.length() < 36 && drumInfo.length() != 6 && drumInfo.length() != 12 && drumInfo.length() != 18 && drumInfo.length() != 24 && drumInfo.length() != 30) {
            List<String> chips = new ArrayList<>();
            for (int i = 0; i < drumInfo.length(); i += 2) {
                chips.add(drumInfo.substring(i, i + 2));
            }

            String pad = "";
            switch (chips.size()) {
                case 1:
                    pad = "020202020202020202020202020202";
                    break;
                case 2:
                    pad = "02020202020202";
                    break;
                case 4:
                    pad = "020202";
                    break;
                case 8:
                    pad = "02";
                    break;
                default:
                    pad = "";
            }

            StringBuilder sb = new StringBuilder();
            for (String chip : chips) {
                sb.append(chip).append(pad);
            }

            return split[0] + ": " + sb.toString();
        }

        int len = drumInfo.length() / 2;
        if (len == 32) {
            List<String> chips = new ArrayList<>();
            for (int i = 0; i < drumInfo.length(); i += 4) {
                chips.add(drumInfo.substring(i, i + 4));
            }

            for (int i = 0; i < chips.size(); i++) {
                String chip = chips.get(i);
                if ("0202".equals(chip)) {
                    chip = "02";
                } else {
                    String left = chip.substring(0, 2);
                    String right = chip.substring(2, 4);
                    if ("02".equals(left)) chip = right;
                    else if ("02".equals(right)) chip = left;
                    else chip = right;
                }
                chips.set(i, chip);
            }

            return input;
        }
        if (len == 64) {
            List<String> chips = new ArrayList<>();
            for (int i = 0; i < drumInfo.length(); i += 8) {
                chips.add(drumInfo.substring(i, i + 8));
            }

            for (int i = 0; i < chips.size(); i++) {
                String chip = chips.get(i);
                if ("02020202".equals(chip)) {
                    chip = "02";
                } else {
                    String a = chip.substring(0, 2);
                    String b = chip.substring(2, 4);
                    String c = chip.substring(4, 6);
                    String d = chip.substring(6, 8);

                    if (!"02".equals(a)) chip = a;
                    else if (!"02".equals(c)) chip = c;
                    else if (!"02".equals(b)) chip = b;
                    else if (!"02".equals(d)) chip = d;
                    else chip = "02";
                }
                chips.set(i, chip);
            }

            return input;
        }

        /* =======================
         * - 长度不能被 4 整除 → 末尾补 02
         * - 均分 4 份
         * - 每份取前两位
         * - ≠02 → 03020202
         * - =02 → 02020202
         * - 固定输出 32 位
         * ======================= */
        // 补齐到能被 4 整除
        StringBuilder drumBuilder = new StringBuilder(drumInfo);
        while (drumBuilder.length() % 4 != 0) {
            drumBuilder.append("02");
        }
        drumInfo = drumBuilder.toString();
        paddedDrumInfo(drumInfo);
        int partLen = drumInfo.length() / 4;
        StringBuilder result = new StringBuilder(32);
        for (int i = 0; i < 4; i++) {
            int start = i * partLen;
            int end = (i == 3) ? drumInfo.length() : start + partLen;
            String part = drumInfo.substring(start, end);
            String head = part.substring(0, 2);
            if (!"02".equals(head)) {
                result.append("03020202");
            } else {
                result.append("02020202");
            }
        }

        return split[0] + ": " + result.toString();
    }

    //获取BPM通道中的对应的值
    public static Integer getBpmIndex(String drumInfo) {
        return getNumberMap(drumInfo) - 2;
    }

    public static List<String> extractDrumLines(String content) {
        List<String> result = new ArrayList<>();
        // 允许的通道号
        Set<String> allowedChannels = new HashSet<>(
                Arrays.asList("18", "2A", "1B", "25", "22", "1D", "24", "1F", "21", "26", "5D")
        );
        // 匹配行格式：#XXXYY: 值
        Pattern pattern = Pattern.compile("(?m)^#(\\d{3})([0-9A-Z]{2}):\\s*([^;\\r\\n]+)");
        Matcher matcher = pattern.matcher(content);
        while (matcher.find()) {
            String channel = matcher.group(2); // 通道号
            String fullLine = matcher.group().trim();
            if (!allowedChannels.contains(channel)) {
                continue; // 不在允许范围，跳过
            }
            // 拆分右半部分（: 之后的内容）
            String[] split = fullLine.split(":\\s*", 2);
            if (split.length < 2) continue;
            String rightPart = split[1].trim();
            if (rightPart.length() > 32 && rightPart.length() % 16 != 0) {
                continue;
            }
            result.add(fullLine);
        }
        return result;
    }


    /**
     * @Description：填充鼓点数据 比如021开头，到027，
     * 遍历每个小节 + 每个通道，生成完整列表
     * 遍历 每个小节 + 每个通道
     * 如果该通道有数据 → 使用原始数据
     * 如果该通道无数据 → 02020202020202020202020202020202
     * 将原始 drumList 里的小节通道数据，按完整通道列表和小节范围归一化，缺失通道补默认值，返回完整 DTX 格式的字符串列表
     * @author Lachesism
     * @date 2025-12-29
     */
    public static List<String> populateData(List<String> drumList) {
        HashMap<String, List<String>> drumMap = new HashMap<>();
        List<String> newDrumList = new ArrayList<>();
        for (int i = 0; i < drumList.size(); i++) {
            List<String> drumInfo = DtxUtils.splitDrumInfo(drumList.get(i));
            String key = drumInfo.get(0);
            drumInfo.remove(0);
            drumMap.put(key, drumInfo);
        }
        String[] channels = {"18", "2A", "1B", "25", "22", "1D", "24", "1F", "21", "26", "5D"};
        String defaultValue = "02020202020202020202020202020202";
        String channelBegin = splitDrumInfo(drumList.get(0)).get(0);
        String channelEnd = splitDrumInfo(drumList.get(drumList.size() - 1)).get(0);
        channelBegin = channelBegin.length() >= 3 ? channelBegin.substring(0, 3) : channelBegin;
        channelEnd = channelEnd.length() >= 3 ? channelEnd.substring(0, 3) : channelEnd;
        int beginIndex = getSectionIndex(channelBegin);
        int endIndex = getSectionIndex(channelEnd);
        if (beginIndex != 0) {
            beginIndex = 0;
        }
        for (int i = beginIndex; i < endIndex + 1; i++) {
            String section = sectionIndexToCode(i);
            for (int j = 0; j < 11; j++) {
                String key = section + channels[j];
                List<String> strings = drumMap.get(key);
                String join;
                if (strings == null || strings.isEmpty()) {
                    join = defaultValue;
                } else {
                    join = String.join("", strings);
                }
                join = key + ": " + join;
                newDrumList.add(join);
            }
        }
        return newDrumList;
    }

    public static List<String> genHammerDtxInfo(List<List<String>> sourceDrumInfoList) {
        if (sourceDrumInfoList == null || sourceDrumInfoList.isEmpty()) {
            return new ArrayList<>();
        }
        List<String> resultList = new ArrayList<>();
        int size = sourceDrumInfoList.size();
        for (int i = 0; i < size; i += 3) {
            int end = Math.min(i + 3, size);
            List<List<String>> batch = sourceDrumInfoList.subList(i, end);
            processBatchBalance(batch);
            for (List<String> subList : batch) {
                resultList.addAll(subList);
            }
        }
        return resultList;
    }


    private static void processBatchBalance(List<List<String>> batch) {
        int sectionCount = batch.size();
        int channelCount = 11; // 1个18 + 10个通道

        // 1. 将数据解析为易操作的数组: [小节][通道][节拍]
        // beatsData[s][c][p] -> s:0-2小节, c:0-10通道, p:0-15节拍
        String[][][] beatsData = new String[sectionCount][channelCount][16];
        String[][] prefixData = new String[sectionCount][channelCount]; // 存储 "07718: " 这种前缀

        for (int s = 0; s < sectionCount; s++) {
            List<String> section = batch.get(s);
            for (int c = 0; c < channelCount; c++) {
                String line = section.get(c);
                String[] parts = line.split(": ");
                prefixData[s][c] = parts[0] + ": ";
                String hexData = parts[1].trim();
                for (int p = 0; p < 16; p++) {
                    beatsData[s][c][p] = hexData.substring(p * 2, p * 2 + 2);
                }
            }
        }

        for (int c = 1; c < channelCount; c++) {
            if (isChannelEmptyInBatch(beatsData, c, sectionCount)) {
                tryBorrowHit(beatsData, c, sectionCount);
            }
        }

        for (int s = 0; s < sectionCount; s++) {
            List<String> section = batch.get(s);
            for (int c = 0; c < channelCount; c++) {
                StringBuilder sb = new StringBuilder(prefixData[s][c]);
                for (int p = 0; p < 16; p++) {
                    sb.append(beatsData[s][c][p]);
                }
                section.set(c, sb.toString());
            }
        }
    }

    // 判断某个通道在这一组小节中是否完全没有 03
    private static boolean isChannelEmptyInBatch(String[][][] data, int channelIdx, int sectionCount) {
        for (int s = 0; s < sectionCount; s++) {
            for (int p = 0; p < 16; p++) {
                if ("03".equals(data[s][channelIdx][p])) return false;
            }
        }
        return true;
    }

    // 移位逻辑：从有连续 03 的通道借调
    private static void tryBorrowHit(String[][][] data, int targetChannelIdx, int sectionCount) {
        for (int s = 0; s < sectionCount; s++) {
            for (int sourceC = 1; sourceC < 11; sourceC++) {
                if (sourceC == targetChannelIdx) continue;

                for (int p = 0; p < 15; p++) {
                    // 查找连续的 03 (例如 p 和 p+1 都是 03)
                    if ("03".equals(data[s][sourceC][p]) && "03".equals(data[s][sourceC][p + 1])) {
                        // 发现连续敲击，执行移位
                        data[s][sourceC][p + 1] = "02";  // 源通道对应位置变 02
                        data[s][targetChannelIdx][p + 1] = "03"; // 死通道对应位置变 03
                        return; // 一个死通道借到一个 03 即可平衡，跳出
                    }
                }
            }
        }

        // 兜底逻辑：如果没有连续的 03，就借用任何一个 03
        for (int s = 0; s < sectionCount; s++) {
            for (int sourceC = 1; sourceC < 11; sourceC++) {
                if (sourceC == targetChannelIdx) continue;
                for (int p = 0; p < 16; p++) {
                    if ("03".equals(data[s][sourceC][p])) {
                        data[s][sourceC][p] = "02";
                        data[s][targetChannelIdx][p] = "03";
                        return;
                    }
                }
            }
        }
    }
        /**
         * @Description： 将鼓点长度固定，变为16位
         * 例如03，则转换为03020202020202020202020202020202
         * 例如0303，转换为03020202020202020302020202020202
         * 例如03020203，则转换为03020202020202020202020203020202
         * @author Lachesism
         * @date 2025-12-29
         */

    public static List<String> reGroupDrumInfo(List<List<String>> grouped) {
        List<String> newDrumList = new ArrayList<>();
        if (grouped == null || grouped.isEmpty()) {
            return newDrumList;
        }
        for (List<String> channelGroupDrum : grouped) {
            if (channelGroupDrum.isEmpty()) continue;
            List<List<String>> chipsList = new ArrayList<>();
            for (String drum : channelGroupDrum) {
                String[] parts = drum.split(":");
                String drumData = parts.length > 1 ? parts[1].trim() : "";
                List<String> chips = new ArrayList<>();
                for (int i = 0; i < drumData.length(); i += 2) {
                    if (i + 2 <= drumData.length()) {
                        chips.add(drumData.substring(i, i + 2));
                    }
                }
                chipsList.add(chips);
            }
            int chipCount = chipsList.stream().mapToInt(List::size).min().orElse(0);
            for (int i = 0; i < chipCount; i++) {
                StringBuilder sb = new StringBuilder();
                for (List<String> chips : chipsList) {
                    sb.append(chips.get(i));
                }
                newDrumList.add(sb.toString());
            }
        }
        return newDrumList;
    }
    public static List<List<String>> reGroupHammerDrumInfo(List<List<String>> grouped) {
        List<List<String>> newGroupedDrumList = new ArrayList<>();
        if (grouped == null || grouped.isEmpty()) {
            return newGroupedDrumList;
        }
        for (List<String> channelGroupDrum : grouped) {
            if (channelGroupDrum.isEmpty()) continue;
            List<List<String>> chipsList = new ArrayList<>();
            for (String drum : channelGroupDrum) {
                String[] parts = drum.split(":");
                String drumData = parts.length > 1 ? parts[1].trim() : "";
                List<String> chips = new ArrayList<>();
                for (int i = 0; i < drumData.length(); i += 2) {
                    if (i + 2 <= drumData.length()) {
                        chips.add(drumData.substring(i, i + 2));
                    }
                }
                chipsList.add(chips);
            }
            int chipCount = chipsList.stream().mapToInt(List::size).min().orElse(0);
            List<String> regroupedChannel = new ArrayList<>();
            for (int i = 0; i < chipCount; i++) {
                StringBuilder sb = new StringBuilder();
                for (List<String> chips : chipsList) {
                    sb.append(chips.get(i));
                }
                regroupedChannel.add(sb.toString());
            }
            newGroupedDrumList.add(regroupedChannel);
        }
        return newGroupedDrumList;
    }

    /**
     * @Description：BPM计算器， 算每个格子的时间长度，一节4拍，一共16个格子，计算每个格子的时间，也就是步长stepTime
     * @author Lachesism
     * @date 2025-12-29
     */

    public static BigDecimal calculateDrumTime(Double bpm) {
        BigDecimal bpmBD = BigDecimal.valueOf(bpm);
        BigDecimal sixty = BigDecimal.valueOf(60);
        BigDecimal four = BigDecimal.valueOf(4);
        BigDecimal sixteen = BigDecimal.valueOf(16);
        // 60 / bpm * 4 / 16
        BigDecimal perGridTime = sixty.divide(bpmBD, 6, RoundingMode.HALF_UP) // 每拍秒数
                .multiply(four)
                .divide(sixteen, 6, RoundingMode.HALF_UP);

        return perGridTime;
    }


    /**
     * @Description：鼓点翻译打印机 例如传入 #0212A: 03020202020202020302020202020202
     * 输出内容为 ：第1节，第LC通道。对应按键分别是1 0 0 0 0 0 0 0 1 0 0 0 0 0 0 0
     * @author Lachesism
     * @date 2025-12-29
     */

    public static String transferMachine(String line) {
        String[] split = line.split(": ", 2);
        String rowId = split[0].replace("#", "");
        String part = rowId.substring(0, 3);
        int sectionIndex = getSectionIndex(part);
        String channel = rowId.substring(3, 5);
        String drumNumberByChannel = getInstrumentByChannel(channel);
        String data = split[1];
        List<String> groups = new ArrayList<>();
        for (int i = 0; i < data.length(); i += 2) {
            if (i + 2 <= data.length()) {
                groups.add(String.valueOf(getNumberMap(data.substring(i, i + 2))));
            }
        }

        return "第" + sectionIndex + "节，第" + drumNumberByChannel + "通道。对应按键分别是" + String.join(" ", groups);
    }

    /**
     * @Description：保存bin
     * @author Lachesism
     * @date 2025-12-29
     */

    public static void saveFile(String path, byte[] msg) {
        File file = new File(path);
        File parent = file.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            return;
        }
        try (OutputStream fos = Files.newOutputStream(file.toPath())) {
            fos.write(msg);
            fos.flush();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static String sanitizeFileName(String name) {
        if (name == null) return "空";
        name = name.replaceAll("[\\\\/:*?\"<>|]", "_");
        name = name.replaceAll("[\\p{Cntrl}]", "");
        name = name.trim();
        if (name.length() > 100) {
            name = name.substring(0, 100);
        }
        if (name.isEmpty()) {
            name = "untitled";
        }
        return name;
    }

    /**
     * @Description：将bin的内容和反编译结果写为excel
     * @author Lachesism
     * @date 2025-12-29
     */

    public static void writeExcelFinal(
            List<int[]> rawRecords,
            List<BigDecimal[]> readableRecords,
            String excelPath
    ) throws Exception {

        Workbook workbook = new XSSFWorkbook();
        Sheet sheet = workbook.createSheet("bin反编译");

        int rowIndex = 0;

        // =========================
        // 第 1 行：协议头
        // =========================
        Row r1 = sheet.createRow(rowIndex++);
        r1.createCell(0).setCellValue("字节头");
        r1.createCell(1).setCellValue("有效个数（高位）");
        r1.createCell(2).setCellValue("有效个数（低位）");
        r1.createCell(3).setCellValue("data");
        r1.createCell(4).setCellValue("校验（异或过程）");
        r1.createCell(5).setCellValue("帧尾");
        sheet.setColumnWidth(0, 12 * 256);
        sheet.setColumnWidth(1, 12 * 256);
        sheet.setColumnWidth(2, 12 * 256);
        sheet.setColumnWidth(3, 36 * 256);
        sheet.setColumnWidth(4, 18 * 256);
        sheet.setColumnWidth(5, 12 * 256);
        // =========================
        // 有效个数
        // =========================
        int recordCount = rawRecords.size();
        int countHigh = (recordCount >> 8) & 0xFF;
        int countLow = recordCount & 0xFF;

        // =========================
        // 构建 data（拼接 + 限制长度）
        // =========================
        StringBuilder dataBuilder = new StringBuilder();
        for (int[] raw : rawRecords) {
            for (int b : raw) {
                dataBuilder.append(String.format("0x%02X", b));
            }
        }

        String dataStr = dataBuilder.toString().trim();
        int maxLen = 32760; // 留一点空间给省略符号
        if (dataStr.length() > maxLen) {
            dataStr = dataStr.substring(0, maxLen) + "..."; // 超过则省略
        }

        // =========================
        // 构建 校验异或过程（不计算结果）
        // =========================
        StringBuilder xorBuilder = new StringBuilder();
        xorBuilder.append("0xAA^ ");
        xorBuilder.append(String.format("0x%02X", countHigh)).append("^ ");
        xorBuilder.append(String.format("0x%02X", countLow));

        for (int[] raw : rawRecords) {
            for (int b : raw) {
                xorBuilder.append(" ^ ").append(String.format("0x%02X", b));
            }
        }

        // 超过单元格限制也省略
        String xorStr = xorBuilder.toString();
        if (xorStr.length() > maxLen) {
            xorStr = xorStr.substring(0, maxLen) + "...";
        }

        // =========================
        // 第 2 行：协议值
        // =========================
        Row r2 = sheet.createRow(rowIndex++);
        r2.createCell(0).setCellValue("0xAA");
        r2.createCell(1).setCellValue(String.format("0x%02X", countHigh));
        r2.createCell(2).setCellValue(String.format("0x%02X", countLow));
        r2.createCell(3).setCellValue(dataStr);
        r2.createCell(4).setCellValue(xorStr);
        r2.createCell(5).setCellValue("0xEE");

        // =========================
        // 第 3 行：数据表头
        // =========================
        Row r3 = sheet.createRow(rowIndex++);
        r3.createCell(0).setCellValue("开始时间（高位）");
        r3.createCell(1).setCellValue("开始时间（低位）");
        r3.createCell(2).setCellValue("结束时间（高位）");
        r3.createCell(3).setCellValue("结束时间（低位）");
        r3.createCell(4).setCellValue("控制点（高位）");
        r3.createCell(5).setCellValue("控制点（低位）");
        r3.createCell(6).setCellValue("开始时间 ×100");
        r3.createCell(7).setCellValue("结束时间 ×100");
        r3.createCell(8).setCellValue("控制点");
        r3.createCell(9).setCellValue("控制点二进制展示");

        // =========================
        // 第 4 行起：数据
        // =========================
        for (int i = 0; i < rawRecords.size(); i++) {
            int[] raw = rawRecords.get(i);
            BigDecimal[] read = readableRecords.get(i);

            Row row = sheet.createRow(rowIndex++);

            for (int j = 0; j < 6; j++) {
                row.createCell(j).setCellValue(String.format("0x%02X", raw[j]));
            }

            row.createCell(6).setCellValue(
                    read[0].multiply(BigDecimal.valueOf(100)).doubleValue()
            );
            row.createCell(7).setCellValue(
                    read[1].multiply(BigDecimal.valueOf(100)).doubleValue()
            );
            row.createCell(8).setCellValue(read[2].intValue());
            int value = read[2].intValue();
            String binary = String.format("%12s", Integer.toBinaryString(value))
                    .replace(' ', '0');
            binary = binary.replaceAll("(.{4})", "$1 ").trim();
            row.createCell(9).setCellValue(binary);

        }

        for (int i = 0; i <= 8; i++) {
            sheet.autoSizeColumn(i);
        }

        try (FileOutputStream fos = new FileOutputStream(excelPath)) {
            workbook.write(fos);
        }
        workbook.close();
    }


    /**
     * @Description：将鼓点变成中等难度 例如：03020202020202020302020202020202
     * 拆分为 03020202	 02020202	03020202	02020202
     * 然后将16分拆分为4分音符，只保留每个拍子第一个音，结果为
     * 03	02	03	02
     * 然后将其后面填充3个02，020202，变成 03020202 02020202 03020202 02020202
     * 最终变成03020202020202020302020202020202
     * <p>
     * 更新：现在调整为如果 02030303020202030203020303030302
     * 02030303 02020203 02030203 03030302
     * 则为02020302 02020202 02020202 03020202
     * 改成 02020302020202020202020203020202
     * @author Lachesism
     * @date 2025-12-29
     */

    public static String toMedium(String value) {
        String[] split = value.split(": ");
        String drumInfo = split[1];
        StringBuilder newDrumInfo = new StringBuilder();
        for (int i = 0; i < 4; i++) {
            System.out.println(drumInfo);
            String part = drumInfo.substring(i * 8, (i + 1) * 8);
            String first = part.substring(0, 2);
            String two = part.substring(2, 4);
            String third = part.substring(4, 6);
            String four = part.substring(6, 8);
//            System.out.println(part + " " + first + " " + two + " " + third + " " + four + " ");
            String newPart;
            if ("02".equals(first) && !"02".equals(third)) {
                newPart = "02020302";
            } else {
                newPart = first + "020202";
            }
            newDrumInfo.append(newPart);
        }
        return split[0] + ": " + newDrumInfo;
    }

    /**
     * @Description：将鼓点变成简单难度 例如：03020202020202020302020202020202
     * 拆分为 03020202	 02020202	03020202	02020202
     * 然后将16分拆分为2分音符，只保留每2个拍子第一个音，结果为
     * 03	02	03	02
     * 然后将其后面填充7个02, 02020202020202，变成 0302020202020202 0302020202020202
     * 最终变成03020202020202020302020202020202
     * @author Lachesism
     * @date 2025-12-29
     */
    public static String toEasy(String value) {
        String[] split = value.split(": ");
        String drumInfo = split[1];
        StringBuilder newDrumInfo = new StringBuilder();
        for (int i = 0; i < 2; i++) {
            String part = drumInfo.substring(i * 16, (i + 1) * 16);
            String first = part.substring(0, 2);
            String third = part.substring(8, 10);
            String newPart;
            if ("02".equals(first) && !"02".equals(third)) {
                newPart = "0202020203020202";
            } else {
                newPart = first + "02020202020202";
            }
            newDrumInfo.append(newPart);
        }
        return split[0] + ": " + newDrumInfo;
    }

    /**
     * 提取 DTX 文件头（直到第一条谱面数据行之前）
     */
    public static String extractHeader(String dtxText) {
        if (dtxText == null || dtxText.isEmpty()) {
            return "";
        }
        Pattern pattern = Pattern.compile(
                "^(.*?)(?=#[0-9A-Fa-f]{3}[0-9A-Fa-f]{2}:)",
                Pattern.DOTALL
        );
        Matcher matcher = pattern.matcher(dtxText);
        if (matcher.find()) {
            return matcher.group(1).trim();
        }
        return dtxText.trim();
    }

    /**
     * @Description：传入内容和路径（带文件名和后缀)写入文件
     * @author Lachesism
     * @date 2025-12-29
     */

    public static void writeContentToFile(String content, String filePath) throws IOException {
        File file = new File(filePath);
        File parent = file.getParentFile();
        if (!parent.exists()) {
            parent.mkdirs();
        }
        try (FileOutputStream fos = new FileOutputStream(file)) {
            fos.write(content.getBytes(StandardCharsets.UTF_8));
        }
    }


    public static int randomByWeight() {
        int r = ThreadLocalRandom.current().nextInt(120); // 0~119
        if (r < 50) {          // 0~49 → 50/120 ≈ 41.7%
            return 1;
        } else if (r < 90) {   // 50~89 → 40/120 ≈ 33.3%
            return 2;
        } else {               // 90~119 → 30/120 = 25%
            return 3;
        }
    }


    public static int getRandomHexSum(int count) {
        int maxValue = 10;
        if (count > maxValue + 1) {
            throw new IllegalArgumentException("count 不能大于 11");
        }
        List<Integer> numbers = new ArrayList<>();
        for (int i = 0; i <= maxValue; i++) {
            numbers.add(i);
        }
        Collections.shuffle(numbers);
        int sum = 0;
        for (int i = 0; i < count; i++) {
            sum += 1 << numbers.get(i);
        }
        return sum;
    }

}