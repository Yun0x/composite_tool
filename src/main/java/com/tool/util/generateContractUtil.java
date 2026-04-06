package com.tool.util;

import net.coobird.thumbnailator.Thumbnails;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.InputStream;

public class generateContractUtil {

    public static void generateContract(String type, String name, String personName, String receiverNo) {
        switch (type) {
            case "58"://1对私
                generatePrivateContract(name, receiverNo);
                break;
            case "57"://对公
                generatePublicContract(name, personName, receiverNo);
                break;
        }
    }


    public static void generatePrivateContract(String name, String receiverNo) {
        try {
            // 1. 读取模板图片
            InputStream imgStream = Thread.currentThread()
                    .getContextClassLoader()
                    .getResourceAsStream("lakala/template/privateAgreement.png");

            BufferedImage image = ImageIO.read(imgStream);

            String[] fonts = {
                    "lakala/template/AaBuYaoYiFoYaoNiHuanXi-2.ttf",
                    "lakala/template/AiWoZheYuWoLaoLong-2.ttf",
                    "lakala/template/DingDingWoYiQingChenTi-2.ttf",
                    "lakala/template/font1.ttf",
                    "lakala/template/font2.ttf",
                    "lakala/template/font4.ttf",
                    "lakala/template/HanChengQingFengYue-2.ttf",
                    "lakala/template/HanChengShiHeYuanFangShouShu-2.ttf",
                    "lakala/template/NaiBuErXingYueTi-2.ttf",
                    "lakala/template/NaiBuErXiShouXie-2.ttf",
                    "lakala/template/SanJiWangShuKaiShu-2.ttf",
                    "lakala/template/ShenHaiBuJiNiXin-2.ttf",
                    "lakala/template/XingChenRuoMeng-2.ttf",
                    "lakala/template/YeZiGongChangQingMeiShouJi-2.ttf",
                    "lakala/template/YeZiGongChangShouJi-2.ttf",
                    "lakala/template/YeZiGongChangWeiFengShouJi-2.ttf",
                    "lakala/template/YiRenQianXiaoZuiQingCheng-2.ttf",
                    "lakala/template/ZiHun104Hao-ShuXinTi-2.ttf",
                    "lakala/template/ZiHun78Hao-KongLingTi-2.ttf",
                    "lakala/template/ZiHun83Hao-LanTi-2.ttf",
                    "lakala/template/ZiHun85Hao-QingYunShouZhaTi-2.ttf",
                    "lakala/template/字小魂清风体.ttf"
            };
            String[] fingers = {
                    "lakala/template/finger1.png",
                    "lakala/template/finger2.png",
                    "lakala/template/finger3.png"
            };

            int index = (int) (Math.random() * fonts.length);
            int index2 = (int) (Math.random() * fingers.length);

            String fontPath = fonts[index];
            String fingerPath = fingers[index2];

            InputStream fontStream = Thread.currentThread()
                    .getContextClassLoader()
                    .getResourceAsStream(fontPath);

            InputStream fontStream2 = Thread.currentThread()
                    .getContextClassLoader()
                    .getResourceAsStream("lakala/template/font0.ttf");

            String time = SpringUtils.getTime("yyyy-MM-dd-01");
            String[] split = time.split("-");

            // 坐标
            int signX = 1488, signY = 2760;
            int printNameX = 595, printNameY = 930;
            int infoDateX = 818, infoDateY = 1610;
            int signDateX1 = 545, signDateY1 = 2895;
            int signDateX2 = 1390, signDateY2 = 2895;

            int fingerX = signX + 60 + (index2 * 45);
            int fingerY = (int) (signY - 100 + (index2 - 1) * 18.548);
            int fingerWidth = 120;
            int fingerHeight = (int) (150 + ((index2 - 1) * 14.336));

            // 字体
            Font font = Font.createFont(Font.TRUETYPE_FONT, fontStream).deriveFont(Font.PLAIN, 140);
            Font font2 = Font.createFont(Font.TRUETYPE_FONT, fontStream2).deriveFont(Font.PLAIN, 42);

            Graphics2D g = image.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

            // 签名（模拟手写）
            g.setFont(font);
            g.setColor(new Color(50, 50, 50));

            double angle = (Math.random() - 0.5) * 0.08;
            g.rotate(angle, signX, signY);

            int currentX = signX;
            for (int i = 0; i < name.length(); i++) {
                String ch = String.valueOf(name.charAt(i));
                int offsetY = signY + (int) (Math.random() * 8 - 4);
                g.drawString(ch, currentX, offsetY);
                currentX += 110 + (int) (Math.random() * 10);
            }

            g.rotate(-angle, signX, signY);
            g.setColor(Color.BLACK);
            g.setFont(font2);
            g.drawString(name, printNameX, printNameY);
            g.drawString(split[0], infoDateX, infoDateY);
            g.drawString(split[1], infoDateX + 170, infoDateY);
            g.drawString(split[3], infoDateX + 300, infoDateY);
            g.drawString(String.valueOf((Integer.parseInt(split[0]) + 2)), infoDateX + 530, infoDateY);
            g.drawString(split[1], infoDateX + 710, infoDateY);
            g.drawString(split[3], infoDateX + 855, infoDateY);
            g.drawString(split[0], signDateX1, signDateY1);
            g.drawString(split[1], signDateX1 + 190, signDateY1);
            g.drawString(split[2], signDateX1 + 340, signDateY1);
            g.drawString(split[0], signDateX2, signDateY2);
            g.drawString(split[1], signDateX2 + 185, signDateY2);
            g.drawString(split[2], signDateX2 + 310, signDateY2);

            // 手印
            InputStream fingerStream = Thread.currentThread()
                    .getContextClassLoader()
                    .getResourceAsStream(fingerPath);

            BufferedImage originalFingerImg = ImageIO.read(fingerStream);
            BufferedImage brokenFingerImg = new BufferedImage(
                    originalFingerImg.getWidth(),
                    originalFingerImg.getHeight(),
                    BufferedImage.TYPE_INT_ARGB
            );

            for (int y = 0; y < originalFingerImg.getHeight(); y++) {
                for (int x = 0; x < originalFingerImg.getWidth(); x++) {
                    int argb = originalFingerImg.getRGB(x, y);
                    brokenFingerImg.setRGB(x, y, Math.random() > 0.35 ? argb : 0x00000000);
                }
            }

            double angle2 = Math.toRadians((Math.random() - 0.5) * 90);

            g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.5f));
            int centerX = fingerX + fingerWidth / 2;
            int centerY = fingerY + fingerHeight / 2;

