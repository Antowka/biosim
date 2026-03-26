package com.microbesim.engine

import com.microbesim.models.Cell
import com.microbesim.models.Nutrient
import com.microbesim.models.NutrientType
import com.microbesim.models.SimulationContext
import com.microbesim.core.SimulationConstants
import kotlin.math.sqrt

/**
 * Пространственный хеш для оптимизации поиска соседних объектов
 * Решает проблему производительности Cell Lab при большом количестве клеток
 */
class SpatialHash(
    private val cellSize: Float = 100f,
    private val worldWidth: Float = SimulationConstants.WORLD_WIDTH,
    private val worldHeight: Float = SimulationConstants.WORLD_HEIGHT
) {
    private val grid = mutableMapOf<Long, MutableSet<SpatialObject>>()
    
    data class SpatialObject(
        val id: Long,
        var x: Float,
        var y: Float,
        val obj: Any
    )
    
    /**
     * Получить координаты ячейки сетки
     */
    private fun getCellKey(x: Float, y: Float): Long {
        val cellX = (x / cellSize).toInt().coerceIn(0, (worldWidth / cellSize).toInt())
        val cellY = (y / cellSize).toInt().coerceIn(0, (worldHeight / cellSize).toInt())
        return ((cellY shl 16) or cellX).toLong()
    }
    
    /**
     * Добавить объект в хеш
     */
    fun addObject(id: Long, x: Float, y: Float, obj: Any) {
        val key = getCellKey(x, y)
        grid.getOrPut(key) { mutableSetOf() }.add(SpatialObject(id, x, y, obj))
    }
    
    /**
     * Обновить позицию объекта
     */
    fun updatePosition(id: Long, oldX: Float, oldY: Float, newX: Float, newY: Float, obj: Any) {
        val oldKey = getCellKey(oldX, oldY)
        val newKey = getCellKey(newX, newY)
        
        if (oldKey != newKey) {
            removeObject(id, oldX, oldY)
            addObject(id, newX, newY, obj)
        } else {
            // Обновляем позицию в существующей ячейке
            grid[oldKey]?.find { it.id == id }?.let {
                it.x = newX
                it.y = newY
            }
        }
    }
    
    /**
     * Удалить объект
     */
    fun removeObject(id: Long, x: Float, y: Float) {
        val key = getCellKey(x, y)
        grid[key]?.removeIf { it.id == id }
        if (grid[key].isNullOrEmpty()) {
            grid.remove(key)
        }
    }
    
    /**
     * Получить объекты в радиусе
     */
    fun getObjectsNearby(x: Float, y: Float, radius: Float): List<SpatialObject> {
        val result = mutableListOf<SpatialObject>()
        val cellsRadius = (radius / cellSize).toInt() + 1
        
        val centerCellX = (x / cellSize).toInt()
        val centerCellY = (y / cellSize).toInt()
        
        for (dy in -cellsRadius..cellsRadius) {
            for (dx in -cellsRadius..cellsRadius) {
                val cellX = centerCellX + dx
                val cellY = centerCellY + dy
                
                if (cellX in 0..(worldWidth / cellSize).toInt() && 
                    cellY in 0..(worldHeight / cellSize).toInt()) {
                    val key = ((cellY shl 16) or cellX).toLong()
                    grid[key]?.forEach { obj ->
                        val distSq = (obj.x - x) * (obj.x - x) + (obj.y - y) * (obj.y - y)
                        if (distSq <= radius * radius) {
                            result.add(obj)
                        }
                    }
                }
            }
        }
        
        return result
    }
    
    /**
     * Очистить хеш
     */
    fun clear() {
        grid.clear()
    }
}

/**
 * Основной движок симуляции
 * Оптимизирован для работы с большим количеством клеток
 */
class SimulationEngine : SimulationContext {
    private val cells = mutableMapOf<Long, Cell>()
    private val nutrients = mutableMapOf<Long, Nutrient>()
    private val cellSpatialHash = SpatialHash()
    private val nutrientSpatialHash = SpatialHash()
    
    override var simulationTime: Float = 0f
    override var temperature: Float = SimulationConstants.TEMPERATURE_DEFAULT
    override var ph: Float = SimulationConstants.PH_DEFAULT
    
