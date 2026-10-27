package org.example;

import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * Count-Suffix Tree实现
 * u 控制的是"项的数量"，而不是"字符的数量"
 * u = 4 的含义：可以提取的子串最多包含4个单词
 * 例如：`["comput", "termin", "system", "cpml"]` ✓（4个单词）
 */
public class CSTree {
    private CSTNode root;
    private int maxSubsequenceLength;  // u: 最大子序列长度
    private double samplePercentage;   // c: 采样百分比
    private Random random;

    /**
     * 私有构造函数
     */
    private CSTree() {
        this.root = new CSTNode();
        this.random = new Random();
    }

    /**
     * 算法3: CSTree (T, u, c)
     * 
     * 输入：当前节点的序列集 T，参数 c（采样百分比）、u（最大子序列长度/树高限制）
     * 输出：一棵构建好的随机化的计数后缀树 CST
     * 
     * @param T 训练序列集
     * @param u 最大子序列长度（树高限制）
     * @param c 采样百分比（0.0-1.0）
     * @return 构建好的CSTree
     */
    public static CSTree build(List<Sequence> T, int u, double c) {
        return build(T, u, c, null);
    }

    /**
     * 构建CSTree（可指定随机种子，便于重复实验可复现）
     * @param T 训练序列集
     * @param u 最大子序列长度（树高限制）
     * @param c 采样百分比（0.0-1.0）
     * @param seed 随机种子（可为null，表示使用默认随机）
     * @return 构建好的CSTree
     */
    public static CSTree build(List<Sequence> T, int u, double c, Long seed) {
        CSTree tree = new CSTree();
        tree.maxSubsequenceLength = u;
        tree.samplePercentage = c;
        if (seed != null) {
            tree.random = new Random(seed);
        }
        
        // 步骤1: root ← createNode()
        // (已在构造函数中完成)
        
        // 步骤2: for i ← 1 to |T| · c% do
        // 根据参数c，从T中选择c%的序列用于构建CST。
        // 论文中为了随机森林的多样性使用“随机采样”，但更合理的做法是：
        //   - 先对序列索引做一次随机打乱（shuffle）
        //   - 再取前 sampleSize 条序列（无放回抽样）
        // 这样保证每条被选中的序列只出现一次，避免同一序列被重复采样导致计数异常。
        int sampleSize = (int) (T.size() * c);
        if (sampleSize <= 0) {
            sampleSize = 1;
        }

        // 构造索引并打乱，实现“无放回随机采样”
        int n = T.size();
        int[] indices = new int[n];
        for (int i = 0; i < n; i++) {
            indices[i] = i;
        }
        // Fisher-Yates 洗牌
        for (int i = n - 1; i > 0; i--) {
            int j = tree.random.nextInt(i + 1);
            int tmp = indices[i];
            indices[i] = indices[j];
            indices[j] = tmp;
        }

        for (int k = 0; k < sampleSize && k < n; k++) {
            int idx = indices[k];
            // 步骤3: s ← RandomSeq (T)（此处实现为：从打乱后的前 sampleSize 条中依次取一条）
            Sequence s = T.get(idx);

            // 为了满足“每个序列对每个子串的计数只贡献一次”的要求，
            // 需要在处理该序列时，记录已经在本序列上计数过的节点，避免重复累加。
            // 这里使用一个 Set<CSTNode> 来存储“本序列已访问过的节点”。
            Set<CSTNode> visitedNodesInSequence = new HashSet<>();
            
            // 步骤5: for j ← 0 to l = |s| do
            // 遍历序列s的每个起始位置j（从0到序列长度|s|-1）
            // 提取所有长度不超过u的连续子串
            for (int j = 0; j < s.size(); j++) {
                // 步骤6: s_node ← root
                // 对于每个起始位置j，都将当前节点重置为根，因为每个子串的插入都从根开始
                CSTNode sNode = tree.root;
                
                // 步骤7: for m ← j to min{j+u, l} do
                // 滑动窗口循环，提取子串s[j, j+u]（实际不超过序列末尾）
                // end = min{j+u, |s|}，循环m从j到end-1，提取长度为min(u, |s|-j)的子串
                int end = Math.min(j + u, s.size());
                for (int m = j; m < end; m++) {
                    // 步骤8: s_node ← InsertItem(s[m], s_node)
                    // 将字符s[m]插入到当前节点s_node下：
                    // 若s_node已有子节点对应字符s[m]，则移动到该子节点；否则创建新节点并移动
                    sNode = tree.insertItem(s.get(m), sNode);
                    
                    // 步骤9：updateCount (s_node, s_order, s.label)
                    // 每个序列对每个子串的计数只贡献一次,即对于本序列而言，每个节点s_node只更新一次计数。
                    if (!visitedNodesInSequence.contains(sNode)) {
                        sNode.updateCount(s.getOrder(), s.getLabel());
                        visitedNodesInSequence.add(sNode);
                    }
                }
            }
        }

        return tree;
    }

