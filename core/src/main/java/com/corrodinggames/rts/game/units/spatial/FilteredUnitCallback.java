package com.corrodinggames.rts.game.units.spatial;

import com.corrodinggames.rts.game.PlayerTeam;
import com.corrodinggames.rts.game.units.BaseUnit;
import com.corrodinggames.rts.game.units.OrderableUnit;

/* JADX INFO: renamed from: com.corrodinggames.rts.game.units.f.i */
/* JADX INFO: loaded from: game-lib.jar:com/corrodinggames/rts/game/units/f/i.class */
public abstract class FilteredUnitCallback extends UnitSpatialCallback {
    public abstract int excludeTeam(OrderableUnit orderableUnit);

    public abstract PlayerTeam onlyEnemiesOfTeam(OrderableUnit orderableUnit);

    public PlayerTeam onlyTeam(OrderableUnit orderableUnit) {
        return null;
    }

    public void setup(OrderableUnit orderableUnit, float f) {
    }

    // RWX 第八轮：回调结果与遍历顺序无关（比如只做计数）时返回 true，
    // 空间索引可以换一种遍历结构扫描；默认 false 保持原顺序语义。
    public boolean orderIndependent() {
        return false;
    }

    // RWX 第十轮：计数型回调越过上限后置 true，空间扫掠立即收手。
    // 语义依据：AbstractNumberBoolean.read 只拿计数跟 greaterThan/lessThan/empty 比较，
    // 把计数饱和到 cap 不改变任何比较结果；readNumber 路径不加 cap，仍是精确值。
    public boolean rwxAbort = false;
    public int rwxCap = Integer.MAX_VALUE;

    public BaseUnit getResult() {
        return null;
    }
}
