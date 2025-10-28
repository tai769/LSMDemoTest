package com.mysqlLockDemo;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PipedReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import javax.security.auth.kerberos.KeyTab;

public class SSTable {

  private final String filePath;

  private final BloomFilter bloomFilter;

  private final long creationTime;

  public SSTable() {
  }

  public SSTable(String filePath, List<KeyValue> sortedData) throws IOException {
    this.filePath = filePath;
    this.bloomFilter = new BloomFilter(sortedData.size(), 0.01);
    this.creationTime = System.currentTimeMillis();

    wireTofile(sortedData);
  }

  /**
   * 从文件路径加载已存在的SSTable
   */
  public SSTable(String filePath) {
    this.filePath = filePath;
    this.creationTime = Files.getLastModifiedTime(Paths.get(filePath)).toMillis();
    this.bloomFilter = new BloomFilter(1000, 0.01);

    // 重新构建布隆过滤器
    rebuildBloomFilter();
  }

  /**
   * 重新构建布隆过滤器
   */
  private void rebuildBloomFilter() throws IOException {
    try (DataInputStream dis = new DataInputStream(
        new BufferedInputStream(new FileInputStream(filePath)))) {
      int totalEntries = dis.readInt();

      for (int i = 0; i < totalEntries; i++) {
        String key = dis.readUTF();
        boolean deleted = dis.readBoolean();
        if (!deleted) {
          dis.readUTF(); // 跳过value
        }
        dis.readLong();

        bloomFilter.add(key);
      }
    }
  }

  /*
   * 将排序数据写入文件
   */
  private void wireTofile(List<KeyValue> sortedDate) throws IOException {
    try (
        DataOutputStream dos = new DataOutputStream(
            new BufferedOutputStream(new FileOutputStream(filePath)))) {
      // 写入条目数量
      dos.writeInt(sortedDate.size());

      // 写入所有数据
      for (KeyValue kv : sortedDate) {
        // 添加到布隆过滤器
        bloomFilter.add(kv.getKey());

        // 写入数据 ： key ，deleted ， value（如果不是删除），timeStamp
        dos.writeUTF(kv.getKey());
        dos.writeBoolean(kv.isDeleted());
        if (!kv.isDeleted()) {
          dos.writeUTF(kv.getValue());
        }
        dos.writeLong(kv.getTimestamp());
      }
    }
  }

  /**
   * 查询键值 - 简化实现， 顺序搜索
   */ 
  public String get(String key){
    //首先检查布隆过滤器
    if(!bloomFilter.mightContain(key)){
      return null;
    }
    try(DataInputStream dis  = new DataInputStream(
    new BufferedInputStream(new FileInputStream(filePath)))){
      int totalEntries = dis.readInt();

      //顺序搜索所有条目
      for(int i = 0; i < totalEntries; i++){
        String currentKey = dis.readUTF();
        boolean deleted = dis.readBoolean();
        String value = null;
        if(!deleted){
          value = dis.readUTF();
        }
        Long timeStamp = dis.readLong();
        

        if(currentKey.equals(key)){
          return deleted ? null : value;
        }

        //由于数据有序，如果当前键大于目标键， 则不存在
        if(currentKey.compareTo(key) > 0){
          break;
        }
      }
    }catch(IOException e){
      e.printStackTrace();
    }
    return null
  }

  /*
   * 获取所有键值对（用于合并）
   */
  public List<KeyValue> getAllEntries() throws IOException {
    List<KeyValue> entries = new ArrayList<>();
    try (DataInputStream dis = new DataInputStream(
        new BufferedInputStream(
            new FileInputStream(filePath)))) {
      int totalEntries = dis.readInt();

      for (int i = 0; i < totalEntries; i++) {
        String key = dis.readUTF();
        boolean deleted = dis.readBoolean();
        String value = null;
        if (!deleted) {
          value = dis.readUTF();
        }
        long timeStamp = dis.readLong();

        entries.add(new KeyValue(key, value, timeStamp, deleted));
      }
    }
    return entries;
  }

  /**
   * 删除SSTable
   */
  public void delete() throws IOException {
    Files.deleteIfExists(Paths.get(filePath));
  }

  public String getFilePath() {
    return filePath;
  }

  public long getCreationTime() {
    return creationTime;
  }

}
