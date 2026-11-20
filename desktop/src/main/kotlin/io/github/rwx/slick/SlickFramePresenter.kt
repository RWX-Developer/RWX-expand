package io.github.rwx.slick

import io.github.rwx.app.GameFramePresenter

class SlickFramePresenter(
    private val presentSnapshot: () -> Unit = {},
    private val refreshPresentation: () -> Unit = {},
) : GameFramePresenter {
    override fun present() {
        presentSnapshot()
    }

    override fun requestPresentationRefresh() {
        refreshPresentation()
    }
}
