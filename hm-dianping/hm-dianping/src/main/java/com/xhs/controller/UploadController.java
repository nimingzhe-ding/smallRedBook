package com.xhs.controller;

import cn.hutool.core.util.StrUtil;
import com.xhs.annotation.Idempotent;
import com.xhs.annotation.SlidingWindowRateLimit;
import com.xhs.compensation.CompensationEventTypes;
import com.xhs.dto.CompleteUploadRequest;
import com.xhs.dto.DirectUploadRequest;
import com.xhs.dto.DirectUploadResult;
import com.xhs.dto.MultipartUploadAbortRequest;
import com.xhs.dto.MultipartUploadCompleteRequest;
import com.xhs.dto.MultipartUploadInitRequest;
import com.xhs.dto.MultipartUploadInitResult;
import com.xhs.dto.MultipartUploadPartSignRequest;
import com.xhs.dto.MultipartUploadPartsRequest;
import com.xhs.dto.MultipartUploadedPart;
import com.xhs.dto.Result;
import com.xhs.dto.UploadResult;
import com.xhs.enums.ErrorCode;
import com.xhs.enums.RateLimitScope;
import com.xhs.exception.BusinessException;
import com.xhs.service.CompensationEventService;
import com.xhs.service.storage.FileStorageService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("upload")
public class UploadController {

    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("jpg", "jpeg", "png", "gif", "webp");
    private static final Set<String> ALLOWED_VIDEO_EXTENSIONS = Set.of("mp4", "webm", "mov");
    private static final long DEFAULT_MULTIPART_PART_SIZE = 8L * 1024 * 1024;
    private static final long MIN_MULTIPART_PART_SIZE = 1024L * 1024;
    private static final long MAX_MULTIPART_PART_SIZE = 64L * 1024 * 1024;
    private static final int MAX_MULTIPART_COUNT = 10_000;

    @Resource
    private FileStorageService fileStorageService;
    @Resource
    private CompensationEventService compensationEventService;

    @Value("${xiaohongshu.upload.max-image-size:5242880}")
    private long maxImageSize;

    @Value("${xiaohongshu.upload.max-video-size:524288000}")
    private long maxVideoSize;

    @Value("${xiaohongshu.upload.direct-upload-expire-minutes:10}")
    private long directUploadExpireMinutes;

    @Value("${xiaohongshu.upload.multipart-cleanup-grace-minutes:30}")
    private long multipartCleanupGraceMinutes;

