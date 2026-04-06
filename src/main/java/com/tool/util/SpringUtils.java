package com.tool.util;

import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.*;
import java.math.BigDecimal;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.*;

public class SpringUtils {
    private static long startTime;
    public static boolean isEmpty(Object object) {
        if (object == null) {
            return true;
        }
        if (object instanceof List) {
            return ((List) object).size() == 0;
        }
        if (object instanceof String) {
            return ((String) object).trim().equals("");
        }
        return false;
    }

    public static <T> boolean isNotEmpty(Collection<T> collection) {
        return collection != null && !collection.isEmpty();
    }

    public static <K, V> boolean isNotEmpty(Map<K, V> map) {
        return map != null && !map.isEmpty();
    }

    public static <T> boolean isNotEmpty(T[] array) {
        return array != null && array.length != 0;
    }

    public static <T> boolean isNotEmpty(T object) {
        return object != null && !object.toString().isEmpty();
    }

    public static boolean isNotEmpty(String str) {
        return str != null && !str.trim().isEmpty();
    }

    //Integer和String的互相转化
    public static Object Int_Str_Convert(Object obj) {
        if (obj instanceof Integer) {
            return String.valueOf(obj);
        } else if (obj instanceof String) {
            try {
                return Integer.parseInt((String) obj);
            } catch (NumberFormatException e) {
                return null;
            }
        } else {
            return null;
        }
    }

    /**
     * 安全的字符串比较
     */
    public static boolean equals(String str1, String str2) {
        return str1 == null ? str2 == null : str1.equals(str2);
    }

