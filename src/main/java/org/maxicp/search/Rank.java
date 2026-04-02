/*
 * MaxiCP is under MIT License
 * Copyright (c)  2024 UCLouvain
 *
 */

package org.maxicp.search;

import org.maxicp.cp.CPFactory;
import org.maxicp.cp.engine.constraints.scheduling.NoOverlapPrecedenceGraph;
import org.maxicp.cp.engine.core.CPConstraint;
import org.maxicp.cp.engine.core.CPIntervalVar;
import org.maxicp.cp.engine.core.CPSolver;
import org.maxicp.state.StateInt;
import org.maxicp.state.datastructures.StateSparseSet;

import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.function.Supplier;

/**
 * Rank Branching
 * @author Pierre Schaus
 */
public class Rank {

    public static Supplier<Runnable[]> rank(CPIntervalVar[][] intervals, CPConstraint[] c, NoOverlapPrecedenceGraph precedenceGraph, HashMap<CPIntervalVar, Integer> mapping) {
        Rank rank = new Rank(intervals, c, precedenceGraph, mapping);
        return rank::alternatives_;
    }

    public static Supplier<Runnable[]> rank(CPIntervalVar[] intervals, CPConstraint[] c, NoOverlapPrecedenceGraph precedenceGraph, HashMap<CPIntervalVar, Integer> mapping) {
        Ranker ranker = new Ranker(intervals, c, precedenceGraph, mapping);
        return ranker::alternatives;
    }

    CPIntervalVar[][] intervals;
    Ranker[] rankers;
    StateSparseSet notRanked;
    StateInt currentRanker;
    CPConstraint[] constraints;
    NoOverlapPrecedenceGraph precedenceGraph;

    public Rank(CPIntervalVar[][] intervals, CPConstraint[] constraints, NoOverlapPrecedenceGraph precedenceGraph, HashMap<CPIntervalVar, Integer> mapping) {
        this.intervals = intervals;
        this.rankers = new Ranker[intervals.length];
        CPSolver cp = intervals[0][0].getSolver();
        this.notRanked = new StateSparseSet(cp.getStateManager(), intervals.length, 0);
        this.currentRanker = cp.getStateManager().makeStateInt(-1);
        for (int i = 0; i < intervals.length; i++) {
            rankers[i] = new Ranker(intervals[i], constraints, precedenceGraph, mapping);
        }
        this.constraints = constraints;
        this.precedenceGraph = precedenceGraph;
    }


    public Runnable[] alternatives_() {
        if (currentRanker.value() == -1 || rankers[currentRanker.value()].isRanked()) {
            // need to find a new ranked
            int bestRankerId = -1;
            int bestSlack = Integer.MAX_VALUE;
            int [] notRankedIterator = new int[notRanked.size()];
            int nNotRanked = notRanked.fillArray(notRankedIterator);
            for (int i = 0; i < nNotRanked; i++) {
                if (rankers[notRankedIterator[i]].isRanked()) {
                    notRanked.remove(notRankedIterator[i]);
                    continue;
                }
                int rankerId = notRankedIterator[i];
                int slack = rankers[rankerId].slack();
                if (slack < bestSlack) {
                    bestSlack = slack;
                    bestRankerId = rankerId;
                }
            }
            if (bestRankerId == -1) {
                return Searches.EMPTY;
            } else {
                currentRanker.setValue(bestRankerId);
                return rankers[currentRanker.value()].alternatives();
            }
        } else {
            return rankers[currentRanker.value()].alternatives();
        }
    }

    public Runnable[] alternatives() {
        int [] notRankedIterator = new int[notRanked.size()];
        int nNotRanked = notRanked.fillArray(notRankedIterator);
        // find the ranker with the least slack
        int bestRankerId = -1;
        int bestSlack = Integer.MAX_VALUE;
        for (int i = 0; i < nNotRanked; i++) {
            if (rankers[notRankedIterator[i]].isRanked()) {
                notRanked.remove(notRankedIterator[i]);
                continue;
            }
            int rankerId = notRankedIterator[i];
            int slack = rankers[rankerId].slack();
            if (slack < bestSlack) {
                bestSlack = slack;
                bestRankerId = rankerId;
            }
        }
        if (bestRankerId == -1) {
            return Searches.EMPTY;
        } else {
            Ranker bestRanker = rankers[bestRankerId];
            return bestRanker.alternatives();
        }
    }




    static class Ranker {

        private final CPIntervalVar[] intervals;
        private final CPSolver cp;
        private final int[] notRankedIterator;
        private final StateSparseSet notRanked;
        private CPConstraint[] constraints;
        private NoOverlapPrecedenceGraph precedenceGraph;
        private HashMap<CPIntervalVar, Integer> mapping;

        Ranker(CPIntervalVar[] intervals, CPConstraint[] constraints, NoOverlapPrecedenceGraph precedenceGraph, HashMap<CPIntervalVar, Integer> mapping) {
            this.intervals = intervals;
            this.cp = intervals[0].getSolver();
            this.notRanked = new StateSparseSet(cp.getStateManager(), intervals.length, 0);
            this.notRankedIterator = new int[intervals.length];
            this.constraints = constraints;
            this.precedenceGraph = precedenceGraph;
            this.mapping = mapping;
        }

        int slack() {
            int minEst = Integer.MAX_VALUE;
            int maxLct = Integer.MIN_VALUE;
            notRanked.fillArray(notRankedIterator);
            int nNotRanked = notRanked.size();
            for (int i = 0; i < nNotRanked; i++) {
                int taskId = notRankedIterator[i];
                CPIntervalVar interval = intervals[taskId];
                minEst = Integer.min(minEst, interval.startMin());
                maxLct = Integer.max(maxLct, interval.endMax());
            }
            return (maxLct - minEst) -
                    Arrays.stream(notRankedIterator, 0, nNotRanked).map(i -> intervals[i].lengthMin()).sum();
        }

        boolean isRanked() {
            return notRanked.isEmpty();
        }

        public Runnable[] alternatives() {
            assert (!isRanked());

            int [] notRankedIterator = new int[notRanked.size()];
            int nNotRanked = notRanked.fillArray(notRankedIterator); // fill the iterator with the unassigned tasks

            record RunnableWithPriority(int priority1, int priority2, Runnable action) {}

            RunnableWithPriority[] branches = new RunnableWithPriority[nNotRanked];

            for (int i = 0; i < nNotRanked; i++) {
                int i_ = i;
                int taskId = notRankedIterator[i];
                int priority1 = intervals[taskId].startMin();
                int priority2 = intervals[taskId].startMax();


                branches[i] = new RunnableWithPriority(priority1, priority2, () -> {
                    notRanked.remove(taskId);
                    for (int j = 0; j < nNotRanked; j++) {
                        if (i_ != j) {
                            int otherTaskId = notRankedIterator[j];
                            cp.post(CPFactory.endBeforeStart(intervals[taskId], intervals[otherTaskId]));
                            precedenceGraph.addPrecedence(mapping.get(intervals[taskId]), mapping.get(intervals[otherTaskId]));
                            precedenceGraph.propagate();
                            precedenceGraph.propageOnPrecedence(constraints);
                            cp.fixPoint();
                        }
                    }
                });
            }

            Arrays.sort(branches, Comparator.comparingInt(RunnableWithPriority::priority1).thenComparing(RunnableWithPriority::priority2));
            return Arrays.stream(branches).map(rwp -> rwp.action).toArray(Runnable[]::new);
        }


    }
}
