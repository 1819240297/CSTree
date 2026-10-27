package org.example;

import java.util.List;

/**
 * 序列类，包含序列数据、顺序和标签
 */
public class Sequence {
    private List<Integer> items;  // 序列项
    private int order;             // 序列顺序
    private int label;             // 序列标签

    public Sequence(List<Integer> items, int order, int label) {
        this.items = items;
        this.order = order;
        this.label = label;
    }

    public List<Integer> getItems() {
        return items;
    }

    public int getOrder() {
        return order;
    }

    public int getLabel() {
        return label;
    }

    public int size() {
        return items.size();
    }

    public Integer get(int index) {
        return items.get(index);
    }
}

