package com.xhs.service.storage;

import com.xhs.dto.DirectUploadResult;
import com.xhs.dto.MultipartUploadInitResult;
import com.xhs.dto.MultipartUploadPartSignResult;
import com.xhs.dto.MultipartUploadedPart;

import java.io.InputStream;
import java.time.Duration;
import java.util.List;

/**
 * 文件存储抽象接口。
 * 通过 application.yaml 的 xiaohongshu.storage.type 配置切换实现：
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

    default MultipartUploadInitResult initiateMultipartUpload(String objectName,
                                                             String contentType,
                                                             long size,
                                                             long partSize,
                                                             Duration expiration) {
        throw new UnsupportedOperationException("Current storage does not support multipart upload");
    }

    default MultipartUploadPartSignResult createMultipartPartUpload(String objectName,
                                                                    String uploadId,
                                                                    int partNumber,
                                                                    String contentType,
                                                                    long partSize,
                                                                    Duration expiration) {
        throw new UnsupportedOperationException("Current storage does not support multipart upload");
    }

    default List<MultipartUploadedPart> listMultipartUploadedParts(String objectName, String uploadId) {
        throw new UnsupportedOperationException("Current storage does not support multipart upload");
    }

    default String completeMultipartUpload(String objectName,
                                           String uploadId,
                                           List<MultipartUploadedPart> parts,
                                           String contentType,
                                           Long size) {
        throw new UnsupportedOperationException("Current storage does not support multipart upload");
    }

    default void abortMultipartUpload(String objectName, String uploadId) {
        throw new UnsupportedOperationException("Current storage does not support multipart upload");
    }

    default boolean exists(String objectName) {
        return true;
    }
}
