package com.hmdp.service.storage;

import cn.hutool.core.util.StrUtil;
import com.aliyun.oss.ClientBuilderConfiguration;
import com.aliyun.oss.HttpMethod;
import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import com.aliyun.oss.model.AbortMultipartUploadRequest;
import com.aliyun.oss.model.CompleteMultipartUploadRequest;
import com.aliyun.oss.model.CompleteMultipartUploadResult;
import com.aliyun.oss.model.GeneratePresignedUrlRequest;
import com.aliyun.oss.model.GetObjectRequest;
import com.aliyun.oss.model.InitiateMultipartUploadRequest;
import com.aliyun.oss.model.InitiateMultipartUploadResult;
import com.aliyun.oss.model.ListPartsRequest;
import com.aliyun.oss.model.ObjectMetadata;
import com.aliyun.oss.model.OSSObject;
import com.aliyun.oss.model.PartETag;
import com.aliyun.oss.model.PartListing;
import com.aliyun.oss.model.PartSummary;
import com.hmdp.dto.DirectUploadResult;
import com.hmdp.dto.MultipartUploadInitResult;
import com.hmdp.dto.MultipartUploadPartSignResult;
import com.hmdp.dto.MultipartUploadedPart;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Slf4j
@Service
@ConditionalOnProperty(name = "hmdp.storage.type", havingValue = "oss")
public class OssStorageService implements FileStorageService {

    @Value("${hmdp.storage.oss.endpoint:}")
    private String endpoint;

    @Value("${hmdp.storage.oss.bucket:}")
    private String bucket;

    @Value("${hmdp.storage.oss.access-key:}")
    private String accessKey;

    @Value("${hmdp.storage.oss.secret-key:}")
    private String secretKey;

    @Value("${hmdp.storage.oss.domain:}")
    private String domain;

    private OSS ossClient;
    private String normalizedEndpoint;

    @PostConstruct
    public void init() {
        if (StrUtil.hasBlank(endpoint, bucket, accessKey, secretKey)) {
            throw new IllegalStateException("OSS storage requires endpoint, bucket, access-key and secret-key");
        }
        normalizedEndpoint = normalizeEndpoint(endpoint);
        ClientBuilderConfiguration configuration = new ClientBuilderConfiguration();
        configuration.setConnectionTimeout(5000);
        configuration.setSocketTimeout(30000);
        configuration.setMaxConnections(128);
        ossClient = new OSSClientBuilder().build(normalizedEndpoint, accessKey, secretKey, configuration);
        log.info("OSS storage initialized, bucket={}, endpoint={}", bucket, normalizedEndpoint);
    }

    @PreDestroy
    public void shutdown() {
        if (ossClient != null) {
            ossClient.shutdown();
        }
    }

    @Override
    public String upload(String objectName, InputStream inputStream, long size) {
        String key = normalizeObjectName(objectName);
        ObjectMetadata metadata = new ObjectMetadata();
        metadata.setContentLength(size);
        metadata.setContentType(contentTypeOf(key));
        ossClient.putObject(bucket, key, inputStream, metadata);
        log.debug("OSS upload success, key={}, size={}", key, size);
        return key;
    }

    @Override
    public void delete(String objectName) {
        String key = normalizeObjectName(objectName);
        if (StrUtil.isBlank(key)) {
            return;
        }
        ossClient.deleteObject(bucket, key);
    }

    @Override
    public String getUrl(String objectName) {
        String key = normalizeObjectName(objectName);
        return "/imgs/" + encodeKey(key);
    }

    @Override
    public DirectUploadResult createDirectUpload(String objectName, String contentType, long size, Duration expiration) {
        String key = normalizeObjectName(objectName);
        String safeContentType = StrUtil.blankToDefault(contentType, contentTypeOf(key)).toLowerCase(Locale.ROOT);
        Instant expireAt = Instant.now().plus(expiration);
        GeneratePresignedUrlRequest request = new GeneratePresignedUrlRequest(bucket, key, HttpMethod.PUT);
        request.setExpiration(Date.from(expireAt));
        request.setContentType(safeContentType);
        String uploadUrl = ossClient.generatePresignedUrl(request).toString();
        return new DirectUploadResult(
                key,
                uploadUrl,
                getUrl(key),
                "PUT",
                safeContentType,
                size,
                expireAt,
                Map.of("Content-Type", safeContentType)
        );
    }

    @Override
    public MultipartUploadInitResult initiateMultipartUpload(String objectName,
                                                            String contentType,
                                                            long size,
                                                            long partSize,
                                                            Duration expiration) {
        String key = normalizeObjectName(objectName);
        String safeContentType = StrUtil.blankToDefault(contentType, contentTypeOf(key)).toLowerCase(Locale.ROOT);
        ObjectMetadata metadata = new ObjectMetadata();
        metadata.setContentType(safeContentType);
        metadata.setContentLength(size);
        InitiateMultipartUploadRequest request = new InitiateMultipartUploadRequest(bucket, key, metadata);
        InitiateMultipartUploadResult result = ossClient.initiateMultipartUpload(request);
        int partCount = (int) Math.ceil((double) size / (double) partSize);
        return new MultipartUploadInitResult(
                key,
                result.getUploadId(),
                getUrl(key),
                safeContentType,
                size,
                partSize,
                partCount,
                Instant.now().plus(expiration),
                List.of()
        );
    }

