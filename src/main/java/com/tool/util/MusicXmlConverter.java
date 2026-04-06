package com.tool.util;

import com.tool.vo.DrumInfo;
import org.springframework.web.multipart.MultipartFile;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.BufferedReader;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;

public class MusicXmlConverter {
    private static final Map<String, Integer> DRUM_MAPPING = new HashMap<>();

    static {
        DRUM_MAPPING.put("G5", 1);   // HH(开镲)
        DRUM_MAPPING.put("A5 x", 1);        // CY(Crash Cymabl)
        DRUM_MAPPING.put("C5", 4);   // SD(Snare Drum)
        DRUM_MAPPING.put("C5 x", 4);        // SD(Snare Drum)
        DRUM_MAPPING.put("B4", 4);   // SD(Snare Drum)
        DRUM_MAPPING.put("E5", 8);   // HT(High Tom/Tom1)
        DRUM_MAPPING.put("F5 x", 2);       // LC(Ride Cymbal)
        DRUM_MAPPING.put("B5 x", 16);       // LP(Crash1)
        DRUM_MAPPING.put("D5", 64);  // LT(Low Tom/Tom2)
        DRUM_MAPPING.put("A4", 128); // FT(Floor Tom/Tom3)
        DRUM_MAPPING.put("D4 x", 256);      // ED(Bass Drum)
        DRUM_MAPPING.put("F4", 512); // ED2(Bass Drum)
        DRUM_MAPPING.put("G5 x", 257);         //闭镲（HH+FI)
        DRUM_MAPPING.put("E4", 512); // 闭镲（HH+FI)
        DRUM_MAPPING.put("C6 x", 16);       // 水镲，但是算做LC
        DRUM_MAPPING.put("B5", 32);  // China Cymbal 算作LP
        DRUM_MAPPING.put("G4", 128);  // China Cymbal 算作LP
        DRUM_MAPPING.put("F5", 8);  // China Cymbal 算作LP
    }

    // 内部类，用于存储音符事件并支持排序
    private static class NoteEvent implements Comparable<NoteEvent> {
        String key;
        long beginTime;

        public NoteEvent(String key, long beginTime) {
            this.key = key;
            this.beginTime = beginTime;
        }

        @Override
        public int compareTo(NoteEvent other) {
            return Long.compare(this.beginTime, other.beginTime);
        }
    }