    var nutrientSpawnRate: Float = 5f // питательных веществ в секунду
    var maxCells: Int = SimulationConstants.MAX_CELLS
    var maxNutrients: Int = 500
    
    private var lastNutrientSpawnTime: Float = 0f
    
    /**
     * Добавить клетку в симуляцию
     */
    fun addCell(cell: Cell) {
        if (cells.size >= maxCells) return
        
        cells[cell.id] = cell
        cellSpatialHash.addObject(cell.id, cell.positionX, cell.positionY, cell)
    }
    
    /**
     * Удалить клетку
     */
    fun removeCell(cellId: Long) {
        cells[cellId]?.let { cell ->
            cellSpatialHash.removeObject(cell.id, cell.positionX, cell.positionY)
            cells.remove(cellId)
        }
    }
    
    /**
     * Добавить питательное вещество
     */
    fun addNutrient(nutrient: Nutrient) {
        if (nutrients.size >= maxNutrients) return
        
        nutrients[nutrient.id] = nutrient
        nutrientSpatialHash.addObject(nutrient.id, nutrient.positionX, nutrient.positionY, nutrient)
    }
    
    /**
     * Спавн питательных веществ
     */
    private fun spawnNutrients(dt: Float) {
        lastNutrientSpawnTime += dt
        val spawnInterval = 1f / nutrientSpawnRate
        
        while (lastNutrientSpawnTime >= spawnInterval && nutrients.size < maxNutrients) {
            lastNutrientSpawnTime -= spawnInterval
            
            val nutrient = Nutrient(
                id = System.nanoTime(),
                positionX = Math.random().toFloat() * SimulationConstants.WORLD_WIDTH,
                positionY = Math.random().toFloat() * SimulationConstants.WORLD_HEIGHT,
                amount = 1f,
                type = if (Math.random() < 0.1f) NutrientType.RARE else NutrientType.BASIC
            )
            
            addNutrient(nutrient)
        }
    }
    
    /**
     * Обновление симуляции
     */
    fun update(deltaTime: Float) {
        simulationTime += deltaTime
        
        // Спавн питательных веществ
        spawnNutrients(deltaTime)
        
        // Обновление клеток
        val deadCells = mutableListOf<Long>()
        val newCells = mutableListOf<Cell>()
        
        cells.values.forEach { cell ->
            if (cell.isAlive) {
                // Сохраняем старую позицию для spatial hash
                val oldX = cell.positionX
                val oldY = cell.positionY
                
                cell.update(this, deltaTime)
                
                // Обновляем spatial hash
                cellSpatialHash.updatePosition(cell.id, oldX, oldY, cell.positionX, cell.positionY, cell)
                
                // Потребление питательных веществ
                consumeNutrients(cell)
                
                // Проверка на деление
                if (cell.divisionsCount > 0) {
                    // Деление уже обработано в cell.update(), получаем новую клетку
                    // Примечание: это упрощение, в реальности нужно доработать
                }
            } else {
                deadCells.add(cell.id)
            }
        }
        
        // Удаляем мертвые клетки
        deadCells.forEach { removeCell(it) }
        
        // Добавляем новые клетки от деления
        newCells.forEach { addCell(it) }
        
        // Декей питательных веществ
        decayNutrients(deltaTime)
    }
    
    /**
     * Потребление питательных веществ клеткой
     */
    private fun consumeNutrients(cell: Cell) {
        val nearby = nutrientSpatialHash.getObjectsNearby(
            cell.positionX, 
            cell.positionY, 
            cell.size
        )
        
        nearby.forEach { obj ->
            val nutrient = obj.obj as? Nutrient ?: return@forEach
            
            if (nutrient.amount > 0) {
                val consumptionRate = when (nutrient.type) {
                    NutrientType.BASIC -> 0.1f
                    NutrientType.RARE -> 0.3f
                    NutrientType.TOXIC -> -0.2f // Вредно
                    NutrientType.SPECIAL -> 0.5f
                }
                
                val amount = minOf(nutrient.amount, consumptionRate)
                cell.consumeNutrient(amount)
                nutrient.amount -= amount
                
                if (nutrient.amount <= 0) {
                    nutrients.remove(nutrient.id)
                    nutrientSpatialHash.removeObject(nutrient.id, nutrient.positionX, nutrient.positionY)
                }
            }
        }
    }
    
