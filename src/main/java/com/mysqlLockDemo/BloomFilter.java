package com.mysqlLockDemo;

import java.util.BitSet;

/*
 * bloom过滤器
 */
public class BloomFilter {
    private final BitSet bitSet;
    private final int size;
    private final int hashFunctions;

    public BloomFilter(int expectedElements, double falsePositiveRate) {
        this.size = (int)(-expectedElements * Math.log(falsePositiveRate)
            /(Math.log(2) * Math.log(2)));
        this.hashFunctions = (int)(size * Math.log(2) / expectedElements);
        bitSet = new BitSet(size);
    }


    /*
     * 添加元素
     */
    public void add(String key){
        for (int i = 0; i < hashFunctions; i++){
            int hash = hash(key,i);
            bitSet.set(Math.abs(hash % size));
        }
    }

    /*
     * 多重hash实现
     * 使用Double Hashing技术避免实现多个独立的哈希函数
     */
    private int hash(String key, int i) {
        int hash1 = key.hashCode();
        int hash2 = hash1 >>> 16;
        return hash1 + i * hash2 ;
    }

    public byte[] toByteArray(){
        return bitSet.toByteArray();
    }

    /*
     * 从字节数组中恢复bloom过滤器
     */
    public static BloomFilter fromByteArray(byte[] bytes, int size, int hashFunctions){
        BloomFilter filter = new BloomFilter(1000, 0.01);
        filter.bitSet.clear();
        BitSet restored = BitSet.valueOf(bytes);
        filter.bitSet.or(restored);
        return filter;
    }

    public boolean mightContain(String key){
        for (int i = 0; i < hashFunctions; i++){
            int hash = hash(key,i);
            if (!bitSet.get(Math.abs(hash % size))){
                return false;
            }
        }
        return true;
    }
}
