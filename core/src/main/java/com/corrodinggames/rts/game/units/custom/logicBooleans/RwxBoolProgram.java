package com.corrodinggames.rts.game.units.custom.logicBooleans;

import com.corrodinggames.rts.game.units.OrderableUnit;

/**
 * RWX：把布尔表达式树编译成一段扁平的线性指令，求值时不再逐节点虚调用。
 *
 * 动机：铁锈的「行动 / 隐藏行动 / 炮塔」三节里布尔表达式极多，且每 tick 每个单位
 * 都要重新求值。原实现是树形递归 + 虚方法派发（每个节点一次 read()），缓存局部性差、
 * 分支预测差。这里做两件事：
 *   1) 只把逻辑骨架（and / or / not / 常量）折算成指令，其它节点（比较、数值、函数）
 *      整体当成叶子，语义完全不变；
 *   2) 求值走一个紧凑的 switch 循环 + 布尔栈，and/or 仍然短路。
 *
 * 这个类本身就是一个 LogicBoolean，所以所有现有调用点无需改动，只要在解析完成处
 * 把树包一层即可。
 */
public class RwxBoolProgram extends LogicBoolean {

    private static final int OP_LEAF = 1;
    private static final int OP_TRUE = 2;
    private static final int OP_FALSE = 3;
    private static final int OP_NOT = 4;
    private static final int OP_AND = 5;   // 栈顶为 false 则跳到 arg 处（保留 false），否则弹出继续
    private static final int OP_OR = 6;    // 栈顶为 true  则跳到 arg 处（保留 true），否则弹出继续
    private static final int OP_HALT = 7;
    private static final int OP_TRUEJUMP = 9;  // 栈顶为 true 则跳到 arg（保留 true），否则弹出继续
    private static final int OP_FALSEJUMP = 10; // 栈顶为 false 则跳到 arg（保留 false），否则弹出继续

    private final int[] code;
    private final int[] arg;
    private final LogicBoolean[] leaves;
    private final LogicBoolean original;
    private final int depth;

    private RwxBoolProgram(int[] code, int[] arg, LogicBoolean[] leaves, int depth, LogicBoolean original) {
        this.code = code;
        this.arg = arg;
        this.leaves = leaves;
        this.depth = depth;
        this.original = original;
    }

    private static final ThreadLocal<boolean[]> RWX_STACK = new ThreadLocal<boolean[]>() {
        @Override
        protected boolean[] initialValue() {
            return new boolean[64];
        }
    };

    @Override
    public boolean read(OrderableUnit orderableUnit) {
        int[] c = this.code;
        int[] a = this.arg;
        LogicBoolean[] l = this.leaves;
        boolean[] st = RWX_STACK.get();
        if (st.length < this.depth) {
            st = new boolean[this.depth + 8];
            RWX_STACK.set(st);
        }
        int sp = 0;
        int pc = 0;
        while (true) {
            int op = c[pc];
            switch (op) {
                case OP_LEAF:
                    st[sp++] = l[a[pc]].read(orderableUnit);
                    pc++;
                    break;
                case OP_TRUE:
                    st[sp++] = true;
                    pc++;
                    break;
                case OP_FALSE:
                    st[sp++] = false;
                    pc++;
                    break;
                case OP_NOT:
                    st[sp - 1] = !st[sp - 1];
                    pc++;
                    break;
                case OP_AND:
                case OP_FALSEJUMP:
                    if (!st[sp - 1]) {
                        pc = a[pc];
                    } else {
                        sp--;
                        pc++;
                    }
                    break;
                case OP_OR:
                case OP_TRUEJUMP:
                    if (st[sp - 1]) {
                        pc = a[pc];
                    } else {
                        sp--;
                        pc++;
                    }
                    break;
                case OP_HALT:
                    return st[sp - 1];
                default:
                    // 不该发生：退回原树，保证行为正确
                    return this.original != null ? this.original.read(orderableUnit) : false;
            }
        }
    }

    @Override
    public String getMatchFailReasonForPlayer(OrderableUnit orderableUnit) {
        return this.original != null ? this.original.getMatchFailReasonForPlayer(orderableUnit) : "compiled";
    }

    @Override
    public LogicBooleanLoader.ParameterMapping getParameters() {
        return this.original != null ? this.original.getParameters() : super.getParameters();
    }

    @Override
    public LogicBoolean createLocked() {
        return this;
    }

    // 非布尔读取模式（数值 / 字符串 / 单位 / 数组）全部转发给原树，
    // 这样把任何表达式包进来都不会改变原有语义，只是这些模式不吃编译收益。
    @Override
    public float readNumber(OrderableUnit orderableUnit) {
        return this.original != null ? this.original.readNumber(orderableUnit) : -9999.0f;
    }

    @Override
    public String readString(OrderableUnit orderableUnit) {
        return this.original != null ? this.original.readString(orderableUnit) : null;
    }

    @Override
    public com.corrodinggames.rts.game.units.BaseUnit readUnit(OrderableUnit orderableUnit) {
        return this.original != null ? this.original.readUnit(orderableUnit) : null;
    }

