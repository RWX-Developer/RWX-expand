package com.corrodinggames.rts.game.units.custom.logicBooleans;

import com.corrodinggames.rts.game.units.BaseUnit;
import com.corrodinggames.rts.game.units.OrderableUnit;
import com.corrodinggames.rts.game.units.custom.CustomUnit;
import com.corrodinggames.rts.game.units.custom.CustomUnitConfig;
import com.corrodinggames.rts.game.units.custom.CustomUnitEventData;
import com.corrodinggames.rts.gameFramework.GameEngine;
import com.corrodinggames.rts.gameFramework.Utility;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.HashMap;
import java.util.Locale;

/* JADX INFO: loaded from: game-lib.jar:com/corrodinggames/rts/game/units/custom/logicBooleans/LogicBoolean.class */
public abstract class LogicBoolean implements Cloneable {
    public static final boolean not = false;

    static CustomUnitEventData activeEvent;

    static OrderableUnit outerUnitParameterContext;
    public static final StaticBoolean trueBoolean = new StaticBooleanTrue();
    public static final StaticBoolean falseBoolean = new StaticBooleanFalse();
    static CallContext_self callContext_self = new CallContext_self();
    static CallContext_selfAndTarget callContext_selfAndTarget = new CallContext_selfAndTarget();

    static final HashMap parameterMappings = new HashMap();

    static HashMap booleans = new HashMap();

    /* JADX INFO: loaded from: game-lib.jar:com/corrodinggames/rts/game/units/custom/logicBooleans/LogicBoolean$CallContext.class */
    public static class CallContext {
    }

    /* JADX INFO: loaded from: game-lib.jar:com/corrodinggames/rts/game/units/custom/logicBooleans/LogicBoolean$CallContext_self.class */
    public static class CallContext_self extends CallContext {
        public CustomUnit self;
    }

    /* JADX INFO: loaded from: game-lib.jar:com/corrodinggames/rts/game/units/custom/logicBooleans/LogicBoolean$CallContext_selfAndTarget.class */
    public static class CallContext_selfAndTarget extends CallContext_self {
        public BaseUnit target;
    }

    /* JADX INFO: loaded from: game-lib.jar:com/corrodinggames/rts/game/units/custom/logicBooleans/LogicBoolean$Parameter.class */
    @Retention(RetentionPolicy.RUNTIME)
    public @interface Parameter {
        ReturnType type() default ReturnType.undefined;

        boolean required() default false;

        int positional() default -1;

        String key() default "";
    }

    public abstract boolean read(OrderableUnit orderableUnit);

    public abstract String getMatchFailReasonForPlayer(OrderableUnit orderableUnit);

    static {
        LogicBooleanGameFunctions.loadTypes();
        addBooleanType(new VariableScope.KnownMemoryScopeLogicBoolean(), "memory");
        addBooleanType(new VariableScope.ReadUnitMemoryLogicBoolean(), "self.readUnitMemory");
        addBooleanType(new VariableScope.ReadEventMemoryLogicBoolean(), "eventData");
        addBooleanType(new LogicString.Playername(), "self.playername");
        addBooleanType(new LogicString.TeamName(), "self.teamname");
        addBooleanType(new LogicString.Debug(), "debug");
        addBooleanType(new LogicString.DebugPassthrough(), "debugPassthrough");
        addBooleanType(new LogicString.Select(), "select");
        addBooleanType(new LogicString.StringCast(), "str");
        addBooleanType(new LogicString.Substring(), "substring");
        addBooleanType(new LogicString.LowerString(), "lowercase");
        addBooleanType(new LogicString.UpperString(), "uppercase");
        addBooleanType(new LogicNumberFunction.FunctionInt(), "int");
        addBooleanType(new LogicNumberFunction.FunctionRnd(), "rnd");
        addBooleanType(new LogicNumberFunction.FunctionMax(), "max");
        addBooleanType(new LogicNumberFunction.FunctionMin(), "min");
        addBooleanType(new LogicNumberFunction.FunctionLength(), "length");
        addBooleanType(new LogicNumberFunction.FunctionSquareRoot(), "squareRoot");
        addBooleanType(new LogicNumberFunction.FunctionDistanceSquared(), "distanceSquared");
        addBooleanType(new LogicNumberFunction.FunctionCos(), "cos");
        addBooleanType(new LogicNumberFunction.FunctionSin(), "sin");
        addBooleanType(new LogicNumberFunction.FunctionDistance(), "distance");
        addBooleanType(new LogicNumberFunction.FunctionDirection(), "direction");
        addBooleanType(new LogicNumberFunction.FunctionDistanceBetweenSquared(), "distanceBetweenSquared");
        addBooleanType(new LogicNumberFunction.FunctionDistanceBetween(), "distanceBetween");
        addBooleanType(new LogicNumberFunction.FunctionDirectionBetween(), "directionBetween");
        addBooleanType(new LogicNumberFunction.CreateMarker(), "createMarker");
        addBooleanType(trueBoolean, "true");
        addBooleanType(falseBoolean, "false");
    }

    /* JADX INFO: loaded from: game-lib.jar:com/corrodinggames/rts/game/units/custom/logicBooleans/LogicBoolean$ReturnType.class */
    public enum ReturnType {
        undefined,
        voidReturn,
        bool,
        number,
        unit,
        string,
        point,
        boolArray,
        numberArray,
        unitArray;

