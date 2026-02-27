package com.tool.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tool.mapper.UploadMapper;
import com.tool.util.AsyncUtil;
import com.tool.util.DtxUtils;
import com.tool.util.Result;
import com.tool.util.YiYuanSimUtiles;
import com.tool.vo.BinVO;
import com.tool.vo.DrumInfo;
import com.tool.vo.DtxVO;
import com.tool.vo.TSimcardInfo;
import lombok.SneakyThrows;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.w3c.dom.Document;

import javax.annotation.Resource;
import javax.xml.parsers.DocumentBuilder;
import java.io.*;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ThreadLocalRandom;

import static com.tool.util.DtxUtils.*;
import static com.tool.util.MusicXmlUtil.*;

@Service
public class UploadService {
    @Value("${file.excelPath}")
    private String excelPath;

    @Resource
    private UploadMapper uploadMapper;

    @Autowired
    private ObjectMapper objectMapper;

    private static final int BATCH_SIZE = 1000;

    // 记录任务进度（0-100）
    private final Map<String, Integer> progressMap = new ConcurrentHashMap<>();
    // 任务状态
    private final Map<String, String> statusMap = new ConcurrentHashMap<>();

    /**
     * 异步上传文件，返回 taskId
     */
    public String uploadFileAsync(MultipartFile file) {
        String taskId = UUID.randomUUID().toString();
        progressMap.put(taskId, 0);
        statusMap.put(taskId, "RUNNING");

        new Thread(() -> {
            try {
                List<String> allLines = parseTxtFile(file, taskId);
                Map<String, Object> newInfo = fetchLatestInfo(allLines);
                saveResultToExcel(newInfo);
                progressMap.put(taskId, 100);
                statusMap.put(taskId, "COMPLETED");
            } catch (Exception e) {
                e.printStackTrace();
                progressMap.put(taskId, 100);
                statusMap.put(taskId, "FAILED");
            }
        }).start();

        return taskId;
    }

    /**
     * 获取任务进度
     */
    public int getProgress(String taskId) {
        return progressMap.getOrDefault(taskId, 100);
    }

    /**
     * 获取任务状态
     */
    public String getStatus(String taskId) {
        return statusMap.getOrDefault(taskId, "UNKNOWN");
    }

