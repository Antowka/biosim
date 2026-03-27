package com.microbesim.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import androidx.compose.remote.creation.step
import com.microbesim.engine.SimulationEngine
import com.microbesim.models.Cell
import com.microbesim.models.Nutrient
import kotlin.math.max
import kotlin.math.min

/**
 * Custom View для отрисовки симуляции
 * Оптимизирован для высокой производительности
 */
class SimulationView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {
    
    private var engine: SimulationEngine? = null
    
    // Камера
    private var cameraX: Float = 0f
    private var cameraY: Float = 0f
    private var cameraZoom: Float = 1f
    private val minZoom = 0.1f
    private val maxZoom = 5f
    
    // Отрисовка
    private val cellPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val nutrientPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#20FFFFFF")
        strokeWidth = 1f
        style = Paint.Style.STROKE
    }
    private val infoPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 40f
    }
    private val energyBarBgPaint = Paint().apply {
        color = Color.parseColor("#40000000")
    }
    private val energyBarPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#4CAF50")
    }
    
    // Жесты
    private var lastTouchX: Float = 0f
    private var lastTouchY: Float = 0f
    private val scaleDetector = ScaleGestureDetector(context, ScaleListener())
    private val gestureDetector = GestureDetector(context, GestureListener())
    private var isTrackingCell: Long? = null
    
    // Настройки отрисовки
    var showGrid: Boolean = true
    var showEnergyBars: Boolean = true
    var showInfo: Boolean = true
    var cellRenderQuality: RenderQuality = RenderQuality.BALANCED
    
    enum class RenderQuality {
        LOW,      // Простые круги
        BALANCED, // Градиенты
        HIGH      // Детализированные с эффектами
    }
    
    /**
     * Установить движок симуляции
     */
    fun setEngine(engine: SimulationEngine) {
        this.engine = engine
        invalidate()
    }
    
    /**
     * Преобразование координат мира в экранные
     */
    private fun worldToScreen(worldX: Float, worldY: Float): Pair<Float, Float> {
        val screenX = (worldX - cameraX) * cameraZoom + width / 2f
        val screenY = (worldY - cameraY) * cameraZoom + height / 2f
        return Pair(screenX, screenY)
    }
    
    /**
     * Преобразование экранных координат в мировые
     */
    private fun screenToWorld(screenX: Float, screenY: Float): Pair<Float, Float> {
        val worldX = (screenX - width / 2f) / cameraZoom + cameraX
        val worldY = (screenY - height / 2f) / cameraZoom + cameraY
        return Pair(worldX, worldY)
    }
    
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        
        // Очистка фона
        canvas.drawColor(Color.parseColor("#0D1117"))
        
        val engine = engine ?: return
        
        // Сетка
        if (showGrid) {
            drawGrid(canvas)
        }
        
        // Питательные вещества
        drawNutrients(canvas, engine)
        
        // Клетки
        drawCells(canvas, engine)
        
        // Информация
        if (showInfo) {
            drawInfo(canvas, engine)
        }
        
        // Индикатор отслеживаемой клетки
        isTrackingCell?.let { cellId ->
            drawTrackingIndicator(canvas, engine, cellId)
        }
    }
    
    /**
     * Отрисовка сетки
     */
    private fun drawGrid(canvas: Canvas) {
        val gridSize = 100f * cameraZoom
        
        val startWorld = screenToWorld(0f, 0f)
        val endWorld = screenToWorld(width.toFloat(), height.toFloat())
        
        val startX = ((startWorld.first / gridSize).toInt() * gridSize)
        val startY = ((startWorld.second / gridSize).toInt() * gridSize)

        var x = startX
        while (x <= endWorld.first) {
            val screenPos = worldToScreen(x, 0f)
            canvas.drawLine(screenPos.first, 0f, screenPos.first, height.toFloat(), gridPaint)
            x += gridSize
        }

        // Аналогично исправьте цикл для оси Y, если он есть:
        var y = startY // Предполагается, что startY определен ранее
        while (y <= endWorld.second) {
            val screenPos = worldToScreen(0f, y)
            canvas.drawLine(0f, screenPos.second, width.toFloat(), screenPos.second, gridPaint)
            y += gridSize
        }
    }
    
    /**
     * Отрисовка питательных веществ
     */
    private fun drawNutrients(canvas: Canvas, engine: SimulationEngine) {
        engine.getAllCells() // Просто чтобы убедиться что engine используется
        
        // Получаем видимую область
        val startWorld = screenToWorld(0f, 0f)
        val endWorld = screenToWorld(width.toFloat(), height.toFloat())
        
        // Отрисовка только видимых питательных веществ
        // В реальной реализации нужно использовать spatial hash engine
    }
    
    /**
     * Отрисовка клеток
     */
    private fun drawCells(canvas: Canvas, engine: SimulationEngine) {
        val cells = engine.getAllCells()
        
        cells.forEach { cell ->
            if (!cell.isAlive) return@forEach
            
            val screenPos = worldToScreen(cell.positionX, cell.positionY)
            val radius = cell.size * cameraZoom / 2f
            
            // Проверка видимости
            if (screenPos.first + radius < 0 || screenPos.first - radius > width ||
                screenPos.second + radius < 0 || screenPos.second - radius > height) {
                return@forEach
            }
            
            when (cellRenderQuality) {
                RenderQuality.LOW -> drawCellSimple(canvas, screenPos.first, screenPos.second, radius, cell)
                RenderQuality.BALANCED -> drawCellBalanced(canvas, screenPos.first, screenPos.second, radius, cell)
                RenderQuality.HIGH -> drawCellHighQuality(canvas, screenPos.first, screenPos.second, radius, cell)
            }
            
            // Полоска энергии
            if (showEnergyBars && radius > 5f) {
                drawEnergyBar(canvas, screenPos.first, screenPos.second, radius, cell)
            }
        }
    }
    
    /**
     * Простая отрисовка (для низкой производительности)
     */
    private fun drawCellSimple(canvas: Canvas, x: Float, y: Float, radius: Float, cell: Cell) {
        cellPaint.color = cell.color
        cellPaint.style = Paint.Style.FILL
        canvas.drawCircle(x, y, radius, cellPaint)
    }
    
    /**
     * Сбалансированная отрисовка
     */
    private fun drawCellBalanced(canvas: Canvas, x: Float, y: Float, radius: Float, cell: Cell) {
        // Градиент для объема
        val gradientRadius = radius * 0.7f
        val gradientColors = intArrayOf(
            lightenColor(cell.color, 0.3f),
            cell.color,
            darkenColor(cell.color, 0.3f)
        )
        
        cellPaint.shader = android.graphics.RadialGradient(
            x - radius * 0.3f,
            y - radius * 0.3f,
            gradientRadius,
            gradientColors,
            null,
            android.graphics.Shader.TileMode.CLAMP
        )
        cellPaint.style = Paint.Style.FILL
        canvas.drawCircle(x, y, radius, cellPaint)
        cellPaint.shader = null
        
        // Мембрана
        cellPaint.color = lightenColor(cell.color, 0.1f)
        cellPaint.style = Paint.Style.STROKE
        cellPaint.strokeWidth = max(1f, radius * 0.1f)
        canvas.drawCircle(x, y, radius, cellPaint)
    }
    
    /**
     * Высококачественная отрисовка
     */
    private fun drawCellHighQuality(canvas: Canvas, x: Float, y: Float, radius: Float, cell: Cell) {
        // Тень
        cellPaint.color = Color.parseColor("#20000000")
        cellPaint.style = Paint.Style.FILL
        canvas.drawCircle(x + radius * 0.1f, y + radius * 0.1f, radius, cellPaint)
        
        // Основной градиент с ядром
        val gradientColors = intArrayOf(
            lightenColor(cell.color, 0.4f),
            cell.color,
            darkenColor(cell.color, 0.2f)
        )
        
        cellPaint.shader = android.graphics.RadialGradient(
            x - radius * 0.3f,
            y - radius * 0.3f,
            radius * 0.8f,
            gradientColors,
            null,
            android.graphics.Shader.TileMode.CLAMP
        )
        cellPaint.style = Paint.Style.FILL
        canvas.drawCircle(x, y, radius, cellPaint)
        cellPaint.shader = null
        
        // Ядро (если размер достаточный)
        if (radius > 10f) {
            cellPaint.color = darkenColor(cell.color, 0.4f)
            canvas.drawCircle(x, y, radius * 0.3f, cellPaint)
        }
        
        // Мембрана с свечением
        cellPaint.color = lightenColor(cell.color, 0.2f)
        cellPaint.style = Paint.Style.STROKE
        cellPaint.strokeWidth = max(2f, radius * 0.08f)
        canvas.drawCircle(x, y, radius, cellPaint)
    }
    
    /**
     * Отрисовка полоски энергии
     */
    private fun drawEnergyBar(canvas: Canvas, x: Float, y: Float, radius: Float, cell: Cell) {
        val barWidth = radius * 2
        val barHeight = max(3f, radius * 0.15f)
        val barY = y - radius - barHeight - 5f
        
        // Фон
        val bgRect = RectF(x - barWidth / 2, barY, x + barWidth / 2, barY + barHeight)
        canvas.drawRoundRect(bgRect, barHeight / 2, barHeight / 2, energyBarBgPaint)
        
        // Заполнение
        val maxEnergy = 100f * cell.size / 10f
        val energyRatio = cell.energy / maxEnergy
        val fillWidth = (barWidth - 4f) * energyRatio
        
        if (fillWidth > 0) {
            val fillRect = RectF(x - barWidth / 2 + 2, barY + 2, x - barWidth / 2 + 2 + fillWidth, barY + barHeight - 2)
            
            // Цвет зависит от уровня энергии
            energyBarPaint.color = when {
                energyRatio > 0.6f -> Color.parseColor("#4CAF50")
                energyRatio > 0.3f -> Color.parseColor("#FFC107")
                else -> Color.parseColor("#F44336")
            }
            
            canvas.drawRoundRect(fillRect, barHeight / 2, barHeight / 2, energyBarPaint)
        }
    }
    
    /**
     * Отрисовка информации
     */
    private fun drawInfo(canvas: Canvas, engine: SimulationEngine) {
        val stats = engine.getStats()
        
        val info = buildString {
            appendLine("Клеток: ${stats.aliveCells}/${stats.totalCells}")
            appendLine("Питания: ${stats.totalNutrients}")
            appendLine("Поколение: ${String.format("%.1f", stats.averageGeneration)}")
            appendLine("Делений: ${stats.totalDivisions}")
            appendLine("Время: ${String.format("%.0f", stats.simulationTime)}с")
            appendLine("FPS: ${getFPS()}")
            appendLine("Зум: ${String.format("%.1fx", cameraZoom)}")
        }
        
        canvas.drawText(info, 20f, 60f, infoPaint)
    }
    
    /**
     * Индикатор отслеживания клетки
     */
    private fun drawTrackingIndicator(canvas: Canvas, engine: SimulationEngine, cellId: Long) {
        val cell = engine.getAllCells().find { it.id == cellId } ?: return
        
        val screenPos = worldToScreen(cell.positionX, cell.positionY)
        val radius = cell.size * cameraZoom / 2f
        
        // Пульсирующий круг
        val pulseRadius = radius + 10f + Math.sin(System.currentTimeMillis() / 200.0) * 5f
        cellPaint.color = Color.parseColor("#80FFFFFF")
        cellPaint.style = Paint.Style.STROKE
        cellPaint.strokeWidth = 3f
        canvas.drawCircle(screenPos.first, screenPos.second, pulseRadius.toFloat(), cellPaint)
    }
    
    /**
     * Обработка касаний
     */
    override fun onTouchEvent(event: MotionEvent): Boolean {
        scaleDetector.onTouchEvent(event)
        gestureDetector.onTouchEvent(event)
        
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                lastTouchX = event.x
                lastTouchY = event.y
                
                // Проверка тапа по клетке
                val worldPos = screenToWorld(event.x, event.y)
                engine?.getAllCells()?.forEach { cell ->
                    val dx = cell.positionX - worldPos.first
                    val dy = cell.positionY - worldPos.second
                    val dist = kotlin.math.sqrt(dx * dx + dy * dy)
                    if (dist < cell.size / 2f) {
                        isTrackingCell = cell.id
                        invalidate()
                        return true
                    }
                }
            }
            
            MotionEvent.ACTION_MOVE -> {
                if (!scaleDetector.isInProgress) {
                    val dx = event.x - lastTouchX
                    val dy = event.y - lastTouchY
                    
                    cameraX -= dx / cameraZoom
                    cameraY -= dy / cameraZoom
                    
                    lastTouchX = event.x
                    lastTouchY = event.y
                    
                    invalidate()
                }
            }
            
            MotionEvent.ACTION_UP -> {
                // Если не перетаскивали, возможно это клик для выбора
            }
        }
        
        return true
    }
    
    /**
     * Слушатель масштабирования
     */
    private inner class ScaleListener : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            val newZoom = cameraZoom * detector.scaleFactor
            cameraZoom = newZoom.coerceIn(minZoom, maxZoom)
            invalidate()
            return true
        }
    }
    
    /**
     * Слушатель жестов
     */
    private inner class GestureListener : GestureDetector.SimpleOnGestureListener() {
        override fun onDoubleTap(e: MotionEvent): Boolean {
            // Сброс зума
            cameraZoom = 1f
            invalidate()
            return true
        }
        
        override fun onSingleTapUp(e: MotionEvent): Boolean {
            // Снять выделение
            isTrackingCell = null
            invalidate()
            return true
        }
    }
    
    /**
     * Осветление цвета
     */
    private fun lightenColor(color: Int, factor: Float): Int {
        val a = Color.alpha(color)
        val r = (Color.red(color) + (255 - Color.red(color)) * factor).toInt()
        val g = (Color.green(color) + (255 - Color.green(color)) * factor).toInt()
        val b = (Color.blue(color) + (255 - Color.blue(color)) * factor).toInt()
        return Color.argb(a, r.coerceIn(0, 255), g.coerceIn(0, 255), b.coerceIn(0, 255))
    }
    
    /**
     * Затемнение цвета
     */
    private fun darkenColor(color: Int, factor: Float): Int {
        val a = Color.alpha(color)
        val r = (Color.red(color) * (1 - factor)).toInt()
        val g = (Color.green(color) * (1 - factor)).toInt()
        val b = (Color.blue(color) * (1 - factor)).toInt()
        return Color.argb(a, r.coerceIn(0, 255), g.coerceIn(0, 255), b.coerceIn(0, 255))
    }
    
    /**
     * Получить FPS (заглушка)
     */
    private fun getFPS(): Int = 60
    
    /**
     * Центрировать камеру на точке
     */
    fun centerOn(x: Float, y: Float) {
        cameraX = x
        cameraY = y
        invalidate()
    }
    
    /**
     * Сброс камеры
     */
    fun resetCamera() {
        cameraX = 500f
        cameraY = 500f
        cameraZoom = 1f
        invalidate()
    }
}
