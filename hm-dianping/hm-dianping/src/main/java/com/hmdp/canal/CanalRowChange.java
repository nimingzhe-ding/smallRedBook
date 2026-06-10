package com.hmdp.canal;

import com.alibaba.otter.canal.protocol.CanalEntry;

import java.util.Map;

public record CanalRowChange(
        String tableName,
        CanalEntry.EventType eventType,
        Map<String, String> beforeColumns,
        Map<String, String> afterColumns
) {
    public boolean delete() {
        return CanalEntry.EventType.DELETE == eventType;
    }

    public Map<String, String> currentColumns() {
        return delete() ? beforeColumns : afterColumns;
    }
}
