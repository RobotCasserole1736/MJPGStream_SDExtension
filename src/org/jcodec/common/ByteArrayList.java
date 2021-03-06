package org.jcodec.common;

import java.util.Arrays;

import static java.lang.System.arraycopy;

/**
 * This class is part of JCodec ( www.jcodec.org ) This software is distributed
 * under FreeBSD License
 * 
 * @author The JCodec project
 * 
 */
public class ByteArrayList {
    private static final int DEFAULT_GROW_AMOUNT = 2048;
    private byte[] storage;
    private int size;
    private int growAmount;

    public ByteArrayList() {
        this(DEFAULT_GROW_AMOUNT);
    }

    public ByteArrayList(int growAmount) {
        this.growAmount = growAmount;
        this.storage = new byte[growAmount];
    }

    public byte[] toArray() {
        byte[] result = new byte[size];
        arraycopy(storage, 0, result, 0, size);
        return result;
    }

    public void add(byte val) {
        if (size >= storage.length) {
            byte[] ns = new byte[storage.length + growAmount];
            arraycopy(storage, 0, ns, 0, storage.length);
            storage = ns;
        }
        storage[size++] = val;
    }
    
    public void push(byte id) {
        this.add(id);
    }
    
    public void pop() {
        if (size == 0)
            return;
        size--;
    }

    public void set(int index, byte value) {
        storage[index] = value;
    }

    public byte get(int index) {
        return storage[index];
    }

    public void fill(int start, int end, byte val) {
        if (end > storage.length) {
            byte[] ns = new byte[end + growAmount];
            arraycopy(storage, 0, ns, 0, storage.length);
            storage = ns;
        }
        Arrays.fill(storage, start, end, val);
        size = Math.max(size, end);
    }

    public int size() {
        return size;
    }

    public void addAll(byte[] other) {
        if (size + other.length >= storage.length) {
            byte[] ns = new byte[size + growAmount + other.length];
            arraycopy(storage, 0, ns, 0, size);
            storage = ns;
        }
        arraycopy(other, 0, storage, size, other.length);
        size += other.length;
    }
    
    public boolean contains(byte needle) {
        for (int i = 0; i < size; i++)
            if (storage[i] == needle)
                return true;
        return false;
    }
}
