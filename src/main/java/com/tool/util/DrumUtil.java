package com.tool.util;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.tool.vo.DrumInfo;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * @author Lachesism
 * @Description：股普工具类
 * @date 2026-03-10
 */

public class DrumUtil {
    public static List<DrumInfo> parseJsonToDrumList(MultipartFile jsonFile) {
        try {
            ObjectMapper mapper = new ObjectMapper();
            List<Map<String, Object>> rawList = mapper.readValue(
                    jsonFile.getInputStream(),
                    new TypeReference<List<Map<String, Object>>>() {
                    }
            );
            List<DrumInfo> result = new ArrayList<>();
            for (Map<String, Object> item : rawList) {
                DrumInfo drum = new DrumInfo();
                BigDecimal beginTime = new BigDecimal(item.get("beginTime").toString());
                BigDecimal endTime = new BigDecimal(item.get("endTime").toString());
                Integer key = Integer.valueOf(item.get("key").toString());
                drum.setBeginTime(beginTime);
                drum.setEndTime(endTime);
                drum.setKey(key);
                result.add(drum);
            }
            return result;
        } catch (Exception e) {
            throw new RuntimeException("JSON解析失败", e);
        }
    }

    public static List<DrumInfo> parseBinToDrumList(MultipartFile binFile) {
        try {
            byte[] bytes = binFile.getBytes();
            if (bytes.length < 5 || bytes[0] != (byte) 0xAA || bytes[bytes.length - 1] != (byte) 0xEE) {
                throw new RuntimeException("文件格式不正确");
            }
            int lengthHigh = bytes[1] & 0xFF;
            int lengthLow = bytes[2] & 0xFF;
            int dataLength = (lengthHigh << 8) | lengthLow;
            int expectedLength = dataLength + 5;
            if (expectedLength != bytes.length) {
                throw new RuntimeException("长度不一致，文件可能损坏");
            }
            List<DrumInfo> result = new ArrayList<>();
            int index = 3;
            BigDecimal prevBegin = null;
            while (index < bytes.length - 2) {
                int begin = ((bytes[index] & 0xFF) << 8) | (bytes[index + 1] & 0xFF);
                int end = ((bytes[index + 2] & 0xFF) << 8) | (bytes[index + 3] & 0xFF);
                int key = ((bytes[index + 4] & 0xFF) << 8) | (bytes[index + 5] & 0xFF);
                BigDecimal beginTime = BigDecimal.valueOf(begin * 10L);
                BigDecimal endTime = BigDecimal.valueOf(end * 10L);
                DrumInfo drum = new DrumInfo();
                drum.setBeginTime(beginTime);
                drum.setEndTime(endTime);
                drum.setKey(key);
                if (prevBegin == null) {
                    drum.setIntervalTime(BigDecimal.ZERO);
                } else {
                    drum.setIntervalTime(beginTime.subtract(prevBegin));
                }
                prevBegin = beginTime;
                result.add(drum);
                index += 6;
            }
            return result;

        } catch (Exception e) {
            throw new RuntimeException("BIN解析失败", e);
        }
    }

    public static List<DrumInfo> parseXmlToDrumList(MultipartFile xmlFile) {
        try {
            File tempDir = Files.createTempDirectory("uploadDir").toFile();
            File tempFile = new File(tempDir, xmlFile.getOriginalFilename());
            xmlFile.transferTo(tempFile);
            ProcessBuilder pb = new ProcessBuilder(
                    "python",
                    "-Xutf8",
                    "D:\\Code\\python\\musicXml\\adv_xml2Json.py",
                    tempDir.getAbsolutePath(),String.valueOf(0)
            );
            pb.redirectErrorStream(true);

            Process process = pb.start();

            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8)
            );

            String line;
            String jsonPath = null;
            while ((line = reader.readLine()) != null) {
                System.out.println("PYTHON -> " + line);
                if (line.startsWith("SUCCESS:")) {
                    jsonPath = line.replace("SUCCESS:", "").trim();
                }
            }
            int exitCode = process.waitFor();
            if (exitCode != 0) {
                throw new RuntimeException("Python 执行失败");
            }
            ObjectMapper mapper = new ObjectMapper();
            List<DrumInfo> drumList = mapper.readValue(
                    new File(jsonPath),
                    new TypeReference<List<DrumInfo>>() {}
            );
            tempFile.delete(); // XML 文件
            if (jsonPath != null) {
                File jsonFile = new File(jsonPath);
                if (jsonFile.exists()) {
                    jsonFile.delete();
                }
            }
            tempDir.delete();

            return drumList;

        } catch (Exception e) {
            throw new RuntimeException("解析 XML 文件失败", e);
        }
    }
}