package com.mysqlLockDemo;

public class KeyValue implements Comparable<KeyValue> {
  private final String key;

  private final String value;

  private final long timestamp;

  private final boolean deleted;

  public String getKey() {
    return key;
  }

  public String getValue() {
    return value;
  }

  public long getTimestamp() {
    return timestamp;
  }

  public boolean isDeleted() {
    return deleted;
  }

  public KeyValue(String key, String value) {
    this(key, value, System.currentTimeMillis(), false);
  }

  public KeyValue(String key, String value, long timestamp, boolean deleted) {
    this.key = key;
    this.value = value;
    this.timestamp = timestamp;
    this.deleted = deleted;
  }

  public static KeyValue createTombstone(String key) {
    return new KeyValue(key, null,System.currentTimeMillis(), true);

  }

  @Override
  public int compareTo(KeyValue o) {
    // TODO Auto-generated method stub
    int keyCompare = this.key.compareTo(o.key);
    if (keyCompare != 0) {
      return keyCompare;
    }
    return Long.compare(o.timestamp, this.timestamp);
  }

}
