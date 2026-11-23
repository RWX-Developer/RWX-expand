package io.github.rwx.input;

import com.corrodinggames.rts.gameFramework.GameEngine;

/* Legacy pointer-state shape retained while input is supplied by Kool. */
public class MultiTouchPointerState {
    private static final boolean DEBUG_SLICK_MENU = "1".equals(System.getenv("RWX_DEBUG_SLICK_MENU"));

    private int numPointers;

    private float[] x = new float[10];

    private float[] y = new float[10];

    private float[] pressure = new float[10];

    private int[] pointerIds = new int[10];

    private float startX;

    private float startY;

    private float startPressure;

    private float distX;

    private float distY;

    private boolean isDown;

    private boolean isMultiTouch;

    private boolean wasDown;

    private int lastNumPointers;

    private boolean isDragging;

    private boolean isPinching;

    private boolean isRotating;

    private int historyCount;

    private boolean releaseButtonPendingClear;

    public MultiTouchPointerState() {
        for (int i = 0; i < this.x.length; i++) {
            this.x[i] = 40.0f;
        }
        for (int i2 = 0; i2 < this.y.length; i2++) {
            this.y[i2] = 40.0f;
        }
    }

    /** RWX：测试用只读探针。 */
    public boolean rwxIsDown() { return this.isDown; }

    public int rwxNumPointers() { return this.numPointers; }

    public int getLastNumPointers() {
        return this.lastNumPointers;
    }

    public boolean wasDown() {
        return this.wasDown;
    }

    // RWX：测试注入开关。每次 updateState 时把注入的指针强行写回，否则会被真实输入（0 个指针）覆盖。
    public static volatile boolean rwxInjectedDown = false;
    public static volatile float rwxInjectedX = 0.0f;
    public static volatile float rwxInjectedY = 0.0f;

    public void updateState() {
        if (rwxInjectedDown) {
            this.x[0] = rwxInjectedX;
            this.y[0] = rwxInjectedY;
            this.isDown = true;
            this.numPointers = 1;
        }
        boolean previousWasDown = this.wasDown;
        this.wasDown = this.isDown;
        this.lastNumPointers = this.numPointers;
        if (!this.isDown && this.releaseButtonPendingClear && !previousWasDown) {
            for (int i = 0; i < MultiTouchController.pointerIndices.length; i++) {
                MultiTouchController.pointerIndices[i] = -1;
            }
            this.releaseButtonPendingClear = false;
        }
        if (DEBUG_SLICK_MENU && (this.isDown || this.wasDown || previousWasDown || this.lastNumPointers != 0 || this.numPointers != 0 || this.releaseButtonPendingClear)) {
            GameEngine.log("RWX_DEBUG_SLICK_MENU pointer.updateState previousWasDown=" + previousWasDown
                    + " wasDown=" + this.wasDown
                    + " isDown=" + this.isDown
                    + " numPointers=" + this.numPointers
                    + " lastNumPointers=" + this.lastNumPointers
                    + " releasePendingClear=" + this.releaseButtonPendingClear
                    + " x0=" + this.x[0]
                    + " y0=" + this.y[0]
                    + " pointer0=" + MultiTouchController.pointerIndices[0]);
        }
    }

    /**
     * RWX：给测试用的指针注入。按下一帧后由调用方在若干帧后调 rwxRelease()。
     * 不走操作系统，所以不依赖窗口焦点——窗口在后台也能生效。
     */
    public void rwxPress(float px, float py) {
        this.x[0] = px;
        this.y[0] = py;
        this.startX = px;
        this.startY = py;
        this.isDown = true;
        this.numPointers = 1;
        this.updateState();
    }

    public void rwxRelease() {
        this.isDown = false;
        this.numPointers = 0;
        this.updateState();
    }

    public void setStart(float f, float f2) {
        this.x[0] = f;
        this.y[0] = f2;
        this.startX = this.x[0];
        this.startY = this.y[0];
        this.distY = 0.0f;
        this.distX = 0.0f;
    }

    public void processEvent(float f, float f2, boolean z, int i) {
        this.historyCount = 0;
        this.numPointers = z ? 1 : 0;
        for (int i2 = 0; i2 < MultiTouchController.pointerIndices.length; i2++) {
            MultiTouchController.pointerIndices[i2] = -1;
        }
        if (i != -1) {
            MultiTouchController.pointerIndices[0] = i;
        }
        this.releaseButtonPendingClear = !z && i != -1;
        this.x[0] = f;
        this.y[0] = f2;
        this.pressure[0] = 0.0f;
        this.pointerIds[0] = z ? i : -1;
        this.isDown = z;
        this.isMultiTouch = false;
        if (this.isDown) {
            this.wasDown = this.isDown;
        }
        if (this.numPointers > 0) {
            this.lastNumPointers = this.numPointers;
        }
        this.startX = this.x[0];
        this.startY = this.y[0];
        this.startPressure = this.pressure[0];
        this.distY = 0.0f;
        this.distX = 0.0f;
        this.isRotating = false;
        this.isPinching = false;
        this.isDragging = false;
        if (DEBUG_SLICK_MENU) {
            GameEngine.log("RWX_DEBUG_SLICK_MENU pointer.processEvent x=" + f
                    + " y=" + f2
                    + " down=" + z
                    + " pointerId=" + i
                    + " wasDown=" + this.wasDown
                    + " numPointers=" + this.numPointers
                    + " lastNumPointers=" + this.lastNumPointers
                    + " releasePendingClear=" + this.releaseButtonPendingClear
                    + " pointer0=" + MultiTouchController.pointerIndices[0]);
        }
    }

    public void processTouchSnapshot(float[] pointerX, float[] pointerY, int count, boolean down, int buttonState) {
        // RWX：平台每帧推一份真实指针快照（通常是 0 个指针），在这里把测试注入的指针合并进去。
        if (rwxInjectedDown) {
            pointerX[0] = rwxInjectedX;
            pointerY[0] = rwxInjectedY;
            count = 1;
            down = true;
            buttonState = 1;
        }
        int pointerCount = Math.max(0, Math.min(count, this.x.length));
        this.historyCount = 0;
        this.numPointers = pointerCount;
        for (int i = 0; i < MultiTouchController.pointerIndices.length; i++) {
            MultiTouchController.pointerIndices[i] = -1;
        }
        for (int i = 0; i < pointerCount; i++) {
            this.x[i] = pointerX[i];
            this.y[i] = pointerY[i];
            this.pressure[i] = 0.0f;
            this.pointerIds[i] = -1;
            MultiTouchController.pointerIndices[i] = buttonState;
        }
        this.releaseButtonPendingClear = !down && buttonState != 0;
        this.isDown = down;
        this.isMultiTouch = pointerCount > 1;
        if (down) {
            this.wasDown = true;
        }
        if (pointerCount > 0) {
            this.lastNumPointers = pointerCount;
        }
        this.distY = 0.0f;
        this.distX = 0.0f;
        this.isRotating = false;
        this.isPinching = false;
        this.isDragging = false;
    }

    public float[] getX() {
        return this.x;
    }

    public int[] getPointerIndices() {
        return MultiTouchController.pointerIndices;
    }

    public float[] getY() {
        return this.y;
    }
}
