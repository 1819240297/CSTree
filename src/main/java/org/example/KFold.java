package org.example;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * K折交叉验证工具类
 * 参考sklearn的KFold实现
 */
public class KFold {
    private int nSplits;
    private boolean shuffle;
    private Random random;
    
    /**
     * 构造函数
     * @param nSplits 折数（如5表示5折交叉验证）
     * @param shuffle 是否打乱数据
     * @param randomState 随机种子
     */
    public KFold(int nSplits, boolean shuffle, int randomState) {
        this.nSplits = nSplits;
        this.shuffle = shuffle;
        this.random = new Random(randomState);
    }
    
    /**
     * 生成训练集和测试集的索引
     * @param nSamples 总样本数
     * @return 迭代器，每次返回(trainIndices, testIndices)
     */
    public List<FoldSplit> split(int nSamples) {
        List<FoldSplit> splits = new ArrayList<>();
        
        // 生成索引列表
        List<Integer> indices = new ArrayList<>();
        for (int i = 0; i < nSamples; i++) {
            indices.add(i);
        }
        
        // 如果需要打乱
        if (shuffle) {
            Collections.shuffle(indices, random);
        }
        
        // 计算每折的大小
        int foldSize = nSamples / nSplits;
        int remainder = nSamples % nSplits;
        
        int start = 0;
        for (int i = 0; i < nSplits; i++) {
            // 当前折的大小（如果有余数，前面的折多分配一个）
            int currentFoldSize = foldSize + (i < remainder ? 1 : 0);
            int end = start + currentFoldSize;
            
            // 测试集索引
            List<Integer> testIndices = new ArrayList<>(indices.subList(start, end));
            
            // 训练集索引（除了测试集之外的所有索引）
            List<Integer> trainIndices = new ArrayList<>();
            trainIndices.addAll(indices.subList(0, start));
            trainIndices.addAll(indices.subList(end, nSamples));
            
            splits.add(new FoldSplit(trainIndices, testIndices));
            
            start = end;
        }
        
        return splits;
    }
    
    /**
     * 单次折叠的划分结果
     */
    public static class FoldSplit {
        private List<Integer> trainIndices;
        private List<Integer> testIndices;
        
        public FoldSplit(List<Integer> trainIndices, List<Integer> testIndices) {
            this.trainIndices = trainIndices;
            this.testIndices = testIndices;
        }
        
        public List<Integer> getTrainIndices() {
            return trainIndices;
        }
        
        public List<Integer> getTestIndices() {
            return testIndices;
        }
    }
}
