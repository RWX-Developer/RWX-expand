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

    public BaseUnit getResult() {
        return null;
    }
}