        public static boolean canBeNull(ReturnType returnType) {
            boolean z = false;
            if (returnType == string) {
                z = true;
            }
            if (returnType == point) {
                z = true;
            }
            if (returnType == unit) {
                z = true;
            }
            if (returnType == numberArray) {
                z = true;
            }
            if (returnType == boolArray) {
                z = true;
            }
            if (returnType == unitArray) {
                z = true;
            }
            return z;
        }

        public static boolean isArrayType(ReturnType returnType) {
            return returnType == numberArray || returnType == boolArray || returnType == unitArray;
        }

        public static ReturnType getArrayBaseType(ReturnType returnType) {
            if (returnType == boolArray) {
                return bool;
            }
            if (returnType == numberArray) {
                return number;
            }
            if (returnType == unitArray) {
                return unit;
            }
            return null;
        }

        public static ReturnType getArrayTypeFromBase(ReturnType returnType) {
            if (returnType == bool) {
                return boolArray;
            }
            if (returnType == number) {
                return numberArray;
            }
            if (returnType == unit) {
                return unitArray;
            }
            return null;
        }

        public static String toUserString(ReturnType returnType) {
            return returnType == null ? "<NULL TYPE>" : returnType == numberArray ? "number[]" : returnType == boolArray ? "bool[]" : returnType == unitArray ? "unit[]" : returnType.name();
        }
    }

    public static final OrderableUnit getParameterContext(OrderableUnit orderableUnit) {
        OrderableUnit orderableUnit2 = outerUnitParameterContext;
        if (orderableUnit2 != null) {
            return orderableUnit2;
        }
        return orderableUnit;
    }

    public static final void setOuterUnitParameterContext(OrderableUnit orderableUnit) {
        outerUnitParameterContext = orderableUnit;
    }

    public static final void clearOuterUnitParameterContext() {
        outerUnitParameterContext = null;
    }

    public static void enableContextEventSource() {
    }

    public static void setContextEventSource(CustomUnitEventData customUnitEventData) {
        activeEvent = customUnitEventData;
    }

    public static void clearContext() {
        activeEvent = null;
    }

    static void addBooleanType(LogicBoolean logicBoolean, String... strArr) {
        for (String str : strArr) {
            String lowerCase = str.toLowerCase(Locale.ROOT);
            if (booleans.get(lowerCase) != null) {
                throw new RuntimeException("logicBoolean: " + lowerCase + " already exists");
            }
            booleans.put(lowerCase, logicBoolean);
        }
    }

    public void setArgumentsRaw(String str, CustomUnitConfig customUnitConfig, String str2) {
        LogicBooleanLoader.setArgumentsWithMapping(getParameters(), this, str, customUnitConfig, str2);
    }

    public LogicBooleanLoader.ParameterMapping getParameters() {
        LogicBooleanLoader.ParameterMapping parameterMapping = (LogicBooleanLoader.ParameterMapping) parameterMappings.get(getClass());
        if (parameterMapping == null) {
            parameterMapping = new LogicBooleanLoader.ParameterMapping(getClass());
            parameterMappings.put(parameterMapping.type, parameterMapping);
        }
        return parameterMapping;
    }

    public boolean isLocked() {
        return false;
    }

    public LogicBoolean createLocked() {
        throw new RuntimeException("Not implemented");
    }

    /* JADX INFO: loaded from: game-lib.jar:com/corrodinggames/rts/game/units/custom/logicBooleans/LogicBoolean$LogicBooleanCommon.class */
    public abstract static class LogicBooleanCommon extends LogicBoolean {
        public abstract String getName();

        @Override // com.corrodinggames.rts.game.units.custom.logicBooleans.LogicBoolean
        public String getMatchFailReasonForPlayer(OrderableUnit orderableUnit) {
            return getName() + "=" + (read(orderableUnit) ? "true" : "false") + VariableScope.nullOrMissingString;
        }
    }

    /* JADX INFO: loaded from: game-lib.jar:com/corrodinggames/rts/game/units/custom/logicBooleans/LogicBoolean$LogicBooleanCommonLocking.class */
    public abstract static class LogicBooleanCommonLocking extends LogicBoolean {
        boolean locked = false;

        @Override // com.corrodinggames.rts.game.units.custom.logicBooleans.LogicBoolean
        public LogicBoolean createLocked() {
            try {
                LogicBooleanCommonLocking logicBooleanCommonLocking = (LogicBooleanCommonLocking) clone();
                logicBooleanCommonLocking.locked = true;
                return logicBooleanCommonLocking;
            } catch (CloneNotSupportedException e) {
                throw new RuntimeException(e);
            }
        }

        @Override // com.corrodinggames.rts.game.units.custom.logicBooleans.LogicBoolean
        public boolean isLocked() {
            return this.locked;
        }
    }

    public void forMeta(CustomUnitConfig customUnitConfig) {
    }

    public LogicBoolean with(String str) {
        return with(null, str, null);
    }

    public LogicBoolean with(CustomUnitConfig customUnitConfig, String str, String str2) {
        try {
            LogicBoolean logicBoolean = (LogicBoolean) clone();
            logicBoolean.forMeta(customUnitConfig);
            if (isLocked()) {
                if (str != null && !str.trim().equals(VariableScope.nullOrMissingString)) {
                    throw new BooleanParseException("No parameters accepted for " + getClass().getSimpleName());
                }
            } else {
                logicBoolean.setArgumentsRaw(str, customUnitConfig, str2);
            }
            return logicBoolean;
        } catch (CloneNotSupportedException e) {
            throw new RuntimeException(e);
        }
    }