            g.rotate(angle2, centerX, centerY);
            g.drawImage(brokenFingerImg, fingerX, fingerY, fingerWidth, fingerHeight, null);
            g.rotate(-angle2, centerX, centerY);

            g.dispose();
            BufferedImage rgbImage = new BufferedImage(
                    image.getWidth(),
                    image.getHeight(),
                    BufferedImage.TYPE_INT_RGB
            );

            Graphics2D rgbG = rgbImage.createGraphics();
            rgbG.drawImage(image, 0, 0, Color.WHITE, null);
            rgbG.dispose();
            String dirPath = "D:\\contract";

//            String dirPath = "/usr/apache-tomcat-8.0.51/webapps/ROOT/file/contract/";
            File dir = new File(dirPath);
            if (!dir.exists()) dir.mkdirs();

            File outFile = new File(dir, name + receiverNo + ".jpg");
            float quality = 0.4f;
            int width = 1000;

            while (true) {
                Thumbnails.of(rgbImage)
                        .size(width, width)
                        .outputFormat("jpg")
                        .outputQuality(quality)
                        .toFile(outFile);

                long size = outFile.length();

                if (size <= 100 * 1024) {
                    break;
                }
                if (quality > 0.15f) {
                    quality -= 0.05f;
                } else {
                    width -= 100;
                }
                if (width <= 400) break;
            }