    /**
     * 核心解析方法，边读边处理
     */
    private List<String> parseTxtFile(MultipartFile file, String taskId) throws Exception {
        List<String> iccidList = new ArrayList<>(BATCH_SIZE);
        List<String> msisdnList = new ArrayList<>(BATCH_SIZE);
        List<String> allLines = new ArrayList<>();

        int totalLines = countLines(file);
//        System.out.println(totalLines);
        int processedLines = 0;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) continue;
                allLines.add(line);
                String type = identify(line);
                if ("MSISDN".equals(type)) {
                    msisdnList.add(line);
                    if (msisdnList.size() >= BATCH_SIZE) {
                        processMsisdnBatch(msisdnList);
                        msisdnList.clear();
                    }
                } else if ("ICCID".equals(type)) {
                    iccidList.add(line);
                    if (iccidList.size() >= BATCH_SIZE) {
                        processIccidBatch(iccidList);
                        iccidList.clear();
                    }
                }
                processedLines++;
                int percent = (int) ((processedLines * 100L) / totalLines);
                progressMap.put(taskId, percent);
            }
        }
        if (!iccidList.isEmpty()) processIccidBatch(iccidList);
        if (!msisdnList.isEmpty()) processMsisdnBatch(msisdnList);
        progressMap.put(taskId, 100);
        return allLines;
    }

    private Map<String, Object> fetchLatestInfo(List<String> allLines) {
        List<TSimcardInfo> batchLineList = new ArrayList<>();
        HashMap<String, Object> map = new HashMap<>();
        for (String line : allLines) {
            TSimcardInfo tSimcardInfo = new TSimcardInfo();
            String type = identify(line);
            if ("ICCID".equals(type)) {
                tSimcardInfo.setIccid(line);
            } else if ("MSISDN".equals(type)) {
                tSimcardInfo.setMsisdn(line);
            }
            batchLineList.add(tSimcardInfo);
        }
        List<TSimcardInfo> tSimcardInfos = uploadMapper.selectNewInfo(batchLineList);
        map.put("simcardInfos", tSimcardInfos);
        map.put("batchLineList", allLines);
        return map;
    }


    private void saveResultToExcel(Map<String, Object> map) {
        if (map == null || map.isEmpty()) {
            System.out.println("无数据可写入 Excel。");
            return;
        }
        List<String> allLines = (List<String>) map.get("batchLineList");
        List<TSimcardInfo> batchLineList = (List<TSimcardInfo>) map.get("simcardInfos");
        Map<String, TSimcardInfo> msisdnMap = new HashMap<>();
        Map<String, TSimcardInfo> iccidMap = new HashMap<>();
        for (TSimcardInfo info : batchLineList) {
            if (info.getMsisdn() != null) msisdnMap.put(info.getMsisdn(), info);
            if (info.getIccid() != null) iccidMap.put(info.getIccid(), info);
        }
        String filePath = excelPath + new SimpleDateFormat("yyyyMMddHHmmss").format(new Date()) + ".xlsx";
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("SIM卡信息");
            Row header = sheet.createRow(0);
            String[] titles = {"MSISDN", "ICCID", "过期时间", "更新时间"};
            CellStyle headerStyle = workbook.createCellStyle();
            Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerStyle.setFont(headerFont);
            for (int i = 0; i < titles.length; i++) {
                Cell cell = header.createCell(i);
                cell.setCellValue(titles[i]);
                cell.setCellStyle(headerStyle);
            }
            int rowNum = 1;
            for (String line : allLines) {
                Row row = sheet.createRow(rowNum++);
                String type = identify(line);
                for (int i = 0; i < 4; i++) row.createCell(i).setCellValue("");
                if ("MSISDN".equals(type)) {
                    row.getCell(0).setCellValue(safeStr(line));
                    TSimcardInfo info = msisdnMap.get(line);
                    if (info != null) {
                        row.getCell(2).setCellValue(safeStr(new SimpleDateFormat("yyyy-MM-dd").format(info.getExpiryDate())));
                        row.getCell(3).setCellValue(safeStr(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(info.getDataRefreshTime())));
                    }
                } else if ("ICCID".equals(type)) {
                    row.getCell(1).setCellValue(safeStr(line));
                    TSimcardInfo info = iccidMap.get(line);
                    if (info != null) {
                        row.getCell(2).setCellValue(safeStr(new SimpleDateFormat("yyyy-MM-dd").format(info.getExpiryDate())));
                        row.getCell(3).setCellValue(safeStr(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(info.getDataRefreshTime())));
                    }
                }
            }
            for (int i = 0; i < titles.length; i++) sheet.autoSizeColumn(i);
            try (FileOutputStream out = new FileOutputStream(filePath)) {
                workbook.write(out);
            }
            System.out.println("Excel 文件导出成功，路径：" + filePath);
        } catch (IOException e) {
            e.printStackTrace();
            System.err.println("导出 Excel 失败：" + e.getMessage());
        }
    }

    /**
     * 避免 null 值写入 Excel 出错
     */
    private String safeStr(Object obj) {
        return obj == null ? "" : String.valueOf(obj);
    }

    /**
     * 批量处理 ICCID
     */
    private void processIccidBatch(List<String> iccidBatch) {
        List<TSimcardInfo> infos = YiYuanSimUtiles.GetSIMCardListByIccids(String.join(",", iccidBatch));
        if (infos != null && !infos.isEmpty()) uploadMapper.batchUpdate(infos);
    }

    /**
     * 批量处理 MSISDN
     */
    private void processMsisdnBatch(List<String> msisdnBatch) {
        List<TSimcardInfo> infos = YiYuanSimUtiles.GetSIMCardListByMsisdns(String.join(",", msisdnBatch));
        if (infos != null && !infos.isEmpty()) uploadMapper.batchUpdate(infos);
    }

    /**
     * 判断类型
     */
    private String identify(String value) {
        if (value == null || value.isEmpty()) return "";
        int len = value.length();
        if (len == 13) return "MSISDN";
        if (len == 20) return "ICCID";
        return "";
    }

    /**
     * 统计文件总行数
     */
    private int countLines(MultipartFile file) throws Exception {
        int lines = 0;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {
            while (reader.readLine() != null) lines++;
        }
        return lines;
    }


    /**
     * =========================================================================================================
     */
    /**
     * @Description：初始DTX生成不同难度DTX的方法
     * @author Lachesism
     * @date 2025-12-29
     */
    public Boolean genDiffAndEasy(MultipartFile file, String outPutPath) {
        if (file == null || file.isEmpty()) {
            System.err.println("上传的文件为空");
            return false;
        }
        String originalName = file.getOriginalFilename();
        String fileName = originalName.substring(0, originalName.lastIndexOf('.'));
        try {
            StringBuilder sb = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line).append("\n");
                }
            }
            String fileContent = sb.toString();
            String header = DtxUtils.extractHeader(fileContent);
            DtxUtils.writeContentToFile(fileContent, outPutPath + "/" + fileName + "_困难.dtx");
            List<String> drumInfo = DtxUtils.extractDrumLines(fileContent);
            for (int i = 0; i < drumInfo.size(); i++) {
                drumInfo.set(i, DtxUtils.paddedDrumInfo(drumInfo.get(i)));
            }
            for (int i = 0; i < drumInfo.size(); i++) {
                drumInfo.set(i, DtxUtils.toMedium(drumInfo.get(i)));
            }
            fileContent = header + "\n" + String.join("\n", drumInfo);
            DtxUtils.writeContentToFile(fileContent, outPutPath + "/" + fileName + "_中等.dtx");
            for (int i = 0; i < drumInfo.size(); i++) {
                drumInfo.set(i, DtxUtils.toEasy(drumInfo.get(i)));
            }
            fileContent = header + "\n" + String.join("\n", drumInfo);
            DtxUtils.writeContentToFile(fileContent, outPutPath + "/" + fileName + "_简单.dtx");
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }


    /**
     * =========================================================================================================
     */
    /**
     * @Description：dtx转bin的工具方法
     * @author Lachesism
     * @date 2025-12-12
     */

    public Boolean invertDtxFile(MultipartFile file, String outPutPath) {
        if (file == null || file.isEmpty()) {
            System.err.println("上传的文件为空");
            return false;
        }
        String originalName = file.getOriginalFilename();
        String fileName = originalName.substring(0, originalName.lastIndexOf('.'));
        try {
            StringBuilder sb = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line).append("\n");
                }
            }
            String fileContent = sb.toString();
            String title = DtxUtils.extractTitle(fileContent);
            List<Double> bpmInfo = DtxUtils.extractBpmMap(fileContent);
            List<String> drumInfo = DtxUtils.extractDrumLines(fileContent);
            for (int i = 0; i < drumInfo.size(); i++) {
                drumInfo.set(i, DtxUtils.paddedDrumInfo(drumInfo.get(i)));
            }
            List<String> finalDrumList = DtxUtils.populateData(drumInfo);
            int groupSize = 11;
            List<List<String>> grouped = new ArrayList<>();
            for (int i = 0; i < finalDrumList.size(); i += groupSize) {
                int end = Math.min(i + groupSize, finalDrumList.size());
                grouped.add(finalDrumList.subList(i, end));
            }
            List<String> newDrumList = DtxUtils.reGroupDrumInfo(grouped);
            List<RawDrumEvent> rawDrumEventList = new ArrayList<>();
            Double currentBpmValue = bpmInfo.get(0); // 初始bpm的值
            BigDecimal currentBeginTime = new BigDecimal(0).setScale(4, RoundingMode.HALF_UP);
            for (String everyDrumInfo : newDrumList) {
                String bpmSubValue = everyDrumInfo.substring(0, 2);
                if (!bpmSubValue.equals("02") && !bpmSubValue.equals("01") && !bpmSubValue.equals("00")) {
                    int bpmIndex = DtxUtils.getNumberMap(bpmSubValue);
                    currentBpmValue = bpmInfo.get(bpmIndex);
                }
                // 计算当前格子的时长
                BigDecimal stepTime = DtxUtils.calculateDrumTime(currentBpmValue);
                // 按键信息
                everyDrumInfo = everyDrumInfo.substring(2);
                String[] channels = {"2A", "1B", "25", "22", "1D", "24", "1F", "21", "26", "5D"};
                // 判断此格是否“全是 02”
                if (!everyDrumInfo.equals("02020202020202020202")) {
                    List<Integer> non02List = new ArrayList<>();
                    List<String> parts = new ArrayList<>();
                    for (int i = 0; i < everyDrumInfo.length(); i += 2) {
                        parts.add(everyDrumInfo.substring(i, i + 2));
                    }
                    for (int i = 0; i < parts.size(); i++) {
                        if (!"02".equals(parts.get(i))) {
                            non02List.add(i);
                        }
                    }
                    //计算当前格子的鼓键 KEY
                    Integer drumNumSum = 0;
                    for (Integer i : non02List) {
                        Integer drumNumberByChannel = DtxUtils.getDrumNumberByChannel(channels[i]);
                        drumNumSum += drumNumberByChannel;
                    }
                    // 将事件加入原始列表，此处一个格子只产生一个事件（多键位叠加）
                    rawDrumEventList.add(new RawDrumEvent(drumNumSum, currentBeginTime));
                }
                // 一行处理完后，起始时间推进
                currentBeginTime = currentBeginTime.add(stepTime);
            }
            // 根据下一个同键事件计算 EndTime 和 IntervalTime
            List<DrumInfo> drumInfoArrayList = new ArrayList<>();
            BigDecimal oneSecond = new BigDecimal(1.0).setScale(4, RoundingMode.HALF_UP);
            BigDecimal oneCentisecond = new BigDecimal(0.01).setScale(4, RoundingMode.HALF_UP);
            for (int i = 0; i < rawDrumEventList.size(); i++) {
                RawDrumEvent currentEvent = rawDrumEventList.get(i);
                DrumInfo drum = new DrumInfo();
                drum.setKey(currentEvent.key);
                drum.setBeginTIme(currentEvent.beginTime);
                BigDecimal nextBeginTime = null;
                // 查找下一个同键事件
                for (int j = i + 1; j < rawDrumEventList.size(); j++) {
                    RawDrumEvent nextEvent = rawDrumEventList.get(j);
                    if (nextEvent.key.equals(currentEvent.key)) {
                        nextBeginTime = nextEvent.beginTime;
                        break;
                    }
                }
                BigDecimal endTime;
                if (nextBeginTime != null) {
                    // 找到了下一个同键事件
                    BigDecimal timeDifference = nextBeginTime.subtract(currentEvent.beginTime);
                    // 如果间隔 > 1.0s, 则 endTime = beginTime + 1.0s
                    if (timeDifference.compareTo(oneSecond) > 0) {
                        endTime = currentEvent.beginTime.add(oneSecond);
                    } else {
                        // 如果间隔 <= 1.0s, 则 endTime = nextBeginTime - 0.01s
                        endTime = nextBeginTime.subtract(oneCentisecond);
                    }
                } else {
                    // 当前是该键的最后一个事件，默认 endTime = beginTime + 1.0s
                    endTime = currentEvent.beginTime.add(oneSecond);
                }
                drum.setEndTIme(endTime);
                // 重新计算 intervalTime
                drum.setIntervalTime(drum.getEndTIme().subtract(drum.getBeginTIme()));
                drumInfoArrayList.add(drum);
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
                BigDecimal begin = info.getBeginTIme().multiply(new BigDecimal(100)).setScale(0, RoundingMode.HALF_UP);
                BigDecimal end = info.getEndTIme().multiply(new BigDecimal(100)).setScale(0, RoundingMode.HALF_UP);
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
            DtxUtils.saveFile(outPutPath + "/" + fileName + ".bin", bytes);
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    public boolean processMp3(MultipartFile file, String outputDir, Integer startSecond, Integer duration) {
        File tempFile = null;
        try {
            File dir = new File(outputDir);
            if (!dir.exists()) dir.mkdirs();

            String originalFilename = file.getOriginalFilename();
            if (originalFilename == null || !originalFilename.toLowerCase().endsWith(".mp3")) {
                return false;
            }
            String baseName = originalFilename.substring(0, originalFilename.lastIndexOf('.'));
            // 目标 2.5MB，设为 2.45MB
            long targetSizeByte = (long) (2.45 * 1024 * 1024);
            tempFile = File.createTempFile("upload_", ".mp3");
            file.transferTo(tempFile);
            File processedFile = new File(dir, baseName + "_压缩.mp3");
            // 核心逻辑：无论是否超过3MB，都进行去除信息
            // 如果超过3MB则计算比特率，否则用高质量（如192k）重新编码以剔除元数据
            long durationMs = getMp3Duration(tempFile);
            if (durationMs <= 0) return false;
            double durationSec = durationMs / 1000.0;

            int bitrate;
            if (tempFile.length() > (3L * 1024 * 1024)) {
                // 计算刚好能装进 3MB 的比特率
                bitrate = (int) ((targetSizeByte * 8) / durationSec / 1000);
                bitrate = Math.min(Math.max(bitrate, 32), 320); // 范围限制
            } else {
                bitrate = 192;
            }
            // FFmpeg 执行：重新编码并丢弃所有元数据
            execFFmpeg(new String[]{
                    "D:\\Code\\ffmpeg\\bin\\ffmpeg.exe",
                    "-y",
                    "-i", tempFile.getAbsolutePath(),
                    "-c:a", "libmp3lame",
                    "-b:a", bitrate + "k",
                    "-map_metadata", "-1",      // 剔除所有全局元数据
                    "-map", "0:a",              // 只保留音频流，自动丢弃封面图(视频流)
                    "-id3v2_version", "0",      // 不写入任何 ID3 标签
                    "-ac", "2",
                    "-ar", "44100",
                    processedFile.getAbsolutePath()
            });
            if (startSecond != null && startSecond >= 0 && duration != null && duration > 0) {
                File cut = new File(dir, baseName + "_预览.mp3");
                execFFmpeg(new String[]{
                        "D:\\Code\\ffmpeg\\bin\\ffmpeg.exe",
                        "-y",
                        "-ss", String.valueOf(startSecond),
                        "-t", String.valueOf(duration),
                        "-i", processedFile.getAbsolutePath(),
                        "-c", "copy",
                        cut.getAbsolutePath()
                });
            }

            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        } finally {
            if (tempFile != null && tempFile.exists()) tempFile.delete();
        }
    }

    private void execFFmpeg(String[] command) throws Exception {
        ProcessBuilder pb = new ProcessBuilder(command);
        pb.redirectErrorStream(true);
        Process process = pb.start();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            while (br.readLine() != null) {
            }
        }
        int code = process.waitFor();
        if (code != 0) {
            throw new RuntimeException("FFmpeg 执行失败，exitCode=" + code);
        }
    }

    private long getMp3Duration(File file) throws Exception {
        ProcessBuilder pb = new ProcessBuilder(
                "D:\\Code\\ffmpeg\\bin\\ffmpeg.exe",
                "-i", file.getAbsolutePath()
        );
        pb.redirectErrorStream(true);
        Process process = pb.start();
        long duration = 0;
        try (BufferedReader br = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = br.readLine()) != null) {
                if (line.contains("Duration:")) {
                    String dur = line.substring(line.indexOf("Duration:") + 10, line.indexOf(", start:")).trim();
                    String[] parts = dur.split(":");
                    double h = Double.parseDouble(parts[0]);
                    double m = Double.parseDouble(parts[1]);
                    double s = Double.parseDouble(parts[2]);
                    duration = (long) ((h * 3600 + m * 60 + s) * 1000);
                    break;
                }
            }
        }
        process.waitFor();
        return duration;
    }


    public Map<String, Object> fullProcess(MultipartFile mp3File, MultipartFile dtxFile,
                                           String outputDir, Integer startSecond, Integer duration) {
        Map<String, Object> resultMap = new HashMap<>();
        resultMap.put("mp3Success", false);
        resultMap.put("dtxSuccess", false);
        resultMap.put("overallSuccess", false);

        if (mp3File == null || mp3File.isEmpty()) {
            resultMap.put("message", "MP3 文件为空");
            return resultMap;
        }
        if (dtxFile == null || dtxFile.isEmpty()) {
            resultMap.put("message", "DTX 文件为空");
            return resultMap;
        }
        try {
            // 1. 获取基础文件名
            String originalFilename = mp3File.getOriginalFilename();
            if (originalFilename == null || !originalFilename.toLowerCase().endsWith(".mp3")) {
                resultMap.put("message", "MP3 文件格式不正确");
                return resultMap;
            }
            String baseName = originalFilename.substring(0, originalFilename.lastIndexOf('.'));

            // 2. 在 outputDir 下创建文件名文件夹
            File dir = new File(outputDir, baseName);
            if (!dir.exists()) dir.mkdirs();
            String finalOutputDir = dir.getAbsolutePath();
            // 3. 并行处理 MP3 和 DTX
            CompletableFuture<Boolean> mp3Future = CompletableFuture.supplyAsync(() -> {
                try {
                    return processMp3(mp3File, finalOutputDir, startSecond, duration);
                } catch (Exception e) {
                    e.printStackTrace();
                    return false;
                }
            });
            CompletableFuture<Boolean> dtxFuture = CompletableFuture.supplyAsync(() -> {
                try {
                    return convertDtx(dtxFile, finalOutputDir);
                } catch (Exception e) {
                    e.printStackTrace();
                    return false;
                }
            });
            // 等待两个任务完成
            boolean mp3Result = mp3Future.get();
            boolean dtxResult = dtxFuture.get();
            resultMap.put("mp3Success", mp3Result);
            resultMap.put("dtxSuccess", dtxResult);
            // 4. 最终整体成功条件：两个都成功
            boolean overall = mp3Result && dtxResult;
            resultMap.put("overallSuccess", overall);
            resultMap.put("message", overall ? "全部处理成功" : "部分处理失败");
        } catch (InterruptedException | ExecutionException e) {
            e.printStackTrace();
            resultMap.put("message", "处理异常：" + e.getMessage());
        }

        return resultMap;
    }


    public Result genHammerModelHand(String outPutPath,
                                     Integer beginTime,
                                     Integer endTime,
                                     Integer songDuration,
                                     Integer BPM) {
        BigDecimal begin = BigDecimal.valueOf(beginTime).multiply(BigDecimal.valueOf(100));
        BigDecimal endPadding = BigDecimal.valueOf(endTime).multiply(BigDecimal.valueOf(100));
        BigDecimal duration = BigDecimal.valueOf(songDuration).multiply(BigDecimal.valueOf(100));
        double i1 = 60.0 / BPM;
        BigDecimal interval = BigDecimal.valueOf(i1)
                .multiply(BigDecimal.valueOf(100))
                .setScale(3, RoundingMode.HALF_UP);
        duration = duration.subtract(begin).subtract(endPadding);
        int loopCount = duration.divide(interval, 0, RoundingMode.FLOOR).intValue();
        List<BinVO> drumInfoList = new ArrayList<>();
        List<Integer> result = new ArrayList<>();
        BigDecimal currentBegin = begin;
        for (int i = 0; i < loopCount; i++) {
            BinVO binVO = new BinVO();
            int weight = randomByWeight();
            int key = 0;
            if (weight == 0) {
                binVO.setIsValue(0);
            } else {
                binVO.setIsValue(1);
                key = getRandomHexSum(weight);
                binVO.setKeyHigh((byte) ((key >> 8) & 0xFF));
                binVO.setKeyLow((byte) (key & 0xFF));
                result.add(key);
            }
            int beginTimeValue = currentBegin.intValue();
            int endTimeValue = currentBegin.add(interval).intValue();
            binVO.setBeginHigh((byte) ((beginTimeValue >> 8) & 0xFF));
            binVO.setBeginLow((byte) (beginTimeValue & 0xFF));
            binVO.setEndHigh((byte) ((endTimeValue >> 8) & 0xFF));
            binVO.setEndLow((byte) (endTimeValue & 0xFF));
            drumInfoList.add(binVO);
            currentBegin = currentBegin.add(interval);
        }
        int count = (int) drumInfoList.stream().filter(bin -> bin.getIsValue() != null && bin.getIsValue() == 1).count();
        byte[] bytes = new byte[count * 6 + 5];
        bytes[0] = (byte) 0xAA;
        int realSize = count * 6;
        bytes[1] = (byte) ((realSize >> 8) & 0xFF);
        bytes[2] = (byte) (realSize & 0xFF);
        int index = 3;
        for (BinVO binVO : drumInfoList) {
            if (binVO.getIsValue() != null && binVO.getIsValue() == 1) {
                bytes[index++] = binVO.getBeginHigh();
                bytes[index++] = binVO.getBeginLow();
                bytes[index++] = binVO.getEndHigh();
                bytes[index++] = binVO.getEndLow();
                bytes[index++] = binVO.getKeyHigh();
                bytes[index++] = binVO.getKeyLow();
            }
        }
        // 校验码
        byte check = 0;
        for (int i = 0; i < index; i++) {
            check ^= bytes[i];
        }
        bytes[index++] = check;
        bytes[index] = (byte) 0xEE;
        outPutPath = outPutPath + "\\打地鼠.level1.bin";
        System.out.println(outPutPath);
        DtxUtils.saveFile(outPutPath, bytes);
        System.out.println("保存完成");
        return Result.success(result);
    }


    private static class RawDrumEvent {
        Integer key;
        BigDecimal beginTime;

        public RawDrumEvent(Integer key, BigDecimal beginTime) {
            this.key = key;
            this.beginTime = beginTime;
        }
    }