    public static LogicBoolean convertAlwaysTrueToNull(LogicBoolean logicBoolean) {
        if (logicBoolean == trueBoolean) {
            return null;
        }
        return logicBoolean;
    }

    public static boolean isStaticFalse(LogicBoolean logicBoolean) {
        return logicBoolean == falseBoolean;
    }

    public static boolean isStaticTrue(LogicBoolean logicBoolean) {
        return logicBoolean == trueBoolean;
    }

    public static boolean isStaticNull(LogicBoolean logicBoolean) {
        return logicBoolean instanceof UnitReference.NullUnitReference;
    }

    public static boolean isStaticNumber(LogicBoolean logicBoolean) {
        return logicBoolean instanceof StaticValueBoolean;
    }

    public static Float getStaticNumber(LogicBoolean logicBoolean) {
        if (logicBoolean instanceof StaticValueBoolean) {
            return Float.valueOf(((StaticValueBoolean) logicBoolean).staticNumber);
        }
        return null;
    }

    public static float getKnownStaticNumber(LogicBoolean logicBoolean) {
        return ((StaticValueBoolean) logicBoolean).staticNumber;
    }

    public static LogicBoolean create(CustomUnitConfig customUnitConfig, String str) {
        return create(customUnitConfig, str, null);
    }

    public static LogicBoolean create(CustomUnitConfig customUnitConfig, String str, LogicBoolean logicBoolean) {
        if (str == null) {
            return logicBoolean;
        }
        try {
            String lowerCase = str.toLowerCase(Locale.ENGLISH);
            if (lowerCase.equals("true")) {
                return trueBoolean;
            }
            if (lowerCase.equals("false")) {
                return falseBoolean;
            }
            if (lowerCase.startsWith("if ")) {
                return LogicBooleanLoader.parseBooleanBlock(customUnitConfig, str.substring("if ".length()), true);
            }
            throw new BooleanParseException("Cannot parse:'" + str + "' expected true, false or statement starting with 'if'");
        } catch (RuntimeException e) {
            throw new RuntimeException("LogicBoolean - Error: " + e.getMessage() + ", [parsing: '" + str + "']", e);
        }
    }

    public ReturnType getReturnType() {
        return ReturnType.bool;
    }

    public String valueToStringDebug(OrderableUnit orderableUnit) {
        ReturnType returnType = getReturnType();
        if (returnType == ReturnType.number) {
            return Utility.padString(readNumber(orderableUnit), 2);
        }
        if (returnType == ReturnType.unit) {
            return BaseUnit.serialize(readUnit(orderableUnit));
        }
        if (returnType == ReturnType.string) {
            return readString(orderableUnit);
        }
        if (ReturnType.isArrayType(returnType)) {
            return LogicString.arraySummaryToString(orderableUnit, this);
        }
        return read(orderableUnit) ? "true" : "false";
    }

    public void validate(String str, String str2, String str3, LogicBooleanLoader.LogicBooleanContext logicBooleanContext, boolean z) {
    }

    public LogicBoolean validateAndOptimize(String str, String str2, String str3, LogicBooleanLoader.LogicBooleanContext logicBooleanContext, boolean z) {
        validate(str, str2, str3, logicBooleanContext, z);
        return this;
    }

    public float readNumber(OrderableUnit orderableUnit) {
        return -9999.0f;
    }

    public String readString(OrderableUnit orderableUnit) {
        return null;
    }

    public BaseUnit readUnit(OrderableUnit orderableUnit) {
        return null;
    }

    public int getArraySize(OrderableUnit orderableUnit) {
        return 0;
    }

    public LogicBoolean readArrayElement(OrderableUnit orderableUnit, int i) {
        return null;
    }

    public String getDebugDetails(CustomUnit customUnit) {
        return getMatchFailReasonForPlayer(customUnit) + "==" + (read(customUnit) ? "true" : "false");
    }

    /* JADX INFO: loaded from: game-lib.jar:com/corrodinggames/rts/game/units/custom/logicBooleans/LogicBoolean$StaticBoolean.class */
    public abstract static class StaticBoolean extends LogicBoolean {
        @Override // com.corrodinggames.rts.game.units.custom.logicBooleans.LogicBoolean
        public LogicBoolean with(String str) {
            return this;
        }
    }

    /* JADX INFO: loaded from: game-lib.jar:com/corrodinggames/rts/game/units/custom/logicBooleans/LogicBoolean$StaticBooleanTrue.class */
    public static final class StaticBooleanTrue extends StaticBoolean {
        @Override // com.corrodinggames.rts.game.units.custom.logicBooleans.LogicBoolean
        public String getMatchFailReasonForPlayer(OrderableUnit orderableUnit) {
            return "true";
        }

        @Override // com.corrodinggames.rts.game.units.custom.logicBooleans.LogicBoolean
        public boolean read(OrderableUnit orderableUnit) {
            return true;
        }
    }

    /* JADX INFO: loaded from: game-lib.jar:com/corrodinggames/rts/game/units/custom/logicBooleans/LogicBoolean$StaticBooleanFalse.class */
    public static final class StaticBooleanFalse extends StaticBoolean {
        @Override // com.corrodinggames.rts.game.units.custom.logicBooleans.LogicBoolean
        public String getMatchFailReasonForPlayer(OrderableUnit orderableUnit) {
            return "false";
        }

