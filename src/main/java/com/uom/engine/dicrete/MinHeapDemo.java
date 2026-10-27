package com.uom.engine.discrete;

public class MinHeapDemo {
    public static void main(String[] args) {
        MinHeap heap = new MinHeap(2);          // tiny capacity, so it has to grow
        heap.push(5.0, 0);
        heap.push(3.0, 1);
        heap.push(8.0, 2);
        heap.push(1.0, 3);
        heap.push(3.0, 4);                      // duplicate key, which is allowed

        System.out.print("Pop order (vertex ids): ");
        while (!heap.isEmpty()) System.out.print(heap.popValue() + " ");
        System.out.println();
    }
}