    /**
     * 将 MusicXML 转换为 JSON 的静态方法
     *
     * @param xmlFilePath    输入的 XML 文件路径
     * @param outputJsonPath 输出的 JSON 文件路径
     * @return 转换成功返回 true，出现异常返回 false
     */
    public static boolean parseMusicXmlToJson(String xmlFilePath, String outputJsonPath) {
        try {
            File xmlFile = new File(xmlFilePath);
            if (!xmlFile.exists()) {
                System.err.println("找不到文件: " + xmlFilePath);
                return false;
            }
            DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
            dbFactory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", false);
            dbFactory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
            DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
            Document doc = dBuilder.parse(xmlFile);
            doc.getDocumentElement().normalize();
            // 初始默认状态
            int divisions = 1;
            double bpm = 120.0;
            int beats = 4;
            int beatType = 4;

            long currentDiv = 0;
            long prevDur = 0;

            List<NoteEvent> events = new ArrayList<>();

            NodeList measureNodes = doc.getElementsByTagName("measure");

            // 遍历所有小节
            for (int i = 0; i < measureNodes.getLength(); i++) {
                Node measureNode = measureNodes.item(i);
                if (measureNode.getNodeType() != Node.ELEMENT_NODE) continue;

                Element measure = (Element) measureNode;
                long measureStartDiv = currentDiv;
                long measureMaxDiv = currentDiv;
                // 预先检查本小节是否有 attributes
                Element attr = getChildElement(measure, "attributes");
                if (attr != null) {
                    Element divElem = getChildElement(attr, "divisions");
                    if (divElem != null) divisions = Integer.parseInt(divElem.getTextContent().trim());
                    Element timeElem = getChildElement(attr, "time");
                    if (timeElem != null) {
                        Element beatsElem = getChildElement(timeElem, "beats");
                        Element beatTypeElem = getChildElement(timeElem, "beat-type");
                        if (beatsElem != null && beatTypeElem != null) {
                            beats = Integer.parseInt(beatsElem.getTextContent().trim());
                            beatType = Integer.parseInt(beatTypeElem.getTextContent().trim());
                        }
                    }
                }
                // 计算当前小节理论上的总 division 长度
                int expectedMeasureDur = (int) ((beats * 4.0 / beatType) * divisions);
                NodeList children = measure.getChildNodes();
                for (int j = 0; j < children.getLength(); j++) {
                    Node childNode = children.item(j);
                    if (childNode.getNodeType() != Node.ELEMENT_NODE) continue;

                    Element elem = (Element) childNode;
                    String tag = elem.getNodeName();

                    // 抓取 BPM
                    if (tag.equals("direction")) {
                        NodeList perMinuteNodes = elem.getElementsByTagName("per-minute");
                        if (perMinuteNodes.getLength() > 0) {
                            bpm = Double.parseDouble(perMinuteNodes.item(0).getTextContent().trim());
                        }
                    }
                    // 处理音符
                    else if (tag.equals("note")) {
                        boolean isChord = getChildElement(elem, "chord") != null;
                        Element durElem = getChildElement(elem, "duration");
                        long dur = durElem != null ? Long.parseLong(durElem.getTextContent().trim()) : 0;

                        if (isChord) {
                            currentDiv -= prevDur;
                        }

                        // 如果不是休止符
                        if (getChildElement(elem, "rest") == null) {
                            Element pitchElem = getChildElement(elem, "pitch");
                            if (pitchElem != null) {
                                String step = getChildElementText(pitchElem, "step");
                                String octave = getChildElementText(pitchElem, "octave");
                                Element alterElem = getChildElement(pitchElem, "alter");

                                String alter = "";
                                if (alterElem != null) {
                                    int alterVal = Integer.parseInt(alterElem.getTextContent().trim());
                                    if (alterVal == 1) alter = "#";
                                    else if (alterVal == -1) alter = "b";
                                }

                                String key = step + alter + octave;

                                Element noteheadElem = getChildElement(elem, "notehead");
                                if (noteheadElem != null && "x".equals(noteheadElem.getTextContent().trim())) {
                                    key += " x";
                                }

                                double msPerDiv = 60000.0 / (bpm * divisions);
                                long beginTimeMs = Math.round(currentDiv * msPerDiv);

                                events.add(new NoteEvent(key, beginTimeMs));
                            }
                        }

                        currentDiv += dur;
                        prevDur = dur;
                        measureMaxDiv = Math.max(measureMaxDiv, currentDiv);
                    }
                    // 多声部回退
                    else if (tag.equals("backup")) {
                        Element durElem = getChildElement(elem, "duration");
                        if (durElem != null) {
                            currentDiv -= Long.parseLong(durElem.getTextContent().trim());
                        }
                    }
                    // 多声部前进
                    else if (tag.equals("forward")) {
                        Element durElem = getChildElement(elem, "duration");
                        if (durElem != null) {
                            currentDiv += Long.parseLong(durElem.getTextContent().trim());
                            measureMaxDiv = Math.max(measureMaxDiv, currentDiv);
                        }
                    }
                }

                // 小节结束处理：强制对其到下一小节起点
                currentDiv = measureStartDiv + expectedMeasureDur;
            }

            // 按时间排序
            Collections.sort(events);
            // 构建 JSON 字符串 (手动构建以避免引入第三方依赖，且保持与 Python 相同的 4 空格缩进)
            StringBuilder jsonBuilder = new StringBuilder();
            jsonBuilder.append("[\n");
            for (int i = 0; i < events.size(); i++) {
                NoteEvent event = events.get(i);
                jsonBuilder.append("    {\n");
                jsonBuilder.append("        \"key\": \"").append(event.key).append("\",\n");
                jsonBuilder.append("        \"beginTime\": ").append(event.beginTime).append("\n");
                jsonBuilder.append("    }");
                if (i < events.size() - 1) {
                    jsonBuilder.append(",");
                }
                jsonBuilder.append("\n");
            }
            jsonBuilder.append("]");
            // 写入文件
            Files.write(Paths.get(outputJsonPath), jsonBuilder.toString().getBytes(StandardCharsets.UTF_8));
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }


    // 辅助方法：获取直属子元素（模拟 Python 中 elem.find() 只找第一层子节点的行为）
    private static Element getChildElement(Element parent, String tagName) {
        if (parent == null) return null;
        NodeList children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE && child.getNodeName().equals(tagName)) {
                return (Element) child;
            }
        }
        return null;
    }

    // 辅助方法：获取直属子元素的文本
    private static String getChildElementText(Element parent, String tagName) {
        Element child = getChildElement(parent, tagName);
        return child != null ? child.getTextContent().trim() : "";
    }

    // 测试主函数
    public static void main(String[] args) {
        String inputPath = "D:\\Downloads\\S00032_千千阙歌\\S00032.xml";
        String outputPath = "D:\\Downloads\\S00032_千千阙歌\\convert.json";

        boolean success = parseMusicXmlToJson(inputPath, outputPath);
        System.out.println("转换" + (success ? "成功!" : "失败!"));
    }


    /**
     * 将上传的 MusicXML 文件解析为 DrumInfo 列表
     * * @param file 传入的 MultipartFile
     * @return DrumInfo 列表，解析失败或为空则返回空列表
     */
    public static List<DrumInfo> parseMusicXmlToInitList(MultipartFile file) {
        List<DrumInfo> drumInfoList = new ArrayList<>();

        if (file == null || file.isEmpty()) {
            System.err.println("上传的文件为空");
            return drumInfoList;
        }

        try (InputStream is = file.getInputStream()) {
            DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
            dbFactory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", false);
            dbFactory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);

            DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
            Document doc = dBuilder.parse(is);
            doc.getDocumentElement().normalize();

            // 关键修正 1：先获取 part，按音轨解析，防止多音轨时时间互相叠加
            NodeList partNodes = doc.getElementsByTagName("part");

            for (int p = 0; p < partNodes.getLength(); p++) {
                Node partNode = partNodes.item(p);
                if (partNode.getNodeType() != Node.ELEMENT_NODE) continue;
                Element partElem = (Element) partNode;

                int divisions = 1;
                double bpm = 120.0;
                int beats = 4;
                int beatType = 4;
                long currentDiv = 0;
                long prevDur = 0;

                // 关键修正 2：用双精度浮点数记录当前小节起点的绝对时间 (毫秒)
                double measureStartMs = 0.0;

                List<NoteEvent> events = new ArrayList<>();
                NodeList measureNodes = partElem.getElementsByTagName("measure");

                for (int i = 0; i < measureNodes.getLength(); i++) {
                    Node measureNode = measureNodes.item(i);
                    if (measureNode.getNodeType() != Node.ELEMENT_NODE) continue;

                    Element measure = (Element) measureNode;
                    long measureStartDiv = currentDiv;

                    Element attr = getChildElement(measure, "attributes");
                    if (attr != null) {
                        Element divElem = getChildElement(attr, "divisions");
                        if (divElem != null) divisions = Integer.parseInt(divElem.getTextContent().trim());
                        Element timeElem = getChildElement(attr, "time");
                        if (timeElem != null) {
                            Element beatsElem = getChildElement(timeElem, "beats");
                            Element beatTypeElem = getChildElement(timeElem, "beat-type");
                            if (beatsElem != null && beatTypeElem != null) {
                                beats = Integer.parseInt(beatsElem.getTextContent().trim());
                                beatType = Integer.parseInt(beatTypeElem.getTextContent().trim());
                            }
                        }
                    }

                    // 计算本小节理论上的总 divisions
                    int expectedMeasureDur = (int) ((beats * 4.0 / beatType) * divisions);
                    double msPerDiv = 60000.0 / (bpm * divisions);

                    NodeList children = measure.getChildNodes();

                    for (int j = 0; j < children.getLength(); j++) {
                        Node childNode = children.item(j);
                        if (childNode.getNodeType() != Node.ELEMENT_NODE) continue;

                        Element elem = (Element) childNode;
                        String tag = elem.getNodeName();

                        if (tag.equals("direction")) {
                            NodeList perMinuteNodes = elem.getElementsByTagName("per-minute");
                            if (perMinuteNodes.getLength() > 0) {
                                bpm = Double.parseDouble(perMinuteNodes.item(0).getTextContent().trim());
                                // 更新后续音符的计算比率
                                msPerDiv = 60000.0 / (bpm * divisions);
                            }
                        } else if (tag.equals("note")) {
                            boolean isChord = getChildElement(elem, "chord") != null;
                            Element durElem = getChildElement(elem, "duration");
                            long dur = durElem != null ? Long.parseLong(durElem.getTextContent().trim()) : 0;

                            if (isChord) {
                                currentDiv -= prevDur;
                            }

                            if (getChildElement(elem, "rest") == null) {
                                Element pitchElem = getChildElement(elem, "pitch");
                                if (pitchElem != null) {
                                    String step = getChildElementText(pitchElem, "step");
                                    String octave = getChildElementText(pitchElem, "octave");
                                    Element alterElem = getChildElement(pitchElem, "alter");
                                    String alter = "";
                                    if (alterElem != null) {
                                        int alterVal = Integer.parseInt(alterElem.getTextContent().trim());
                                        if (alterVal == 1) alter = "#";
                                        else if (alterVal == -1) alter = "b";
                                    }
                                    String key = step + alter + octave;
                                    Element noteheadElem = getChildElement(elem, "notehead");
                                    if (noteheadElem != null && "x".equals(noteheadElem.getTextContent().trim())) {
                                        key += " x";
                                    }

                                    // 关键修正 3：基于当前小节起点的相对偏移量来计算绝对时间
                                    long offsetDiv = currentDiv - measureStartDiv;
                                    long beginTimeMs = Math.round(measureStartMs + (offsetDiv * msPerDiv));

                                    events.add(new NoteEvent(key, beginTimeMs));
                                }
                            }
                            currentDiv += dur;
                            prevDur = dur;
                        } else if (tag.equals("backup")) {
                            Element durElem = getChildElement(elem, "duration");
                            if (durElem != null) currentDiv -= Long.parseLong(durElem.getTextContent().trim());
                        } else if (tag.equals("forward")) {
                            Element durElem = getChildElement(elem, "duration");
                            if (durElem != null) currentDiv += Long.parseLong(durElem.getTextContent().trim());
                        }
                    }

                    // 关键修正 4：处理完一个小节后，让时间和刻度强行推进一个标准小节的长度
                    // 这样无论谱面里的 Voice 怎么写、有没有写错，下一个小节的起点永远是准确的
                    measureStartMs += expectedMeasureDur * msPerDiv;
                    currentDiv = measureStartDiv + expectedMeasureDur;
                }

                Collections.sort(events);

                for (NoteEvent event : events) {
                    DrumInfo drumInfo = new DrumInfo();
                    // 增加空指针保护
                    if (DRUM_MAPPING != null && DRUM_MAPPING.containsKey(event.key)) {
                        drumInfo.setKey(DRUM_MAPPING.get(event.key));
                    } else {
                        drumInfo.setKey(null);
                    }
                    drumInfo.setBeginTime(BigDecimal.valueOf(event.beginTime));
                    drumInfo.setEndTime(BigDecimal.valueOf(event.beginTime + 50));
                    drumInfoList.add(drumInfo);
                }
            } // 结束 part 的循环

        } catch (Exception e) {
            e.printStackTrace();
        }
        return drumInfoList;
    }

    public static String  runPythonParse(String outputDir,Integer delayTime) {
        try {
            ProcessBuilder pb = new ProcessBuilder(
                    "python",
                    "-Xutf8",
                    "D:\\Code\\python\\musicXml\\adv_xml2Json.py",
                    outputDir,String.valueOf(delayTime == null ? 0 : delayTime)
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
                    jsonPath = line.replace("SUCCESS:", "");
                }
            }

            int exitCode = process.waitFor();
            if (exitCode != 0) {
                throw new RuntimeException("Python执行失败");
            }
            return jsonPath;

        } catch (Exception e) {
            throw new RuntimeException("调用Python失败", e);
        }
    }
}