        @Override // com.corrodinggames.rts.game.units.custom.logicBooleans.LogicBoolean
        public boolean read(OrderableUnit orderableUnit) {
            return false;
        }
    }

    /* JADX INFO: loaded from: game-lib.jar:com/corrodinggames/rts/game/units/custom/logicBooleans/LogicBoolean$JoinerBoolean.class */
    public static abstract class JoinerBoolean extends LogicBoolean {
        LogicBoolean[] children;

        public abstract String type();

        public static JoinerBoolean getNewJoiner(String str) {
            JoinerBoolean mathDivideJoinerBoolean;
            if (str.equals("or")) {
                mathDivideJoinerBoolean = new OrBoolean();
            } else if (str.equals("and")) {
                mathDivideJoinerBoolean = new AndBoolean();
            } else if (str.equals("==")) {
                mathDivideJoinerBoolean = new CompareJoinerBoolean.CompareEqualBoolean();
            } else if (str.equals("!=")) {
                mathDivideJoinerBoolean = new CompareJoinerBoolean.CompareNotEqualBoolean();
            } else if (str.equals(">")) {
                mathDivideJoinerBoolean = new CompareJoinerBoolean.CompareGreaterThanNumbers();
            } else if (str.equals(">=")) {
                mathDivideJoinerBoolean = new CompareJoinerBoolean.CompareGreaterThanOrEqualNumbers();
            } else if (str.equals("<")) {
                mathDivideJoinerBoolean = new CompareJoinerBoolean.CompareLessThanNumbers();
            } else if (str.equals("<=")) {
                mathDivideJoinerBoolean = new CompareJoinerBoolean.CompareLessThanOrEqualNumbers();
            } else if (str.equals("%")) {
                mathDivideJoinerBoolean = new CompareJoinerBoolean.MathModulusJoinerBoolean();
            } else if (str.equals("+")) {
                mathDivideJoinerBoolean = new CompareJoinerBoolean.MathAddJoinerBoolean();
            } else if (str.equals("-")) {
                mathDivideJoinerBoolean = new CompareJoinerBoolean.MathSubtractJoinerBoolean();
            } else if (str.equals("*")) {
                mathDivideJoinerBoolean = new CompareJoinerBoolean.MathMultiplyJoinerBoolean();
            } else if (str.equals("/")) {
                mathDivideJoinerBoolean = new CompareJoinerBoolean.MathDivideJoinerBoolean();
            } else {
                throw new BooleanParseException("Unknown joiner:'" + str + "'");
            }
            return mathDivideJoinerBoolean;
        }

        @Override // com.corrodinggames.rts.game.units.custom.logicBooleans.LogicBoolean
        public String getMatchFailReasonForPlayer(OrderableUnit orderableUnit) {
            String str = "(";
            boolean z = true;
            for (LogicBoolean logicBoolean : this.children) {
                if (z) {
                    z = false;
                } else {
                    str = str + " " + type() + " ";
                }
                str = str + logicBoolean.getMatchFailReasonForPlayer(orderableUnit);
            }
            return str + ")";
        }

        @Override // com.corrodinggames.rts.game.units.custom.logicBooleans.LogicBoolean
        public String getDebugDetails(CustomUnit customUnit) {
            String str = "(";
            boolean z = true;
            for (LogicBoolean logicBoolean : this.children) {
                if (z) {
                    z = false;
                } else {
                    str = str + " " + type() + " ";
                }
                str = str + logicBoolean.getDebugDetails(customUnit);
            }
            return str + ")";
        }

        @Override // com.corrodinggames.rts.game.units.custom.logicBooleans.LogicBoolean
        public final void validate(String str, String str2, String str3, LogicBooleanLoader.LogicBooleanContext logicBooleanContext, boolean z) {
        }

        @Override // com.corrodinggames.rts.game.units.custom.logicBooleans.LogicBoolean
        public LogicBoolean validateAndOptimize(String str, String str2, String str3, LogicBooleanLoader.LogicBooleanContext logicBooleanContext, boolean z) {
            return this;
        }

        public boolean requireBooleanChildren() {
            return true;
        }
    }

    /* JADX INFO: loaded from: game-lib.jar:com/corrodinggames/rts/game/units/custom/logicBooleans/LogicBoolean$OrBoolean.class */
    public static final class OrBoolean extends JoinerBoolean {
        @Override // com.corrodinggames.rts.game.units.custom.logicBooleans.LogicBoolean.JoinerBoolean
        public String type() {
            return "or";
        }

        @Override // com.corrodinggames.rts.game.units.custom.logicBooleans.LogicBoolean
        public boolean read(OrderableUnit orderableUnit) {
            for (LogicBoolean logicBoolean : this.children) {
                if (logicBoolean.read(orderableUnit)) {
                    return true;
                }
            }
            return false;
        }
    }

    /* JADX INFO: loaded from: game-lib.jar:com/corrodinggames/rts/game/units/custom/logicBooleans/LogicBoolean$AndBoolean.class */
    public static final class AndBoolean extends JoinerBoolean {
        @Override // com.corrodinggames.rts.game.units.custom.logicBooleans.LogicBoolean.JoinerBoolean
        public String type() {
            return "and";
        }

