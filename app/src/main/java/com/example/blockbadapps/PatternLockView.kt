package com.example.blockbadapps

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import kotlin.math.pow
import kotlin.math.sqrt

class PatternLockView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    // Configuración de la cuadrícula (3x3 por defecto)
    private var gridSize = 3
    private val dots = mutableListOf<Dot>()
    private val selectedDots = mutableListOf<Dot>()

    // Para dibujar la línea mientras se arrastra
    private var currentX = 0f
    private var currentY = 0f
    private var isDrawing = false

    // Colores y estilos
    private val dotPaint = Paint().apply {
        color = 0xFF2196F3.toInt()
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    private val dotOutlinePaint = Paint().apply {
        color = 0xFF1976D2.toInt()
        style = Paint.Style.STROKE
        strokeWidth = 8f
        isAntiAlias = true
    }

    private val selectedDotPaint = Paint().apply {
        color = 0xFF4CAF50.toInt()
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    private val linePaint = Paint().apply {
        color = 0xFF4CAF50.toInt()
        style = Paint.Style.STROKE
        strokeWidth = 12f
        strokeCap = Paint.Cap.ROUND
        isAntiAlias = true
    }

    // Callback para cuando se completa el patrón
    var onPatternListener: ((String) -> Unit)? = null

    data class Dot(
        val id: Int,
        val x: Float,
        val y: Float,
        var isSelected: Boolean = false
    )

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        setupDots()
    }

    private fun setupDots() {
        dots.clear()
        val size = minOf(width, height)
        val spacing = size / (gridSize + 1f)
        val startX = (width - (spacing * (gridSize - 1))) / 2f
        val startY = (height - (spacing * (gridSize - 1))) / 2f

        var id = 0
        for (row in 0 until gridSize) {
            for (col in 0 until gridSize) {
                dots.add(
                    Dot(
                        id = id++,
                        x = startX + col * spacing,
                        y = startY + row * spacing
                    )
                )
            }
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // Dibujar todos los puntos
        for (dot in dots) {
            val paint = if (dot.isSelected) selectedDotPaint else dotPaint
            canvas.drawCircle(dot.x, dot.y, 30f, paint)
            canvas.drawCircle(dot.x, dot.y, 30f, dotOutlinePaint)
        }

        // Dibujar líneas entre puntos seleccionados
        if (selectedDots.size > 1) {
            val path = Path()
            path.moveTo(selectedDots[0].x, selectedDots[0].y)
            for (i in 1 until selectedDots.size) {
                path.lineTo(selectedDots[i].x, selectedDots[i].y)
            }
            canvas.drawPath(path, linePaint)
        }

        // Dibujar línea al dedo actual
        if (isDrawing && selectedDots.isNotEmpty()) {
            val lastDot = selectedDots.last()
            canvas.drawLine(lastDot.x, lastDot.y, currentX, currentY, linePaint)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        currentX = event.x
        currentY = event.y

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                isDrawing = true
                checkDotSelection(currentX, currentY)
            }
            MotionEvent.ACTION_MOVE -> {
                checkDotSelection(currentX, currentY)
                invalidate()
            }
            MotionEvent.ACTION_UP -> {
                isDrawing = false
                if (selectedDots.size >= 4) {
                    val pattern = selectedDots.joinToString("") { it.id.toString() }
                    onPatternListener?.invoke(pattern)
                }
                resetPattern()
            }
        }
        return true
    }

    private fun checkDotSelection(x: Float, y: Float) {
        for (dot in dots) {
            if (dot.isSelected) continue

            val distance = sqrt((dot.x - x).pow(2) + (dot.y - y).pow(2))
            if (distance < 60f) {
                dot.isSelected = true
                selectedDots.add(dot)
                invalidate()
                break
            }
        }
    }

    fun resetPattern() {
        selectedDots.clear()
        dots.forEach { it.isSelected = false }
        invalidate()
    }

    fun showError() {
        // Cambiar color a rojo temporalmente
        linePaint.color = 0xFFE53935.toInt()
        selectedDotPaint.color = 0xFFE53935.toInt()
        invalidate()

        postDelayed({
            linePaint.color = 0xFF4CAF50.toInt()
            selectedDotPaint.color = 0xFF4CAF50.toInt()
            resetPattern()
        }, 500)
    }

    fun showSuccess() {
        postDelayed({ resetPattern() }, 300)
    }
}