package org.example;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.*;

public class CSTTest {

    public static TestResult testDataset(String datasetPath, int u, double c, int k, double alpha, int randomState) {
        try {
            // Load dataset
            List<Sequence> allSequences = DataSetLoader.loadDataSet(datasetPath);
            int nSamples = allSequences.size();
            
            // 5-fold cross-validation
            KFold kf = new KFold(5, true, randomState);

            List<KFold.FoldSplit> splits = kf.split(nSamples);
            
            List<Double> accList = new ArrayList<>();
            List<Integer> sizeList = new ArrayList<>();
            List<Double> lengthList = new ArrayList<>();
            List<Double> trainingTimeList = new ArrayList<>();
            List<Double> predictionTimeList = new ArrayList<>();
            List<Double> timeList = new ArrayList<>();
            List<Double> macroAccList = new ArrayList<>();
            List<Double> f1List = new ArrayList<>();
            
            int fold = 1;
            System.out.print("\t");
            
            for (KFold.FoldSplit split : splits) {
                List<Sequence> trainSequences = new ArrayList<>();
                List<Sequence> testSequences = new ArrayList<>();
                
                for (int idx : split.getTrainIndices()) {
                    trainSequences.add(allSequences.get(idx));
                }
                for (int idx : split.getTestIndices()) {
                    testSequences.add(allSequences.get(idx));
                }
                
                System.out.print("Fold " + fold + ": Building CSTree...");
                
                long treeSeed = ((long) randomState << 32) ^ fold;

                long trainingStartTime = System.nanoTime();
                CSTree csTree = CSTree.build(trainSequences, u, c, treeSeed);
                Map<Integer, Integer> trainCount = ClassifierUtils.computeTrainCount(trainSequences);
                Set<Integer> labelSet = ClassifierUtils.extractLabelSet(trainSequences);
                CSTClassifier classifier = new CSTClassifier(csTree, k, alpha, trainCount);
                double trainingTime = (System.nanoTime() - trainingStartTime) / 1_000_000_000.0;

                System.out.print("done. Predicting...");

                List<Integer> predictions = new ArrayList<>();
                Set<Rule> ruleSet = new HashSet<>();

                long predictionStartTime = System.nanoTime();
                for (Sequence testSeq : testSequences) {
                    List<Integer> testItems = testSeq.getItems();
                    
                    Map<String, Object> details = classifier.classifyWithDetails(testItems, labelSet);
                    int predictedLabel = (Integer) details.get("predictedLabel");
                    predictions.add(predictedLabel);
                    
                    SubsequenceScore explanationScore = (SubsequenceScore) details.get("explanationScore");

                    if (explanationScore != null) {
                        Rule rule = new Rule(explanationScore.getSubsequence(), predictedLabel);
                        ruleSet.add(rule);
                    }
                }
                double predictionTime = (System.nanoTime() - predictionStartTime) / 1_000_000_000.0;
                double elapsedTime = trainingTime + predictionTime;
                
                int correct = 0;
                for (int i = 0; i < predictions.size(); i++) {
                    if (predictions.get(i).equals(testSequences.get(i).getLabel())) {
                        correct++;
                    }
                }
                double accuracy = (double) correct / predictions.size();
                
                double avgLength = 0.0;
                if (!ruleSet.isEmpty()) {
                    int totalLength = 0;
                    for (Rule rule : ruleSet) {
                        totalLength += rule.getSubsequence().size();
                    }
                    avgLength = (double) totalLength / ruleSet.size();
                }
                
                java.util.Map<Integer, Integer> classCorrect = new java.util.HashMap<>();
                java.util.Map<Integer, Integer> classTotal = new java.util.HashMap<>();
                for (int i = 0; i < predictions.size(); i++) {
                    int trueLabel = testSequences.get(i).getLabel();
                    int predLabel = predictions.get(i);
                    classTotal.put(trueLabel, classTotal.getOrDefault(trueLabel, 0) + 1);
                    if (predLabel == trueLabel) {
                        classCorrect.put(trueLabel, classCorrect.getOrDefault(trueLabel, 0) + 1);
                    }
                }
                double macroAccuracy = 0.0;
                for (java.util.Map.Entry<Integer, Integer> e : classTotal.entrySet()) {
                    int correctCount = classCorrect.getOrDefault(e.getKey(), 0);
                    int totalCount = e.getValue();
                    macroAccuracy += (double) correctCount / totalCount;
                }
                macroAccuracy /= classTotal.size();

                double macroF1 = calculateMacroF1(predictions, testSequences);

                accList.add(accuracy * 100);
                macroAccList.add(macroAccuracy * 100);
                f1List.add(macroF1 * 100);
                sizeList.add(ruleSet.size());
                lengthList.add(avgLength);
                trainingTimeList.add(trainingTime);
                predictionTimeList.add(predictionTime);
                timeList.add(elapsedTime);
                
                System.out.printf(
                        "done. Accuracy=%.2f F1=%.2f MacroAcc=%.2f Size=%.2f Length=%.2f Train=%.6fs Predict=%.6fs Total=%.6fs\n",
                        accuracy * 100, macroF1 * 100, macroAccuracy * 100,
                        (double) ruleSet.size(), avgLength, trainingTime, predictionTime, elapsedTime);
                
                fold++;
            }
            
            double avgAcc = accList.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
            double avgMacroAcc = macroAccList.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
            double avgF1 = f1List.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
            double avgSize = sizeList.stream().mapToInt(Integer::intValue).average().orElse(0.0);
            double avgLength = lengthList.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
            double avgTrainingTime = trainingTimeList.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
            double avgPredictionTime = predictionTimeList.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
            double avgTime = timeList.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
            
            return new TestResult(
                    avgAcc, avgMacroAcc, avgF1, avgSize, avgLength,
                    avgTrainingTime, avgPredictionTime, avgTime);
            
        } catch (IOException e) {
            System.err.println("? " + e.getMessage());
            e.printStackTrace();
            return new TestResult(0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0);
        }
    }

