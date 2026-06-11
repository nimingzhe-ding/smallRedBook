package com.hmdp.service.storage;

import com.hmdp.dto.DirectUploadResult;

import java.io.InputStream;
import java.time.Duration;

/**
 * 文件存储抽象接口。
 * 通过 application.yaml 的 hmdp.storage.type 配置切换实现：
 * - local（默认）：本地文件系统
 * - oss：对象存储（如阿里云 OSS）
 */
public interface FileStorageService {

    /**
     * 上传文件。
     * @param objectName  对象路径，如 /blogs/a/b/uuid.jpg
     * @param inputStream 文件输入流
     * @param size        文件大小（字节）
     * @return 可访问的 URL 或路径标识
     */
    String upload(String objectName, InputStream inputStream, long size);

    /**
     * 删除文件。
     * @param objectName 对象路径
     */
    void delete(String objectName);

    /**
     * 获取文件访问 URL。
     * @param objectName 对象路径
     * @return 可访问的 URL
     */
    String getUrl(String objectName);

    default StoredObject open(String objectName, Long rangeStart, Long rangeEnd) {
        throw new UnsupportedOperationException("Current storage does not support object streaming");
    }

    default DirectUploadResult createDirectUpload(String objectName, String contentType, long size, Duration expiration) {
        throw new UnsupportedOperationException("Current storage does not support direct upload");
    }

    default boolean exists(String objectName) {
        return true;
    }
}
