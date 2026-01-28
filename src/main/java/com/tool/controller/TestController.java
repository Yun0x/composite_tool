package com.tool.controller;

import com.tool.service.TestService;
import com.tool.util.HeZhouSimUtiles;
import com.tool.util.Result;
import com.tool.vo.TSimcardInfo;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.*;

@RestController
@RequestMapping("/test")
public class TestController {


    @Resource
    private TestService testService;

    /**
     * 上传文件，返回 taskId
     */
    @PostMapping("/batchUpdate")
    public Result batchUpdate() {
        List<String> iccids = testService.getAllIccid(); // 异步处理并记录 taskId
        String iccid = String.join(",", iccids);
        List<TSimcardInfo> a = HeZhouSimUtiles.GetSIMCardListOnGroup(iccid);
        return Result.success(a);
    }

    @RequestMapping("/receive")
    public Map<String, Object> receive(@RequestBody List<Map<String, Object>> reports) {
        System.out.println(reports);
        for (Map<String, Object> report : reports) {
            // 取 bizId 并去掉 ^0
            String bizId = "";
            Object bizIdObj = report.get("biz_id");
            if (bizIdObj instanceof String) {
                bizId = ((String) bizIdObj).split("\\^")[0];
            }
            // 拿success 状态
            Object successObj = report.get("success");
            boolean success = (successObj instanceof Boolean) && ((Boolean) successObj);
            // 拿错误信息
            String errMsg = "";
            Object errMsgObj = report.get("err_msg");
            if (errMsgObj instanceof String) {
                errMsg = (String) errMsgObj;
            }
            //拿发送时间
            String sendTime = "";
            Object sendTimeObj = report.get("send_time");
            if (sendTimeObj instanceof String) {
                sendTime = (String) sendTimeObj;
            }
            int sendStatus = success ? 1 : 0;
            testService.updateMessageLog(bizId, errMsg, sendStatus, sendTime);
        }
        Map<String, Object> result = new HashMap<String, Object>();
        result.put("code", 0);
        result.put("msg", "成功");
        return result;
    }

}
