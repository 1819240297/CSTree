package org.example;

import java.io.IOException;
import java.util.List;

/**
 * 主程序：在reuters数据集上构建CSTree
 */
public class Main {
    public static void main(String[] args) {
        try {
            // 加载数据集
            String datasetPath = "src/dataset/reuters.txt";
            System.out.println("正在加载数据集: " + datasetPath);
            List<Sequence> sequences = DataSetLoader.loadDataSet(datasetPath);
            System.out.println("加载完成，共 " + sequences.size() + " 个序列");
            
            // 设置参数
            int u = 4;  // u: 最大子序列长度
            double c = 0.5;  // c: 采样百分比
            
            System.out.println("\n开始构建CSTree...");
            System.out.println("参数设置:");
            System.out.println("  T (训练序列集): " + sequences.size() + " 个序列");
            System.out.println("  u (最大子序列长度): " + u);
            System.out.println("  c (采样百分比): " + (c * 100) + "%");
            
            // 构建CSTree：CSTree (T, u, c)
            CSTree csTree = CSTree.build(sequences, u, c);
            
            System.out.println("\nCSTree构建完成！");
            
            // 打印统计信息
            csTree.printStatistics();
            
            // 打印树结构（与论文图4格式一致）
            System.out.println("\n树结构（格式与论文图4一致）:");
            System.out.println("说明：边（edge）上显示字符/文本，节点（node）上显示计数向量 (类别,计数)");
            
            // 如果是文本数据集，获取ID到文本的映射以显示原始文本
            java.util.Map<Integer, String> idToTextMap = null;
            if (DataSetLoader.isTextDataset()) {
                idToTextMap = DataSetLoader.getIdToTextMap();
                System.out.println("注意：这是文本数据集，边显示的是原始单词（而非数字ID）");
            } else {
                System.out.println("注意：这是数字数据集，边显示的是数字");
            }
            System.out.println();
            csTree.printTree(5, idToTextMap);  // 限制深度为5层，避免输出过多
            
        } catch (IOException e) {
            System.err.println("加载数据集时出错: " + e.getMessage());
            e.printStackTrace();
        } catch (Exception e) {
            System.err.println("发生错误: " + e.getMessage());
            e.printStackTrace();
        }
    }
}