package com.suming.speedspy

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View

class VerticalLineWithBallView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    // 小球半径
    private val ballRadius = 30f
    // 竖线长度
    private val lineLength = 900f
    // 小球圆心坐标
    private var ballY1 = 100f
    private var ballX1 = 100f
    private var ballY2 = 100f
    private var ballX2 = 200f

    private val ballPaintBlack = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        style = Paint.Style.FILL
    }
    private val ballPaintWhite = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.FILL
    }
    private val linePaintBlack = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        strokeWidth = 5f
    }
    private val linePaintWhite = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        strokeWidth = 5f
    }


    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val startX1 = ballX1
        val startY1 = ballY1
        val stopY1 = startY1 + lineLength
        canvas.drawLine(startX1, startY1, startX1, stopY1, linePaintBlack)
        canvas.drawCircle(ballX1, ballY1, ballRadius, ballPaintBlack)

        val startX2 = ballX2
        val startY2 = ballY2
        val stopY2 = startY2 + lineLength
        canvas.drawLine(startX2, startY2, startX2, stopY2, linePaintWhite)
        canvas.drawCircle(ballX2, ballY2, ballRadius, ballPaintWhite)
    }

    private var draggingBall: Int = 0
    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                val dx1 = event.x - ballX1
                val dy1 = event.y - ballY1
                val dx2 = event.x - ballX2
                val dy2 = event.y - ballY2
                val r2 = ballRadius * ballRadius

                draggingBall = when {
                    dx1 * dx1 + dy1 * dy1 <= r2 -> 1
                    dx2 * dx2 + dy2 * dy2 <= r2 -> 2
                    else -> 0
                }
                return draggingBall != 0
            }

            MotionEvent.ACTION_MOVE -> {
                if (draggingBall == 0) return false
                val y = event.y.coerceIn(0f, (height - lineLength * 0.1f))
                when (draggingBall) {
                    1 -> { ballX1 = event.x; ballY1 = y }
                    2 -> { ballX2 = event.x; ballY2 = y }
                }
                invalidate()
                return true
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                draggingBall = 0
                return true
            }
        }
        return super.onTouchEvent(event)
    }
}