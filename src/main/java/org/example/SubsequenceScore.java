package org.example;

import java.util.ArrayList;
import java.util.List;

/**
 * Score metadata for a candidate subsequence.
 *
 * The object stores a source sequence window instead of copying the full
 * subsequence for every candidate. Materialization is delayed until rule
 * reporting needs it.
 */
public class SubsequenceScore {
    private final List<Integer> sourceSequence;
    private final int startPos;
    private final int length;
    private final CSTNode node;
    private final double score;
    private final Integer bestLabel;
    private final long order;

    public SubsequenceScore(List<Integer> sourceSequence, int startPos, int length,
                            CSTNode node, double score, Integer bestLabel, long order) {
        this.sourceSequence = sourceSequence;
        this.startPos = startPos;
        this.length = length;
        this.node = node;
        this.score = score;
        this.bestLabel = bestLabel;
        this.order = order;
    }

    public List<Integer> getSubsequence() {
        return new ArrayList<>(sourceSequence.subList(startPos, startPos + length));
    }

    public int getLength() {
        return length;
    }

    public CSTNode getNode() {
        return node;
    }

    public double getScore() {
        return score;
    }

    public int getStartPos() {
        return startPos;
    }

    public Integer getBestLabel() {
        return bestLabel;
    }

    public long getOrder() {
        return order;
    }

    @Override
    public String toString() {
        return String.format("SubsequenceScore{subsequence=%s, score=%.4f, startPos=%d, bestLabel=%d}",
                getSubsequence(), score, startPos, bestLabel);
    }
}