    private static double calculateMacroF1(List<Integer> predictions, List<Sequence> testSequences) {
        Set<Integer> labels = new HashSet<>();
        for (Sequence testSequence : testSequences) {
            labels.add(testSequence.getLabel());
        }
        labels.addAll(predictions);
        labels.remove(-1);

        if (labels.isEmpty()) {
            return 0.0;
        }

        double f1Sum = 0.0;
        for (int label : labels) {
            int tp = 0;
            int fp = 0;
            int fn = 0;

            for (int i = 0; i < predictions.size(); i++) {
                int predictedLabel = predictions.get(i);
                int trueLabel = testSequences.get(i).getLabel();

                if (predictedLabel == label && trueLabel == label) {
                    tp++;
                } else if (predictedLabel == label) {
                    fp++;
                } else if (trueLabel == label) {
                    fn++;
                }
            }

            double precision = (tp + fp == 0) ? 0.0 : (double) tp / (tp + fp);
            double recall = (tp + fn == 0) ? 0.0 : (double) tp / (tp + fn);
            double f1 = (precision + recall == 0.0) ? 0.0 : 2 * precision * recall / (precision + recall);
            f1Sum += f1;
        }

        return f1Sum / labels.size();
    }

    
    public static TestResult testDatasetRepeated(String datasetPath, int u, double c, int k, double alpha,
                                                int runs, int baseRandomState) {
        List<Double> accRuns = new ArrayList<>();
        List<Double> macroAccRuns = new ArrayList<>();
        List<Double> f1Runs = new ArrayList<>();
        List<Double> sizeRuns = new ArrayList<>();
        List<Double> lengthRuns = new ArrayList<>();
        List<Double> trainingTimeRuns = new ArrayList<>();
        List<Double> predictionTimeRuns = new ArrayList<>();
        List<Double> timeRuns = new ArrayList<>();

        for (int r = 0; r < runs; r++) {
            int randomState = baseRandomState + r;
            TestResult one = testDataset(datasetPath, u, c, k, alpha, randomState);
            accRuns.add(one.accuracy);
            macroAccRuns.add(one.macroAccuracy);
            f1Runs.add(one.f1);
            sizeRuns.add(one.size);
            lengthRuns.add(one.length);
            trainingTimeRuns.add(one.trainingTime);
            predictionTimeRuns.add(one.predictionTime);
            timeRuns.add(one.time);
        }

        double avgAcc = accRuns.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
        double avgMacroAcc = macroAccRuns.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
        double avgF1 = f1Runs.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
        double avgSize = sizeRuns.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
        double avgLength = lengthRuns.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
        double avgTrainingTime = trainingTimeRuns.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
        double avgPredictionTime = predictionTimeRuns.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
        double avgTime = timeRuns.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);

