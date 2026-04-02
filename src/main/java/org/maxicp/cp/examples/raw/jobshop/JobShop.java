/*
 * MaxiCP is under MIT License
 * Copyright (c)  2024 UCLouvain
 *
 */

package org.maxicp.cp.examples.raw.jobshop;

import org.maxicp.cp.CPFactory;

import static org.maxicp.cp.CPFactory.*;
import static org.maxicp.search.Searches.*;

import org.maxicp.cp.engine.constraints.scheduling.MinMakespan;
import org.maxicp.cp.engine.constraints.scheduling.NoOverlap;
import org.maxicp.cp.engine.constraints.scheduling.NoOverlapPrecedenceGraph;
import org.maxicp.cp.engine.constraints.scheduling.ThetaTree2;
import org.maxicp.cp.engine.core.CPBoolVar;
import org.maxicp.cp.engine.core.CPIntVar;
import org.maxicp.cp.engine.core.CPSolver;

import org.maxicp.cp.engine.core.CPIntervalVar;
import org.maxicp.search.*;
import org.maxicp.state.StateInt;
import org.maxicp.state.datastructures.StateTriPartition;

import java.io.*;
import java.util.*;
import java.util.stream.Stream;

import static java.lang.Math.*;

/**
 * The JobShop Problem.
 * <a href="https://en.wikipedia.org/wiki/Job_shop_scheduling">Wikipedia.</a>
 *
 * @author Pierre Schaus
 */
public class JobShop {

    public static CPIntervalVar[] flatten(CPIntervalVar[][] x) {
        return Arrays.stream(x).flatMap(Arrays::stream).toArray(CPIntervalVar[]::new);
    }

    public static void main(String[] args) {
        JobShopInstance instance = new JobShopInstance("data/JOBSHOP/la28.txt");

        int nJobs = instance.nJobs;
        int nMachines = instance.nMachines;

        int[][] duration = instance.duration;
        int[][] machine = instance.machine;
        int horizon = instance.horizon;


        CPSolver cp = CPFactory.makeSolver();
        HashMap<CPIntervalVar, Integer> mapping = new HashMap<>();

        // create activities
        CPIntervalVar[][] activities = new CPIntervalVar[nJobs][nMachines];
        for (int j = 0; j < nJobs; j++) {
            for (int m = 0; m < nMachines; m++) {
                activities[j][m] = makeIntervalVar(cp, false, duration[j][m], duration[j][m]);
                activities[j][m].setEndMax(horizon);
                mapping.put(activities[j][m], j*nMachines+m);
            }
        }
        CPIntervalVar[] lasts = Arrays.stream(activities)
                .map(job -> job[nMachines - 1])
                .toArray(CPIntervalVar[]::new);
        CPIntVar makespan = CPFactory.makespan(lasts);
        CPIntervalVar[] allActivities = flatten(activities);

        NoOverlapPrecedenceGraph precedenceGraph = new NoOverlapPrecedenceGraph(Arrays.stream(machine)
                .flatMapToInt(Arrays::stream)
                .toArray(),allActivities);

        // precedence constraints on each job
        for (int j = 0; j < nJobs; j++) {
            for (int m = 1; m < nMachines; m++) {
                cp.post(endBeforeStart(activities[j][m - 1], activities[j][m]));
                precedenceGraph.addPrecedence(j*nMachines + (m-1), j*nMachines+m);
            }
        }


        cp.post(precedenceGraph);


        CPIntervalVar [][] toRank = new CPIntervalVar[nMachines][];
        MinMakespan[] constraints = new MinMakespan[nMachines];
        // no overlap between the activities on the same machine
        for (int m = 0; m < nMachines; m++) {
            ArrayList<CPIntervalVar> machineActivities = new ArrayList<>();
            for (int j = 0; j < nJobs; j++) {
                for (int i = 0; i < nMachines; i++) {
                    if (machine[j][i] == m) {
                        machineActivities.add(activities[j][i]);
                    }
                }
            }
            CPIntervalVar [] onMachine = machineActivities.toArray(new CPIntervalVar[0]);
            cp.post(noOverlap(onMachine));
            MinMakespan minMakespan = new MinMakespan(precedenceGraph, makespan, onMachine);
            constraints[m] = minMakespan;
            cp.post(minMakespan);
            toRank[m] = onMachine;
        }

        System.out.println(makespan);

        Objective obj = cp.minimize(makespan);


        DFSearch dfs = CPFactory.makeDfs(cp,
                and(()->{
                            System.out.println(makespan);
                            return EMPTY;
                        },Rank.rank(toRank, constraints, precedenceGraph, mapping),
                        () -> makespan.isFixed() ? EMPTY: branch(() -> cp.post(le(makespan, makespan.min())))
                ));


        //DFSearch dfs = CPFactory.makeDfs(cp, setTimes(allActivities));

        dfs.onSolution(() -> {
            System.out.println("=========================>makespan:" + makespan );
        });
        SearchStatistics stats = dfs.optimize(obj);
        System.out.format("Statistics: %s\n", stats);
    }

    private static class JobShopInstance {

        public int nJobs;
        public int nMachines;
        public int[][] duration;
        public int[][] machine;
        public int horizon;

        public JobShopInstance(String path) {
            try {
                FileInputStream istream = new FileInputStream(path);
                BufferedReader in = new BufferedReader(new InputStreamReader(istream));
                in.readLine();
                in.readLine();
                in.readLine();
                StringTokenizer tokenizer = new StringTokenizer(in.readLine());
                nJobs = Integer.parseInt(tokenizer.nextToken());
                nMachines = Integer.parseInt(tokenizer.nextToken());
                duration = new int[nJobs][nMachines];
                machine = new int[nJobs][nMachines];
                for (int i = 0; i < nJobs; i++) {
                    tokenizer = new StringTokenizer(in.readLine());
                    for (int j = 0; j < nMachines; j++) {
                        machine[i][j] = Integer.parseInt(tokenizer.nextToken());
                        duration[i][j] = Integer.parseInt(tokenizer.nextToken());
                        horizon+= duration[i][j];
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
                return;
            }
        }
    }


}