    /**
     * Распад питательных веществ со временем
     */
    private fun decayNutrients(dt: Float) {
        val decayRate = SimulationConstants.NUTRIENT_DECAY_RATE * dt
        
        nutrients.values.forEach { nutrient ->
            nutrient.amount *= (1f - decayRate)
            if (nutrient.amount < 0.01f) {
                nutrient.amount = 0f
            }
        }
        
        // Очищаем полностью израсходованные
        nutrients.entries.removeAll { it.value.amount <= 0 }
    }
    
    /**
     * Получить концентрацию питательных веществ в точке
     */
    override fun getNutrientConcentration(x: Float, y: Float): Float {
        val nearby = nutrientSpatialHash.getObjectsNearby(x, y, 50f)
        return nearby.sumOf { (it.obj as? Nutrient)?.amount ?: 0f }.toFloat() / nearby.size.coerceAtLeast(1)
    }
    
    /**
     * Найти ближайшее питательное вещество
     */
    override fun getNearestNutrient(x: Float, y: Float, radius: Float): Nutrient? {
        val nearby = nutrientSpatialHash.getObjectsNearby(x, y, radius)
            .mapNotNull { it.obj as? Nutrient }
            .filter { it.amount > 0 }
        
        if (nearby.isEmpty()) return null
        
        return nearby.minByOrNull { 
            val dx = it.positionX - x
            val dy = it.positionY - y
            dx * dx + dy * dy
        }
    }
    
    /**
     * Найти ближайшего хищника
     */
    override fun getNearestPredator(cell: Cell, maxDistance: Float): Cell? {
        val nearby = cellSpatialHash.getObjectsNearby(cell.positionX, cell.positionY, maxDistance)
            .mapNotNull { it.obj as? Cell }
            .filter { it.isAlive && it.id != cell.id && it.size > cell.size * 1.2f }
        
        if (nearby.isEmpty()) return null
        
        return nearby.minByOrNull {
            val dx = it.positionX - cell.positionX
            val dy = it.positionY - cell.positionY
            dx * dx + dy * dy
        }
    }
    
    /**
     * Дистанция до ближайшего хищника
     */
    override fun getNearestPredatorDistance(cell: Cell): Float {
        val predator = getNearestPredator(cell, Float.MAX_VALUE)
        return predator?.let {
            val dx = it.positionX - cell.positionX
            val dy = it.positionY - cell.positionY
            sqrt(dx * dx + dy * dy)
        } ?: Float.MAX_VALUE
    }
    
    /**
     * Локальная популяция
     */
    override fun getLocalPopulation(x: Float, y: Float, radius: Float): Int {
        return cellSpatialHash.getObjectsNearby(x, y, radius)
            .count { (it.obj as? Cell)?.isAlive == true }
    }
    
    /**
     * Получить соседние клетки
     */
    override fun getNearbyCells(x: Float, y: Float, radius: Float): List<Cell> {
        return cellSpatialHash.getObjectsNearby(x, y, radius)
            .mapNotNull { it.obj as? Cell }
    }
    
    /**
     * Получить все клетки
     */
    fun getAllCells(): List<Cell> = cells.values.toList()
    
    /**
     * Получить статистику симуляции
     */
    fun getStats(): SimulationStats {
        val aliveCells = cells.values.count { it.isAlive }
        val avgGeneration = cells.values.filter { it.isAlive }.averageOrNull { it.generation.toDouble() } ?: 0.0
        val totalDivisions = cells.values.sumOf { it.divisionsCount }
        
        return SimulationStats(
            totalCells = cells.size,
            aliveCells = aliveCells,
            totalNutrients = nutrients.size,
            averageGeneration = avgGeneration,
            totalDivisions = totalDivisions,
            simulationTime = simulationTime
        )
    }
    
    /**
     * Очистить симуляцию
     */
    fun reset() {
        cells.clear()
        nutrients.clear()
        cellSpatialHash.clear()
        nutrientSpatialHash.clear()
        simulationTime = 0f
    }
}

/**
 * Статистика симуляции
 */
data class SimulationStats(
    val totalCells: Int,
    val aliveCells: Int,
    val totalNutrients: Int,
    val averageGeneration: Double,
    val totalDivisions: Int,
    val simulationTime: Float
)