        @Override // com.corrodinggames.rts.game.units.custom.logicBooleans.LogicBoolean
        public boolean read(OrderableUnit orderableUnit) {
            for (LogicBoolean logicBoolean : this.children) {
                if (!logicBoolean.read(orderableUnit)) {
                    return false;
                }
            }
            return true;
        }
    }

    /* JADX INFO: loaded from: game-lib.jar:com/corrodinggames/rts/game/units/custom/logicBooleans/LogicBoolean$NotBoolean.class */
    public static final class NotBoolean extends LogicBoolean {
        LogicBoolean child;

        public NotBoolean(LogicBoolean logicBoolean) {
            this.child = logicBoolean;
        }

        @Override // com.corrodinggames.rts.game.units.custom.logicBooleans.LogicBoolean
        public boolean read(OrderableUnit orderableUnit) {
            return !this.child.read(orderableUnit);
        }

        @Override // com.corrodinggames.rts.game.units.custom.logicBooleans.LogicBoolean
        public String getMatchFailReasonForPlayer(OrderableUnit orderableUnit) {
            return "not(" + this.child.getMatchFailReasonForPlayer(orderableUnit) + ")";
        }
    }

    public static String getAllParametersDebug(LogicBoolean logicBoolean, OrderableUnit orderableUnit) {
        String str = VariableScope.nullOrMissingString;
        LogicBooleanLoader.ParameterMapping parameters = logicBoolean.getParameters();
        for (String str2 : parameters.parameters.keySet()) {
            LogicBooleanLoader.ParameterMapping.FieldOrMethod fieldOrMethod = (LogicBooleanLoader.ParameterMapping.FieldOrMethod) parameters.parameters.get(str2);
            if (fieldOrMethod.field != null) {
                if (!str.equals(VariableScope.nullOrMissingString)) {
                    str = str + ",";
                }
                String strValueToStringDebug = null;
                Object argumentTextWithMapping = LogicBooleanLoader.getArgumentTextWithMapping(fieldOrMethod, logicBoolean);
                if (argumentTextWithMapping instanceof String) {
                    strValueToStringDebug = argumentTextWithMapping.toString();
                }
                if (argumentTextWithMapping instanceof LogicBoolean) {
                    strValueToStringDebug = ((LogicBoolean) argumentTextWithMapping).valueToStringDebug(orderableUnit);
                }
                str = str + str2 + "=" + strValueToStringDebug;
            }
        }
        return str;
    }

    /* JADX INFO: loaded from: game-lib.jar:com/corrodinggames/rts/game/units/custom/logicBooleans/LogicBoolean$LogicNumberOnly.class */
    public abstract static class LogicNumberOnly extends LogicBoolean {
        public abstract String getName();

        @Override // com.corrodinggames.rts.game.units.custom.logicBooleans.LogicBoolean
        public abstract float readNumber(OrderableUnit orderableUnit);

        @Override // com.corrodinggames.rts.game.units.custom.logicBooleans.LogicBoolean
        public ReturnType getReturnType() {
            return ReturnType.number;
        }

        @Override // com.corrodinggames.rts.game.units.custom.logicBooleans.LogicBoolean
        public boolean read(OrderableUnit orderableUnit) {
            return false;
        }

        @Override // com.corrodinggames.rts.game.units.custom.logicBooleans.LogicBoolean
        public String getMatchFailReasonForPlayer(OrderableUnit orderableUnit) {
            return getName() + "(" + getAllParametersDebug(this, orderableUnit) + ")=" + Utility.padString(readNumber(orderableUnit), 3) + VariableScope.nullOrMissingString;
        }
    }

    /* JADX INFO: loaded from: game-lib.jar:com/corrodinggames/rts/game/units/custom/logicBooleans/LogicBoolean$AbstractNumberBoolean.class */
    public abstract static class AbstractNumberBoolean extends LogicBoolean {

        @Parameter
        public boolean full;

        @Parameter
        public boolean empty;

        @Parameter
        public float greaterThan = -1.0f;

        @Parameter
        public float lessThan = -1.0f;

        public abstract String getName();

        public abstract float getValue(OrderableUnit orderableUnit);

        public abstract float getMaxValue(OrderableUnit orderableUnit);

        @Parameter
        public void equalTo(float f) {
            this.greaterThan = f - 1.0E-4f;
            this.lessThan = f + 1.0E-4f;
        }

        @Override // com.corrodinggames.rts.game.units.custom.logicBooleans.LogicBoolean
        public ReturnType getReturnType() {
            if (this.greaterThan == -1.0f && this.lessThan == -1.0f && !this.full && !this.empty) {
                return ReturnType.number;
            }
            return ReturnType.bool;
        }

        @Override // com.corrodinggames.rts.game.units.custom.logicBooleans.LogicBoolean
        public void validate(String str, String str2, String str3, LogicBooleanLoader.LogicBooleanContext logicBooleanContext, boolean z) {
            super.validate(str, str2, str3, logicBooleanContext, z);
            if (z && this.greaterThan == -1.0f && this.lessThan == -1.0f && !this.full && !this.empty) {
                throw new BooleanParseException("Expected greaterThan, lessThan, full, and/or empty to be set for function:" + str + " to return a boolean");
            }
        }

