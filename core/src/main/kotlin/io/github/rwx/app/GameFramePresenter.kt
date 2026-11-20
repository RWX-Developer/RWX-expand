package io.github.rwx.app

fun interface GameFramePresenter {
    fun present()

    /**
     * 请求平台把当前画面整体重新呈现一次。
     * 有的环境（Windows + 某些 GPU 驱动、以及窗口首次映射时）会丢掉重绘，
     * 表现为引擎状态已经切到主菜单、画面却停在加载页那一帧。
     * 默认空实现，平台各自决定怎么刷新，核心层不需要知道是哪个平台。
     */
    fun requestPresentationRefresh() {}
}
