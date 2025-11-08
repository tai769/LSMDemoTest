package com.mysqlLockDemo;



import java.io.*;
import java.util.ArrayList;
import java.util.List;

/*
 * Write - Ahead Log 实现
 * 确保数据持久化和崩溃恢复
 */
public class WriteAheadLog {
    private final String filePath;
    private BufferedWriter writer;
    private final Object lock = new Object();

    public WriteAheadLog(String filePath) throws IOException {
        this.filePath = filePath;
        this.writer = new BufferedWriter(new FileWriter(filePath, true));
    }

    /*
     * 追加日志条数
     */
    public void append(LogEntry entry) throws IOException{
        synchronized (lock){
            writer.write(entry.toString());
            writer.newLine();
            writer.flush();//确保立即刷入磁盘
        }
    }

    /*
     * 检查点操作 - 清理已经刷盘的日志
     */
    public void  checkpoint() throws IOException{
        synchronized (lock){
            if (writer != null){
                writer.close();
            }
            //创建新的空wal文件
            File file = new File(filePath);
            if (file.exists()){
                file.delete();
            }

            //重新打开writer
            this.writer = new BufferedWriter(new FileWriter(filePath, true));
        }
    }

    /*
     * 恢复操作
     */
    public List<LogEntry> recover() throws  IOException{
        List<LogEntry> entries = new ArrayList<>();
        File file = new File(filePath);
        if (!file.exists()){
            return entries;
        }
        try(BufferedReader reader = new BufferedReader(new FileReader(filePath))){
            String line;
            while ((line = reader.readLine()) != null){
                LogEntry entry = LogEntry.fromString(line);
                if (entry != null){
                    entries.add(entry);
                }
            }
        }
        return entries;
    }




    /*
     * WAL日志条目
     */
    public static class LogEntry {
        private final Operation operation;
        private final String key;
        private final String value;
        private final Long timestamp;
        public LogEntry(Operation operation, String key, String value, Long timestamp) {
            this.operation = operation;
            this.key = key;
            this.value = value;
            this.timestamp = timestamp;
        }

        public static LogEntry put(String key, String value){
            return new LogEntry(Operation.PUT, key, value, System.currentTimeMillis());
        }

        public static LogEntry delete(String key){
            return new LogEntry(Operation.DELETE, key, null, System.currentTimeMillis());
        }

        public String getValue() {
            return value;
        }

        public String getKey() {
            return key;
        }

        public Operation getOperation() {
            return operation;
        }
        public Long getTimestamp() {
            return timestamp;
        }

        @Override
        public String toString() {
            return String.format("%s|%s|%s|%d", operation, key, value != null ? value : "",  timestamp);
        }

        public static LogEntry fromString(String line){
            if (line == null || line.trim().isEmpty()){
                return  null;
            }
            String[] parts = line.split("\\|", 4);
            if (parts.length < 3){
                return null;
            }
            try{
                Operation op = Operation.valueOf(parts[0]);
                String key = parts[1];
                String value = parts.length > 2 && !parts[2].isEmpty() ? parts[2] : null;
                long timestamp = parts.length > 3 ? Long.parseLong(parts[3]) : System.currentTimeMillis();

                return new LogEntry(op, key, value, timestamp);
            }catch (Exception e){
                return  null;//忽略
            }
        }
    }
    /*
     * 操作枚举
     */
    public enum Operation {
        PUT,
        DELETE
    }


}
