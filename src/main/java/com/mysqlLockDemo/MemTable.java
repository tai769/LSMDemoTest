package com.mysqlLockDemo;

import java.security.Key;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentSkipListMap;

public class MemTable {
  private final ConcurrentSkipListMap<String, KeyValue> data;

  private final int maxSize;

  private volatile int currentSize;

  public MemTable() {
  }

  public MemTable(ConcurrentSkipListMap<String, KeyValue> data, int maxSize, int currentSize) {
    this.data = new ConcurrentSkipListMap<>();
    this.maxSize = maxSize;
    this.currentSize = currentSize;
  }

  public void put(String key, String value) {
    KeyValue kv = new KeyValue(key, value);
    KeyValue oldValue = data.put(key, kv);
    if (oldValue == null) {
      currentSize++;
    }
  }

  public void delete(String key) {
    KeyValue tobmStone = KeyValue.createTombstone(key);
    KeyValue oldValue = data.put(key, tobmStone);
    if (oldValue == null) {
      currentSize++;
    }
  }

  public String get(String key) {
    KeyValue kv = data.get(Key);
    if (kv == null || kv.isDeleted()) {
      return null;
    }
    return kv.getValue();
  }

  public boolean shouldFlush() {
    return currentSize >= maxSize;
  }

  /**
   * 获取所有键值对的有序列表
   */
  public List<KeyValue> getAllEntries() {
    return new ArrayList<>(data.values());
  }

  /**
   * 清空内存表
   */
  public void clear() {
    data.clear();
    currentSize = 0;
  }

  public int size() {
    return currentSize;
  }

  public boolean isEmpty() {
    return currentSize == 0;
  }

}
