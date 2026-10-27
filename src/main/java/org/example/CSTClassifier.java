package org.example;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;

/**
 * Classifier backed by a Count-Suffix Tree.
 */
public class CSTClassifier {
    private final CSTree csTree;
    private final int k;
    private final double alpha;
    private final Map<Integer, Integer> trainCount;
    private final int totalTrainCount;

    public CSTClassifier(CSTree csTree, int k, double alpha, Map<Integer, Integer> trainCount) {
        this.csTree = csTree;
        this.k = k;
        this.alpha = alpha;
        this.trainCount = new HashMap<>(trainCount);
        this.totalTrainCount = trainCount.values().stream().mapToInt(Integer::intValue).sum();
    }

    public CSTClassifier(CSTree csTree, int k, Map<Integer, Integer> trainCount) {
        this(csTree, k, 0.9, trainCount);
    }

    public int classify(List<Integer> sequence, Set<Integer> labelSet) {
        List<SubsequenceScore> topK = selectTopKStreaming(sequence, labelSet);
        return vote(topK, labelSet);
    }

    public List<Integer> classifyBatch(List<List<Integer>> sequences, Set<Integer> labelSet) {
        List<Integer> predictions = new ArrayList<>();
        for (List<Integer> sequence : sequences) {
            predictions.add(classify(sequence, labelSet));
        }
        return predictions;
    }

    public Map<String, Object> classifyWithDetails(List<Integer> sequence, Set<Integer> labelSet) {
        Map<String, Object> details = new HashMap<>();
        List<SubsequenceScore> topK = selectTopKStreaming(sequence, labelSet);
        Map<Integer, Integer> labelVotes = countVotes(topK);
        int predictedLabel = choosePrediction(labelVotes, labelSet);

        // The highest-scoring candidate need not support the Top-K vote winner.
        SubsequenceScore explanationScore = null;
        for (SubsequenceScore candidate : topK) {
            if (candidate.getBestLabel() != null && candidate.getBestLabel() == predictedLabel) {
                explanationScore = candidate;
                break;
            }
        }

        details.put("allScores", Collections.emptyList());
        details.put("topK", topK);
        details.put("labelVotes", labelVotes);
        details.put("predictedLabel", predictedLabel);
        details.put("explanationScore", explanationScore);
        return details;
    }

    private List<SubsequenceScore> selectTopKStreaming(List<Integer> sequence, Set<Integer> labelSet) {
        if (k <= 0) {
            return new ArrayList<>();
        }

        PriorityQueue<SubsequenceScore> topK = new PriorityQueue<>((a, b) -> {
            int scoreComparison = Double.compare(a.getScore(), b.getScore());
            if (scoreComparison != 0) {
                return scoreComparison;
            }
            return Long.compare(b.getOrder(), a.getOrder());
        });

        int u = csTree.getMaxSubsequenceLength();
        long order = 0L;

        for (int start = 0; start < sequence.size(); start++) {
            CSTNode currentNode = csTree.getRoot();
            int end = Math.min(start + u, sequence.size());

            for (int pos = start; pos < end; pos++) {
                CSTNode childNode = currentNode.getChild(sequence.get(pos));
                if (childNode == null) {
                    break;
                }

                currentNode = childNode;
                ScoreResult result = calculateScore(currentNode);

                if (result.score > 0.0 && result.label != null) {
                    SubsequenceScore candidate = new SubsequenceScore(
                            sequence,
                            start,
                            pos - start + 1,
                            currentNode,
                            result.score,
                            result.label,
                            order
                    );
                    offerTopK(topK, candidate);
                    order++;
                }
            }
        }

        List<SubsequenceScore> result = new ArrayList<>(topK);
        result.sort((a, b) -> {
            int scoreComparison = Double.compare(b.getScore(), a.getScore());
            if (scoreComparison != 0) {
                return scoreComparison;
            }
            return Long.compare(a.getOrder(), b.getOrder());
        });
        return result;
    }

    private void offerTopK(PriorityQueue<SubsequenceScore> topK, SubsequenceScore candidate) {
        if (topK.size() < k) {
            topK.offer(candidate);
            return;
        }

        SubsequenceScore worst = topK.peek();
        if (worst != null && isBetter(candidate, worst)) {
            topK.poll();
            topK.offer(candidate);
        }
    }

    private boolean isBetter(SubsequenceScore candidate, SubsequenceScore current) {
        int scoreComparison = Double.compare(candidate.getScore(), current.getScore());
        if (scoreComparison != 0) {
            return scoreComparison > 0;
        }
        return candidate.getOrder() < current.getOrder();
    }

    private static class ScoreResult {
        final double score;
        final Integer label;

        ScoreResult(double score, Integer label) {
            this.score = score;
            this.label = label;
        }
    }

    private ScoreResult calculateScore(CSTNode node) {
        if (node.getLabelEntryCount() == 0) {
            return new ScoreResult(0.0, null);
        }

        double bestScore = 0.0;
        Integer bestLabel = null;
        int totalNodeCount = node.getTotalLabelCount();

        for (int i = 0; i < node.getLabelEntryCount(); i++) {
            int objLabel = node.getLabelAt(i);
            int objFreq = node.getLabelCountAt(i);
            int objNum = trainCount.getOrDefault(objLabel, 0);
            int otherFreq = totalNodeCount - objFreq;
            int otherNum = totalTrainCount - objNum;

            int tp = objFreq;
            int fp = otherFreq;
            int fn = objNum - objFreq;

            double precision = (tp + fp == 0) ? 0.0 : (double) tp / (tp + fp);
            double recall = (tp + fn == 0) ? 0.0 : (double) tp / (tp + fn);
            double score = alpha * precision + (1.0 - alpha) * recall;

            if (score > bestScore) {
                bestScore = score;
                bestLabel = objLabel;
            }
        }

        return new ScoreResult(bestScore, bestLabel);
    }

    private int vote(List<SubsequenceScore> topK, Set<Integer> labelSet) {
        return choosePrediction(countVotes(topK), labelSet);
    }

    private Map<Integer, Integer> countVotes(List<SubsequenceScore> topK) {
        Map<Integer, Integer> labelVotes = new HashMap<>();
        for (SubsequenceScore score : topK) {
            Integer bestLabel = score.getBestLabel();
            if (bestLabel != null) {
                labelVotes.put(bestLabel, labelVotes.getOrDefault(bestLabel, 0) + 1);
            }
        }
        return labelVotes;
    }

    private int choosePrediction(Map<Integer, Integer> labelVotes, Set<Integer> labelSet) {
        if (labelVotes.isEmpty()) {
            return labelSet.isEmpty() ? -1 : labelSet.iterator().next();
        }

        int predictedLabel = -1;
        int maxVotes = -1;
        for (Map.Entry<Integer, Integer> entry : labelVotes.entrySet()) {
            if (entry.getValue() > maxVotes) {
                maxVotes = entry.getValue();
                predictedLabel = entry.getKey();
            }
        }

        if (predictedLabel == -1 && !labelSet.isEmpty()) {
            predictedLabel = labelSet.iterator().next();
        }
        return predictedLabel;
    }
}
