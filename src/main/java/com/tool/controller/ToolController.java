package com.tool.controller;

import com.tool.service.TestService;
import com.tool.service.UploadService;
import com.tool.util.DtxUtils;
import com.tool.util.Result;
import com.tool.vo.DrumInfo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.stream.Collectors;
import java.util.ArrayList;
import javax.annotation.Resource;
import javax.servlet.http.HttpServletResponse;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static com.tool.util.MusicXmlConverter.*;

@RestController
@RequestMapping("/tool")
public class ToolController {

    @Resource
    private UploadService uploadService;
    @Autowired
    private TestService testService;

    /**
     * 上传文件，返回 taskId
     */
    @PostMapping("/upload")
    public Result<String> upload(@RequestParam("file") MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return Result.error("文件为空");
        }
        String taskId = uploadService.uploadFileAsync(file); // 异步处理并记录 taskId
        return Result.success("上传成功", taskId);
    }


    /**
     * SSE 实时推送进度
     */
    @GetMapping("/uploadProgressSse")
    public SseEmitter progressSse(@RequestParam("taskId") String taskId) {
        SseEmitter emitter = new SseEmitter(0L); // 0L 表示无限超时
        new Thread(() -> {
            try {
                while (true) {
                    int percent = uploadService.getProgress(taskId);
                    emitter.send(SseEmitter.event().name("progress").data(percent));
                    if (percent >= 100) {
                        emitter.complete();
                        break;
                    }
                    Thread.sleep(500);
                }
            } catch (IOException | InterruptedException e) {
                emitter.completeWithError(e);
            }
        }).start();
        return emitter;
    }

    /**
     * @Description：dtx转bin
     * @author Lachesism
     * @date 2025-12-30
     */

    @PostMapping("/uploadDtx")
    public Result uploadDtx(@RequestParam("file") MultipartFile file,
                            @RequestParam String outPutPath) {

        if (file == null || file.isEmpty()) {
            return Result.error("文件为空");
        }
        Boolean res = uploadService.invertDtxFile(file, outPutPath);
        if (Boolean.TRUE.equals(res)) {
            return Result.success("上传成功");
        } else {
            return Result.error("上传失败");
        }
    }

    /**
     * @Description：生成难易度不同的dtx
     * @author Lachesism
     * @date 2025-12-30
     */

    @PostMapping("/genDiffAndEasy")
    public Result genDiffAndEasy(@RequestParam("file") MultipartFile file,
                                 @RequestParam String outPutPath) {
        if (file == null || file.isEmpty()) {
            return Result.error("文件为空");
        }
        Boolean res = uploadService.genDiffAndEasy(file, outPutPath);
        if (Boolean.TRUE.equals(res)) {
            return Result.success("上传成功");
        } else {
            return Result.error("上传失败");
        }
    }

    /**
     * @Description：反编译Bin
     * @author Lachesism
     * @date 2025-12-30
     */

    @PostMapping("/decompileBin")
    public Result decompileBin(@RequestParam("file") MultipartFile file, @RequestParam String outPutPath) {
        if (file == null || file.isEmpty()) {
            return Result.error("文件为空");
        }
        Boolean res = uploadService.decompileBin(file, outPutPath);
        if (Boolean.TRUE.equals(res)) {
            return Result.success("上传成功");
        } else {
            return Result.error("上传失败");
        }
    }


    /**
     * @Description：反编译Bin
     * @author Lachesism
     * @date 2025-12-30
     */

    @PostMapping("/convertDtx")
    public Result convertDtx(@RequestParam("file") MultipartFile file, @RequestParam String outPutPath) {
        if (file == null || file.isEmpty()) {
            return Result.error("文件为空");
        }
        Boolean res = uploadService.convertDtx(file, outPutPath);
        if (Boolean.TRUE.equals(res)) {
            return Result.success("上传成功");
        } else {
            return Result.error("上传失败");
        }
    }

    /**
     * @Description：处理mp3，压缩以及截取高潮部分
     * @author Lachesism
     * @date 2026-01-27
     */
    @PostMapping("/processMp3")
    public Result processMp3(
            @RequestParam("file") MultipartFile mp3File,
            @RequestParam("outputDir") String outputDir,
            @RequestParam("startSecond") Integer startSecond,
            @RequestParam("duration") Integer duration) {
        if (mp3File == null || mp3File.isEmpty()) {
            return Result.error("MP3 文件为空");
        }
        boolean success = uploadService.processMp3(mp3File, outputDir, startSecond, duration);
        return success ? Result.success("处理成功") : Result.error("处理失败");
    }

    /**
     * @Description：全流程一键化处理
     * @author Lachesism
     * @date 2026-01-27
     */
    @PostMapping("/fullProcess")
    public Result<Map<String, Object>> fullProcess(
            @RequestParam("file") MultipartFile mp3File,
            @RequestParam(value = "dtx") MultipartFile dtxFile,
            @RequestParam("outputDir") String outputDir,
            @RequestParam(value = "startSecond", required = false) Integer startSecond,
            @RequestParam(value = "duration", required = false) Integer duration) {
        if (mp3File == null || mp3File.isEmpty()) {
            return Result.error("MP3 文件为空");
        }
        Map<String, Object> resultMap = uploadService.fullProcess(mp3File, dtxFile, outputDir, startSecond, duration);
        boolean overallSuccess = (boolean) resultMap.getOrDefault("overallSuccess", false);
        if (overallSuccess) {
            return Result.success("处理成功", resultMap);
        } else {
            return Result.error(500, "处理失败", resultMap);
        }
    }

    @GetMapping(value = "/sdCardTest", produces = "text/event-stream;charset=UTF-8")
    public SseEmitter sdCardTest(
            @RequestParam String drivePath,
            @RequestParam(defaultValue = "1") int rounds,
            @RequestParam(defaultValue = "true") boolean randomPattern
    ) {
        return testService.startSdCardTest(drivePath, rounds, randomPattern);
    }

    @PostMapping(value = "/wipeSdCard")
    public SseEmitter wipeSdCard(@RequestParam Integer type) {
        return testService.wipeSdCard(type);
    }


    @PostMapping("/copyDrumFiles")
    public Result copyDrumFiles(@RequestParam String path) {
        return testService.copyDrumFiles(path);
    }

    @PostMapping("/genHammerModelHand")
    public Result genHammerModel(@RequestParam String outPutPath, @RequestParam Integer beginTime, @RequestParam Integer endTime, @RequestParam Integer songDuration, @RequestParam Integer BPM) {
        return uploadService.genHammerModelHand(outPutPath, beginTime, endTime, songDuration, BPM);
    }

    @PostMapping("/genHammerModel")
    public Result genHammerModel(
            @RequestParam("mp3File") MultipartFile mp3File,
            @RequestParam("dtxFile") MultipartFile dtxFile,
            @RequestParam String outPutPath,
            @RequestParam Integer beginTime,
            @RequestParam Integer endTime) {
        File tempMp3 = null;
        try {
            String originalFilename = mp3File.getOriginalFilename();
            String suffix = (originalFilename != null && originalFilename.contains("."))
                    ? originalFilename.substring(originalFilename.lastIndexOf("."))
                    : ".mp3";
            tempMp3 = File.createTempFile("process_", suffix);
            try (InputStream is = mp3File.getInputStream()) {
                java.nio.file.Files.copy(is, tempMp3.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
            Integer actualDurationSeconds = getMp3DurationInSeconds(tempMp3);
            StringBuilder sb = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(dtxFile.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line).append("\n");
                }
            }
            String fileContent = sb.toString();
            List<Double> bpmList = DtxUtils.extractBpmMap(fileContent);
            int BPM = (bpmList != null && !bpmList.isEmpty()) ? bpmList.get(0).intValue() : 120;
            String title = "未知曲目";
            if (originalFilename != null && !originalFilename.isEmpty()) {
                int underscoreIndex = originalFilename.indexOf(".");
                if (underscoreIndex > 0) {
                    title = originalFilename.substring(0, underscoreIndex);
                } else {
                    int dotIndex = originalFilename.lastIndexOf(".");
                    title = (dotIndex > 0) ? originalFilename.substring(0, dotIndex) : originalFilename;
                }
            }
            uploadService.hammerInvertDtxFile(dtxFile, outPutPath + File.separator + title + "_level2.bin");
            return uploadService.genHammerModel(
                    outPutPath,
                    beginTime,
                    endTime,
                    actualDurationSeconds,
                    BPM,
                    title);

        } catch (Exception e) {
            e.printStackTrace();
            return Result.error("处理失败：" + e.getMessage());
        } finally {
            if (tempMp3 != null && tempMp3.exists()) {
                tempMp3.delete();
            }
        }
    }

    private Integer getMp3DurationInSeconds(File file) throws Exception {
        ProcessBuilder pb = new ProcessBuilder(
                "D:\\Code\\ffmpeg\\bin\\ffmpeg.exe",
                "-i", file.getAbsolutePath()
        );
        pb.redirectErrorStream(true);
        Process process = pb.start();
        double totalSeconds = 0;

        try (BufferedReader br = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = br.readLine()) != null) {
                if (line.contains("Duration:")) {
                    String dur = line.substring(line.indexOf("Duration:") + 10, line.indexOf(", start:")).trim();
                    String[] parts = dur.split(":");
                    double h = Double.parseDouble(parts[0]);
                    double m = Double.parseDouble(parts[1]);
                    double s = Double.parseDouble(parts[2]);
                    totalSeconds = h * 3600 + m * 60 + s;
                    break;
                }
            }
        }
        process.waitFor();
        return (int) Math.round(totalSeconds);
    }

    /**
     * @Description：转换xml
     * @author Lachesism
     * @date 2025-12-30
     */

    @PostMapping("/convertXml")
    public Result convertXml(@RequestParam String outPutPath, @RequestParam(value = "delayTime", defaultValue = "0") Integer delayTime) {
        Boolean res = uploadService.convertXml(outPutPath, delayTime);
        if (Boolean.TRUE.equals(res)) {
            return Result.success("生成成功");
        } else {
            return Result.error("上传失败");
        }
    }

    /**
     * @Description：musicXml全流程一键化处理
     * @author Lachesism
     * @date 2026-01-27
     */
    @PostMapping("/fullProcessMusicXml")
    public Result<Map<String, Object>> fullProcessMusicXml(
            @RequestParam("mp3") MultipartFile mp3File,
            @RequestParam("mp3Temp") MultipartFile mp3TempFile,
            @RequestParam(value = "xml") MultipartFile xmlFile,
            @RequestParam("outputDir") String outputDir,
            @RequestParam(value = "startSecond", required = false) Integer startSecond,
            @RequestParam(value = "duration", required = false) Integer duration,
            @RequestParam(value = "delayTime", defaultValue = "0") Integer delayTime,
            @RequestParam Integer beginTime,
            @RequestParam Integer endTime) {
        if (mp3File == null || mp3File.isEmpty()) {
            return Result.error("MP3 文件为空");
        }
        Map<String, Object> resultMap = uploadService.fullProcessMusicXml(mp3File, xmlFile, mp3TempFile, outputDir, startSecond, duration, delayTime, beginTime, endTime);
        boolean overallSuccess = (boolean) resultMap.getOrDefault("overallSuccess", false);
        if (overallSuccess) {
            return Result.success("处理成功", resultMap);
        } else {
            return Result.error(500, "处理失败", resultMap);
        }
    }


    public static void main(String[] args) {
        String outputDir = "D:\\Downloads\\S00028_小毛驴";
//        System.out.println(runPythonParse(outputDir));
    }

    /**
     * @Description：musicXml全流程一键化处理
     * @author Lachesism
     * @date 2026-01-27
     */
    @PostMapping("/testXml")
    public void testXml(@RequestParam(value = "xml") MultipartFile xmlFile) {
        List<DrumInfo> drumInfoArrayList = parseMusicXmlToInitList(xmlFile);
        List<DrumInfo> resultList = drumInfoArrayList.stream()
                .collect(Collectors.toMap(
                        DrumInfo::getBeginTime,    // 以 BigDecimal 类型的 beginTime 作为 Key
                        info -> info,               // Value 是对象本身
                        (existing, replacement) -> {
                            // 当 beginTime 一样时进入此合并逻辑

                            Integer k1 = existing.getKey();
                            Integer k2 = replacement.getKey();

                            // 如果 key 不一样，则将数值相加，并替换第一个元素的 key
                            if (k1 != null && k2 != null && !k1.equals(k2)) {
                                existing.setKey(k1 + k2);
                            }
                            // 如果 key 一样，这里什么都不用做，直接返回 existing
                            // 实际上就满足了“移除相同元素”的要求

                            return existing;
                        },
                        LinkedHashMap::new // 保持原始处理顺序
                ))
                .values()
                .stream()
                .sorted(Comparator.comparing(DrumInfo::getBeginTime)) // 最终按时间轴正序排列
                .collect(Collectors.toList());
        drumInfoArrayList = resultList;
        for (DrumInfo drumInfo : drumInfoArrayList) {
            System.out.println(drumInfo);
        }
    }


    /**
     * @Description：反编译Bin
     * @author Lachesism
     * @date 2025-12-30
     */
    @PostMapping("/analyzeDrumInfo")
    public Result analyzeDrumInfo(@RequestParam("file") MultipartFile file) {
        List<DrumInfo> drumInfo = uploadService.analyzeDrumInfo(file);
        return Result.success(drumInfo);
    }


    /**
     * @Description：生成合同
     * @author Lachesism
     * @date 2026-03-19
     */
    @GetMapping("generateContract")
    public Result generateContractsTemp(@RequestParam(required = false) String userName,
                                        @RequestParam String personName,
                                        @RequestParam Integer type,
                                        @RequestParam String receiverNo) {
        uploadService.generateContractsTemp(userName, personName, type, receiverNo);
        return Result.success();
    }

}
