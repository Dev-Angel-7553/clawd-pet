package com.clawd.pet

import android.annotation.SuppressLint
import android.content.Context
import android.view.MotionEvent
import android.view.View

@SuppressLint("ViewConstructor")
class TouchView(
    context: Context,
    private val engine: PetEngine,
    private val onGesture: () -> Unit,
) : View(context) {

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        val handled = engine.onTouch(event)
        onGesture()
        return handled
    }
}
