package com.mysqlLockDemo;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/*
 * LSM树实现
 */
public class LSMTree implements  AutoCloseable{

    private final String dataDir;
    private final int memTableMaxSize;
    private final ReadWriteLock lock;

    //内存组件
    private volatile MemTable activeMemTable;
    private final List<MemTable> immutableMemTables;

    //磁盘组件
    private final List<SSTable> ssTables;

    //后台任务
    private final ExecutorService compactionExecutor;
    private final CompactionStrategy compactionStrategy;

    //WAL相关
    private final WriteAheadLog wal;

    public LSMTree(String dataDir, int memTableMaxSize)throws IOException{
        this.dataDir = dataDir;
        this.memTableMaxSize = memTableMaxSize;
        this.lock = new ReentrantReadWriteLock();

        //初始化目录
        createDirectoryIfNotExists(dataDir);

        //初始化组件
        this.activeMemTable = new MemTable(new ConcurrentSkipListMap<>(), memTableMaxSize, 0);
        this.immutableMemTables = new ArrayList<>();
        this.ssTables = new ArrayList<>();

        //初始化压缩策略
        this.compactionStrategy = new CompactionStrategy(dataDir, 4, 10);
        //初始化wal
        this.wal = new WriteAheadLog(dataDir + "/wal.log");

        //启动后台压缩任务
        this.compactionExecutor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r,"LSMTree-Compaction");
            t.setDaemon( true);
            return t;
        });

        //恢复现在现有数据
        recover();

        //暂时禁用后台压缩人物，避免测试时的线程问题
        //startBackgroundCompactionTask();
    }

    /*
     * 插入键值对
     */
    public void put(String key, String value) throws IOException {
        if (key == null || value == null){
            throw new IllegalArgumentException("key and value cannot be null");
        }

        lock.writeLock().lock();
        try{
            //写入wal
            wal.append(WriteAheadLog.LogEntry.put(key, value));
            //写入内存表
            activeMemTable.put(key, value);

            //检查是否需要刷盘
            if (activeMemTable.shouldFlush()){
                flushMemTable();
            }
        }finally {
            lock.writeLock().unlock();
        }
    }

    //删除键值对
    public void delete(String key) throws IOException {
        if (key == null){
            throw new IllegalArgumentException("key cannot be null")
        }
        lock.writeLock().lock();
        try {
            //写入WAL
            wal.append(WriteAheadLog.LogEntry.delete(key));
            activeMemTable.delete(key);
            //检查是否需要刷盘
            if (activeMemTable.shouldFlush()){
                flushMemTable();
            }
        }finally {
            lock.writeLock().unlock();
        }
    }

    /*
     * 查询键值对
     */
    public String get(String key) {
        if (key == null){
            throw new IllegalArgumentException("key cannot be null")
        }
        lock.readLock().lock();
        try {
            // 1. 首先查询活跃的MemTable
            String value = activeMemTable.get(key);
            if (value != null){
                return value;
            }
            //2. 查询不可变的MemTable
            for (int i = immutableMemTables.size()-1;  i > 0; i--){
                value = immutableMemTables.get(i).get(key);
                if (value != null){
                    return value;
                }
            }

            //3. 查询SSTable
            List<SSTable> sortedSSTables = new ArrayList<>(ssTables);

        }
    }



    @Override
    public void close() throws Exception {

    }
}
