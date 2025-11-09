package com.mysqlLockDemo;

import java.io.IOException;
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

        for (Map.Entry<Integer , List<SSTable>> entry: levelmap.entrySet()){
            int level = entry.getKey();
            List<SSTable> tablesInLevel = levelmap.get(level);

            int maxSize = level == 0 ? maxLevelSize : maxLevelSize * (int) Math.pow(levelSizeMultiplier, level);
            if (tablesInLevel.size() > maxSize){
                return true;
            }
        }
        return false;
    }

    /*
     * 执行压缩操作
     */
    public List<SSTable> compact(List<SSTable> ssTables) throws IOException {
        Map<Integer, List<SSTable>> levelMap = groupByLevel(ssTables);
        List<SSTable>  newTables = new ArrayList<>();
        for(Map.Entry<Integer,List<SSTable>> entry  : levelMap.entrySet()){
            Integer level = entry.getKey();
            List<SSTable> tablesInLevel = entry.getValue();
            int maxSize = level == 0 ? maxLevelSize : maxLevelSize * (int) Math.pow(levelSizeMultiplier, level);
            if (tablesInLevel.size() > maxSize){
                //需要压缩这个级别
                List<SSTable> compactedTables = compactLevel(tablesInLevel , level + 1);
                newTables.addAll(compactedTables);

                //删除旧的SSTable文件
                for (SSTable oldTable : tablesInLevel){
                    oldTable.delete();
                }
            }else {
                newTables.addAll(tablesInLevel);
            }
        }
        return newTables;
    }

    private List<SSTable> compactLevel(List<SSTable> tables, int targetLevel) throws IOException {
        //收集所有键值对
        List<KeyValue> allEntries = new ArrayList<>();
        for (SSTable table : tables){
            allEntries.addAll(table.getAllEntries());
        }
        //合并排序去重
        List<KeyValue> mergeEntries = mergeAndDedup(allEntries);
        //分割成多个SSTable （如果数据太大）
        List<SSTable> newTables = new ArrayList<>();
        int entriesPerTable = 10000;
        for (int i = 0 ; i < mergeEntries.size(); i+= entriesPerTable){
            int endIndex = Math.min(i + entriesPerTable, mergeEntries.size());
            List<KeyValue> tableEntries = mergeEntries.subList(i, endIndex);
            String fileName = String.format("%s/sstable_level%d_%d_%d.db",
                    dataDir, targetLevel, System.currentTimeMillis(), i);
            SSTable newTable = new SSTable(fileName, tableEntries);
            newTables.add(newTable);
        }
        return newTables;
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

    /**
     *
     * 合并和去重键值对
     * 保留每个键的最新版班
     */
    private List<KeyValue> mergeAndDedup(List<KeyValue> entries){
        //按照键和时间戳排序
        entries.sort(KeyValue::compareTo);

        List<KeyValue> dedupedEntries = new ArrayList<>();
        Map<String, KeyValue> lastestEntries = new HashMap<>();

        //保留每个键的最新版本
        for (KeyValue entry : entries){
            String key = entry.getKey();
            if (!lastestEntries.containsKey(key) ||
                entry.getTimestamp() > lastestEntries.get(key).getTimestamp()){
                lastestEntries.put(key, entry);
            }
        }
        //移除删除标记的过期条目
        for (KeyValue entry : lastestEntries.values()){
            if (!entry.isDeleted()){
                dedupedEntries.add(entry);
            }
        }

        //最终排序
        dedupedEntries.sort((a,b) -> a.getKey().compareTo(b.getKey()));
        return dedupedEntries;
    }

    private int extractLevelFromPath(String filePath) {
        //从文件名中解析级别，   例如: sstable_level1_timestamp_index.db
        String fileName = filePath.substring(filePath.lastIndexOf('/')+1);
        if (fileName.contains("level")){
            try{
                String levelStr = fileName.substring(fileName.indexOf("level") + 5);
                levelStr = levelStr.substring(0,levelStr.indexOf('_'));
                return Integer.parseInt(levelStr);
            }catch (Exception e){
                return 0; // 默认级别
            }
        }
        return 0;
    }

    /*
     * 大小分层压缩策略
     * 当某个级别的SSTable数量超过阈值时触发压缩
     */
    public CompactionTask selectCompactionTask(List<SSTable> ssTables) {
        Map<Integer, List<SSTable>> levelMap = groupByLevel(ssTables);

        for (int level = 0; level < 10; level++){//最多10个级别
            List<SSTable> tablesInLevel = levelMap.getOrDefault(level, new ArrayList<>());
            int maxSize = level == 0 ? maxLevelSize : maxLevelSize * (int) Math.pow(levelSizeMultiplier, level);

            if (tablesInLevel.size() > maxSize){
                return new  CompactionTask(level, tablesInLevel);
            }

        }
        return null; // 不需要压缩
    }

    public  static class CompactionTask{
        private final int level;
        private final List<SSTable> tables;

        public CompactionTask(int level, List<SSTable> tables) {
            this.level = level;
            this.tables = tables;
        }

        public int getLevel() {
            return level;
        }

        public List<SSTable> getTables() {
            return tables;
        }
    }
}
