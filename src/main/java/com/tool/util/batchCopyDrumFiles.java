package com.tool.util;

import java.io.IOException;
import java.nio.file.*;
import java.util.stream.Stream;

public class batchCopyDrumFiles {

    public static void main(String[] args) {
        Path sourceDir = Paths.get("C:\\Users\\Admin\\Desktop\\TestSong\\正式歌曲\\result");
        Path targetDir = Paths.get("E:\\");

        if (!Files.exists(sourceDir) || !Files.isDirectory(sourceDir)) {
            System.err.println("源目录不存在：" + sourceDir);
            return;
        }

        try (Stream<Path> paths = Files.list(sourceDir)) { // 只扫描当前目录
            paths
                    .filter(Files::isRegularFile)
                    .filter(path -> {
                        String fileName = path.getFileName().toString().toLowerCase();
                        return fileName.endsWith(".mp3") || fileName.endsWith(".bin");
                    })
                    .forEach(path -> {
                        String fileName = path.getFileName().toString();
                        String targetFileName;

                        if (fileName.toLowerCase().endsWith(".mp3")) {
                            int dotIndex = fileName.lastIndexOf('.');
                            targetFileName = fileName.substring(0, dotIndex) + "_" + fileName.substring(dotIndex);
                        } else {
                            targetFileName = fileName;
                        }
                        Path targetPath = targetDir.resolve(targetFileName);

                        try {
                            Files.copy(
                                    path,
                                    targetPath,
                                    StandardCopyOption.REPLACE_EXISTING
                            );
                            System.out.println("已复制: " + fileName + " -> " + targetFileName);
                        } catch (IOException e) {
                            System.err.println("复制失败: " + fileName);
                            e.printStackTrace();
                        }
                    });
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
