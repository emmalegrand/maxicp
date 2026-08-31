//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by FernFlower decompiler)
//

package org.maxicp.cp.engine.constraints.scheduling;

import org.maxicp.cp.engine.core.AbstractCPConstraint;
import org.maxicp.cp.engine.core.CPIntervalVar;
import org.maxicp.util.exception.InconsistencyException;

public class NotFirst extends AbstractCPConstraint {
    final CPIntervalVar A;
    final CPIntervalVar[] vars;

    public NotFirst(CPIntervalVar A, CPIntervalVar[] vars) {
        super(A.getSolver());
        this.A = A;
        this.vars = vars;
    }

    public void post() {
        if (!this.A.isAbsent()) {
            this.A.propagateOnChange(this);
        }

        if (this.vars.length == 0) {
            throw new InconsistencyException();
        } else {
            CPIntervalVar[] var1 = this.vars;
            int var2 = var1.length;

            for(int var3 = 0; var3 < var2; ++var3) {
                CPIntervalVar var = var1[var3];
                if (!var.isAbsent()) {
                    var.propagateOnChange(this);
                }
            }

            this.propagate();
        }
    }

    public void propagate() {
        int minEct = Integer.MAX_VALUE;
        CPIntervalVar[] var2 = this.vars;
        int var3 = var2.length;

        for(int var4 = 0; var4 < var3; ++var4) {
            CPIntervalVar var = var2[var4];
            if (!var.isAbsent()) {
                minEct = Math.min(minEct, var.endMin());
            }
        }

        if (minEct == Integer.MAX_VALUE) {
            throw new InconsistencyException();
        } else {
            this.A.setStartMin(minEct);
        }
    }
}

