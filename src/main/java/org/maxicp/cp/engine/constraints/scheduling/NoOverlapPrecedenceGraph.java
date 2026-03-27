package org.maxicp.cp.engine.constraints.scheduling;

import org.maxicp.cp.engine.core.AbstractCPConstraint;
import org.maxicp.cp.engine.core.CPConstraint;
import org.maxicp.cp.engine.core.CPIntervalVar;
import org.maxicp.cp.engine.core.IntDomainListener;
import org.maxicp.modeling.IntervalVar;
import org.maxicp.state.StateInt;
import org.maxicp.state.datastructures.StateStack;
import org.maxicp.state.datastructures.StateTriPartition;
import org.maxicp.util.exception.InconsistencyException;

import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;

import static java.lang.Math.max;

public class NoOverlapPrecedenceGraph extends AbstractCPConstraint {

    final CPIntervalVar[] vars;
    private StateTriPartition[] predecessors, successors;
    private int[] iterator1;
    private int[] iterator2;
    private Integer[] sortEst;
    private Integer[] sortLct;
    private HashMap<CPIntervalVar, StateInt> q;
    private int[] machine;
    private StateStack<CPConstraint> onBind;


    public NoOverlapPrecedenceGraph(int[] machine, CPIntervalVar... vars) {
        super(vars[0].getSolver());
        iterator1 = new int[vars.length];
        iterator2 = new int[vars.length];
        sortEst = new Integer[vars.length];
        sortLct = new Integer[vars.length];
        predecessors = new StateTriPartition[vars.length];
        successors = new StateTriPartition[vars.length];
        for (int i = 0; i < vars.length; i++) {
            predecessors[i] = new StateTriPartition(vars[0].getSolver().getStateManager(), vars.length);
            successors[i] = new StateTriPartition(vars[0].getSolver().getStateManager(), vars.length);
        }
        this.vars = vars;
        this.q = new HashMap<>();
        for (int i = 0; i < vars.length; i++) {
            q.put(vars[i], vars[0].getSolver().getStateManager().makeStateInt(0));
        }
        this.machine = machine;
        this.onBind = new StateStack<>(vars[0].getSolver().getStateManager());
    }

    @Override
    public void post() {
        for (CPIntervalVar var : vars) {
            var.propagateOnChange(this);
        }
        propagate();
    }

    public void computeQ( int idx){
        if (predecessors[idx].nIncluded()==0){
            return;
        }
        int[] iterator = new int[vars.length];
        int nPred = predecessors[idx].fillIncluded(iterator);
        for (int i = 0; i < nPred; i++) {
            int predi = iterator[i];

            if (q.get(vars[predi]).value() < q.get(vars[idx]).value()+vars[idx].lengthMin() ){
                q.get(vars[predi]).setValue( q.get(vars[idx]).value()+vars[idx].lengthMin());
                computeQ(predi);
            }
        }
    }


    public void addPrecedence(int i, int j) {
        // i -> j
        if (predecessors[j].isIncluded(i)) {
            return; // already known
        }

        predecessors[j].include(i);
        successors[i].include(j);
        if (q.get(vars[i]).value() < q.get(vars[j]).value()+vars[j].lengthMin() ){
            q.get(vars[i]).setValue( q.get(vars[j]).value()+vars[j].lengthMin());
            computeQ(i);
        }


    }

    public void propageOnPrecedence(CPConstraint[] constraints){
        for(CPConstraint c : constraints) {
            onBind.push(c);
        }
        scheduleAll(onBind);
    }

    protected void scheduleAll(StateStack<CPConstraint> constraints) {
        for (int i = 0; i < constraints.size(); i++)
            vars[0].getSolver().schedule(constraints.get(i));
    }

    public int fillPredecessors(int varIdx, int[] out) {
        return predecessors[varIdx].fillIncluded(out);
    }

    public int fillSuccessors(int varIdx, int[] out) {
        return successors[varIdx].fillIncluded(out);
    }

    public int getQ(CPIntervalVar var) {
        return q.get(var).value();
    }

    @Override
    public void propagate() {
        for(int i = 0 ; i< vars.length; i++){
            for (int j = 0 ; j< vars.length; j++){
                if (i!=j && machine[i]==machine[j]){
                    if (vars[i].endMin() > vars[j].startMax()){
                        predecessors[i].include(j);
                        vars[0].getSolver().post(new EndBeforeStart(vars[j], vars[i]));
                        computeQ(j);
                    }
                }
            }
        }
    }
}