package com.uom.engine.discrete;

import java.util.Arrays;

final class MinHeap {
    private double[] keys;     // priority: the cost to reach the vertex
    private int[] values;      // the vertex id
    private int size;          // how many entries are in use

    MinHeap(int initialCapacity) {
        keys = new double[Math.max(2, initialCapacity)];
        values = new int[keys.length];
    }

    boolean isEmpty() { return size == 0; }

    void push(double key, int value) {
        if (size == keys.length) {                       // grow by doubling: amortised O(1)
            keys = Arrays.copyOf(keys, size * 2);
            values = Arrays.copyOf(values, size * 2);
        }
        int i = size++;
        while (i > 0) {                                  // sift up
            int parent = (i - 1) >>> 1;
            if (keys[parent] <= key) break;
            keys[i] = keys[parent];
            values[i] = values[parent];
            i = parent;
        }
        keys[i] = key;
        values[i] = value;
    }

    int popValue() {
        int top = values[0];
        size--;
        if (size>0) {
            double key = keys[size];
            int value = values[size];
            int i = 0;
            while (true) {                                // sift down
                int child = 2*i+1;
                if (child >= size) break;
                if (child+1 < size && keys[child+1] < keys[child]) child++;
                if (keys[child]>=key) break;
                keys[i] = keys[child];
                values[i] = values[child];
                i = child;
            }
            keys[i] = key;
            values[i] = value;
        }
        return top;
    }
}