            System.out.println("成功: " + outFile.getAbsolutePath() + " 大小：" + outFile.length() / 1024 + "KB");

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void generatePublicContract(String publicName, String personName, String receiverNo) {
        try {
            // 1. 读取模板图片
            InputStream imgStream = Thread.currentThread()
                    .getContextClassLoader()
                    .getResourceAsStream("lakala/template/publicAgreement.png");

            BufferedImage image = ImageIO.read(imgStream);

            String[] fonts = {
                    "lakala/template/AaBuYaoYiFoYaoNiHuanXi-2.ttf",
                    "lakala/template/AiWoZheYuWoLaoLong-2.ttf",
                    "lakala/template/DingDingWoYiQingChenTi-2.ttf",
                    "lakala/template/font1.ttf",
                    "lakala/template/font2.ttf",
                    "lakala/template/font4.ttf",
                    "lakala/template/HanChengQingFengYue-2.ttf",
                    "lakala/template/HanChengShiHeYuanFangShouShu-2.ttf",
                    "lakala/template/NaiBuErXingYueTi-2.ttf",
                    "lakala/template/NaiBuErXiShouXie-2.ttf",
                    "lakala/template/SanJiWangShuKaiShu-2.ttf",
                    "lakala/template/ShenHaiBuJiNiXin-2.ttf",
                    "lakala/template/XingChenRuoMeng-2.ttf",
                    "lakala/template/YeZiGongChangQingMeiShouJi-2.ttf",
                    "lakala/template/YeZiGongChangShouJi-2.ttf",
                    "lakala/template/YeZiGongChangWeiFengShouJi-2.ttf",
                    "lakala/template/YiRenQianXiaoZuiQingCheng-2.ttf",
                    "lakala/template/ZiHun104Hao-ShuXinTi-2.ttf",
                    "lakala/template/ZiHun78Hao-KongLingTi-2.ttf",
                    "lakala/template/ZiHun83Hao-LanTi-2.ttf",
                    "lakala/template/ZiHun85Hao-QingYunShouZhaTi-2.ttf",
                    "lakala/template/字小魂清风体.ttf"
            };
            String[] fingers = {
                    "lakala/template/finger1.png",
                    "lakala/template/finger2.png",
                    "lakala/template/finger3.png"
            };

            int index = (int) (Math.random() * fonts.length);
            int index2 = (int) (Math.random() * fingers.length);

            String fontPath = fonts[index];
            String fingerPath = fingers[index2];

            InputStream fontStream = Thread.currentThread()
                    .getContextClassLoader()
                    .getResourceAsStream(fontPath);

            InputStream fontStream2 = Thread.currentThread()
                    .getContextClassLoader()
                    .getResourceAsStream("lakala/template/font0.ttf");

            String time = SpringUtils.getTime("yyyy-MM-dd-01");
            String[] split = time.split("-");

            // 坐标
            int signX = 1397, signY = 2765;
            int printNameX = 595, printNameY = 930;
            int printNameX2 = 1380, printNameY2 = 2584;
            int infoDateX = 818, infoDateY = 1610;
            int signDateX1 = 545, signDateY1 = 2895;
            int signDateX2 = 1380, signDateY2 = 2895;

            int fingerX = signX + 60 + (index2 * 45);
            int fingerY = (int) (signY - 100 + (index2 - 1) * 18.548);
            int fingerWidth = 120;
            int fingerHeight = (int) (150 + ((index2 - 1) * 14.336));

            // 字体
            Font font = Font.createFont(Font.TRUETYPE_FONT, fontStream).deriveFont(Font.PLAIN, 140);
            Font font2 = Font.createFont(Font.TRUETYPE_FONT, fontStream2).deriveFont(Font.PLAIN, 42);

            Graphics2D g = image.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

            // 签名（模拟手写）
            g.setFont(font);
            g.setColor(new Color(50, 50, 50));

            double angle = (Math.random() - 0.5) * 0.08;
            g.rotate(angle, signX, signY);

            int currentX = signX;
            for (int i = 0; i < personName.length(); i++) {
                String ch = String.valueOf(personName.charAt(i));
                int offsetY = signY + (int) (Math.random() * 8 - 4);
                g.drawString(ch, currentX, offsetY);
                currentX += 110 + (int) (Math.random() * 10);
            }

            g.rotate(-angle, signX, signY);
            g.setColor(Color.BLACK);
            g.setFont(font2);
            g.drawString(publicName, printNameX, printNameY);
            g.drawString(publicName, printNameX2, printNameY2);
            g.drawString(split[0], infoDateX, infoDateY);
            g.drawString(split[1], infoDateX + 170, infoDateY);
            g.drawString(split[3], infoDateX + 300, infoDateY);
            g.drawString(String.valueOf((Integer.parseInt(split[0]) + 2)), infoDateX + 530, infoDateY);
            g.drawString(split[1], infoDateX + 710, infoDateY);
            g.drawString(split[3], infoDateX + 855, infoDateY);
            g.drawString(split[0], signDateX1, signDateY1);
            g.drawString(split[1], signDateX1 + 190, signDateY1);
            g.drawString(split[2], signDateX1 + 340, signDateY1);
            g.drawString(split[0], signDateX2, signDateY2);
            g.drawString(split[1], signDateX2 + 185, signDateY2);
            g.drawString(split[2], signDateX2 + 310, signDateY2);

            // 手印
            InputStream fingerStream = Thread.currentThread()
                    .getContextClassLoader()
                    .getResourceAsStream(fingerPath);

            BufferedImage originalFingerImg = ImageIO.read(fingerStream);
            BufferedImage brokenFingerImg = new BufferedImage(
                    originalFingerImg.getWidth(),
                    originalFingerImg.getHeight(),
                    BufferedImage.TYPE_INT_ARGB
            );

            for (int y = 0; y < originalFingerImg.getHeight(); y++) {
                for (int x = 0; x < originalFingerImg.getWidth(); x++) {
                    int argb = originalFingerImg.getRGB(x, y);
                    brokenFingerImg.setRGB(x, y, Math.random() > 0.35 ? argb : 0x00000000);
                }
            }

            double angle2 = Math.toRadians((Math.random() - 0.5) * 90);

            g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.5f));
            int centerX = fingerX + fingerWidth / 2;
            int centerY = fingerY + fingerHeight / 2;

