package com.tool.controller;

import com.tool.service.TestService;
import com.tool.util.HeZhouSimUtiles;
import com.tool.util.Result;
import com.tool.util.TokenUtil;
import com.tool.vo.TSimcardInfo;
import com.tool.vo.testVO.ToolUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.*;

@RestController
@RequestMapping("/test")
public class TestController {

    private static final Logger logger = LoggerFactory.getLogger(TestController.class);

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

    @RequestMapping("/getFirstOrder")
    public Result getFirstOrder(@RequestParam(required = true) String machineNo) {
        logger.info("方法：getFirstOrder， 登录名 {}  机器编号 {}", getCurrentLoginName(), machineNo);
        if (!StringUtils.hasText(machineNo)) {
            return Result.error(400, "machineNo不能为空");
        }
        Map<String, Object> info = testService.getFirstOrder(machineNo);
        return Result.success(info);
    }

    @RequestMapping("/getRepairRecords")
    public Result getRepairRecords(@RequestParam(required = true) String machineNo) {
        logger.info("方法：getRepairRecords， 登录名{}  机器编号{}", getCurrentLoginName(), machineNo);
        Map<String, Object> info = testService.getRepairRecords(machineNo);
        return Result.success(info);
    }


    @RequestMapping("/getFirstOrder2")
    public Result getFirstOrder2(@RequestParam(required = true) String machineNo) {
        Map<String, Object> info = testService.getFirstOrder2(machineNo);
        return Result.success(info);
    }

    //生成20万用户 + 100万机器 + 1亿订单
    @RequestMapping("/all")
    public Map<String, Object> generateAll() {
        return testService.generateAll();
    }

    private String getCurrentLoginName() {
        ToolUser toolUser = TokenUtil.getToolUser();
        if (toolUser == null || !StringUtils.hasText(toolUser.getLoginName())) {
            return "unknown";
        }
        return toolUser.getLoginName();
    }

}
