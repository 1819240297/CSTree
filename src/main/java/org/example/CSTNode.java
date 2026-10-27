package org.example;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.BiConsumer;

/**
 * Lightweight Count-Suffix Tree node.
 *
 * The hot-path storage avoids per-node HashMap objects for label counts and
 * low-degree children. This keeps the classifier semantics the same while
 * reducing the number of long-lived objects in large CSTs.
 */
public class CSTNode {
    private static final int INLINE_CHILD_LIMIT = 8;
    private static final int INITIAL_CHILD_CAPACITY = 2;
    private static final int INITIAL_LABEL_CAPACITY = 2;

    private int[] childKeys;
    private CSTNode[] childNodes;
    private int childSize;
    private Map<Integer, CSTNode> childMap;

    private int[] labelKeys;
    private int[] labelCounts;
    private int labelSize;
    private int totalLabelCount;

    public CSTNode() {
        this.childKeys = new int[INITIAL_CHILD_CAPACITY];
        this.childNodes = new CSTNode[INITIAL_CHILD_CAPACITY];
        this.labelKeys = new int[INITIAL_LABEL_CAPACITY];
        this.labelCounts = new int[INITIAL_LABEL_CAPACITY];
    }

    public CSTNode getOrCreateChild(Integer item) {
        int key = item;
        if (childMap != null) {
            return childMap.computeIfAbsent(key, ignored -> new CSTNode());
        }

        int index = findInlineChildIndex(key);
        if (index >= 0) {
            return childNodes[index];
        }

        CSTNode child = new CSTNode();
        if (childSize < INLINE_CHILD_LIMIT) {
            ensureChildCapacity(childSize + 1);
            childKeys[childSize] = key;
            childNodes[childSize] = child;
            childSize++;
            return child;
        }

        promoteChildrenToMap();
        childMap.put(key, child);
        return child;
    }

    public CSTNode getChild(Integer item) {
        int key = item;
        if (childMap != null) {
            return childMap.get(key);
        }

        int index = findInlineChildIndex(key);
        return index >= 0 ? childNodes[index] : null;
    }

    public void updateCount(int order, int label) {
        incrementLabelCount(label);
    }

    public Map<Integer, CSTNode> getChildren() {
        if (childMap != null) {
            return childMap;
        }

        Map<Integer, CSTNode> result = new LinkedHashMap<>(childSize);
        forEachChild(result::put);
        return result;
    }

    public int getChildCount() {
        return childMap != null ? childMap.size() : childSize;
    }

    public void forEachChild(BiConsumer<Integer, CSTNode> consumer) {
        if (childMap != null) {
            childMap.forEach(consumer);
            return;
        }

        for (int i = 0; i < childSize; i++) {
            consumer.accept(childKeys[i], childNodes[i]);
        }
    }

    public Map<Integer, Integer> getLabelCountMap() {
        Map<Integer, Integer> result = new LinkedHashMap<>(labelSize);
        for (int i = 0; i < labelSize; i++) {
            result.put(labelKeys[i], labelCounts[i]);
        }
        return result;
    }

    public int getLabelEntryCount() {
        return labelSize;
    }

    public int getLabelAt(int index) {
        return labelKeys[index];
    }

    public int getLabelCountAt(int index) {
        return labelCounts[index];
    }

    public int getTotalLabelCount() {
        return totalLabelCount;
    }

    public String getCountVectorString() {
        if (labelSize == 0) {
            return "()";
        }

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < labelSize; i++) {
            sb.append("(")
                    .append(labelKeys[i])
                    .append(",")
                    .append(labelCounts[i])
                    .append(")");
        }
        return sb.toString();
    }

    public int getCount(int label) {
        int index = findLabelIndex(label);
        return index >= 0 ? labelCounts[index] : 0;
    }

    private int findInlineChildIndex(int key) {
        for (int i = 0; i < childSize; i++) {
            if (childKeys[i] == key) {
                return i;
            }
        }
        return -1;
    }

    private void ensureChildCapacity(int minCapacity) {
        if (childKeys.length >= minCapacity) {
            return;
        }

        int newCapacity = Math.max(minCapacity, childKeys.length * 2);
        int[] newKeys = new int[newCapacity];
        CSTNode[] newNodes = new CSTNode[newCapacity];
        System.arraycopy(childKeys, 0, newKeys, 0, childSize);
        System.arraycopy(childNodes, 0, newNodes, 0, childSize);
        childKeys = newKeys;
        childNodes = newNodes;
    }

    private void promoteChildrenToMap() {
        childMap = new LinkedHashMap<>(INLINE_CHILD_LIMIT * 2);
        for (int i = 0; i < childSize; i++) {
            childMap.put(childKeys[i], childNodes[i]);
        }
        childKeys = null;
        childNodes = null;
        childSize = 0;
    }

    private void incrementLabelCount(int label) {
        int index = findLabelIndex(label);
        if (index >= 0) {
            labelCounts[index]++;
            totalLabelCount++;
            return;
        }

        ensureLabelCapacity(labelSize + 1);
        int insertAt = labelSize;
        while (insertAt > 0 && labelKeys[insertAt - 1] > label) {
            labelKeys[insertAt] = labelKeys[insertAt - 1];
            labelCounts[insertAt] = labelCounts[insertAt - 1];
            insertAt--;
        }
        labelKeys[insertAt] = label;
        labelCounts[insertAt] = 1;
        labelSize++;
        totalLabelCount++;
    }

    private int findLabelIndex(int label) {
        int low = 0;
        int high = labelSize - 1;
        while (low <= high) {
            int mid = (low + high) >>> 1;
            int value = labelKeys[mid];
            if (value < label) {
                low = mid + 1;
            } else if (value > label) {
                high = mid - 1;
            } else {
                return mid;
            }
        }
        return -1;
    }

    private void ensureLabelCapacity(int minCapacity) {
        if (labelKeys.length >= minCapacity) {
            return;
        }

        int newCapacity = Math.max(minCapacity, labelKeys.length * 2);
        int[] newKeys = new int[newCapacity];
        int[] newCounts = new int[newCapacity];
        System.arraycopy(labelKeys, 0, newKeys, 0, labelSize);
        System.arraycopy(labelCounts, 0, newCounts, 0, labelSize);
        labelKeys = newKeys;
        labelCounts = newCounts;
    }
}
