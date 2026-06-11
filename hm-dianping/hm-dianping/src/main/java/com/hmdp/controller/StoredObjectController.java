package com.hmdp.controller;

import cn.hutool.core.util.StrUtil;
import com.aliyun.oss.OSSException;
import com.hmdp.service.storage.FileStorageService;
import com.hmdp.service.storage.StoredObject;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.InputStream;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

@RestController
public class StoredObjectController {

    @Resource
    private FileStorageService fileStorageService;

    @GetMapping("/imgs/**")
    public ResponseEntity<StreamingResponseBody> getObject(HttpServletRequest request,
                                                           @RequestHeader(value = HttpHeaders.RANGE, required = false) String rangeHeader) {
        String objectName = objectName(request);
        Range range = null;
        try {
            StoredObject object = fileStorageService.open(objectName, null, null);
            range = parseRange(rangeHeader, object.getTotalLength());
            if (range != null) {
                closeQuietly(object.getInputStream());
                object = fileStorageService.open(objectName, range.start(), range.end());
            }
            StoredObject responseObject = object;
            StreamingResponseBody body = outputStream -> {
                try (InputStream inputStream = responseObject.getInputStream()) {
                    inputStream.transferTo(outputStream);
                }
            };
            ResponseEntity.BodyBuilder builder = ResponseEntity
                    .status(responseObject.isPartial() ? HttpStatus.PARTIAL_CONTENT : HttpStatus.OK)
                    .contentType(MediaType.parseMediaType(contentType(responseObject)))
                    .cacheControl(CacheControl.maxAge(7, TimeUnit.DAYS).cachePublic())
                    .header(HttpHeaders.ACCEPT_RANGES, "bytes")
                    .contentLength(responseObject.getContentLength());
            if (responseObject.isPartial()) {
                builder.header(HttpHeaders.CONTENT_RANGE, "bytes "
                        + responseObject.getRangeStart() + "-"
                        + responseObject.getRangeEnd() + "/"
                        + responseObject.getTotalLength());
            }
            return builder.body(body);
        } catch (OSSException e) {
            if ("NoSuchKey".equals(e.getErrorCode())) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND);
            }
            throw e;
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.REQUESTED_RANGE_NOT_SATISFIABLE);
        }
    }

    private String objectName(HttpServletRequest request) {
        String prefix = request.getContextPath() + "/imgs/";
        String uri = request.getRequestURI();
        if (!uri.startsWith(prefix)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        String objectName = URLDecoder.decode(uri.substring(prefix.length()), StandardCharsets.UTF_8);
        if (StrUtil.isBlank(objectName) || objectName.contains("..") || objectName.startsWith("/")) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        return objectName;
    }

    private Range parseRange(String rangeHeader, long totalLength) {
        if (StrUtil.isBlank(rangeHeader) || !rangeHeader.startsWith("bytes=") || totalLength <= 0) {
            return null;
        }
        String spec = rangeHeader.substring("bytes=".length()).split(",", 2)[0].trim();
        if (StrUtil.isBlank(spec)) {
            return null;
        }
        long start;
        long end;
        int dash = spec.indexOf('-');
        if (dash < 0) {
            return null;
        }
        String startText = spec.substring(0, dash).trim();
        String endText = spec.substring(dash + 1).trim();
        if (StrUtil.isBlank(startText)) {
            long suffixLength = Long.parseLong(endText);
            if (suffixLength <= 0) {
                throw new IllegalArgumentException("Invalid range");
            }
            start = Math.max(0, totalLength - suffixLength);
            end = totalLength - 1;
        } else {
            start = Long.parseLong(startText);
            end = StrUtil.isBlank(endText) ? totalLength - 1 : Long.parseLong(endText);
        }
        if (start < 0 || start >= totalLength || end < start) {
            throw new IllegalArgumentException("Invalid range");
        }
        return new Range(start, Math.min(end, totalLength - 1));
    }

    private String contentType(StoredObject object) {
        return StrUtil.blankToDefault(object.getContentType(), "application/octet-stream");
    }

    private void closeQuietly(InputStream inputStream) {
        try {
            inputStream.close();
        } catch (Exception ignored) {
        }
    }

    private record Range(long start, long end) {
    }
}