    @Override
    public int getArraySize(OrderableUnit orderableUnit) {
        return this.original != null ? this.original.getArraySize(orderableUnit) : 0;
    }

    @Override
    public LogicBoolean readArrayElement(OrderableUnit orderableUnit, int i) {
        return this.original != null ? this.original.readArrayElement(orderableUnit, i) : null;
    }

    public int rwxLeafCount() {
        return this.leaves.length;
    }

    public int rwxCodeSize() {
        return this.code.length;
    }

    // ------------------------------------------------------------------
    // 编译器
    // ------------------------------------------------------------------
    private static final int MAX_CODE = 8192;
    private static final int MAX_LEAVES = 512;

    public static LogicBoolean compile(LogicBoolean tree) {
        if (tree == null) {
            return null;
        }
        if (tree instanceof RwxBoolProgram) {
            return tree;
        }
        try {
            Builder b = new Builder();
            b.emit(tree);
            if (b.failed || b.n == 0 || b.n > MAX_CODE || b.leafCount > MAX_LEAVES) {
                return tree;
            }
            b.emitOp(OP_HALT, 0);
            int[] code = new int[b.n];
            int[] arg = new int[b.n];
            System.arraycopy(b.code, 0, code, 0, b.n);
            System.arraycopy(b.arg, 0, arg, 0, b.n);
            LogicBoolean[] leaves = new LogicBoolean[b.leafCount];
            for (int i = 0; i < b.leafCount; i++) {
                leaves[i] = b.leaves.get(i);
            }
            return new RwxBoolProgram(code, arg, leaves, b.maxDepth, tree);
        } catch (Throwable t) {
            return tree;
        }
    }

    private static final class Builder {
        int[] code = new int[64];
        int[] arg = new int[64];
        java.util.ArrayList<LogicBoolean> leaves = new java.util.ArrayList<LogicBoolean>();
        int leafCount;
        int n;
        int curDepth;
        int maxDepth;
        boolean failed;

        void emitOp(int op, int a) {
            if (n >= MAX_CODE) {
                failed = true;
                return;
            }
            if (n == code.length) {
                int[] c2 = new int[code.length * 2];
                int[] a2 = new int[arg.length * 2];
                System.arraycopy(code, 0, c2, 0, n);
                System.arraycopy(arg, 0, a2, 0, n);
                code = c2;
                arg = a2;
            }
            code[n] = op;
            arg[n] = a;
            n++;
        }

        void emit(LogicBoolean e) {
            if (failed || e == null) {
                failed = true;
                return;
            }
            if (e instanceof LogicBoolean.StaticBooleanTrue) {
                emitOp(OP_TRUE, 0);
                bumpDepth(1);
                return;
            }
            if (e instanceof LogicBoolean.StaticBooleanFalse) {
                emitOp(OP_FALSE, 0);
                bumpDepth(1);
                return;
            }
            if (e instanceof LogicBoolean.AndBoolean) {
                LogicBoolean[] children = ((LogicBoolean.JoinerBoolean) e).children;
                if (children == null || children.length == 0) {
                    emitOp(OP_TRUE, 0);
                    bumpDepth(1);
                    return;
                }
                emit(children[0]);
                java.util.ArrayList<Integer> jumps = new java.util.ArrayList<Integer>();
                for (int i = 1; i < children.length; i++) {
                    int jumpAt = n;
                    emitOp(OP_AND, -1);
                    jumps.add(Integer.valueOf(jumpAt));
                    emit(children[i]);
                }
                int end = n;
                patch(jumps, end);
                return;
            }
            if (e instanceof LogicBoolean.OrBoolean) {
                LogicBoolean[] children = ((LogicBoolean.JoinerBoolean) e).children;
                if (children == null || children.length == 0) {
                    emitOp(OP_FALSE, 0);
                    bumpDepth(1);
                    return;
                }
                emit(children[0]);
                java.util.ArrayList<Integer> jumps = new java.util.ArrayList<Integer>();
                for (int i = 1; i < children.length; i++) {
                    int jumpAt = n;
                    emitOp(OP_OR, -1);
                    jumps.add(Integer.valueOf(jumpAt));
                    emit(children[i]);
                }
                int end = n;
                patch(jumps, end);
                return;
            }
            if (e instanceof LogicBoolean.NotBoolean) {
                emit(((LogicBoolean.NotBoolean) e).child);
                emitOp(OP_NOT, 0);
                return;
            }
            // 其它一切（比较、数值、函数、自定义条件）整体当叶子
            if (leafCount >= MAX_LEAVES) {
                failed = true;
                return;
            }
            leaves.add(e);
            emitOp(OP_LEAF, leafCount);
            leafCount++;
            bumpDepth(1);
        }

        void bumpDepth(int d) {
            this.curDepth += d;
            if (this.curDepth > this.maxDepth) {
                this.maxDepth = this.curDepth;
            }
        }

        void patch(java.util.ArrayList<Integer> jumps, int target) {
            for (int i = 0; i < jumps.size(); i++) {
                int at = jumps.get(i);
                if (arg[at] == -1) {
                    arg[at] = target;
                }
            }
            jumps.clear();
        }
    }
}