        @Override // com.corrodinggames.rts.game.units.custom.logicBooleans.LogicBoolean
        public String getMatchFailReasonForPlayer(OrderableUnit orderableUnit) {
            String str = getName() + "=" + Utility.padString(getValue(orderableUnit), 3) + VariableScope.nullOrMissingString;
            if (this.full) {
                str = str + "(full)";
            }
            if (this.empty) {
                str = str + "(empty)";
            }
            if (this.greaterThan != -1.0f) {
                str = str + ">" + Utility.padString(this.greaterThan, 3);
            }
            if (this.lessThan != -1.0f) {
                str = str + "<" + Utility.padString(this.lessThan, 3);
            }
            return str;
        }

        // RWX 第十轮：只有「非负计数」型函数才允许提前收手，其余函数保持原样。
        protected boolean rwxCountOnly() {
            return false;
        }

        // RWX 第十轮：本次 getValue 的计数上限；默认 MAX 表示要求精确值。
        protected int rwxCountCap = Integer.MAX_VALUE;

        // RWX 第十二轮（顾寂：「自动触发必须关……不能让他空转」）：
        // `numberOfUnitsInTeam(withinRange=400) > 999999` 这种把阈值写在外层的写法，内层原来拿不到
        // 阈值，每 tick 全范围清点（4000 兵实测 180 万回调访问/tick）。现在两条锁，全部保持真值表不变：
        //   1) 饱和 cap（第十/十一轮）：扫描数到 阈值+1 就收手；饱和值 min(count,cap) 与真值在
        //      「与阈值±ε 比较」下结果恒等，所以 readNumber 也照设 cap（外层字面量比较拿到的
        //      是饱和值，判断仍正确）。纯数值读取（f(...) 当数用）保持精确，cap=MAX 时不动。
        //   2) 保护锁（本方法）：全队总数是免费的 O(1) 上界，范围计数 ≤ 总数；总数够不到阈值时
        //      连扫描都不发起，直接返回总数（如 `f(withinRange=400) > 999999` 恒假 → 空转条件秒判）。
        // 阈值必须严格在外层比较里才用保护锁；带 == 的比较会跨子表达式污染阈值，一律退回精确值。
        // -Drwx.countLock=0 整体退回原行为；-Drwx.countVerify=1 影子扫描对账（慢，仅调试）。
        // RWX 第十二轮（顾寂：「自动触发必须关……不能让他空转」）：
        // `numberOfUnitsInTeam(withinRange=400) > 999999` 这种把阈值写在外层的写法，内层原来拿不到
        // 阈值，每 tick 全范围清点（4000 兵实测 180 万回调访问/tick）。现在两条锁，真值表保持不变：
        //   1) 饱和 cap：解析期把外层字面量阈值下推进计数函数（rwxPushedGt/rwxPushedLt），扫描数到
        //      阈值+1 就收手；min(count,cap) 与真值在「与该阈值比较」下结果恒等，所以数值读取
        //      （readNumber）也照样设 cap。
        //   2) 保护锁：全队总数是免费的 O(1) 上界，范围计数 ≤ 总数；总数够不到阈值 → 连扫描都不发起，
        //      直接返回总数（`> 999999` 恒假的空转条件秒判）。仅在 targetTeam 为普通颜色队或
        //      敌/友军求和时启用——SystemTeam(TEAM_ALL/UNKNOWN) 的统计与索引桶不保证同步，保守不锁。
        // == / != / full / empty / 无阈值纯读取一律保持精确。-Drwx.countLock=0 整体退回原行为。
        public static final boolean rwxCountLock = !"0".equals(System.getProperty("rwx.countLock", "1"));
        // -Drwx.countVerify=1：跳过时仍然真扫一遍并核对两种答案的比较结果，只用于取证（很慢）。
        public static final boolean rwxCountVerify = "1".equals(System.getProperty("rwx.countVerify", "0"));
        public static int RWX_LOCK_N = 0;
        public static int RWX_LOCK_MISMATCH = 0;
        // 本节点的结果必须精确：被 == / != 或数学运算消费时，饱和值与总数都会改变判断，
        // 于是 cap 与保护锁一起关闭（解析期标记，见 CompareJoinerBoolean）。
        private boolean rwxExactNeeded;

        public void rwxMarkExactNeeded() {
            this.rwxExactNeeded = true;
        }
        private float rwxPushedGt = -1.0f;
        private float rwxPushedLt = -1.0f;

        // 解析期由字面量比较节点调用：把外层阈值下推给「计数 与字面量」的计数函数。
        // strOp ∈ {">", ">=", "<", "<="}；==/!= 不调用这里。
        public void rwxPushThreshold(String strOp, float f) {
            if (!rwxCountLock || this.rwxExactNeeded || !rwxCountOnly() || this.full || this.empty) {
                return;
            }
            if (strOp.equals(">")) {
                this.rwxPushedGt = Math.max(this.rwxPushedGt, f);
            } else if (strOp.equals(">=")) {
                this.rwxPushedGt = Math.max(this.rwxPushedGt, f - 1.0E-4f);
            } else if (strOp.equals("<")) {
                this.rwxPushedLt = this.rwxPushedLt < 0.0f ? f : Math.min(this.rwxPushedLt, f);
            } else if (strOp.equals("<=")) {
                this.rwxPushedLt = this.rwxPushedLt < 0.0f ? f + 1.0E-4f : Math.min(this.rwxPushedLt, f + 1.0E-4f);
            }
        }

        private float rwxMergedGt() {
            return Math.max(this.greaterThan, this.rwxPushedGt);
        }

