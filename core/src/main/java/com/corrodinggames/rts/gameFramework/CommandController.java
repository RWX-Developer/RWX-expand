package com.corrodinggames.rts.gameFramework;

import com.corrodinggames.rts.game.PlayerTeam;
import io.github.rwx.mod.CommandQueue;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;

/* JADX INFO: renamed from: com.corrodinggames.rts.gameFramework.c */
/* JADX INFO: loaded from: game-lib.jar:com/corrodinggames/rts/gameFramework/c.class */
public class CommandController {
    // RWX：累计被跳过的空 AI 命令数，供诊断读取
    public static long rwxEmptyAiCommands = 0L;
    // RWX：与 Command.java 同语义的拒令开关（RWX_AI_GUARD=0 关）
    private static final boolean RWX_AI_GUARD_OFF = "0".equals((System.getenv("RWX_AI_GUARD") == null ? "" : System.getenv("RWX_AI_GUARD").trim()));

    /* JADX INFO: renamed from: a */
    static final boolean DEBUG_TRACE_ENABLED = false;

    /* JADX INFO: renamed from: b */
    public ArrayList<Command> pendingCommands = new ArrayList();

    /* JADX INFO: renamed from: c */
    public ArrayList<Command> executedCommands = new ArrayList();

    /* JADX INFO: renamed from: d */
    public ArrayList<Command> queuedCommands = new ArrayList();

    /* JADX INFO: renamed from: e */
    static int logRateLimitCounter;

    /* JADX INFO: renamed from: a */
    public static void logWithRateLimit(String str) {
        logRateLimitCounter++;
        if (logRateLimitCounter == 5) {
            GameEngine.log("(Rate Limiting...)");
        }
        if (logRateLimitCounter >= 5) {
            return;
        }
        GameEngine.log(str);
    }

    /* JADX INFO: renamed from: a */
    public void clearAllCommands() {
        CommandQueue.clear();
        this.pendingCommands.clear();
        this.executedCommands.clear();
        this.queuedCommands.clear();
    }

    /* JADX INFO: renamed from: b */
    public Command createCommand() {
        Command command = new Command(this);
        if (DEBUG_TRACE_ENABLED) {
            GameEngine.log("Tracing source");
            command.debugStackTrace = GameEngine.getStackTrace(new Exception("Test"));
        }
        return command;
    }

    /* JADX INFO: renamed from: a */
    public Command newCommandForTeam(PlayerTeam playerTeam) {
        return createCommandForTeam(playerTeam);
    }

    /* JADX INFO: renamed from: b */
    public Command createCommandForTeam(PlayerTeam playerTeam) {
        if (playerTeam == null) {
            throw new RuntimeException("team==null");
        }
        GameEngine gameEngine = GameEngine.getInstance();
        Command command = new Command(this);
        command.team = playerTeam;
        // RWX：AI 下的命令要带标记，落点先做可通行校正。
        command.rwxFromAi = playerTeam instanceof com.corrodinggames.rts.game.ai.AIController;
        command.createdTick = gameEngine.gameTimeMillis;
        if (DEBUG_TRACE_ENABLED) {
            GameEngine.log("Tracing source");
            command.debugStackTrace = GameEngine.getStackTrace(new Exception("Test"));
        }
        if (!gameEngine.networkEngine.networkGameActive) {
            if (!command.prepareAndValidateCommand()) {
                GameEngine.logColored("Command failed prepareAndCheckOnServer()");
            }
            this.pendingCommands.add(command);
        } else {
            this.queuedCommands.add(command);
        }
        return command;
    }

    /* JADX INFO: renamed from: c */
    public void executeAllCommands() throws IOException {
        GameEngine gameEngine = GameEngine.getInstance();
        logRateLimitCounter = 0;
        if (!gameEngine.networkEngine.networkGameActive) {
            executeLocalCommands();
        } else {
            executeNetworkCommands();
        }
    }

    /* JADX INFO: renamed from: d */
    public void executeLocalCommands() throws IOException {
        GameEngine gameEngine = GameEngine.getInstance();
        int i = gameEngine.currentTick;
        int i2 = 0;
        for (Command command : this.pendingCommands) {
            // RWX：老师指正「AI 指挥的算法要优化」——没有选中单位的命令是空转：
            // 它仍然要序列化进 replay、要跑一遍 executeCommand 的全部前置逻辑。
            // AI 每 800ms 无条件建一条，过滤后常常一个单位都没有。这里直接跳过。
            if (!RWX_AI_GUARD_OFF && command.rwxFromAi && (command.selectedUnits == null || command.selectedUnits.isEmpty())) {
                rwxEmptyAiCommands++;
                if (rwxEmptyAiCommands % 20 == 0) {
                    io.github.rwx.probe.RwxPerf.out("RWX_AI_EMPTY_SKIPPED total=" + rwxEmptyAiCommands
                        + " tick=" + i + " pending=" + this.pendingCommands.size());
                }
                continue;
            }
            gameEngine.replayEngine.a(command, i);
            command.executeCommand();
            i2++;
        }
        this.pendingCommands.clear();
        if (i2 > 0) {
            gameEngine.replayEngine.c();
        }
    }

    /* JADX INFO: renamed from: e */
    public void executeNetworkCommands() throws IOException {
        GameEngine gameEngine = GameEngine.getInstance();
        int i = gameEngine.currentTick;
        int i2 = 0;
        Iterator it = this.pendingCommands.iterator();
        while (it.hasNext()) {
            Command command = (Command) it.next();
            if (command.scheduledTick == i) {
                gameEngine.replayEngine.a(command, i);
                command.executeCommand();
                it.remove();
                i2++;
            }
        }
        if (i2 > 0) {
            gameEngine.replayEngine.c();
        }
    }
}
