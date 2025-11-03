package com.mysqlLockDemo;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 压缩策略
 */
public class CompactionStrategy {
    private final String dataDir;
    private final int maxLevelSize;
    private final int levelSizeMultiplier;

    public CompactionStrategy(String dataDir, int maxLevelSize, int levelSizeMultiplier) {
        this.dataDir = dataDir;
        this.maxLevelSize = maxLevelSize;
        this.levelSizeMultiplier = levelSizeMultiplier;
    }

    /*
     * 判断是否需要压缩
     */
    public boolean needsCompaction(List<SSTable> ssTables) {
        Map<Integer, List<SSTable>> levelmap = groupByLevel(ssTables);

        return true;
    }

    /*
     *  按照级别分组SSTable
     */
    private Map<Integer,List<SSTable>> groupByLevel(List<SSTable> ssTables) {
        Map<Integer,List<SSTable>> levelMap = new HashMap<>();
        for (SSTable table : ssTables){
            int level = extractLevelFromPath(table.getFilePath());
            levelMap.computeIfAbsent(level, k -> new ArrayList<>()).add( table);
        }
        return levelMap;
    }

    private int extractLevelFromPath(String filePath) {
        return 0;
    }
}
