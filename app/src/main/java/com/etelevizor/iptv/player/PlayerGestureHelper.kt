package com.etelevizor.iptv.player

import android.content.Context
import android.view.GestureDetector
import android.view.MotionEvent
import kotlin.math.abs

interface PlayerGestureCallbacks {
    fun onSingleTap()
    fun onDoubleTapLeft()
    fun onDoubleTapCenter()
    fun onDoubleTapRight()
    fun onBrightnessSlide(deltaPercent: Float)
    fun onVolumeSlide(deltaPercent: Float)
    fun onSeekSlide(deltaMs: Long)
    fun onGestureEnd()
}

class PlayerGestureHelper(
    context: Context,
    private val callbacks: PlayerGestureCallbacks
) {
    private enum class DragMode { NONE, BRIGHTNESS, VOLUME, SEEK }

    private var dragMode = DragMode.NONE
    private var viewWidth = 1f
    private var viewHeight = 1f
    private var lastX = 0f
    private var lastY = 0f

    private val detector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
            callbacks.onSingleTap()
            return true
        }

        override fun onDoubleTap(e: MotionEvent): Boolean {
            val ratio = e.x / viewWidth.coerceAtLeast(1f)
            when {
                ratio < 0.33f -> callbacks.onDoubleTapLeft()
                ratio > 0.67f -> callbacks.onDoubleTapRight()
                else -> callbacks.onDoubleTapCenter()
            }
            return true
        }

        override fun onDown(e: MotionEvent): Boolean = true
    })

    fun onTouchEvent(event: MotionEvent, width: Int, height: Int, gesturesEnabled: Boolean): Boolean {
        viewWidth = width.toFloat().coerceAtLeast(1f)
        viewHeight = height.toFloat().coerceAtLeast(1f)

        if (!gesturesEnabled) {
            return detector.onTouchEvent(event)
        }

        val handled = detector.onTouchEvent(event)

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                dragMode = DragMode.NONE
                lastX = event.x
                lastY = event.y
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = event.x - lastX
                val dy = event.y - lastY
                if (dragMode == DragMode.NONE) {
                    val threshold = 24f
                    if (abs(dx) > threshold && abs(dx) > abs(dy) * 1.4f) {
                        dragMode = DragMode.SEEK
                    } else if (abs(dy) > threshold && abs(dy) > abs(dx) * 1.4f) {
                        dragMode = if (event.x < viewWidth * 0.5f) DragMode.BRIGHTNESS else DragMode.VOLUME
                    }
                }
                when (dragMode) {
                    DragMode.BRIGHTNESS -> {
                        val delta = (-dy / (viewHeight * 0.65f))
                        callbacks.onBrightnessSlide(delta)
                        lastX = event.x; lastY = event.y
                    }
                    DragMode.VOLUME -> {
                        val delta = (-dy / (viewHeight * 0.65f))
                        callbacks.onVolumeSlide(delta)
                        lastX = event.x; lastY = event.y
                    }
                    DragMode.SEEK -> {
                        val deltaMs = ((dx / viewWidth) * 120_000f).toLong()
                        callbacks.onSeekSlide(deltaMs)
                        lastX = event.x; lastY = event.y
                    }
                    DragMode.NONE -> Unit
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (dragMode != DragMode.NONE) {
                    dragMode = DragMode.NONE
                    callbacks.onGestureEnd()
                }
            }
        }
        return handled || dragMode != DragMode.NONE
    }
}
