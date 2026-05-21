package com.hmdp.controller;

import cn.hutool.core.util.StrUtil;
import com.hmdp.dto.Result;
import com.hmdp.enums.ErrorCode;
import com.hmdp.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("upload")
public class UploadController {

    private static final long MAX_IMAGE_SIZE = 5 * 1024 * 1024;
    private static final long MAX_VIDEO_SIZE = 50 * 1024 * 1024;
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("jpg", "jpeg", "png", "gif", "webp");
    private static final Set<String> ALLOWED_VIDEO_EXTENSIONS = Set.of("mp4", "webm", "mov");

    @Value("${hmdp.upload.image-dir}")
    private String imageUploadDir;

    @PostMapping("note")
    public Result uploadImage(@RequestParam("file") MultipartFile image) {
        if (image == null || image.isEmpty()) {
            throw new BusinessException(ErrorCode.FILE_EMPTY);
        }
        if (image.getSize() > MAX_IMAGE_SIZE) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "图片大小不能超过5MB");
        }

        String suffix = getValidatedSuffix(image);
        if (suffix == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "只支持 jpg、jpeg、png、gif、webp 图片");
        }

        try {
            String fileName = createNewFileName(suffix);
            Path target = resolveUploadPath(fileName);
            Files.createDirectories(target.getParent());
            image.transferTo(target.toFile());
            log.debug("文件上传成功: {}", fileName);
            return Result.ok(fileName);
        } catch (IOException e) {
            throw new RuntimeException("文件上传失败", e);
        }
    }

    @PostMapping("video")
    public Result uploadVideo(@RequestParam("file") MultipartFile video) {
        if (video == null || video.isEmpty()) {
            throw new BusinessException(ErrorCode.FILE_EMPTY);
        }
        if (video.getSize() > MAX_VIDEO_SIZE) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "视频大小不能超过50MB");
        }
        String suffix = getValidatedVideoSuffix(video);
        if (suffix == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "只支持 mp4、webm、mov 视频");
        }
        try {
            String fileName = createNewMediaFileName("videos", suffix);
            Path target = resolveUploadPath(fileName);
            Files.createDirectories(target.getParent());
            video.transferTo(target.toFile());
            log.debug("视频上传成功: {}", fileName);
            return Result.ok(fileName);
        } catch (IOException e) {
            throw new RuntimeException("视频上传失败", e);
        }
    }

    private String getValidatedSuffix(MultipartFile image) {
        String originalFilename = StrUtil.blankToDefault(image.getOriginalFilename(), "");
        String suffix = StrUtil.subAfter(originalFilename, ".", true).toLowerCase(Locale.ROOT);
        if (!ALLOWED_EXTENSIONS.contains(suffix)) {
            return null;
        }
        String contentType = StrUtil.blankToDefault(image.getContentType(), "").toLowerCase(Locale.ROOT);
        if (!contentType.startsWith("image/")) {
            return null;
        }
        return suffix;
    }

    private String getValidatedVideoSuffix(MultipartFile video) {
        String originalFilename = StrUtil.blankToDefault(video.getOriginalFilename(), "");
        String suffix = StrUtil.subAfter(originalFilename, ".", true).toLowerCase(Locale.ROOT);
        if (!ALLOWED_VIDEO_EXTENSIONS.contains(suffix)) {
            return null;
        }
        String contentType = StrUtil.blankToDefault(video.getContentType(), "").toLowerCase(Locale.ROOT);
        if (!contentType.startsWith("video/") && !"application/octet-stream".equals(contentType)) {
            return null;
        }
        return suffix;
    }

    private String createNewFileName(String suffix) {
        return createNewMediaFileName("blogs", suffix);
    }

    private String createNewMediaFileName(String folder, String suffix) {
        String name = UUID.randomUUID().toString();
        int hash = name.hashCode();
        int d1 = hash & 0xF;
        int d2 = (hash >> 4) & 0xF;
        return StrUtil.format("/{}/{}/{}/{}.{}", folder, d1, d2, name, suffix);
    }

    private Path resolveUploadPath(String filename) {
        String normalizedName = filename.replace("\\", "/");
        while (normalizedName.startsWith("/")) {
            normalizedName = normalizedName.substring(1);
        }
        Path root = uploadRoot();
        Path target = root.resolve(normalizedName).normalize();
        if (!target.startsWith(root)) {
            throw new IllegalArgumentException("错误的文件路径");
        }
        return target;
    }

    private Path uploadRoot() {
        return Paths.get(imageUploadDir).toAbsolutePath().normalize();
    }
}