    @Override
    public MultipartUploadPartSignResult createMultipartPartUpload(String objectName,
                                                                   String uploadId,
                                                                   int partNumber,
                                                                   String contentType,
                                                                   long partSize,
                                                                   Duration expiration) {
        String key = normalizeObjectName(objectName);
        String safeContentType = StrUtil.blankToDefault(contentType, contentTypeOf(key)).toLowerCase(Locale.ROOT);
        Instant expireAt = Instant.now().plus(expiration);
        GeneratePresignedUrlRequest request = new GeneratePresignedUrlRequest(bucket, key, HttpMethod.PUT);
        request.setExpiration(Date.from(expireAt));
        request.setContentType(safeContentType);
        request.addQueryParameter("partNumber", String.valueOf(partNumber));
        request.addQueryParameter("uploadId", uploadId);
        String uploadUrl = ossClient.generatePresignedUrl(request).toString();
        return new MultipartUploadPartSignResult(
                key,
                uploadId,
                partNumber,
                uploadUrl,
                "PUT",
                expireAt,
                Map.of("Content-Type", safeContentType)
        );
    }

    @Override
    public List<MultipartUploadedPart> listMultipartUploadedParts(String objectName, String uploadId) {
        String key = normalizeObjectName(objectName);
        List<MultipartUploadedPart> parts = new ArrayList<>();
        ListPartsRequest request = new ListPartsRequest(bucket, key, uploadId);
        PartListing listing;
        do {
            listing = ossClient.listParts(request);
            for (PartSummary summary : listing.getParts()) {
                parts.add(new MultipartUploadedPart(summary.getPartNumber(), summary.getETag(), summary.getSize()));
            }
            request.setPartNumberMarker(listing.getNextPartNumberMarker());
        } while (listing.isTruncated());
        parts.sort(Comparator.comparing(MultipartUploadedPart::getPartNumber));
        return parts;
    }

    @Override
    public String completeMultipartUpload(String objectName,
                                          String uploadId,
                                          List<MultipartUploadedPart> parts,
                                          String contentType,
                                          Long size) {
        String key = normalizeObjectName(objectName);
        List<PartETag> partETags = parts.stream()
                .sorted(Comparator.comparing(MultipartUploadedPart::getPartNumber))
                .map(part -> new PartETag(part.getPartNumber(), normalizeETag(part.getETag())))
                .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
        CompleteMultipartUploadRequest request = new CompleteMultipartUploadRequest(bucket, key, uploadId, partETags);
        CompleteMultipartUploadResult result = ossClient.completeMultipartUpload(request);
        log.debug("OSS multipart upload complete, key={}, uploadId={}, etag={}", key, uploadId, result.getETag());
        return key;
    }

    @Override
    public void abortMultipartUpload(String objectName, String uploadId) {
        String key = normalizeObjectName(objectName);
        ossClient.abortMultipartUpload(new AbortMultipartUploadRequest(bucket, key, uploadId));
    }

    @Override
    public boolean exists(String objectName) {
        String key = normalizeObjectName(objectName);
        return StrUtil.isNotBlank(key) && ossClient.doesObjectExist(bucket, key);
    }

    @Override
    public StoredObject open(String objectName, Long rangeStart, Long rangeEnd) {
        String key = normalizeObjectName(objectName);
        ObjectMetadata metadata = ossClient.getObjectMetadata(bucket, key);
        long total = metadata.getContentLength();
        if (rangeStart == null && rangeEnd == null) {
            OSSObject object = ossClient.getObject(bucket, key);
            ObjectMetadata objectMetadata = object.getObjectMetadata();
            return new StoredObject(object.getObjectContent(), safeContentType(objectMetadata, key), total);
        }
        long start = rangeStart == null ? 0 : Math.max(0, rangeStart);
        long end = rangeEnd == null ? total - 1 : Math.min(rangeEnd, total - 1);
        if (start >= total || end < start) {
            throw new IllegalArgumentException("Invalid object range");
        }
        GetObjectRequest request = new GetObjectRequest(bucket, key);
        request.setRange(start, end);
        OSSObject object = ossClient.getObject(request);
        long length = end - start + 1;
        return new StoredObject(object.getObjectContent(), safeContentType(object.getObjectMetadata(), key),
                length, total, start, end);
    }

    private String normalizeEndpoint(String value) {
        String trimmed = value.trim();
        if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            return trimmed;
        }
        return "https://" + trimmed;
    }

    private String defaultPublicDomain() {
        String host = normalizedEndpoint
                .replaceFirst("^https?://", "")
                .replaceAll("/+$", "");
        return "https://" + bucket + "." + host;
    }

    private String normalizeObjectName(String objectName) {
        String normalized = StrUtil.blankToDefault(objectName, "").replace("\\", "/").trim();
        while (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        if (normalized.contains("..")) {
            throw new IllegalArgumentException("Illegal object name");
        }
        return normalized;
    }

    private String contentTypeOf(String key) {
        String suffix = StrUtil.subAfter(key, ".", true).toLowerCase(Locale.ROOT);
        return switch (suffix) {
            case "jpg", "jpeg" -> "image/jpeg";
            case "png" -> "image/png";
            case "gif" -> "image/gif";
            case "webp" -> "image/webp";
            case "webm" -> "video/webm";
            case "mov" -> "video/quicktime";
            case "mp4" -> "video/mp4";
            default -> "application/octet-stream";
        };
    }

    private String safeContentType(ObjectMetadata metadata, String key) {
        String contentType = metadata == null ? null : metadata.getContentType();
        return StrUtil.blankToDefault(contentType, contentTypeOf(key));
    }

    private String normalizeETag(String eTag) {
        return StrUtil.blankToDefault(eTag, "").replace("\"", "");
    }

    private String encodeKey(String key) {
        return StrUtil.split(key, '/')
                .stream()
                .map(part -> URLEncoder.encode(part, StandardCharsets.UTF_8).replace("+", "%20"))
                .reduce((left, right) -> left + "/" + right)
                .orElse("");
    }
}
