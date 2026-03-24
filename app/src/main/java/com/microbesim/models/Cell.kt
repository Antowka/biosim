package com.microbesim.models

import android.graphics.Color
import com.microbesim.genome.Genome
import com.microbesim.genome.GeneContext
import com.microbesim.core.SimulationConstants

/**
 * Клетка - основной объект симуляции
 * Использует ECS-подобный подход для гибкости
 */
data class Cell(
    val id: Long,
    var positionX: Float,
    var positionY: Float,
    var velocityX: Float = 0f,
    var velocityY: Float = 0f,
    var size: Float = 10f,
    var energy: Float = 50f,
    var age: Int = 0,
    val genome: Genome,
    var generation: Int = 1,
    val parentId: Long? = null,
    var color: Int = Color.rgb(100, 200, 100),
    var isAlive: Boolean = true,
    
    // Статистика
    var nutrientsConsumed: Float = 0f,
    var divisionsCount: Int = 0,
    var killsCount: Int = 0
) {
    /**
     * Обновление состояния клетки на основе генома
     */
    fun update(context: SimulationContext, dt: Float) {
        if (!isAlive) return
        
        age++
        
        // Получаем активные гены
        val geneContext = GeneContext(
            energy = energy,
            size = size,
            nutrientConcentration = context.getNutrientConcentration(positionX, positionY),
            predatorDistance = context.getNearestPredatorDistance(this),
            localPopulation = context.getLocalPopulation(positionX, positionY, 100f),
            simulationHour = ((context.simulationTime / 3600) % 24).toInt(),
            temperature = context.temperature,
            ph = context.ph
        )
        
        val activeGenes = genome.getActiveGenes(geneContext)
        
        // Применяем поведение генов
        applyBehaviors(activeGenes, context, dt)
        
        // Метаболизм
        applyMetabolism(activeGenes, dt)
        
        // Физика
        applyPhysics(dt)
        
        // Проверка смерти
        checkDeath()
        
        // Проверка деления
        if (shouldDivide(activeGenes)) {
            divide(context)
        }
    }
    
    /**
     * Применение поведенческих генов
     */
    private fun applyBehaviors(genes: List<com.microbesim.genome.Gene>, context: SimulationContext, dt: Float) {
        var accelerationX = 0f
        var accelerationY = 0f
        
        for (gene in genes) {
            when (gene.type) {
                com.microbesim.genome.GeneType.MOVE_RANDOM -> {
                    val changeChance = gene.parameters["changeDirectionChance"] ?: 0.1f
                    if (Math.random() < changeChance * dt) {
                        val angle = Math.random().toFloat() * Math.PI * 2
                        velocityX += Math.cos(angle).toFloat() * 0.5f
                        velocityY += Math.sin(angle).toFloat() * 0.5f
                    }
                }
                
                com.microbesim.genome.GeneType.MOVE_TOWARD_NUTRIENT -> {
                    val sensingRadius = gene.parameters["sensingRadius"] ?: 50f
                    val speed = gene.parameters["speed"] ?: 1f
                    val nearest = context.getNearestNutrient(positionX, positionY, sensingRadius)
                    if (nearest != null) {
                        val dx = nearest.positionX - positionX
                        val dy = nearest.positionY - positionY
                        val dist = kotlin.math.sqrt(dx * dx + dy * dy)
                        if (dist > 0) {
                            velocityX += (dx / dist) * speed * 0.3f * dt
                            velocityY += (dy / dist) * speed * 0.3f * dt
                        }
                    }
                }
                
                com.microbesim.genome.GeneType.MOVE_AWAY_PREDATOR -> {
                    val maxDistance = gene.parameters["maxDistance"] ?: 100f
                    val speed = gene.parameters["speed"] ?: 1.5f
                    val predator = context.getNearestPredator(this, maxDistance)
                    if (predator != null && predator.id != id) {
                        val dx = positionX - predator.positionX
                        val dy = positionY - predator.positionY
                        val dist = kotlin.math.sqrt(dx * dx + dy * dy)
                        if (dist > 0) {
                            velocityX += (dx / dist) * speed * 0.5f * dt
                            velocityY += (dy / dist) * speed * 0.5f * dt
                        }
                    }
                }
                
                com.microbesim.genome.GeneType.FORM_COLONY -> {
                    val radius = gene.parameters["radius"] ?: 150f
                    val strength = gene.parameters["strength"] ?: 0.2f
                    val neighbors = context.getNearbyCells(positionX, positionY, radius)
                        .filter { it.id != id && it.isAlive }
                    
                    if (neighbors.isNotEmpty()) {
                        val centerX = neighbors.average { it.positionX }.toFloat()
                        val centerY = neighbors.average { it.positionY }.toFloat()
                        val dx = centerX - positionX
                        val dy = centerY - positionY
                        val dist = kotlin.math.sqrt(dx * dx + dy * dy)
                        if (dist > 0 && dist < radius) {
                            velocityX += (dx / dist) * strength * dt
                            velocityY += (dy / dist) * strength * dt
                        }
                    }
                }
                
                else -> {}
            }
        }
    }
    
    /**
     * Применение метаболических процессов
     */
    private fun applyMetabolism(genes: List<com.microbesim.genome.Gene>, dt: Float) {
        var metabolicRate = SimulationConstants.METABOLIC_BASE_RATE
        var efficiency = 0.8f
        
        // Находим активные метаболические гены
        val metabolismGene = genes.firstOrNull { 
            it.type == com.microbesim.genome.GeneType.METABOLISM_FAST || 
            it.type == com.microbesim.genome.GeneType.METABOLISM_EFFICIENT 
        }
        
        when (metabolismGene?.type) {
            com.microbesim.genome.GeneType.METABOLISM_FAST -> {
                metabolicRate *= metabolismGene.parameters["rate"] ?: 1.5f
                efficiency = metabolismGene.parameters["efficiency"] ?: 0.6f
            }
            com.microbesim.genome.GeneType.METABOLISM_EFFICIENT -> {
                metabolicRate *= metabolismGene.parameters["rate"] ?: 0.7f
                efficiency = metabolismGene.parameters["efficiency"] ?: 0.9f
            }
            else -> {}
        }
        
        // Базовый расход энергии
        val baseCost = metabolicRate * size * dt
        energy -= baseCost
        
        // Фотосинтез (если есть)
        val photosynthesisGene = genes.firstOrNull { it.type == com.microbesim.genome.GeneType.PHOTOSYNTHESIS }
        if (photosynthesisGene != null) {
            val lightLevel = 1.0f // Можно добавить освещение в симуляцию
            val photoEfficiency = photosynthesisGene.parameters["efficiency"] ?: 0.5f
            energy += lightLevel * photoEfficiency * dt * 10f
        }
        
        // Ограничиваем энергию
        energy = energy.coerceIn(0f, 100f * size / 10f)
    }
    
    /**
     * Применение физики движения
     */
    private fun applyPhysics(dt: Float) {
        // Трение
        val friction = 0.95f
        velocityX *= friction
        velocityY *= friction
        
        // Ограничение скорости
        val maxSpeed = 5f
        val speed = kotlin.math.sqrt(velocityX * velocityX + velocityY * velocityY)
        if (speed > maxSpeed) {
            velocityX = (velocityX / speed) * maxSpeed
            velocityY = (velocityY / speed) * maxSpeed
        }
        
        // Обновление позиции
        positionX += velocityX * dt
        positionY += velocityY * dt
        
        // Границы мира
        positionX = positionX.coerceIn(0f, SimulationConstants.WORLD_WIDTH)
        positionY = positionY.coerceIn(0f, SimulationConstants.WORLD_HEIGHT)
        
        // Отскок от стен
        if (positionX <= 0 || positionX >= SimulationConstants.WORLD_WIDTH) velocityX *= -0.8f
        if (positionY <= 0 || positionY >= SimulationConstants.WORLD_HEIGHT) velocityY *= -0.8f
    }
    
    /**
     * Проверка условий смерти
     */
    private fun checkDeath() {
        if (energy <= 0) {
            isAlive = false
        }
        
        // Смерть от старости (опционально)
        val maxAge = 10000 // тиков
        if (age > maxAge) {
            isAlive = false
        }
    }
    
    /**
     * Проверка возможности деления
     */
    private fun shouldDivide(genes: List<com.microbesim.genome.Gene>): Boolean {
        if (!isAlive || energy < 20f) return false
        
        val divideGene = genes.firstOrNull { it.type == com.microbesim.genome.GeneType.DIVIDE_ASEXUAL }
        if (divideGene == null) return false
        
        val minEnergy = divideGene.parameters["minEnergy"] ?: 0.75f
        val sizeRatio = divideGene.parameters["sizeRatio"] ?: 0.5f
        
        // Проверяем размер и энергию
        val maxSize = SimulationConstants.CELL_MAX_SIZE * sizeRatio
        return size >= maxSize && energy >= minEnergy * 100f
    }
    
    /**
     * Деление клетки
     */
    fun divide(context: SimulationContext): Cell? {
        if (!shouldDivide(genome.getActiveGenes(GeneContext(
            energy = energy,
            size = size,
            nutrientConcentration = 0f,
            predatorDistance = Float.MAX_VALUE,
            localPopulation = 0,
            simulationHour = 0,
            temperature = 25f,
            ph = 7f
        )))) return null
        
        val divideGene = genome.getActiveGenes(GeneContext(
            energy = energy,
            size = size,
            nutrientConcentration = 0f,
            predatorDistance = Float.MAX_VALUE,
            localPopulation = 0,
            simulationHour = 0,
            temperature = 25f,
            ph = 7f
        )).firstOrNull { it.type == com.microbesim.genome.GeneType.DIVIDE_ASEXUAL }
        
        val mutationRate = divideGene?.parameters?.get("mutationRate") ?: SimulationConstants.MUTATION_RATE_DEFAULT
        val mutatedGenome = genome.mutate(mutationRate)
        
        // Создаем дочернюю клетку
        val daughter = Cell(
            id = System.nanoTime(),
            positionX = positionX + (Math.random().toFloat() - 0.5f) * size,
            positionY = positionY + (Math.random().toFloat() - 0.5f) * size,
            size = size * 0.6f,
            energy = energy * 0.4f,
            genome = mutatedGenome,
            generation = generation + 1,
            parentId = id,
            color = mutateColor(color)
        )
        
        // Уменьшаем родительскую клетку
        size *= 0.7f
        energy *= 0.5f
        divisionsCount++
        
        return daughter
    }
    
    /**
     * Мутация цвета для визуального различия видов
     */
    private fun mutateColor(baseColor: Int): Int {
        val r = Color.red(baseColor)
        val g = Color.green(baseColor)
        val b = Color.blue(baseColor)
        
        val mutation = (-20..20).random()
        return Color.rgb(
            (r + mutation).coerceIn(0, 255),
            (g + mutation).coerceIn(0, 255),
            (b + mutation).coerceIn(0, 255)
        )
    }
    
    /**
     * Потребление питательных веществ
     */
    fun consumeNutrient(amount: Float) {
        energy = (energy + amount * 10f).coerceIn(0f, 100f * size / 10f)
        nutrientsConsumed += amount
    }
}

/**
 * Контекст симуляции для доступа к миру
 */
interface SimulationContext {
    val simulationTime: Float
    val temperature: Float
    val ph: Float
    
    fun getNutrientConcentration(x: Float, y: Float): Float
    fun getNearestNutrient(x: Float, y: Float, radius: Float): Nutrient?
    fun getNearestPredator(cell: Cell, maxDistance: Float): Cell?
    fun getNearestPredatorDistance(cell: Cell): Float
    fun getLocalPopulation(x: Float, y: Float, radius: Float): Int
    fun getNearbyCells(x: Float, y: Float, radius: Float): List<Cell>
}

/**
 * Питательное вещество
 */
data class Nutrient(
    val id: Long,
    var positionX: Float,
    var positionY: Float,
    var amount: Float = 1f,
    var type: NutrientType = NutrientType.BASIC
)

enum class NutrientType {
    BASIC,      // Обычное питание
    RARE,       // Редкое, ценное
    TOXIC,      // Токсичное
    SPECIAL     // Специальное для эволюции
}