        return new TestResult(
                avgAcc, avgMacroAcc, avgF1, avgSize, avgLength,
                avgTrainingTime, avgPredictionTime, avgTime);
    }


    public static class TestResult {
        double accuracy;
        double macroAccuracy;
        double f1;
        double size;
        double length;
        double trainingTime;
        double predictionTime;
        double time;
        
        public TestResult(double accuracy, double macroAccuracy, double f1, double size, double length,
                          double trainingTime, double predictionTime, double time) {
            this.accuracy = accuracy;
            this.macroAccuracy = macroAccuracy;
            this.f1 = f1;
            this.size = size;
            this.length = length;
            this.trainingTime = trainingTime;
            this.predictionTime = predictionTime;
            this.time = time;
        }
    }


    private static class Rule {
        private List<Integer> subsequence;
        private int label;
        
        public Rule(List<Integer> subsequence, int label) {
            this.subsequence = subsequence;
            this.label = label;
        }
        
        public List<Integer> getSubsequence() {
            return subsequence;
        }
        
        public int getLabel() {
            return label;
        }
        
        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Rule rule = (Rule) o;
            return label == rule.label && Objects.equals(subsequence, rule.subsequence);
        }
        
        @Override
        public int hashCode() {
            return Objects.hash(subsequence, label);
        }
    }



    public static void main(String[] args) {
        String[] datasets = {
            "activity", "aslbu", "auslan2", "context", "epitope",
            "gene", "news", "pioneer", "question", "reuters",
            "robot", "skating", "unix", "webkb"
        };

        int u = 4;
        double c = 0.8;
        int k = 5;
        int runs = 5;
        int baseRandomState = 42;
        double alpha08 = 0.8;
        double alpha09 = 0.9;

        File resultDir = new File("src/result");
        if (!resultDir.exists()) {
            resultDir.mkdirs();
        }

        String resultPath = "src/result/result_alpha08_alpha09_u4_c08_k5_runs5_f1_size_timing.txt";
        System.out.println("\n========== Alpha comparison: u=4, c=0.8, k=5, runs=5 ==========");

        try (PrintWriter writer = new PrintWriter(new FileWriter(resultPath))) {
            writer.println("# Dataset Accuracy_alpha08 F1_alpha08 Size_alpha08 Length_alpha08 TrainingTime_alpha08 PredictionTime_alpha08 TotalTime_alpha08 Accuracy_alpha09 F1_alpha09 Size_alpha09 Length_alpha09 TrainingTime_alpha09 PredictionTime_alpha09 TotalTime_alpha09");

            double totalAcc08 = 0.0;
            double totalF108 = 0.0;
            double totalSize08 = 0.0;
            double totalLength08 = 0.0;
            double totalTrainingTime08 = 0.0;
            double totalPredictionTime08 = 0.0;
            double totalTime08 = 0.0;
            double totalAcc09 = 0.0;
            double totalF109 = 0.0;
            double totalSize09 = 0.0;
            double totalLength09 = 0.0;
            double totalTrainingTime09 = 0.0;
            double totalPredictionTime09 = 0.0;
            double totalTime09 = 0.0;

            for (String dataset : datasets) {
                String datasetPath = "src/dataset/" + dataset + ".txt";
                System.out.println("\n" + dataset + ":");

                System.out.print("  alpha=0.8: ");
                TestResult result08 = testDatasetRepeated(datasetPath, u, c, k, alpha08, runs, baseRandomState);
                System.out.printf(
                        "Accuracy=%.2f, F1=%.2f, Size=%.2f, Length=%.2f, Train=%.6f, Predict=%.6f, Total=%.6f\n",
                        result08.accuracy, result08.f1, result08.size, result08.length,
                        result08.trainingTime, result08.predictionTime, result08.time);

                System.out.print("  alpha=0.9: ");
                TestResult result09 = testDatasetRepeated(datasetPath, u, c, k, alpha09, runs, baseRandomState);
                System.out.printf(
                        "Accuracy=%.2f, F1=%.2f, Size=%.2f, Length=%.2f, Train=%.6f, Predict=%.6f, Total=%.6f\n",
                        result09.accuracy, result09.f1, result09.size, result09.length,
                        result09.trainingTime, result09.predictionTime, result09.time);

                writer.printf("%s %.2f %.2f %.2f %.2f %.6f %.6f %.6f %.2f %.2f %.2f %.2f %.6f %.6f %.6f\n",
                        dataset,
                        result08.accuracy, result08.f1, result08.size, result08.length,
                        result08.trainingTime, result08.predictionTime, result08.time,
                        result09.accuracy, result09.f1, result09.size, result09.length,
                        result09.trainingTime, result09.predictionTime, result09.time);
                writer.flush();

                totalAcc08 += result08.accuracy;
                totalF108 += result08.f1;
                totalSize08 += result08.size;
                totalLength08 += result08.length;
                totalTrainingTime08 += result08.trainingTime;
                totalPredictionTime08 += result08.predictionTime;
                totalTime08 += result08.time;
                totalAcc09 += result09.accuracy;
                totalF109 += result09.f1;
                totalSize09 += result09.size;
                totalLength09 += result09.length;
                totalTrainingTime09 += result09.trainingTime;
                totalPredictionTime09 += result09.predictionTime;
                totalTime09 += result09.time;
            }

            int datasetCount = datasets.length;
            writer.printf("average %.8f %.8f %.8f %.8f %.8f %.8f %.8f %.8f %.8f %.8f %.8f %.8f %.8f %.8f\n",
                    totalAcc08 / datasetCount,
                    totalF108 / datasetCount,
                    totalSize08 / datasetCount,
                    totalLength08 / datasetCount,
                    totalTrainingTime08 / datasetCount,
                    totalPredictionTime08 / datasetCount,
                    totalTime08 / datasetCount,
                    totalAcc09 / datasetCount,
                    totalF109 / datasetCount,
                    totalSize09 / datasetCount,
                    totalLength09 / datasetCount,
                    totalTrainingTime09 / datasetCount,
                    totalPredictionTime09 / datasetCount,
                    totalTime09 / datasetCount);

            System.out.printf("\naverage alpha=0.8: Accuracy=%.8f, F1=%.8f, Size=%.8f, Length=%.8f, Train=%.8f, Predict=%.8f, Total=%.8f\n",
                    totalAcc08 / datasetCount,
                    totalF108 / datasetCount,
                    totalSize08 / datasetCount,
                    totalLength08 / datasetCount,
                    totalTrainingTime08 / datasetCount,
                    totalPredictionTime08 / datasetCount,
                    totalTime08 / datasetCount);
            System.out.printf("average alpha=0.9: Accuracy=%.8f, F1=%.8f, Size=%.8f, Length=%.8f, Train=%.8f, Predict=%.8f, Total=%.8f\n",
                    totalAcc09 / datasetCount,
                    totalF109 / datasetCount,
                    totalSize09 / datasetCount,
                    totalLength09 / datasetCount,
                    totalTrainingTime09 / datasetCount,
                    totalPredictionTime09 / datasetCount,
                    totalTime09 / datasetCount);
            System.out.println("\nResult saved to " + resultPath);
        } catch (IOException e) {
            System.err.println("Failed to write result file: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
