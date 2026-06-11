package com.xhs.service.storage;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.FilterInputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

/**
 * 本地文件系统存储实现（默认）。
 */
@Slf4j
@Service
@ConditionalOnProperty(name = "xiaohongshu.storage.type", havingValue = "local", matchIfMissing = true)
public class LocalStorageService implements FileStorageService {

    @Value("${xiaohongshu.upload.image-dir}")
    private String uploadDir;

    @Override
    public String upload(String objectName, InputStream inputStream, long size) {
        try {
            Path root = Paths.get(uploadDir).toAbsolutePath().normalize();
            String normalized = objectName.replace("\\", "/");
            while (normalized.startsWith("/")) {
                normalized = normalized.substring(1);
            }
            Path target = root.resolve(normalized).normalize();
            if (!target.startsWith(root)) {
                throw new IllegalArgumentException("非法文件路径");
            }
            Files.createDirectories(target.getParent());
            Files.copy(inputStream, target, StandardCopyOption.REPLACE_EXISTING);
            log.debug("本地存储上传成功: {}", objectName);
            return objectName;
        } catch (IOException e) {
            throw new RuntimeException("文件上传失败", e);
        }
    }

    @Override
    public void delete(String objectName) {
        try {
            Path root = Paths.get(uploadDir).toAbsolutePath().normalize();
            String normalized = objectName.replace("\\", "/");
            while (normalized.startsWith("/")) {
                normalized = normalized.substring(1);
            }
            Path target = root.resolve(normalized).normalize();
            if (!target.startsWith(root)) {
                throw new IllegalArgumentException("非法文件路径");
            }
            Files.deleteIfExists(target);
        } catch (IOException e) {
            log.warn("本地文件删除失败: {}", objectName, e);
        }
    }

    @Override
    public String getUrl(String objectName) {
        return objectName;
    }

    @Override
    public StoredObject open(String objectName, Long rangeStart, Long rangeEnd) {
        try {
            Path root = Paths.get(uploadDir).toAbsolutePath().normalize();
            Path target = resolve(root, objectName);
            long total = Files.size(target);
            long start = rangeStart == null ? 0 : Math.max(0, rangeStart);
            long end = rangeEnd == null ? total - 1 : Math.min(rangeEnd, total - 1);
            if (start >= total || end < start) {
                throw new IllegalArgumentException("Invalid object range");
            }
            InputStream stream = Files.newInputStream(target);
            if (start > 0) {
                stream.skipNBytes(start);
            }
            long length = end - start + 1;
            return new StoredObject(new LimitedInputStream(stream, length), contentTypeOf(target), length, total,
                    rangeStart == null ? null : start, rangeEnd == null ? null : end);
        } catch (IOException e) {
            throw new RuntimeException("Object read failed", e);
        }
    }

    private Path resolve(Path root, String objectName) {
        String normalized = objectName.replace("\\", "/");
        while (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        Path target = root.resolve(normalized).normalize();
        if (!target.startsWith(root)) {
            throw new IllegalArgumentException("Illegal object path");
        }
        return target;
    }

    private String contentTypeOf(Path target) throws IOException {
        String contentType = Files.probeContentType(target);
        return contentType == null ? "application/octet-stream" : contentType;
    }

    private static class LimitedInputStream extends FilterInputStream {
        private long remaining;

        LimitedInputStream(InputStream in, long limit) {
            super(in);
            this.remaining = limit;
        }

        @Override
        public int read() throws IOException {
            if (remaining <= 0) {
                return -1;
            }
            int value = super.read();
            if (value != -1) {
                remaining--;
            }
            return value;
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            if (remaining <= 0) {
                return -1;
            }
            int count = super.read(b, off, (int) Math.min(len, remaining));
            if (count != -1) {
                remaining -= count;
            }
            return count;
        }
    }
}
