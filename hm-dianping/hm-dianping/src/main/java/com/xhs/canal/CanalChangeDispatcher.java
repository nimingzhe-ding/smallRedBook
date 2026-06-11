package com.xhs.canal;

import com.alibaba.otter.canal.protocol.CanalEntry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class CanalChangeDispatcher {

    private final CanalRedisSearchSyncService syncService;

    public void dispatch(List<CanalEntry.Entry> entries) throws Exception {
        if (entries == null || entries.isEmpty()) {
            return;
        }
        List<CanalRowChange> changes = new ArrayList<>();
        for (CanalEntry.Entry entry : entries) {
            if (entry.getEntryType() != CanalEntry.EntryType.ROWDATA) {
                continue;
            }
            CanalEntry.RowChange rowChange = CanalEntry.RowChange.parseFrom(entry.getStoreValue());
            if (rowChange.getIsDdl()) {
                continue;
            }
            String tableName = entry.getHeader().getTableName().toLowerCase(Locale.ROOT);
            if (!syncService.supports(tableName)) {
                continue;
            }
            for (CanalEntry.RowData rowData : rowChange.getRowDatasList()) {
                changes.add(new CanalRowChange(
                        tableName,
                        rowChange.getEventType(),
                        toColumnMap(rowData.getBeforeColumnsList()),
                        toColumnMap(rowData.getAfterColumnsList())
                ));
            }
        }
        if (!changes.isEmpty()) {
            syncService.sync(changes);
        }
    }

    private Map<String, String> toColumnMap(List<CanalEntry.Column> columns) {
        Map<String, String> values = new LinkedHashMap<>();
        for (CanalEntry.Column column : columns) {
            values.put(
                    column.getName().toLowerCase(Locale.ROOT),
                    column.getIsNull() ? null : column.getValue()
            );
        }
        return values;
    }
}