    /**
     * 随机选择一个序列
     */
    private Sequence randomSeq(List<Sequence> trainingSet) {
        int index = random.nextInt(trainingSet.size());
        return trainingSet.get(index);
    }

    /**
     * 插入一个项到树中
     */
    private CSTNode insertItem(Integer item, CSTNode node) {
        return node.getOrCreateChild(item);
    }

    /**
     * 获取根节点
     */
    public CSTNode getRoot() {
        return root;
    }
    
    /**
     * 获取最大子序列长度（u）
     */
    public int getMaxSubsequenceLength() {
        return maxSubsequenceLength;
    }

    /**
     * 打印树结构（用于调试）
     * 格式与论文图4一致：显示边（字符）和节点（计数向量）
     */
    public void printTree() {
        printTree(null);
    }
    
    /**
     * 打印树结构（带ID到文本的映射）
     * @param idToTextMap ID到文本的映射，如果为null则显示数字
     */
    public void printTree(java.util.Map<Integer, String> idToTextMap) {
        System.out.println("root");
        printTree(root, 0, "", Integer.MAX_VALUE, idToTextMap);
    }

    /**
     * 递归打印树结构
     * @param node 当前节点
     * @param depth 当前深度
     * @param prefix 前缀（用于显示树形结构）
     * @param maxDepth 最大深度
     * @param idToTextMap ID到文本的映射
     */
    private void printTree(CSTNode node, int depth, String prefix, int maxDepth, 
                           java.util.Map<Integer, String> idToTextMap) {
        if (depth >= maxDepth || node.getChildCount() == 0) {
            return;
        }
        
        var entries = new java.util.ArrayList<java.util.Map.Entry<Integer, CSTNode>>();
        node.forEachChild((item, child) ->
                entries.add(new java.util.AbstractMap.SimpleEntry<>(item, child)));
        
        for (int i = 0; i < entries.size(); i++) {
            var entry = entries.get(i);
            Integer item = entry.getKey();
            CSTNode child = entry.getValue();
            
            // 判断是否是最后一个子节点
            boolean isLast = (i == entries.size() - 1);
            
            // 构建树形结构的连接符
            String connector = isLast ? "└── " : "├── ";
            String nextPrefix = isLast ? "    " : "│   ";
            
            // 获取显示文本：如果有映射则显示文本，否则显示数字
            String itemDisplay = (idToTextMap != null && idToTextMap.containsKey(item)) 
                ? idToTextMap.get(item) 
                : String.valueOf(item);
            
            // 显示边（字符/文本）和节点（计数向量）
            String countVector = child.getCountVectorString();
            System.out.println(prefix + connector + itemDisplay + " → " + countVector);
            
            // 递归打印子节点
            printTree(child, depth + 1, prefix + nextPrefix, maxDepth, idToTextMap);
        }
    }
    
    /**
     * 打印树结构（限制深度版本，用于大型树）
     * @param maxDepth 最大打印深度
     */
    public void printTree(int maxDepth) {
        printTree(maxDepth, null);
    }
    
    /**
     * 打印树结构（限制深度版本，带ID到文本的映射）
     * @param maxDepth 最大打印深度
     * @param idToTextMap ID到文本的映射，如果为null则显示数字
     */
    public void printTree(int maxDepth, java.util.Map<Integer, String> idToTextMap) {
        System.out.println("root");
        printTree(root, 0, "", maxDepth, idToTextMap);
    }

    /**
     * 获取树的统计信息
     */
    public void printStatistics() {
        int[] stats = getStatistics(root);
        System.out.println("树统计信息:");
        System.out.println("  总节点数: " + stats[0]);
        System.out.println("  总边数: " + stats[1]);
    }

    private int[] getStatistics(CSTNode node) {
        int[] stats = {1, node.getChildCount()};

        node.forEachChild((item, child) -> {
            int[] childStats = getStatistics(child);
            stats[0] += childStats[0];
            stats[1] += childStats[1];
        });

        return stats;
    }
}
