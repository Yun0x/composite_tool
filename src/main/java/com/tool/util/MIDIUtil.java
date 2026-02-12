package com.tool.util;

import javax.sound.midi.*;

public class MIDIUtil {
//    public static void main(String[] args) {
//        System.out.println("<?xml version=\"1.0\" encoding=\"utf-8\"?>");
//        System.out.println("<DTXC_MIDI转换设置 xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" xmlns:xsd=\"http://www.w3.org/2001/XMLSchema\">");
//        System.out.println("  <分配>");
//
//        for (int i = 127; i >= 0; i--) {
//            int remainder = i % 12;
//            String channel = "";
//            String reverse = "false";
//            String comment = "";
//
//            // 根据音名余数分配 5鼓5镲
//            switch (remainder) {
//                case 0:  channel = "BD"; comment = "底鼓 (C)"; break;
//                case 2:  channel = "SD"; comment = "军鼓 (D)"; break;
//                case 4:  channel = "HT"; comment = "高音嗵 (E)"; break;
//                case 5:  channel = "LT"; comment = "中音嗵 (F)"; break;
//                case 7:  channel = "FT"; comment = "落地嗵 (G)"; break;
//                case 9:  channel = "HH"; comment = "闭嚓 (A)"; break;
//                case 10: channel = "HH"; reverse = "true"; comment = "开嚓 (A#)"; break;
//                case 11: channel = "CY"; reverse = "true"; comment = "叮叮镲 (B)"; break;
//                case 1:  channel = "LC"; comment = "吊镲1 (C#)"; break;
//                case 3:  channel = "CY"; comment = "吊镲2 (D#)"; break;
//                case 6:  channel = "SD"; comment = "副军鼓 (F#)"; break;
//                case 8:  channel = "HH"; reverse = "true"; comment = "开嚓 (G#)"; break;
//                default: channel = "* 禁用 *"; break;
//            }
//
//            System.out.println("    <DTXC_MIDI转换设置_分配>");
//            System.out.println("      <MIDI_键值>" + i + "</MIDI_键值>");
//            System.out.println("      <DTX_通道>" + channel + "</DTX_通道>");
//            System.out.println("      <反向通道>" + reverse + "</反向通道>");
//            System.out.println("      <注释>" + comment + "</注释>");
//            System.out.println("    </DTXC_MIDI转换设置_分配>");
//        }
//
//        System.out.println("  </分配>");
//        System.out.println("  <其他>");
//        System.out.println("    <力度最大值127>true</力度最大值127>");
//        System.out.println("    <力度曲线调整>true</力度曲线调整>");
//        System.out.println("    <DTX音量>15</DTX音量>");
//        System.out.println("  </其他>");
//        System.out.println("</DTXC_MIDI转换设置>");
//    }

    public static void main(String[] args) throws Exception {
        // 替换为你文件的实际路径
        java.io.File midiFile = new java.io.File("D:\\Downloads\\大东北我的家乡.mid");
        Sequence sequence = MidiSystem.getSequence(midiFile);

        // 获取所有轨道
        for (Track track : sequence.getTracks()) {
            for (int i = 0; i < track.size(); i++) {
                MidiEvent event = track.get(i);
                MidiMessage message = event.getMessage();
                if (message instanceof MetaMessage) {
                    MetaMessage mm = (MetaMessage) message;
                    // 类型 0x51 是速度标记 (Tempo)
                    if (mm.getType() == 0x51) {
                        byte[] data = mm.getData();
                        // MIDI 速度存储为：24位微秒/四分音符
                        int mspq = ((data[0] & 0xff) << 16) | ((data[1] & 0xff) << 8) | (data[2] & 0xff);
                        int bpm = 60000000 / mspq;
                        System.out.println("检测到 BPM: " + bpm);
                    }
                }
            }
        }
    }
}