        private float rwxMergedLt() {
            if (this.lessThan == -1.0f) {
                return this.rwxPushedLt;
            }
            if (this.rwxPushedLt == -1.0f) {
                return this.lessThan;
            }
            return Math.min(this.lessThan, this.rwxPushedLt);
        }

        // 保护锁：全队总数 total 已决定比较结果（不用再扫）时返回 true。
        // 调用方保证 total 是扫描集合的真上界（普通颜色队 / 敌友军求和）。
        public boolean rwxSkipLockApplies(int total) {
            if (!rwxCountLock || this.rwxExactNeeded || !rwxCountOnly() || this.full || this.empty) {
                return false;
            }
            float g = rwxMergedGt();
            float l = rwxMergedLt();
            if (g != -1.0f && l == -1.0f) {
                return total <= (int) Math.floor((double) g); // count ≤ total ≤ g → 「> g」必假
            }
            if (l != -1.0f && g == -1.0f) {
                return total < (int) Math.ceil((double) l);   // count ≤ total < l → 「< l」必真，total 即答案
            }
            return false; // 两侧夹逼或纯精确读取：不锁
        }

        // 保护锁用得对不对，只有「总数是不是扫描集合的真上界」这一个前提。rwxCountVerify 打开时，
        // 跳过之前仍真扫一遍，比较两种值代入阈值后的结果；不一致就留痕（最多 3 条）。
        public void rwxVerifySkipped(int skipped, int scanned) {
            RWX_LOCK_N++;
            if (!rwxCountVerify) {
                return;
            }
            if (rwxOutcomeFromCount(skipped) != rwxOutcomeFromCount(scanned)) {
                if (RWX_LOCK_MISMATCH++ < 3) {
                    io.github.rwx.probe.RwxPerf.out("RWX_LOCK_MISMATCH skipped=" + skipped + " scanned=" + scanned + " cond=" + getClass().getSimpleName() + " gt=" + rwxMergedGt() + " lt=" + rwxMergedLt());
                }
            }
        }

        private boolean rwxOutcomeFromCount(int i) {
            float g = rwxMergedGt();
            float l = rwxMergedLt();
            if (this.full || this.empty) {
                return false; // 这两种本来就不走锁
            }
            if (g != -1.0f && i <= g) {
                return false;
            }
            if (l != -1.0f && i >= l) {
                return false;
            }
            return true;
        }

        private int rwxCapForThreshold() {
            // RWX 第十一轮：显式阈值也参与 cap。第十二轮：外层字面量阈值（下推值）同样参与。
            // min(count,need) 至少要能区分的最小数：>g 需要 g+1，<l 需要 l，empty 需要 1
            float g = rwxCountLock ? rwxMergedGt() : this.greaterThan;
            float l = rwxCountLock ? rwxMergedLt() : this.lessThan;
            int need = 1;
            if (this.empty) {
                need = Math.max(need, 1);
            }
            if (g != -1.0f) {
                need = Math.max(need, (int) Math.floor((double) g) + 1);
            }
            if (l != -1.0f) {
                // 判「count < l」需要 l 以下精确：cap 取 l 时 min(count,l) 两侧都不会误判
                need = Math.max(need, (int) Math.ceil((double) l));
            }
            if (this.full) {
                return Integer.MAX_VALUE; // ==maxValue 需要精确总数
            }
            int cap = Math.max(need, 1);
            if (cap == 1 && !this.empty && g == -1.0f && l == -1.0f) {
                return Integer.MAX_VALUE; // 没带阈值的纯计数：要精确值
            }
            return cap;
        }

        @Override // com.corrodinggames.rts.game.units.custom.logicBooleans.LogicBoolean
        public float readNumber(OrderableUnit orderableUnit) {
            // RWX 第十二轮：`f(...) > N` 走的是数值读取，原来完全不设 cap、永远全扫描。
            // 现在与布尔读法同样：有阈值（含下推值）就按 min(count,cap) 交给外层比较，结果恒等。
            if (rwxCountLock && !this.rwxExactNeeded && rwxCountOnly()) {
                int cap = rwxCapForThreshold();
                if (cap != Integer.MAX_VALUE) {
                    this.rwxCountCap = cap;
                    try {
                        return getValue(orderableUnit);
                    } finally {
                        this.rwxCountCap = Integer.MAX_VALUE;
                    }
                }
            }
            return getValue(orderableUnit);
        }

        @Override // com.corrodinggames.rts.game.units.custom.logicBooleans.LogicBoolean
        public boolean read(OrderableUnit orderableUnit) {
            float value;
            if (rwxCountOnly() && !this.rwxExactNeeded) {
                this.rwxCountCap = rwxCapForThreshold();
                try {
                    value = getValue(orderableUnit);
                } finally {
                    this.rwxCountCap = Integer.MAX_VALUE;
                }
            } else {
                value = getValue(orderableUnit);
            }
            boolean z = true;
            float g = rwxCountLock ? rwxMergedGt() : this.greaterThan;
            float l = rwxCountLock ? rwxMergedLt() : this.lessThan;
            if (this.full && value < getMaxValue(orderableUnit)) {
                z = false;
            }
            if (this.empty && value > 0.0f) {
                z = false;
            }
            if (g != -1.0f && value <= g) {
                z = false;
            }
            if (l != -1.0f && value >= l) {
                z = false;
            }
            return z;
        }
    }

