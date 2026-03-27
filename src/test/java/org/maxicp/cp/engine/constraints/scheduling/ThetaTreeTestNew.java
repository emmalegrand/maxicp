package org.maxicp.cp.engine.constraints.scheduling;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class ThetaTreeTestNew {
    @Test
    public void simpleTest0() {
        ThetaTree2 tree = new ThetaTree2(6);
        tree.insert(0, 0, 8, 25);
        assertEquals(33, tree.getEct());
        tree.insert(1, 4, 6, 20);
        assertEquals(34, tree.getEct());
        tree.insert(2, 9, 4, 30);
        assertEquals(43, tree.getEct());
        tree.insert(3, 15, 5, 9);
        assertEquals(43, tree.getEct());
        tree.insert(4, 20, 8, 14);
        assertEquals(43, tree.getEct());
        tree.insert(5, 21, 8, 16);
        assertEquals(50, tree.getEct());
        tree.remove(5);
        assertEquals(43, tree.getEct());
        tree.reset();
        assertEquals(Integer.MIN_VALUE, tree.getEct());
    }
    @Test
    public void simpleTest1() {
        ThetaTree2 tree = new ThetaTree2(4);
        tree.insert(0, 0, 5, 5);
        assertEquals(10, tree.getEct());
        tree.insert(1, 25, 6, 20);
        assertEquals(51, tree.getEct());
        tree.insert(2, 30, 4, 9);
        assertEquals(51, tree.getEct());
        tree.insert(3, 32, 10, 4);
        assertEquals(51, tree.getEct());

        tree.remove(3);
        assertEquals(51, tree.getEct());
        tree.reset();
        assertEquals(Integer.MIN_VALUE, tree.getEct());
    }

}