            g.rotate(angle2, centerX, centerY);
            g.drawImage(brokenFingerImg, fingerX, fingerY, fingerWidth, fingerHeight, null);
            g.rotate(-angle2, centerX, centerY);

            g.dispose();
            BufferedImage rgbImage = new BufferedImage(
                    image.getWidth(),
                    image.getHeight(),
                    BufferedImage.TYPE_INT_RGB
            );

            Graphics2D rgbG = rgbImage.createGraphics();
            rgbG.drawImage(image, 0, 0, Color.WHITE, null);
            rgbG.dispose();
            String dirPath = "D:\\contract";
            File dir = new File(dirPath);
            if (!dir.exists()) dir.mkdirs();

            File outFile = new File(dir, publicName + receiverNo + ".jpg");
            float quality = 0.4f;
            int width = 1000;

            while (true) {
                Thumbnails.of(rgbImage)
                        .size(width, width)
                        .outputFormat("jpg")
                        .outputQuality(quality)
                        .toFile(outFile);

                long size = outFile.length();

                if (size <= 100 * 1024) {
                    break;
                }
                if (quality > 0.15f) {
                    quality -= 0.05f;
                } else {
                    width -= 100;
                }
                if (width <= 400) break;
            }

            System.out.println("成功: " + outFile.getAbsolutePath() + " 大小：" + outFile.length() / 1024 + "KB");

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {
//        String names = "于天龙\n" +
//                "李秀秀\n" +
//                "朱广勋\n" +
//                "潘嶓\n" +
//                "张花莹\n" +
//                "许鑫钊\n" +
//                "郑吉祥";
//        List<String> list = Arrays.asList(names.split("\n"));
//        for (String name : list) {
//            generatePrivateContract(name, "");
//        }
        generatePublicContract("恩施跃升游乐有限公司", "法人签名", "12313131312");

    }

}
