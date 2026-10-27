package org.example;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 数据集加载器
 * 支持两种格式：
 * 1. 数字序列：每行第一列是标签，后面是数字序列（空格分隔）
 * 2. 文本序列：每行第一列是标签，后面是文本序列（空格分隔），文本会被映射为整数ID
 */
public class DataSetLoader {
    private static Map<String, Integer> textToIdMap = new HashMap<>();
    private static Map<Integer, String> idToTextMap = new HashMap<>();  // 反向映射：ID -> 文本
    private static Map<String, Integer> labelToIdMap = new HashMap<>();  // 标签文本到ID的映射
    private static Map<Integer, String> idToLabelMap = new HashMap<>();  // ID到标签文本的反向映射
    private static int nextId = 0;
    private static int nextLabelId = 0;  // 标签ID计数器（独立于序列项ID）
    private static boolean isTextDataset = false;  // 标记是否为文本数据集
    
    /**
     * 从文件加载数据集
     * 格式：每行第一列是标签，后面是序列数据（空格分隔）
     */
    public static List<Sequence> loadDataSet(String filePath) throws IOException {
        List<Sequence> sequences = new ArrayList<>();
        textToIdMap.clear();
        idToTextMap.clear();
        labelToIdMap.clear();
        idToLabelMap.clear();
        nextId = 0;
        nextLabelId = 0;
        isTextDataset = false;
        
        try (BufferedReader reader = new BufferedReader(new FileReader(filePath))) {
            String line;
            int order = 0;
            boolean isNumeric = true;  // 假设是数字格式
            
            // 先读取第一行来判断格式
            String firstLine = reader.readLine();
            if (firstLine == null) {
                return sequences;
            }
            
            String[] firstParts = firstLine.trim().split("\\s+");
            if (firstParts.length >= 2) {
                // 检查第二个元素是否是数字
                try {
                    Integer.parseInt(firstParts[1]);
                    isNumeric = true;
                    isTextDataset = false;
                } catch (NumberFormatException e) {
                    isNumeric = false;
                    isTextDataset = true;  // 文本数据集
                }
            }
            
            // 处理第一行
            if (firstParts.length >= 2) {
                int label = parseLabel(firstParts[0]);
                List<Integer> items = parseSequenceItems(firstParts, 1, isNumeric);
                sequences.add(new Sequence(items, order++, label));
            }
            
            // 处理剩余行
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) {
                    continue;
                }
                
                String[] parts = line.split("\\s+");
                if (parts.length < 2) {
                    continue;
                }
                
                // 第一列是标签（可能是数字或文本）
                int label = parseLabel(parts[0]);
                
                // 后续列是序列数据
                List<Integer> items = parseSequenceItems(parts, 1, isNumeric);
                
                sequences.add(new Sequence(items, order++, label));
            }
        }
        
        return sequences;
    }
    
    /**
     * 解析序列项，支持数字和文本两种格式
     */
    private static List<Integer> parseSequenceItems(String[] parts, int startIndex, boolean isNumeric) {
        List<Integer> items = new ArrayList<>();
        
        if (isNumeric) {
            // 数字格式：直接解析
            for (int i = startIndex; i < parts.length; i++) {
                try {
                    items.add(Integer.parseInt(parts[i]));
                } catch (NumberFormatException e) {
                    // 如果遇到非数字，转换为文本处理
                    items.add(getOrCreateTextId(parts[i]));
                }
            }
        } else {
            // 文本格式：映射为整数ID
            for (int i = startIndex; i < parts.length; i++) {
                items.add(getOrCreateTextId(parts[i]));
            }
        }
        
        return items;
    }
    
    /**
     * 获取文本对应的整数ID，如果不存在则创建新的ID
     */
    private static int getOrCreateTextId(String text) {
        int id = textToIdMap.computeIfAbsent(text, k -> {
            int newId = nextId++;
            idToTextMap.put(newId, text);  // 同时创建反向映射
            return newId;
        });
        // 确保反向映射存在（处理已存在的情况）
        if (!idToTextMap.containsKey(id)) {
            idToTextMap.put(id, text);
        }
        return id;
    }
    
    /**
     * 获取文本到ID的映射（用于调试）
     */
    public static Map<String, Integer> getTextToIdMap() {
        return new HashMap<>(textToIdMap);
    }
    
    /**
     * 获取ID到文本的反向映射（用于显示）
     */
    public static Map<Integer, String> getIdToTextMap() {
        return new HashMap<>(idToTextMap);
    }
    
    /**
     * 判断是否为文本数据集
     */
    public static boolean isTextDataset() {
        return isTextDataset;
    }
    
    /**
     * 根据ID获取对应的文本，如果是数字数据集则返回数字字符串
     */
    public static String getItemDisplay(Integer id) {
        if (isTextDataset && idToTextMap.containsKey(id)) {
            return idToTextMap.get(id);
        } else {
            // 数字数据集或未映射的ID，返回数字字符串
            return String.valueOf(id);
        }
    }
    
    /**
     * 解析标签（支持数字和文本标签）
     * @param labelStr 标签字符串
     * @return 标签的整数ID
     */
    private static int parseLabel(String labelStr) {
        try {
            // 尝试解析为整数
            return Integer.parseInt(labelStr);
        } catch (NumberFormatException e) {
            // 如果是文本标签，映射为整数ID
            return labelToIdMap.computeIfAbsent(labelStr, k -> {
                int newLabelId = nextLabelId++;
                idToLabelMap.put(newLabelId, labelStr);
                return newLabelId;
            });
        }
    }
    
    /**
     * 获取标签ID到标签文本的映射
     */
    public static Map<Integer, String> getIdToLabelMap() {
        return new HashMap<>(idToLabelMap);
    }
    
    /**
     * 根据标签ID获取标签文本
     */
    public static String getLabelText(Integer labelId) {
        return idToLabelMap.getOrDefault(labelId, String.valueOf(labelId));
    }
}
