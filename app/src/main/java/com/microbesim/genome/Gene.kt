package com.microbesim.genome

import kotlinx.serialization.Serializable

/**
 * Представляет ген - базовую единицу поведения клетки
 * Улучшенная версия по сравнению с Cell Lab с более гибкой системой
 */
@Serializable
data class Gene(
    val id: String,
    val type: GeneType,
    val parameters: Map<String, Float> = emptyMap(),
    val conditions: List<Condition> = emptyList(),
    val priority: Int = 0
) {
    /**
     * Проверка условий для активации гена
     */
    fun checkConditions(context: GeneContext): Boolean {
        return conditions.all { it.evaluate(context) }
    }
}

/**
 * Типы генов - расширенные по сравнению с Cell Lab
 */
enum class GeneType {
    // Движение
    MOVE_RANDOM,          // Случайное движение
    MOVE_TOWARD_NUTRIENT, // Движение к питательным веществам
    MOVE_AWAY_PREDATOR,   // Избегание хищников
    MOVE_TOWARD_LIGHT,    // Фототаксис (для фотосинтезирующих)
    MOVE_VERTICAL,        // Вертикальная миграция
    
    // Метаболизм
    METABOLISM_FAST,      // Быстрый метаболизм
    METABOLISM_EFFICIENT, // Эффективный метаболизм
    PHOTOSYNTHESIS,       // Фотосинтез
    CHEMOSYNTHESIS,       // Хемосинтез
    STORE_ENERGY,         // Накопление энергии
    
    // Размножение
    DIVIDE_ASEXUAL,       // Бесполое деление
    DIVIDE_SEXUAL,        // Половое размножение (обмен ДНК)
    BUDDING,              // Почкование
    
    // Защита
    PRODUCE_TOXIN,        // Производство токсинов
    FORM_SPORE,           // Образование спор
    DEVELOP_ARMOR,        // Укрепление мембраны
    CAMOUFLAGE,           // Маскировка
    
    // Социальное поведение
    SIGNAL_OTHERS,        // Сигнализация другим клеткам
    FORM_COLONY,          // Formation колоний
    COOPERATIVE_HUNT,     // Кооперативная охота
    
    // Специализация
    DIFFERENTIATE,        // Дифференциация клеток
    SPECIALIZE_ATTACK,    // Специализация на атаке
    SPECIALIZE_DEFENSE,   // Специализация на защите
    SPECIALIZE_REPRODUCE  // Специализация на размножении
}

/**
 * Условия активации генов
 */
@Serializable
sealed class Condition {
    abstract fun evaluate(context: GeneContext): Boolean
    
    @Serializable
    data class EnergyThreshold(val minEnergy: Float, val maxEnergy: Float = Float.MAX_VALUE) : Condition() {
        override fun evaluate(context: GeneContext) = 
            context.energy >= minEnergy && context.energy <= maxEnergy
    }
    
    @Serializable
    data class SizeThreshold(val minSize: Float, val maxSize: Float = Float.MAX_VALUE) : Condition() {
        override fun evaluate(context: GeneContext) = 
            context.size >= minSize && context.size <= maxSize
    }
    
    @Serializable
    data class NutrientNearby(val minConcentration: Float, val radius: Float) : Condition() {
        override fun evaluate(context: GeneContext) = 
            context.nutrientConcentration >= minConcentration
    }
    
    @Serializable
    data class PredatorNearby(val maxDistance: Float) : Condition() {
        override fun evaluate(context: GeneContext) = 
            context.predatorDistance <= maxDistance
    }
    
    @Serializable
    data class PopulationDensity(val minDensity: Int, val maxDensity: Int = Int.MAX_VALUE) : Condition() {
        override fun evaluate(context: GeneContext) = 
            context.localPopulation in minDensity..maxDensity
    }
    
    @Serializable
    data class TimeOfDay(val hourStart: Int, val hourEnd: Int) : Condition() {
        override fun evaluate(context: GeneContext) = 
            context.simulationHour in hourStart..hourEnd
    }
    
    @Serializable
    data class TemperatureRange(val minTemp: Float, val maxTemp: Float) : Condition() {
        override fun evaluate(context: GeneContext) = 
            context.temperature in minTemp..maxTemp
    }
    
    @Serializable
    object AlwaysActive : Condition() {
        override fun evaluate(context: GeneContext) = true
    }
}

/**
 * Контекст для оценки условий генов
 */
data class GeneContext(
    val energy: Float,
    val size: Float,
    val nutrientConcentration: Float,
    val predatorDistance: Float,
    val localPopulation: Int,
    val simulationHour: Int,
    val temperature: Float,
    val ph: Float
)
