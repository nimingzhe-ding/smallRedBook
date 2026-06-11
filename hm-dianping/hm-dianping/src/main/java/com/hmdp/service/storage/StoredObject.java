package com.hmdp.service.storage;

import java.io.InputStream;

public class StoredObject {

    private final InputStream inputStream;
    private final String contentType;
    private final long contentLength;
    private final long totalLength;
    private final Long rangeStart;
    private final Long rangeEnd;

    public StoredObject(InputStream inputStream, String contentType, long contentLength) {
        this(inputStream, contentType, contentLength, contentLength, null, null);
    }

    public StoredObject(InputStream inputStream,
                        String contentType,
                        long contentLength,
                        long totalLength,
                        Long rangeStart,
                        Long rangeEnd) {
        this.inputStream = inputStream;
        this.contentType = contentType;
        this.contentLength = contentLength;
        this.totalLength = totalLength;
        this.rangeStart = rangeStart;
        this.rangeEnd = rangeEnd;
    }

    public InputStream getInputStream() {
        return inputStream;
    }

    public String getContentType() {
        return contentType;
    }

    public long getContentLength() {
        return contentLength;
    }

    public long getTotalLength() {
        return totalLength;
    }

    public Long getRangeStart() {
        return rangeStart;
    }

    public Long getRangeEnd() {
        return rangeEnd;
    }

    public boolean isPartial() {
        return rangeStart != null && rangeEnd != null;
    }
}
