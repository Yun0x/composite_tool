package com.tool.controller;

import com.tool.service.TestService;
import com.tool.service.UploadService;
import com.tool.util.DtxUtils;
import com.tool.util.Result;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import javax.annotation.Resource;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

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

    public static void main(String[] args) {
        String a = "02020202020202023202020202020202";
        String b = "020202340202020202020202020202020202";
        String f = "0002020202020202020202";
        String c = "02020202020202022A020202020202020202022A2A2A2A022A02020202020202";
        String e = "2C020202020202022C020202020202022C020202020202022C020202020202022C020202020202022C020202020202022C02020202020202022C02020202022C02020202020202022C02020202020202022C02022C02022C02022C0202020202";
        String d = "260202020202020202020202020202020202020202020202020202020202020202020202020202020202020202020202020202020202020202020202020202020202020202020202020202020202020226020202020202020202020202020202260202020202020202020202020202020202020202020202020202020202020202260202020202020202020202020202020202020202020202020202020202020202020202020202020202020202020202020202020202020202020202020202";
        System.out.println(DtxUtils.transferMachine("#09022: 02020202020202022A020202020202020202022A2A2A2A022A02020202020202"));
        // 每 8 个字符为一组
        List<String> groups = new ArrayList<>();

        for (int i = 0; i < a.length(); i += 4) {
            if (i + 4 <= a.length()) {
                groups.add(a.substring(i, i + 4));
            } else {
                // 末尾不满 8 个字符的情况
                groups.add(a.substring(i));
            }
        }

//        System.out.println(groups);
    }


}