    /**
     * 读取文本文件
     */
    public static String readFile(String filePath) throws IOException {
        try (BufferedReader reader = new BufferedReader(
                new FileReader(filePath))) {
            StringBuilder content = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                content.append(line).append("\n");
            }
            return content.toString();
        }
    }

    /**
     * 写入文本文件
     */
    public static void writeFile(String content, String filePath)
            throws IOException {
        try (BufferedWriter writer = new BufferedWriter(
                new FileWriter(filePath))) {
            writer.write(content);
        }
    }

    /**
     * 复制文件
     */
    public static void copyFile(String source, String target)
            throws IOException {
        try (InputStream in = new FileInputStream(source);
             OutputStream out = new FileOutputStream(target)) {
            byte[] buffer = new byte[8192];
            int length;
            while ((length = in.read(buffer)) > 0) {
                out.write(buffer, 0, length);
            }
        }
    }

    /**
     * 计时工具
     */
    //第一个调用时开始计时没有返回值
    public static void  begin() {
        startTime = System.nanoTime();
    }
    /**
     * 第二次调用时停止计时，打印耗时（格式化）并返回秒（保留4位小数）
     */
    public static String printEnd() {
        long endTime = System.nanoTime();
        long duration = endTime - startTime;
        String formatted = formatDuration(duration);
        startTime = 0L;
        return formatted;
    }

    //第二个调用时停止计时并输出两次方法调用之间的时间，返回秒，精确到小数点后4位
    public static double end() {
        long endTime = System.nanoTime();
        double elapsedTime = (endTime - startTime) / 1_000_000_000.0; // 转换为秒
        startTime = 0; // 重置计时器
        return Math.round(elapsedTime * 10000.0) / 10000.0; // 精确到小数点后4位
    }
    /**
     * 把纳秒转成 时:分:秒
     */
    public static String formatDuration(long nanos) {
        long totalSeconds = nanos / 1_000_000_000;
        long hours = totalSeconds / 3600;
        long minutes = (totalSeconds % 3600) / 60;
        long seconds = totalSeconds % 60;
        return String.format("%02d:%02d:%02d", hours, minutes, seconds);
    }

    /**
     * 输入数字输出中文
     */
    public static String convertChineseAmount(String amount) {
        String[] CN_NUMBERS = {"零", "壹", "贰", "叁", "肆", "伍", "陆", "柒", "捌", "玖"};
        String[] CN_UNITS = {"", "拾", "佰", "仟", "万", "拾", "佰", "仟", "亿", "拾", "佰", "仟"};
        String CN_INTEGER = "整";
        String CN_POINT = "点";
        StringBuilder sb = new StringBuilder();
        // 分割整数和小数部分
        String[] parts = amount.split("\\.");
        // 转换整数部分
        String integerPart = parts[0];
        int partLength = integerPart.length();
        boolean isLastZero = false;
        for (int i = 0; i < partLength; i++) {
            int digit = Integer.parseInt(String.valueOf(integerPart.charAt(i)));
            int unitPos = (partLength - i - 1) % 4;
            if (digit == 0) {
                isLastZero = true;
                if (unitPos == 0 && (partLength - i - 1) != 0 && !isLastZero) {
                    sb.append(CN_NUMBERS[digit]);
                }
            } else {
                if (isLastZero) {
                    sb.append(CN_NUMBERS[0]);
                }
                isLastZero = false;
                sb.append(CN_NUMBERS[digit]).append(CN_UNITS[unitPos]);
            }
            if (unitPos == 0 && (partLength - i - 1) != 0) {
                int unit = (partLength - i - 1) / 4;
                if (unit == 1) {
                    sb.append(CN_UNITS[4]); // 万
                } else if (unit == 2) {
                    sb.append(CN_UNITS[8]); // 亿
                } else {
                    sb.append(CN_UNITS[unit + 4]);
                }
            }
        }
        // 转换小数部分
        if (parts.length > 1) {
            String decimalPart = parts[1];
            int decimalLength = decimalPart.length();
            if (decimalLength == 1) {
                int digit = Integer.parseInt(decimalPart);
                if (digit != 0) {
                    sb.append(CN_POINT).append(CN_NUMBERS[digit]);
                }
            } else if (decimalLength == 2) {
                int digit1 = Integer.parseInt(String.valueOf(decimalPart.charAt(0)));
                int digit2 = Integer.parseInt(String.valueOf(decimalPart.charAt(1)));
                if (digit1 != 0 && digit2 != 0) {
                    sb.append(CN_POINT).append(CN_NUMBERS[digit1]).append(CN_NUMBERS[digit2]);
                } else if (digit1 != 0) {
                    sb.append(CN_POINT).append(CN_NUMBERS[digit1]);
                } else if (digit2 != 0) {
                    sb.append(CN_POINT).append(CN_NUMBERS[0]).append(CN_NUMBERS[digit2]);
                }
            }
        }
        // 如果小数部分为空，则添加"整"
        if (parts.length == 1) {
            sb.append(CN_INTEGER);
        }
        return sb.toString();
    }


    /**
     * 实现BigDecimal和String的相互转换
     */
    public static Object convert(Object input) {
        if (input instanceof BigDecimal) {
            // 如果输入是BigDecimal，则将其转换为String类型并返回
            BigDecimal decimalNumber = (BigDecimal) input;
            return decimalNumber.toString();
        } else if (input instanceof String) {
            // 如果输入是String，则将其转换为BigDecimal类型并返回
            String stringNumber = (String) input;
            return new BigDecimal(stringNumber);
        } else {
            // 如果输入的类型不是BigDecimal也不是String，则返回null或抛出异常，根据需求进行处理
            return null;
        }
    }

    /**
     * 生成随机乱码
     */
    public static String generateRandomCode(Integer length) {
        String charsA = "0123456789abcdefghijklmnopqrstuvwxyz";
        String charsB = "0123456789";
        String charsC = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";
        String charsD = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ";
        String charsE = "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ";
        Random rand = new Random();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < length; i++) {
            int index = rand.nextInt(charsB.length());
            sb.append(charsB.charAt(index));
        }
        return sb.toString();
    }

    /**
     * 发送带Body的GET请求
     *
     * @param url    请求的URL
     * @param params URL请求参数，Map格式
     * @param body   请求体的内容
     * @return 返回响应内容
     * @throws IOException 如果发生IO异常
     */
    public static String sendGet(String url, Map<String, String> params, String body) throws IOException {
        // 拼接URL，添加查询参数
        StringBuilder urlBuilder = new StringBuilder(url);
        if (params != null && !params.isEmpty()) {
            urlBuilder.append("?");
            for (Map.Entry<String, String> entry : params.entrySet()) {
                urlBuilder.append(entry.getKey()).append("=")
                        .append(URLEncoder.encode(entry.getValue(), "UTF-8")).append("&");
            }
            urlBuilder.deleteCharAt(urlBuilder.length() - 1); // 删除最后一个多余的&
        }

        // 创建HttpURLConnection连接
        URL realUrl = new URL(urlBuilder.toString());
        HttpURLConnection connection = (HttpURLConnection) realUrl.openConnection();
        connection.setRequestMethod("GET");  // 设置请求方法为GET
        connection.setRequestProperty("Content-Type", "application/json");
        connection.setRequestProperty("Connection", "Keep-Alive");
        connection.setDoOutput(true);
        connection.setDoInput(true);

        // 发送请求体
        if (body != null && !body.isEmpty()) {
            connection.getOutputStream().write(body.getBytes(StandardCharsets.UTF_8));
        }
        connection.connect();

        // 读取响应
        BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8));
        StringBuilder responseBuilder = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            responseBuilder.append(line);
        }
        reader.close();
        connection.disconnect();
        return responseBuilder.toString();  // 返回响应内容
    }

    /**
     * 发送带Body的POST请求
     *
     * @param url    请求的URL
     * @param params URL请求参数，Map格式
     * @param body   请求体的内容
     * @return 返回响应内容
     * @throws IOException 如果发生IO异常
     */
    public static String sendPost(String url, Map<String, String> params, String body) throws IOException {
        // 拼接URL，添加查询参数
        StringBuilder urlBuilder = new StringBuilder(url);
        if (params != null && !params.isEmpty()) {
            urlBuilder.append("?");
            for (Map.Entry<String, String> entry : params.entrySet()) {
                urlBuilder.append(entry.getKey()).append("=")
                        .append(URLEncoder.encode(entry.getValue(), "UTF-8")).append("&");
            }
            urlBuilder.deleteCharAt(urlBuilder.length() - 1); // 删除最后一个多余的&
        }

        // 创建HttpURLConnection连接
        URL realUrl = new URL(urlBuilder.toString());
        HttpURLConnection connection = (HttpURLConnection) realUrl.openConnection();
        connection.setRequestMethod("POST");  // 设置请求方法为POST
        connection.setRequestProperty("Content-Type", "application/json");
        connection.setRequestProperty("Connection", "Keep-Alive");
        connection.setDoOutput(true);
        connection.setDoInput(true);

        // 发送请求体
        if (body != null && !body.isEmpty()) {
            connection.getOutputStream().write(body.getBytes(StandardCharsets.UTF_8));
        }
        connection.connect();

        // 读取响应
        BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8));
        StringBuilder responseBuilder = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            responseBuilder.append(line);
        }
        reader.close();
        connection.disconnect();
        return responseBuilder.toString();  // 返回响应内容
    }


    public static Map<String, String> parseXml(InputStream inputStream) throws Exception {
        Map<String, String> map = new HashMap<>();
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder = factory.newDocumentBuilder();
        Document document = builder.parse(inputStream);
        NodeList nodeList = document.getDocumentElement().getChildNodes();

        for (int i = 0; i < nodeList.getLength(); i++) {
            Node node = nodeList.item(i);
            if (node.getNodeType() == Node.ELEMENT_NODE) {
                map.put(node.getNodeName(), node.getTextContent());
            }
        }
        return map;
    }

    /**
     * 获取时间格式，传入需要的类型，如"yyyy_MM_dd HH_mm_ss"  yyyy年MM月dd日HH时mm分ss秒  或 E 获取日期
     */
    public static String getTime(String patten) {//获取时间字符串
        Date date = new Date();
        SimpleDateFormat formatter = new SimpleDateFormat(patten);
        String time = formatter.format(date);
        return time;
    }
    public static Map<String, String> getYesterday(String time) {
        Calendar calendar = Calendar.getInstance();
        SimpleDateFormat sdfDate = new SimpleDateFormat("yyyy-MM-dd");
        SimpleDateFormat sdfDateTime = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        Date beginTime;
        Date endTime;
        try {
            if (time == null || time.trim().isEmpty()) {
                calendar.setTime(new Date());
                calendar.add(Calendar.DAY_OF_MONTH, -1);
            } else {
                Date targetDate = sdfDate.parse(time.trim());
                calendar.setTime(targetDate);
            }
            calendar.set(Calendar.HOUR_OF_DAY, 0);
            calendar.set(Calendar.MINUTE, 0);
            calendar.set(Calendar.SECOND, 0);
            calendar.set(Calendar.MILLISECOND, 0);
            beginTime = calendar.getTime();
            calendar.set(Calendar.HOUR_OF_DAY, 23);
            calendar.set(Calendar.MINUTE, 59);
            calendar.set(Calendar.SECOND, 59);
            calendar.set(Calendar.MILLISECOND, 999);
            endTime = calendar.getTime();
        } catch (Exception e) {
            throw new RuntimeException("解析时间失败: " + time, e);
        }
        Map<String, String> result = new HashMap<>();
        result.put("beginTime", sdfDateTime.format(beginTime));
        result.put("endTime", sdfDateTime.format(endTime));
        return result;
    }

    public static Map<String, Object> getPrefixAndDate(String time) {
        Calendar calendar = Calendar.getInstance();
        SimpleDateFormat sdfDate = new SimpleDateFormat("yyyy-MM-dd");
        SimpleDateFormat sdfPrefix = new SimpleDateFormat("yyyyMMdd");
        Date date;

        try {
            if (time == null || time.trim().isEmpty()) {
                // 不传 → 昨天
                calendar.setTime(new Date());
                calendar.add(Calendar.DAY_OF_MONTH, -1);
            } else {
                // 传了 → 指定日期
                Date targetDate = sdfDate.parse(time.trim());
                calendar.setTime(targetDate);
            }

            // 设置时分秒
            calendar.set(Calendar.HOUR_OF_DAY, 0);
            calendar.set(Calendar.MINUTE, 0);
            calendar.set(Calendar.SECOND, 0);
            calendar.set(Calendar.MILLISECOND, 0);

            date = calendar.getTime();
        } catch (Exception e) {
            throw new RuntimeException("解析时间失败: " + time, e);
        }

        String prefix = sdfPrefix.format(date);

        Map<String, Object> result = new HashMap<String, Object>();
        result.put("prefix", prefix);
        result.put("date", date);

        return result;
    }

}