/**
 * 把上传的 DTX 文件解析成二进制数据并保存成 .bin 文件，整个流程可以这样理解：
 * <p>
 * 1. 首先检查上传的文件是否为空，如果为空就直接返回失败。
 * 2. 读取文件内容，把每一行都拼接成一个完整的字符串。
 * 3. 从文件内容里提取一些基本信息，比如歌曲标题、全局 BPM 值、以及每个鼓的原始谱面数据。
 * 4. 对鼓谱数据进行预处理，比如补齐长度，让每一小节的数据长度统一。
 * 5. 把整个鼓谱横向排列的数据分组，每组包含一定数量的鼓通道。
 * 6. 再把每组数据纵向重排，也就是把原本每行的鼓谱转换成按时间顺序排列的列表。
 * 7. 遍历重排后的列表，针对每个时间点：
 * - 根据前两位判断当前小节的 BPM，如果有变化就更新 BPM。
 * - 计算这个小节的持续时间（步长）。
 * - 提取每个鼓的按键信息，筛选出有击打的鼓。
 * - 生成 DrumInfo 对象，并设置它的开始时间、结束时间、按键信息和响应时间。
 * - 把 DrumInfo 对象放入一个列表里，方便后续处理。
 * 8. 遍历 DrumInfo 列表，把每个对象的开始时间、结束时间、按键信息转成字节，高位和低位分别存储。
 * 9. 把所有的字节按固定顺序依次放入最终的字节数组里。
 * 10. 最后计算一个校验位（把除了最后两个字节之外的所有字节做异或运算），放到倒数第二位。
 * 11. 字节数组的最后一位固定写入结束符 0xEE。
 * 12. 调用保存方法，把字节数组写成 .bin 文件，文件名用歌曲标题，路径用传进来的输出路径。
 * <p>
 * * 整体鼓点信息全部处理完了
 * * 最终格式是
 * * -
 * * 02218: 06020202020202020302020202020202
 * * 0222A: 03030303030303030303030303030303
 * * 0221B: 02020202020202020202020202020202
 * * 02225: 02020202020202020202020202020202
 * * 02222: 02020202020202020202020202020202
 * * 0221D: 02020202020202020202020202020202
 * * 02224: 02020202020202020202020202020202
 * * 0221F: 02020202020202020202020202020202
 * * 02221: 02020202020202020202020202020202
 * * 02226: 02020202020202020202020202020202
 * * 0225D: 02020202020202020202020202020202
 * * -
 * * 现在需要的是给转过来，从横向变成纵向，开始造对象给对象赋值
 * *
 * * 0 = "0302020202020202020202"
 * * 1 = "0202020202020202020202"
 * * 2 = "0202020202020202020202"
 * * 3 = "0202020202020202020202"
 * * 4 = "0202020202020202020202"
 * * 5 = "0202020202020202020202"
 * * 6 = "0202020202020202020202"
 * * 7 = "0202020202020202020202"
 * * 8 = "0202020202020202020202"
 * * 9 = "0202020202020202020202"
 * * 10 = "0202020202020202020202"
 * * 11 = "0202020202020202020202"
 * * 12 = "0202020202020202020202"
 * * 13 = "0202020202020202020202"
 * * 14 = "0202020202020202020202"
 * * 15 = "0202020202020202020202"
 * * 16 = "0202020202020202020202"
 * * 17 = "0202020202020202020202"
 * * 18 = "0202020202020202020202"
 * * 19 = "0202020202020202020202"
 * * 20 = "0202020202020202020202"
 * * 21 = "0202020202020202020202"
 * * 22 = "0202020202020202020202"
 * * 23 = "0202020202020202020202"
 * * 24 = "0202020202020202020202"
 * * 25 = "0202020202020202020202"
 * * 26 = "0202020202020202020202"
 * * 27 = "0202020202020202020202"
 * * 28 = "0202020202020202020202"
 * * 29 = "0202020202020202020202"
 * * 30 = "0202020202020202020202"
 * * 31 = "0202020202020202020202"
 * * 32 = "0202020202020202020202"
 * * 33 = "0202020202020202020202"
 * * 34 = "0202020202020202020202"
 * * 35 = "0202020202020202020202"
 * * 36 = "0202020202020202020202"
 * * 37 = "0202020202020202020202"
 * * 38 = "0202020202020202020202"
 * * 39 = "0202020202020202020202"
 * * 40 = "0202020202020202020202"
 * * 41 = "0202020202020202020202"
 * * 42 = "0202020202020202020202"
 * * 43 = "0202020202020202020202"
 * * 44 = "0202020202020202020202"
 * * 45 = "0202020202020202020202"
 * * 46 = "0202020202020202020202"
 * * 47 = "0202020202020202020202"
 * * 48 = "0203030303030303030303"
 * * 49 = "0203030303030303030303"
 * * 50 = "0203030303030303030303"
 * * 51 = "0203030303030303030303"
 * * 52 = "0203030303030303030303"
 * * 53 = "0203030303030303030303"
 * * 54 = "0203030303030303030303"
 * * 55 = "0203030303030303030303"
 * * 56 = "0203030303030303030303"
 * * 57 = "0203030303030303030303"
 * * 58 = "0203030303030303030303"
 * * 59 = "0203030303030303030303"
 * * 60 = "0203030303030303030303"
 * * 61 = "0203030303030303030303"
 * * 62 = "0203030303030303030303"
 * * 63 = "0203030303030303030303"
 * *
 * 不考虑bgm，不考虑bg图，不考虑音源，只处理鼓点数据
 * 0 = {DrumInfo@8126} "DrumInfo(beginTIme=0.0000, intervalTime=0.125000, key=1023, endTIme=0.125000)"
 * 1 = {DrumInfo@8042} "DrumInfo(beginTIme=0.500000, intervalTime=0.125000, key=31, endTIme=0.625000)"
 * 2 = {DrumInfo@8093} "DrumInfo(beginTIme=0.625000, intervalTime=0.125000, key=31, endTIme=0.750000)"
 * 3 = {DrumInfo@8127} "DrumInfo(beginTIme=2.000000, intervalTime=0.125000, key=1023, endTIme=2.125000)"
 * 4 = {DrumInfo@8128} "DrumInfo(beginTIme=4.000000, intervalTime=0.125000, key=1023, endTIme=4.125000)"
 * 5 = {DrumInfo@8129} "DrumInfo(beginTIme=6.000000, intervalTime=0.125000, key=1023, endTIme=6.125000)"
 * 6 = {DrumInfo@8130} "DrumInfo(beginTIme=8.000000, intervalTime=0.125000, key=1023, endTIme=8.125000)"
 */

    /**
     * @Description：反编译Bin文件
     * @author Lachesism
     * @date 2025-12-29
     */


    public Boolean decompileBin(MultipartFile inputFile, String outputDir) {
        try {
            String originalName = inputFile.getOriginalFilename();
            int dotIndex = originalName.lastIndexOf('.');
            if (dotIndex > 0) {
                originalName = originalName.substring(0, dotIndex);
            }

            // 1. 定义输出路径
            String txtOutputFile = outputDir + File.separator + originalName + "反编译.txt";
            String excelOutputFile = outputDir + File.separator + originalName + "反编译.xlsx";
            String jsonOutputFile = outputDir + File.separator + originalName + "反编译.json"; // 新增 JSON 路径

            byte[] bytes = inputFile.getBytes();
            if (bytes.length < 5 || bytes[0] != (byte) 0xAA || bytes[bytes.length - 1] != (byte) 0xEE) {
                System.out.println("文件格式不正确");
                return false;
            }

            int lengthHigh = bytes[1] & 0xFF;
            int lengthLow = bytes[2] & 0xFF;
            int dataLength = (lengthHigh << 8) | lengthLow;
            int expectedLength = dataLength + 5;
            System.out.println("dataLength" + dataLength);
            System.out.println("length" + bytes.length);
            if (expectedLength != bytes.length) {
                System.out.println("长度不一致，文件可能损坏");
                return false;
            }

            StringBuilder sb = new StringBuilder();
            sb.append("反编译结果如下：\n");
            sb.append("总数据段长度(dataLength): ").append(dataLength).append("\n\n");

            List<int[]> rawRecords = new ArrayList<>();
            List<BigDecimal[]> readableRecords = new ArrayList<>();

            // 2. 准备一个List用于存储JSON数据
            List<Map<String, Object>> jsonList = new ArrayList<>();

            int index = 3;
            int recordIndex = 1;
            while (index < bytes.length - 2) {
                int begin = ((bytes[index] & 0xFF) << 8) | (bytes[index + 1] & 0xFF);
                int end = ((bytes[index + 2] & 0xFF) << 8) | (bytes[index + 3] & 0xFF);
                int key = ((bytes[index + 4] & 0xFF) << 8) | (bytes[index + 5] & 0xFF);

                BigDecimal beginTime = new BigDecimal(begin).divide(new BigDecimal(100));
                BigDecimal endTime = new BigDecimal(end).divide(new BigDecimal(100));

                sb.append("第 ").append(recordIndex).append(" 条：\n");
                sb.append("  beginTime: ").append(beginTime).append(" 秒\n");
                sb.append("  endTime  : ").append(endTime).append(" 秒\n");
                sb.append("  key      : ").append(key).append("\n\n");

                rawRecords.add(new int[]{
                        bytes[index] & 0xFF,
                        bytes[index + 1] & 0xFF,
                        bytes[index + 2] & 0xFF,
                        bytes[index + 3] & 0xFF,
                        bytes[index + 4] & 0xFF,
                        bytes[index + 5] & 0xFF
                });
                readableRecords.add(new BigDecimal[]{
                        beginTime,
                        endTime,
                        new BigDecimal(key)
                });

                // 3. 收集JSON数据 (key 和 beginTime 毫秒)
                // 原始 begin 只有除以 100 才是秒，说明原始单位是 10ms。
                // 毫秒 = 原始值 * 10
                long beginTimeMillis = begin * 10L;

                Map<String, Object> jsonMap = new LinkedHashMap<>(); // 使用LinkedHashMap保持字段顺序
                jsonMap.put("key", key);
                jsonMap.put("beginTime", beginTimeMillis); // 存入毫秒
                jsonList.add(jsonMap);

                index += 6;
                recordIndex++;
            }

            byte check = bytes[bytes.length - 2];
            sb.append("校验码(check): 0x").append(String.format("%02X", check)).append("\n");

            File txtOut = new File(txtOutputFile);
            txtOut.getParentFile().mkdirs();
            Files.write(txtOut.toPath(), sb.toString().getBytes("UTF-8"));

            DtxUtils.writeExcelFinal(rawRecords, readableRecords, excelOutputFile);
            ObjectMapper objectMapper = new ObjectMapper();
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(new File(jsonOutputFile), jsonList);

            System.out.println("反编译完成：");
            System.out.println("TXT   -> " + txtOutputFile);
            System.out.println("Excel -> " + excelOutputFile);
            System.out.println("JSON  -> " + jsonOutputFile); // 打印JSON路径

        } catch (Exception e) {
            e.printStackTrace();
        }
        return true;
    }

    public Boolean convertDtx(MultipartFile file, String outPutPath) {
        String originalName = file.getOriginalFilename();
        String fileName = originalName.substring(0, originalName.lastIndexOf('.'));
        genDiffAndEasy(file, outPutPath);
        String[] dtxPaths = {
                outPutPath + "\\" + fileName + "_简单.dtx",
                outPutPath + "\\" + fileName + "_中等.dtx",
                outPutPath + "\\" + fileName + "_困难.dtx"
        };
        String[] binPaths = {
                outPutPath + "\\" + fileName + "_简单.bin",
                outPutPath + "\\" + fileName + "_中等.bin",
                outPutPath + "\\" + fileName + "_困难.bin"
        };
        String[] binNames = {
                fileName + "_简单.bin",
                fileName + "_中等.bin",
                fileName + "_困难.bin"
        };
        for (int i1 = 0; i1 < dtxPaths.length; i1++) {

            try {
                StringBuilder sb = new StringBuilder();
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(new FileInputStream(dtxPaths[i1]), StandardCharsets.UTF_8)
                )) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        sb.append(line).append("\n");
                    }
                }
                String fileContent = sb.toString();
                List<Double> bpmInfo = DtxUtils.extractBpmMap(fileContent);
                List<String> drumInfo = DtxUtils.extractDrumLines(fileContent);
                for (int i = 0; i < drumInfo.size(); i++) {
                    drumInfo.set(i, DtxUtils.paddedDrumInfo(drumInfo.get(i)));
                }
                List<String> finalDrumList = DtxUtils.populateData(drumInfo);
                int groupSize = 11;
                List<List<String>> grouped = new ArrayList<>();
                for (int i = 0; i < finalDrumList.size(); i += groupSize) {
                    int end = Math.min(i + groupSize, finalDrumList.size());
                    grouped.add(finalDrumList.subList(i, end));
                }
                List<String> newDrumList = DtxUtils.reGroupDrumInfo(grouped);
                List<RawDrumEvent> rawDrumEventList = new ArrayList<>();
                Double currentBpmValue = bpmInfo.get(0); // 初始bpm的值
                BigDecimal currentBeginTime = new BigDecimal(0.0).setScale(4, RoundingMode.HALF_UP);
                for (String everyDrumInfo : newDrumList) {
                    String bpmSubValue = everyDrumInfo.substring(0, 2);
                    if (!bpmSubValue.equals("02") && !bpmSubValue.equals("01") && !bpmSubValue.equals("00")) {
                        int bpmIndex = DtxUtils.getNumberMap(bpmSubValue);
                        currentBpmValue = bpmInfo.get(bpmIndex);
                    }
                    // 计算当前格子的时长
                    BigDecimal stepTime = DtxUtils.calculateDrumTime(currentBpmValue);
                    // 按键信息
                    everyDrumInfo = everyDrumInfo.substring(2);
                    String[] channels = {"2A", "1B", "25", "22", "1D", "24", "1F", "21", "26", "5D"};
                    // 判断此格是否“全是 02”
                    if (!everyDrumInfo.equals("02020202020202020202")) {
                        List<Integer> non02List = new ArrayList<>();
                        List<String> parts = new ArrayList<>();
                        for (int i = 0; i < everyDrumInfo.length(); i += 2) {
                            parts.add(everyDrumInfo.substring(i, i + 2));
                        }
                        for (int i = 0; i < parts.size(); i++) {
                            if (!"02".equals(parts.get(i))) {
                                non02List.add(i);
                            }
                        }
                        //计算当前格子的鼓键 KEY
                        Integer drumNumSum = 0;
                        for (Integer i : non02List) {
                            Integer drumNumberByChannel = DtxUtils.getDrumNumberByChannel(channels[i]);
                            drumNumSum += drumNumberByChannel;
                        }
                        // 将事件加入原始列表，注意：此处一个格子只产生一个事件（多键位叠加）
                        rawDrumEventList.add(new RawDrumEvent(drumNumSum, currentBeginTime));
                    }
                    // 一行处理完后，起始时间推进
                    currentBeginTime = currentBeginTime.add(stepTime);
                }
                // 根据下一个同键事件计算 EndTime 和 IntervalTime
                List<DrumInfo> drumInfoArrayList = new ArrayList<>();
                BigDecimal oneSecond = new BigDecimal(1.0).setScale(4, RoundingMode.HALF_UP);
                BigDecimal oneCentisecond = new BigDecimal(0.01).setScale(4, RoundingMode.HALF_UP);
                for (int i = 0; i < rawDrumEventList.size(); i++) {
                    RawDrumEvent currentEvent = rawDrumEventList.get(i);
                    DrumInfo drum = new DrumInfo();
                    drum.setKey(currentEvent.key);
                    drum.setBeginTIme(currentEvent.beginTime);
                    BigDecimal nextBeginTime = null;
                    // 查找下一个同键事件
                    for (int j = i + 1; j < rawDrumEventList.size(); j++) {
                        RawDrumEvent nextEvent = rawDrumEventList.get(j);
                        if (nextEvent.key.equals(currentEvent.key)) {
                            nextBeginTime = nextEvent.beginTime;
                            break;
                        }
                    }
                    BigDecimal endTime;
                    if (nextBeginTime != null) {
                        // 找到了下一个同键事件
                        BigDecimal timeDifference = nextBeginTime.subtract(currentEvent.beginTime);
                        // 如果间隔 > 1.0s, 则 endTime = beginTime + 1.0s
                        if (timeDifference.compareTo(oneSecond) > 0) {
                            endTime = currentEvent.beginTime.add(oneSecond);
                        } else {
                            // 如果间隔 <= 1.0s, 则 endTime = nextBeginTime - 0.01s
                            endTime = nextBeginTime.subtract(oneCentisecond);
                        }
                    } else {
                        // 当前是该键的最后一个事件，默认 endTime = beginTime + 1.0s
                        endTime = currentEvent.beginTime.add(oneSecond);
                    }
                    drum.setEndTIme(endTime);
                    // 重新计算 intervalTime
                    drum.setIntervalTime(drum.getEndTIme().subtract(drum.getBeginTIme()));
                    drumInfoArrayList.add(drum);
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
                    BigDecimal begin = info.getBeginTIme().multiply(new BigDecimal(100)).setScale(0, RoundingMode.HALF_UP);
                    BigDecimal end = info.getEndTIme().multiply(new BigDecimal(100)).setScale(0, RoundingMode.HALF_UP);
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
                DtxUtils.saveFile(binPaths[i1], bytes);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        for (int i = 0; i < binNames.length; i++) {
            try {
                int dotIndex = binNames[i].lastIndexOf('.');
                if (dotIndex > 0) {
                    originalName = binNames[i].substring(0, dotIndex);
                }
                String txtOutputFile = outPutPath + File.separator + originalName + "反编译.txt";
                String excelOutputFile = outPutPath + File.separator + originalName + "反编译.xlsx";
                String path = binPaths[i];
                byte[] bytes = Files.readAllBytes(Paths.get(path));
                if (bytes.length < 5 || bytes[0] != (byte) 0xAA || bytes[bytes.length - 1] != (byte) 0xEE) {
                    System.out.println("文件格式不正确");
                    return false;
                }
                int lengthHigh = bytes[1] & 0xFF;
                int lengthLow = bytes[2] & 0xFF;
                int dataLength = (lengthHigh << 8) | lengthLow;
                int expectedLength = dataLength + 5;
                if (expectedLength != bytes.length) {
                    System.out.println("长度不一致，文件可能损坏");
                    return false;
                }
                StringBuilder sb = new StringBuilder();
                sb.append("反编译结果如下：\n");
                sb.append("总数据段长度(dataLength): ").append(dataLength).append("\n\n");
                List<int[]> rawRecords = new ArrayList<>();
                List<BigDecimal[]> readableRecords = new ArrayList<>();
                int index = 3;
                int recordIndex = 1;
                while (index < bytes.length - 2) {
                    int begin = ((bytes[index] & 0xFF) << 8) | (bytes[index + 1] & 0xFF);
                    int end = ((bytes[index + 2] & 0xFF) << 8) | (bytes[index + 3] & 0xFF);
                    int key = ((bytes[index + 4] & 0xFF) << 8) | (bytes[index + 5] & 0xFF);
                    BigDecimal beginTime = new BigDecimal(begin).divide(new BigDecimal(100));
                    BigDecimal endTime = new BigDecimal(end).divide(new BigDecimal(100));
                    sb.append("第 ").append(recordIndex).append(" 条：\n");
                    sb.append("  beginTime: ").append(beginTime).append(" 秒\n");
                    sb.append("  endTime  : ").append(endTime).append(" 秒\n");
                    sb.append("  key      : ").append(key).append("\n\n");
                    rawRecords.add(new int[]{
                            bytes[index] & 0xFF,
                            bytes[index + 1] & 0xFF,
                            bytes[index + 2] & 0xFF,
                            bytes[index + 3] & 0xFF,
                            bytes[index + 4] & 0xFF,
                            bytes[index + 5] & 0xFF
                    });
                    readableRecords.add(new BigDecimal[]{
                            beginTime,
                            endTime,
                            new BigDecimal(key)
                    });

                    index += 6;
                    recordIndex++;
                }
                byte check = bytes[bytes.length - 2];
                sb.append("校验码(check): 0x").append(String.format("%02X", check)).append("\n");
                File txtOut = new File(txtOutputFile);
                txtOut.getParentFile().mkdirs();
                Files.write(txtOut.toPath(), sb.toString().getBytes("UTF-8"));
                DtxUtils.writeExcelFinal(rawRecords, readableRecords, excelOutputFile);
                System.out.println(".bin   ==> " + binPaths[i]);
                System.out.println(".dtx   ==> " + dtxPaths[i]);
                System.out.println(".txt   ==> " + txtOutputFile);
                System.out.println(".excel ==> " + excelOutputFile);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return true;
    }

    public Result genHammerModel(String outPutPath,
                                 Integer beginTime,
                                 Integer endTime,
                                 Integer songDuration,
                                 Integer BPM,
                                 String title) {
        BigDecimal begin = BigDecimal.valueOf(beginTime).multiply(BigDecimal.valueOf(100));
        BigDecimal endPadding = BigDecimal.valueOf(endTime).multiply(BigDecimal.valueOf(100));
        BigDecimal duration = BigDecimal.valueOf(songDuration).multiply(BigDecimal.valueOf(100));
        double i1 = 60.0 / BPM;
        BigDecimal interval = BigDecimal.valueOf(i1)
                .multiply(BigDecimal.valueOf(100))
                .setScale(3, RoundingMode.HALF_UP);
        duration = duration.subtract(begin).subtract(endPadding);
        int loopCount = duration.divide(interval, 0, RoundingMode.FLOOR).intValue();
        List<BinVO> drumInfoList = new ArrayList<>();
        List<Integer> result = new ArrayList<>();
        BigDecimal currentBegin = begin;
        for (int i = 0; i < loopCount; i++) {
            BinVO binVO = new BinVO();
            int weight = randomByWeight();
            int key = 0;
            if (weight == 0) {
                binVO.setIsValue(0);
            } else {
                binVO.setIsValue(1);
                key = getRandomHexSum(weight);
                binVO.setKeyHigh((byte) ((key >> 8) & 0xFF));
                binVO.setKeyLow((byte) (key & 0xFF));
                result.add(key);
            }
            int beginTimeValue = currentBegin.intValue();
            int endTimeValue = currentBegin.add(interval).intValue();
            binVO.setBeginHigh((byte) ((beginTimeValue >> 8) & 0xFF));
            binVO.setBeginLow((byte) (beginTimeValue & 0xFF));
            binVO.setEndHigh((byte) ((endTimeValue >> 8) & 0xFF));
            binVO.setEndLow((byte) (endTimeValue & 0xFF));
            drumInfoList.add(binVO);
            currentBegin = currentBegin.add(interval);
        }
        int count = (int) drumInfoList.stream().filter(bin -> bin.getIsValue() != null && bin.getIsValue() == 1).count();
        byte[] bytes = new byte[count * 6 + 5];
        bytes[0] = (byte) 0xAA;
        int realSize = count * 6;
        bytes[1] = (byte) ((realSize >> 8) & 0xFF);
        bytes[2] = (byte) (realSize & 0xFF);
        int index = 3;
        for (BinVO binVO : drumInfoList) {
            if (binVO.getIsValue() != null && binVO.getIsValue() == 1) {
                bytes[index++] = binVO.getBeginHigh();
                bytes[index++] = binVO.getBeginLow();
                bytes[index++] = binVO.getEndHigh();
                bytes[index++] = binVO.getEndLow();
                bytes[index++] = binVO.getKeyHigh();
                bytes[index++] = binVO.getKeyLow();
            }
        }
        byte check = 0;
        for (int i = 0; i < index; i++) {
            check ^= bytes[i];
        }
        bytes[index++] = check;
        bytes[index] = (byte) 0xEE;
        outPutPath = outPutPath + File.separator + title + "_level1.bin";
        DtxUtils.saveFile(outPutPath, bytes);
        System.out.println("保存完成");
        return Result.success(result);
    }


    public static void main(String[] args) throws IOException {

        // 歌曲编号映射文件
        File file = new File("D:\\Downloads\\歌曲列表\\all\\song.txt");

        // MP3 原始目录
        String folderPath = "D:\\Downloads\\歌曲列表\\all";
        File folder = new File(folderPath);

        // 目标 MP3 输出目录
        Path targetDir = Paths.get("D:\\Downloads\\歌曲列表\\all\\mp3");
        Files.createDirectories(targetDir); // 不存在就创建

        File[] mp3Files = folder.listFiles(
                (dir, name) -> name.toLowerCase().endsWith(".mp3")
        );

        Map<String, String> songMap = new LinkedHashMap<>();

        // 读取 song.txt → 歌名 -> 编号
        try (BufferedReader br = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) continue;

                int idx = line.indexOf("\t");
                if (idx > 0) {
                    String songName = line.substring(idx + 1); // 歌名
                    String songId = line.substring(0, idx);    // 编号
                    songMap.put(songName, songId);
                }
            }
        }

        // 遍历 MP3
        for (File mp3 : mp3Files) {
            if (!mp3.isFile()) continue;

            String fileName = mp3.getName();
            String[] part = fileName.split("\\.");

            if (part.length < 2) continue;

            String songName = part[0]; // 去掉 .mp3 的歌名
            String num = songMap.get(songName);

            if (num == null) {
                System.out.println("未找到编号: " + songName);
                continue;
            }

            String rename = num + "_" + fileName;

            Path source = mp3.toPath();
            Path target = targetDir.resolve(rename);

            try {
                Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
                System.out.println("✔ 复制成功: " + rename);
            } catch (Exception e) {
                System.out.println("x 复制失败: " + fileName + " -> " + e.getMessage());
            }
        }
    }

    public Boolean hammerInvertDtxFile(MultipartFile file, String outPutPath) {
        if (file == null || file.isEmpty()) {
            System.err.println("上传的文件为空");
            return false;
        }
        String originalName = file.getOriginalFilename();
        String fileName = originalName.substring(0, originalName.lastIndexOf('.'));
        try {
            StringBuilder sb = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(file.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line).append("\n");
                }
            }
            String fileContent = sb.toString();
            String title = DtxUtils.extractTitle(fileContent);
            List<Double> bpmInfo = DtxUtils.extractBpmMap(fileContent);
            List<String> drumInfo = DtxUtils.extractDrumLines(fileContent);
            for (int i = 0; i < drumInfo.size(); i++) {
                drumInfo.set(i, DtxUtils.paddedDrumInfo(drumInfo.get(i)));
            }
            List<String> finalDrumList = DtxUtils.populateData(drumInfo);
            int groupSize = 11;
            List<List<String>> grouped = new ArrayList<>();
            for (int i = 0; i < finalDrumList.size(); i += groupSize) {
                int end = Math.min(i + groupSize, finalDrumList.size());
                grouped.add(finalDrumList.subList(i, end));
            }
            List<String> newDrumList = DtxUtils.reGroupDrumInfo(grouped);
            List<RawDrumEvent> rawDrumEventList = new ArrayList<>();
            Double currentBpmValue = bpmInfo.get(0); // 初始bpm的值
            BigDecimal currentBeginTime = new BigDecimal(0).setScale(4, RoundingMode.HALF_UP);
            for (String everyDrumInfo : newDrumList) {
                String bpmSubValue = everyDrumInfo.substring(0, 2);
                if (!bpmSubValue.equals("02") && !bpmSubValue.equals("01") && !bpmSubValue.equals("00")) {
                    int bpmIndex = DtxUtils.getNumberMap(bpmSubValue);
                    currentBpmValue = bpmInfo.get(bpmIndex);
                }
                // 计算当前格子的时长
                BigDecimal stepTime = DtxUtils.calculateDrumTime(currentBpmValue);
                // 按键信息
                everyDrumInfo = everyDrumInfo.substring(2);
                String[] channels = {"2A", "1B", "25", "22", "1D", "24", "1F", "21", "26", "5D"};
                // 判断此格是否“全是 02”
                if (!everyDrumInfo.equals("02020202020202020202")) {
                    List<Integer> non02List = new ArrayList<>();
                    List<String> parts = new ArrayList<>();
                    for (int i = 0; i < everyDrumInfo.length(); i += 2) {
                        parts.add(everyDrumInfo.substring(i, i + 2));
                    }
                    for (int i = 0; i < parts.size(); i++) {
                        if (!"02".equals(parts.get(i))) {
                            non02List.add(i);
                        }
                    }
                    //计算当前格子的鼓键 KEY
                    Integer drumNumSum = 0;
                    for (Integer i : non02List) {
                        Integer drumNumberByChannel = DtxUtils.getDrumNumberByChannel(channels[i]);
                        drumNumSum += drumNumberByChannel;
                    }
                    // 将事件加入原始列表，此处一个格子只产生一个事件（多键位叠加）
                    rawDrumEventList.add(new RawDrumEvent(drumNumSum, currentBeginTime));
                }
                // 一行处理完后，起始时间推进
                currentBeginTime = currentBeginTime.add(stepTime);
            }
            // 根据下一个同键事件计算 EndTime 和 IntervalTime
            List<DrumInfo> drumInfoArrayList = new ArrayList<>();
            BigDecimal oneSecond = new BigDecimal(1.0).setScale(4, RoundingMode.HALF_UP);
            BigDecimal oneCentisecond = new BigDecimal(0.01).setScale(4, RoundingMode.HALF_UP);
            for (int i = 0; i < rawDrumEventList.size(); i++) {
                RawDrumEvent currentEvent = rawDrumEventList.get(i);
                DrumInfo drum = new DrumInfo();
                drum.setKey(currentEvent.key);
                drum.setBeginTIme(currentEvent.beginTime);
                BigDecimal nextBeginTime = null;
                // 查找下一个同键事件
                for (int j = i + 1; j < rawDrumEventList.size(); j++) {
                    RawDrumEvent nextEvent = rawDrumEventList.get(j);
                    if (nextEvent.key.equals(currentEvent.key)) {
                        nextBeginTime = nextEvent.beginTime;
                        break;
                    }
                }
                BigDecimal endTime;
                if (nextBeginTime != null) {
                    // 找到了下一个同键事件
                    BigDecimal timeDifference = nextBeginTime.subtract(currentEvent.beginTime);
                    // 如果间隔 > 1.0s, 则 endTime = beginTime + 1.0s
                    if (timeDifference.compareTo(oneSecond) > 0) {
                        endTime = currentEvent.beginTime.add(oneSecond);
                    } else {
                        // 如果间隔 <= 1.0s, 则 endTime = nextBeginTime - 0.01s
                        endTime = nextBeginTime.subtract(oneCentisecond);
                    }
                } else {
                    // 当前是该键的最后一个事件，默认 endTime = beginTime + 1.0s
                    endTime = currentEvent.beginTime.add(oneSecond);
                }
                drum.setEndTIme(endTime);
                // 重新计算 intervalTime
                drum.setIntervalTime(drum.getEndTIme().subtract(drum.getBeginTIme()));
                drumInfoArrayList.add(drum);
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
                BigDecimal begin = info.getBeginTIme().multiply(new BigDecimal(100)).setScale(0, RoundingMode.HALF_UP);
                BigDecimal end = info.getEndTIme().multiply(new BigDecimal(100)).setScale(0, RoundingMode.HALF_UP);
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
            DtxUtils.saveFile(outPutPath, bytes);
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    //=================================== musicXml部分 =================================
    public Boolean convertXml(MultipartFile file, String outPutPath) {
        if (file == null || file.isEmpty()) {
            System.err.println("上传的文件为空");
            return false;
        }
        String originalName = file.getOriginalFilename();
        String fileName = originalName.substring(0, originalName.lastIndexOf('.'));
        try {
            List<DrumInfo> drumInfoArrayList = parseMusicWithVariableTempo(file);
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
                BigDecimal begin = info.getBeginTIme().multiply(new BigDecimal(0.1)).setScale(0, RoundingMode.HALF_UP);
                BigDecimal end = info.getEndTIme().multiply(new BigDecimal(0.1)).setScale(0, RoundingMode.HALF_UP);
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
            DtxUtils.saveFile(outPutPath + "/" + fileName + "_level2.bin", bytes);
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }

    }


    public Map<String, Object> fullProcessMusicXml(MultipartFile mp3File, MultipartFile xmlFile, MultipartFile mp3TempFile, MultipartFile xmlTempFile, String outputDir, Integer startSecond, Integer duration, Integer beginTime, Integer endTime) {
        try {
            Map<String, Object> resultMap = new HashMap<>();
            resultMap.put("mp3Success", false);
            resultMap.put("xmlSuccess", false);
            resultMap.put("hammerResult", false);
            resultMap.put("overallSuccess", false);
            if (mp3File == null || mp3File.isEmpty()) {
                resultMap.put("message", "MP3 文件为空");
                return resultMap;
            }
            if (xmlFile == null || xmlFile.isEmpty()) {
                resultMap.put("message", "musicXml文件为空");
                return resultMap;
            }
            // 1. 获取基础文件名
            String originalFilename = mp3File.getOriginalFilename();
            if (originalFilename == null || !originalFilename.toLowerCase().endsWith(".mp3")) {
                resultMap.put("message", "MP3 文件格式不正确");
                return resultMap;
            }
            String baseName = originalFilename.substring(0, originalFilename.lastIndexOf('.'));
            // 2. 在 outputDir 下创建文件名文件夹
            File dir = new File(outputDir, baseName);
            if (!dir.exists()) dir.mkdirs();
            String finalOutputDir = dir.getAbsolutePath();
            CompletableFuture<Boolean> mp3Future = CompletableFuture.supplyAsync(() -> {
                try {
                    return processMp3(mp3File, finalOutputDir, startSecond, duration);
                } catch (Exception e) {
                    e.printStackTrace();
                    return false;
                }
            });

            CompletableFuture<Boolean> xmlFuture = CompletableFuture.supplyAsync(() -> {
                try {
                    return convertXml(xmlFile, finalOutputDir);
                } catch (Exception e) {
                    e.printStackTrace();
                    return false;
                }
            });
            CompletableFuture<Boolean> hammerFuture = CompletableFuture.supplyAsync(() -> {
                try {
                    File tempFile = File.createTempFile("tempMp3", ".mp3");
                    mp3TempFile.transferTo(tempFile);
                    File tempFile2 = File.createTempFile("tempXml", ".xml");
                    xmlTempFile.transferTo(tempFile2);
                    int durationFromMp3 = getDurationFromMultipartFile(tempFile);
                    int bpm = getFirstBpmFromXml(tempFile2);
                    boolean b = genHammerModel(finalOutputDir, beginTime, endTime, durationFromMp3, bpm, baseName).getCode() == 200;
                    tempFile.delete();
                    tempFile2.delete();
                    return b;
                } catch (Exception e) {
                    e.printStackTrace();
                    return false;
                }
            });
            // 等待所有任务完成
            boolean mp3Result = mp3Future.join();
            boolean xmlResult = xmlFuture.join();
            boolean hammerResult = hammerFuture.join();
            // 4. 最终整体成功条件：两个都成功
            boolean overall = mp3Result && xmlResult && hammerResult;
            resultMap.put("overallSuccess", overall);
            resultMap.put("mp3Success", mp3Result);
            resultMap.put("xmlSuccess", xmlResult);
            resultMap.put("hammerResult", hammerResult);
            resultMap.put("message", overall ? "全部处理成功" : "部分处理失败");
            return resultMap;
        } catch (Exception e) {
            System.out.println(e.getMessage());
            return null;
        }
    }

}
