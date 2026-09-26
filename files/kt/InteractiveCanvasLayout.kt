package com.bitoneko.kouecanvas

import android.content.Context
import android.graphics.Canvas
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.widget.FrameLayout

class InteractiveCanvasLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private var scaleFactor = 1.0f
    private var offsetX = 0.0f
    private var offsetY = 0.0f
    
    private var lastTouchX = 0.0f
    private var lastTouchY = 0.0f
    private var activePointerId = INVALID_POINTER_ID

    private val scaleDetector = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            scaleFactor *= detector.scaleFactor
            scaleFactor = Math.max(0.1f, Math.min(scaleFactor, 10.0f))
            invalidate()
            return true
        }
    })

    private val gestureDetector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onScroll(e1: MotionEvent?, e2: MotionEvent, distanceX: Float, distanceY: Float): Boolean {
            if (!scaleDetector.isInProgress) {
                offsetX -= distanceX
                offsetY -= distanceY
                invalidate()
            }
            return true
        }
    })

    companion object {
        private const val INVALID_POINTER_ID = -1
    }
    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        return ev.pointerCount > 1 || super.onInterceptTouchEvent(ev)
    }

    override fun onTouchEvent(ev: MotionEvent): Boolean {
        scaleDetector.onTouchEvent(ev)
        gestureDetector.onTouchEvent(ev)
        
        when (ev.action and MotionEvent.ACTION_MASK) {
            MotionEvent.ACTION_DOWN -> {
                lastTouchX = ev.x
                lastTouchY = ev.y
                activePointerId = ev.getPointerId(0)
            }
            MotionEvent.ACTION_MOVE -> {
                val pointerIndex = ev.findPointerIndex(activePointerId)
                if (pointerIndex != INVALID_POINTER_ID) {
                    val x = ev.getX(pointerIndex)
                    val y = ev.getY(pointerIndex)
                    if (!scaleDetector.isInProgress) {
                        val dx = x - lastTouchX
                        val dy = y - lastTouchY
                        offsetX += dx
                        offsetY += dy
                        invalidate()
                    }
                    lastTouchX = x
                    lastTouchY = y
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                activePointerId = INVALID_POINTER_ID
            }
            MotionEvent.ACTION_POINTER_UP -> {
                val pointerIndex = (ev.action and MotionEvent.ACTION_POINTER_INDEX_MASK) shr MotionEvent.ACTION_POINTER_INDEX_SHIFT
                val pointerId = ev.getPointerId(pointerIndex)
                if (pointerId == activePointerId) {
                    val newPointerIndex = if (pointerIndex == 0) 1 else 0
                    lastTouchX = ev.getX(newPointerIndex)
                    lastTouchY = ev.getY(newPointerIndex)
                    activePointerId = ev.getPointerId(newPointerIndex)
                }
            }
        }
        return true
    }

    override fun dispatchDraw(canvas: Canvas) {
        canvas.save()
        canvas.translate(offsetX, offsetY)
        canvas.scale(scaleFactor, scaleFactor)
        super.dispatchDraw(canvas)
        canvas.restore()
    }
}