    /* JADX INFO: loaded from: game-lib.jar:com/corrodinggames/rts/game/units/custom/logicBooleans/LogicBoolean$TimeBoolean.class */
    public abstract static class TimeBoolean extends LogicBoolean {

        @Parameter
        public float laterThanSeconds = -1.0f;

        @Parameter
        public float withinSeconds = -1.0f;

        public abstract String getName();

        public abstract int getTime(OrderableUnit orderableUnit);

        @Override // com.corrodinggames.rts.game.units.custom.logicBooleans.LogicBoolean
        public ReturnType getReturnType() {
            if (this.laterThanSeconds == -1.0f && this.withinSeconds == -1.0f) {
                return ReturnType.number;
            }
            return ReturnType.bool;
        }

        @Override // com.corrodinggames.rts.game.units.custom.logicBooleans.LogicBoolean
        public void validate(String str, String str2, String str3, LogicBooleanLoader.LogicBooleanContext logicBooleanContext, boolean z) {
            super.validate(str, str2, str3, logicBooleanContext, z);
            if (z && this.laterThanSeconds == -1.0f && this.withinSeconds == -1.0f) {
                throw new BooleanParseException("Expended laterThanSeconds, or withinSeconds argument for function:" + str + " to return a boolean");
            }
        }

        @Override // com.corrodinggames.rts.game.units.custom.logicBooleans.LogicBoolean
        public String getMatchFailReasonForPlayer(OrderableUnit orderableUnit) {
            String str = getName() + "=" + msToSecondsString(GameEngine.getInstance().gameTimeMillis - getTime(orderableUnit));
            if (this.laterThanSeconds != -1.0f) {
                str = str + ">" + msToSecondsString(this.laterThanSeconds * 1000.0f);
            }
            if (this.withinSeconds != -1.0f) {
                str = str + "<" + msToSecondsString(this.withinSeconds * 1000.0f);
            }
            return str;
        }

        private String msToSecondsString(float f) {
            return Utility.padString(f / 1000.0f) + "s";
        }

        @Override // com.corrodinggames.rts.game.units.custom.logicBooleans.LogicBoolean
        public float readNumber(OrderableUnit orderableUnit) {
            return (GameEngine.getInstance().gameTimeMillis - getTime(orderableUnit)) * 0.001f;
        }

        @Override // com.corrodinggames.rts.game.units.custom.logicBooleans.LogicBoolean
        public boolean read(OrderableUnit orderableUnit) {
            int time = getTime(orderableUnit);
            boolean z = true;
            GameEngine gameEngine = GameEngine.getInstance();
            if (this.withinSeconds > 0.0f && gameEngine.gameTimeMillis - (this.withinSeconds * 1000.0f) > time) {
                z = false;
            }
            if (this.laterThanSeconds > 0.0f && gameEngine.gameTimeMillis - (this.laterThanSeconds * 1000.0f) < time) {
                z = false;
            }
            return z;
        }
    }

    /* JADX INFO: loaded from: game-lib.jar:com/corrodinggames/rts/game/units/custom/logicBooleans/LogicBoolean$StaticValueBoolean.class */
    public static final class StaticValueBoolean extends LogicNumberOnly {
        public static final StaticValueBoolean static_0 = new StaticValueBoolean(0.0f);
        public static final StaticValueBoolean static_neg1 = new StaticValueBoolean(-1.0f);
        public static final StaticValueBoolean static_1 = new StaticValueBoolean(1.0f);
        float staticNumber;

        public static StaticValueBoolean getStaticNumber(String str) {
            try {
                return getStaticNumber(Float.parseFloat(str));
            } catch (NumberFormatException e) {
                throw new RuntimeException("Error reading number: " + str, e);
            }
        }

        public static StaticValueBoolean getStaticNumber(float f) {
            return f == 0.0f ? static_0 : f == 1.0f ? static_1 : f == -1.0f ? static_neg1 : new StaticValueBoolean(f);
        }

        StaticValueBoolean(float f) {
            this.staticNumber = f;
        }

        @Override // com.corrodinggames.rts.game.units.custom.logicBooleans.LogicBoolean.LogicNumberOnly
        public String getName() {
            return VariableScope.nullOrMissingString + this.staticNumber;
        }

        @Override
        // com.corrodinggames.rts.game.units.custom.logicBooleans.LogicBoolean.LogicNumberOnly, com.corrodinggames.rts.game.units.custom.logicBooleans.LogicBoolean
        public final float readNumber(OrderableUnit orderableUnit) {
            return this.staticNumber;
        }

        public float getStaticValue() {
            return this.staticNumber;
        }

        @Override
        // com.corrodinggames.rts.game.units.custom.logicBooleans.LogicBoolean.LogicNumberOnly, com.corrodinggames.rts.game.units.custom.logicBooleans.LogicBoolean
        public String getMatchFailReasonForPlayer(OrderableUnit orderableUnit) {
            return Utility.padString(this.staticNumber, 3);
        }
    }

    public LogicBooleanLoader.LogicBooleanContext createContext() {
        return LogicBooleanLoader.voidContextReader;
    }

    public void throwVoidReturnError(String str) {
        throw new RuntimeException("Function or object:'" + str + "' cannot be returned");
    }

    public LogicBoolean setChild(LogicBoolean logicBoolean) {
        throw new RuntimeException("setChild not supported on: " + getClass().getSimpleName());
    }

    public void setParent(LogicBoolean logicBoolean) {
    }
}