    @PostMapping("note")
    @SlidingWindowRateLimit(key = "upload:image", maxRequests = 20, windowSeconds = 60, scope = RateLimitScope.USER_OR_IP)
    public Result uploadImage(@RequestParam("file") MultipartFile image) {
        if (image == null || image.isEmpty()) {
            throw new BusinessException(ErrorCode.FILE_EMPTY);
        }
        if (image.getSize() > maxImageSize) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "图片大小超出限制");
        }

        String suffix = getValidatedSuffix(image);
        if (suffix == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "只支持 jpg、jpeg、png、gif、webp 图片");
        }

        try {
            String fileName = createNewFileName(suffix);
            String objectName = fileStorageService.upload(fileName, image.getInputStream(), image.getSize());
            String url = normalizeAccessUrl(fileStorageService.getUrl(objectName));
            log.debug("Image upload success: {}", objectName);
            return Result.ok(new UploadResult(objectName, url, image.getContentType(), image.getSize()));
        } catch (IOException e) {
            throw new RuntimeException("文件上传失败", e);
        }
    }

    @PostMapping("video")
    @SlidingWindowRateLimit(key = "upload:video", maxRequests = 5, windowSeconds = 600, scope = RateLimitScope.USER_OR_IP)
    public Result uploadVideo(@RequestParam("file") MultipartFile video) {
        if (video == null || video.isEmpty()) {
            throw new BusinessException(ErrorCode.FILE_EMPTY);
        }
        if (video.getSize() > maxVideoSize) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "视频大小超出限制");
        }
        String suffix = getValidatedVideoSuffix(video);
        if (suffix == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "只支持 mp4、webm、mov 视频");
        }
        try {
            String fileName = createNewMediaFileName("videos", suffix);
            String objectName = fileStorageService.upload(fileName, video.getInputStream(), video.getSize());
            String url = normalizeAccessUrl(fileStorageService.getUrl(objectName));
            log.debug("Video upload success: {}", objectName);
            return Result.ok(new UploadResult(objectName, url, video.getContentType(), video.getSize()));
        } catch (IOException e) {
            throw new RuntimeException("视频上传失败", e);
        }
    }

    @PostMapping("video/direct-signature")
    @SlidingWindowRateLimit(key = "upload:video:direct-signature", maxRequests = 10, windowSeconds = 60, scope = RateLimitScope.USER_OR_IP)
    public Result createVideoDirectUpload(@RequestBody DirectUploadRequest request) {
        if (request == null || request.getSize() == null || request.getSize() <= 0) {
            throw new BusinessException(ErrorCode.PARAM_EMPTY, "视频文件信息不能为空");
        }
        if (request.getSize() > maxVideoSize) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "视频大小超出限制");
        }
        String suffix = getValidatedVideoSuffix(request.getFileName(), request.getContentType());
        if (suffix == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "只支持 mp4、webm、mov 视频");
        }
        String contentType = normalizeVideoContentType(request.getContentType(), suffix);
        String objectName = createNewMediaFileName("videos", suffix);
        DirectUploadResult result = fileStorageService.createDirectUpload(
                objectName,
                contentType,
                request.getSize(),
                Duration.ofMinutes(Math.max(1, directUploadExpireMinutes))
        );
        return Result.ok(result);
    }

    @PostMapping("video/complete")
    @Idempotent(key = "upload:video:complete", expireSeconds = 60)
    public Result completeVideoUpload(@RequestBody CompleteUploadRequest request) {
        if (request == null || StrUtil.isBlank(request.getObjectName())) {
            throw new BusinessException(ErrorCode.PARAM_EMPTY, "视频对象路径不能为空");
        }
        String suffix = getValidatedVideoSuffix(request.getObjectName(), request.getContentType());
        if (suffix == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "视频格式不支持");
        }
        if (request.getSize() != null && request.getSize() > maxVideoSize) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "视频大小超出限制");
        }
        if (!fileStorageService.exists(request.getObjectName())) {
            throw new BusinessException(ErrorCode.UPLOAD_FAIL, "OSS 未找到已上传的视频文件");
        }
        String url = normalizeAccessUrl(fileStorageService.getUrl(request.getObjectName()));
        return Result.ok(new UploadResult(
                request.getObjectName(),
                url,
                normalizeVideoContentType(request.getContentType(), suffix),
                request.getSize()
        ));
    }

    @PostMapping("video/multipart/init")
    @SlidingWindowRateLimit(key = "upload:video:multipart:init", maxRequests = 10, windowSeconds = 60, scope = RateLimitScope.USER_OR_IP)
    @Idempotent(key = "upload:video:multipart:init", expireSeconds = 30)
    public Result initVideoMultipartUpload(@RequestBody MultipartUploadInitRequest request) {
        if (request == null || request.getSize() == null || request.getSize() <= 0) {
            throw new BusinessException(ErrorCode.PARAM_EMPTY, "视频文件信息不能为空");
        }
        if (request.getSize() > maxVideoSize) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "视频大小超出限制");
        }
        String suffix = getValidatedVideoSuffix(request.getFileName(), request.getContentType());
        if (suffix == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "只支持 mp4、webm、mov 视频");
        }
        long partSize = normalizePartSize(request.getPartSize());
        int partCount = partCount(request.getSize(), partSize);
        if (partCount > MAX_MULTIPART_COUNT) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "视频分片数量过多，请调大分片大小");
        }
        String contentType = normalizeVideoContentType(request.getContentType(), suffix);
        String objectName = createNewMediaFileName("videos", suffix);
        MultipartUploadInitResult result = fileStorageService.initiateMultipartUpload(
                objectName,
                contentType,
                request.getSize(),
                partSize,
                Duration.ofMinutes(Math.max(1, directUploadExpireMinutes))
        );
        registerMultipartAbortCompensation(result);
        return Result.ok(result);
    }

    @PostMapping("video/multipart/part-signature")
    @SlidingWindowRateLimit(key = "upload:video:multipart:part-signature", maxRequests = 600, windowSeconds = 60, scope = RateLimitScope.USER_OR_IP)
    public Result createVideoMultipartPartUpload(@RequestBody MultipartUploadPartSignRequest request) {
        requireMultipartTarget(request == null ? null : request.getObjectName(), request == null ? null : request.getUploadId());
        if (request.getPartNumber() == null || request.getPartNumber() < 1 || request.getPartNumber() > MAX_MULTIPART_COUNT) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "分片编号不合法");
        }
        String suffix = getValidatedVideoSuffix(request.getObjectName(), request.getContentType());
        if (suffix == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "视频格式不支持");
        }
        String contentType = normalizeVideoContentType(request.getContentType(), suffix);
        return Result.ok(fileStorageService.createMultipartPartUpload(
                request.getObjectName(),
                request.getUploadId(),
                request.getPartNumber(),
                contentType,
                normalizePartSize(request.getPartSize()),
                Duration.ofMinutes(Math.max(1, directUploadExpireMinutes))
        ));
    }

    @PostMapping("video/multipart/parts")
    public Result listVideoMultipartParts(@RequestBody MultipartUploadPartsRequest request) {
        requireMultipartTarget(request == null ? null : request.getObjectName(), request == null ? null : request.getUploadId());
        return Result.ok(fileStorageService.listMultipartUploadedParts(request.getObjectName(), request.getUploadId()));
    }

    @PostMapping("video/multipart/complete")
    @Idempotent(key = "upload:video:multipart:complete", expireSeconds = 60)
    public Result completeVideoMultipartUpload(@RequestBody MultipartUploadCompleteRequest request) {
        requireMultipartTarget(request == null ? null : request.getObjectName(), request == null ? null : request.getUploadId());
        String suffix = getValidatedVideoSuffix(request.getObjectName(), request.getContentType());
        if (suffix == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "视频格式不支持");
        }
        if (request.getSize() != null && request.getSize() > maxVideoSize) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "视频大小超出限制");
        }
        List<MultipartUploadedPart> parts = request.getParts();
        if (parts == null || parts.isEmpty()) {
            parts = fileStorageService.listMultipartUploadedParts(request.getObjectName(), request.getUploadId());
        }
        if (parts == null || parts.isEmpty()) {
            throw new BusinessException(ErrorCode.UPLOAD_FAIL, "未找到已上传的视频分片");
        }
        parts = parts.stream()
                .filter(part -> part.getPartNumber() != null && StrUtil.isNotBlank(part.getETag()))
                .sorted(Comparator.comparing(MultipartUploadedPart::getPartNumber))
                .toList();
        if (parts.isEmpty()) {
            throw new BusinessException(ErrorCode.UPLOAD_FAIL, "视频分片信息不完整");
        }
        if (request.getSize() != null && parts.stream().allMatch(part -> part.getSize() != null)) {
            long uploadedSize = parts.stream().mapToLong(MultipartUploadedPart::getSize).sum();
            if (uploadedSize != request.getSize()) {
                throw new BusinessException(ErrorCode.UPLOAD_FAIL, "视频分片未上传完整");
            }
        }
        String contentType = normalizeVideoContentType(request.getContentType(), suffix);
        String objectName = fileStorageService.completeMultipartUpload(
                request.getObjectName(),
                request.getUploadId(),
                parts,
                contentType,
                request.getSize()
        );
        compensationEventService.cancelByKey(multipartAbortKey(request.getObjectName(), request.getUploadId()),
                "Multipart upload completed");
        String url = normalizeAccessUrl(fileStorageService.getUrl(objectName));
        return Result.ok(new UploadResult(objectName, url, contentType, request.getSize()));
    }

    @PostMapping("video/multipart/abort")
    @Idempotent(key = "upload:video:multipart:abort", expireSeconds = 60)
    public Result abortVideoMultipartUpload(@RequestBody MultipartUploadAbortRequest request) {
        requireMultipartTarget(request == null ? null : request.getObjectName(), request == null ? null : request.getUploadId());
        try {
            fileStorageService.abortMultipartUpload(request.getObjectName(), request.getUploadId());
            compensationEventService.cancelByKey(multipartAbortKey(request.getObjectName(), request.getUploadId()),
                    "Multipart upload aborted by user");
        } catch (Exception e) {
            compensationEventService.record(
                    CompensationEventTypes.OSS_MULTIPART_ABORT,
                    "OSS",
                    request.getObjectName(),
                    multipartAbortKey(request.getObjectName(), request.getUploadId()),
                    Map.of("objectName", request.getObjectName(), "uploadId", request.getUploadId()),
                    e.getMessage()
            );
            throw e;
        }
        return Result.ok();
    }

    private void registerMultipartAbortCompensation(MultipartUploadInitResult result) {
        if (result == null || StrUtil.hasBlank(result.getObjectName(), result.getUploadId())) {
            return;
        }
        LocalDateTime cleanupAt = LocalDateTime.ofInstant(
                result.getExpireAt().plus(Duration.ofMinutes(Math.max(1, multipartCleanupGraceMinutes))),
                ZoneId.systemDefault()
        );
        compensationEventService.record(
                CompensationEventTypes.OSS_MULTIPART_ABORT,
                "OSS",
                result.getObjectName(),
                multipartAbortKey(result.getObjectName(), result.getUploadId()),
                Map.of("objectName", result.getObjectName(), "uploadId", result.getUploadId()),
                "Multipart upload has not completed before cleanup deadline",
                cleanupAt,
                24
        );
    }

    private String multipartAbortKey(String objectName, String uploadId) {
        return "oss:multipart:abort:" + objectName + ":" + uploadId;
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
        return getValidatedVideoSuffix(video.getOriginalFilename(), video.getContentType());
    }

    private String getValidatedVideoSuffix(String fileName, String contentType) {
        String originalFilename = StrUtil.blankToDefault(fileName, "");
        String suffix = StrUtil.subAfter(originalFilename, ".", true).toLowerCase(Locale.ROOT);
        if (!ALLOWED_VIDEO_EXTENSIONS.contains(suffix)) {
            return null;
        }
        String type = StrUtil.blankToDefault(contentType, "").toLowerCase(Locale.ROOT);
        if (StrUtil.isNotBlank(type) && !type.startsWith("video/") && !"application/octet-stream".equals(type)) {
            return null;
        }
        return suffix;
    }

    private String normalizeVideoContentType(String contentType, String suffix) {
        String type = StrUtil.blankToDefault(contentType, "").toLowerCase(Locale.ROOT);
        if (type.startsWith("video/")) {
            return type;
        }
        return switch (suffix) {
            case "webm" -> "video/webm";
            case "mov" -> "video/quicktime";
            default -> "video/mp4";
        };
    }

    private long normalizePartSize(Long partSize) {
        long value = partSize == null || partSize <= 0 ? DEFAULT_MULTIPART_PART_SIZE : partSize;
        return Math.max(MIN_MULTIPART_PART_SIZE, Math.min(value, MAX_MULTIPART_PART_SIZE));
    }

    private int partCount(long size, long partSize) {
        return (int) Math.ceil((double) size / (double) partSize);
    }

    private void requireMultipartTarget(String objectName, String uploadId) {
        if (StrUtil.hasBlank(objectName, uploadId)) {
            throw new BusinessException(ErrorCode.PARAM_EMPTY, "分片上传信息不能为空");
        }
        String normalized = objectName.replace("\\", "/");
        while (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        if (!normalized.startsWith("videos/") || normalized.contains("..")) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "视频对象路径不合法");
        }
    }

    private String createNewFileName(String suffix) {
        return createNewMediaFileName("notes", suffix);
    }

    private String createNewMediaFileName(String folder, String suffix) {
        String name = UUID.randomUUID().toString();
        int hash = name.hashCode();
        int d1 = hash & 0xF;
        int d2 = (hash >> 4) & 0xF;
        return StrUtil.format("/{}/{}/{}/{}.{}", folder, d1, d2, name, suffix);
    }

    private String normalizeAccessUrl(String objectName) {
        if (StrUtil.isBlank(objectName)
                || objectName.startsWith("http://")
                || objectName.startsWith("https://")
                || objectName.startsWith("/imgs/")) {
            return objectName;
        }
        return "/imgs" + (objectName.startsWith("/") ? objectName : "/" + objectName);
    }
}
