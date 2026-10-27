package org.example;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 分类器工具类
 * 提供一些辅助方法用于分类器的构建和使用
 */
public class ClassifierUtils {
    
    /**
     * 从训练序列集中计算每个类别的总数
     * 与personal.py中的train_count计算一致
     * 
     * @param trainingSet 训练序列集
     * @return 每个类别标签及其在训练集中的总数
     */
    public static Map<Integer, Integer> computeTrainCount(List<Sequence> trainingSet) {
        Map<Integer, Integer> trainCount = new HashMap<>();
        
        for (Sequence seq : trainingSet) {
            int label = seq.getLabel();
            trainCount.put(label, trainCount.getOrDefault(label, 0) + 1);
        }
        
        return trainCount;
    }
    
    /**
     * 从训练序列集中提取去重后的标签集合（HashSet 自动去重）
     * 
     * @param trainingSet 训练序列集
     * @return 所有唯一的类别标签集合
     */
    public static java.util.Set<Integer> extractLabelSet(List<Sequence> trainingSet) {
        java.util.Set<Integer> labelSet = new java.util.HashSet<>();
        
        for (Sequence seq : trainingSet) {
            labelSet.add(seq.getLabel());
        }
        
        return labelSet;
    }
}
