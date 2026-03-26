package com.microbesim.genome

import kotlinx.serialization.Serializable
import com.microbesim.core.SimulationConstants

/**
 * Геном клетки - последовательность генов, определяющих поведение
 * Улучшенная архитектура по сравнению с Cell Lab:
 * - Поддержка приоритетов генов
 * - Условная активация
 * - Модульная структура
 */
@Serializable
data class Genome(
    val genes: List<Gene> = emptyList(),
    val speciesName: String = "Unknown",
    val version: Int = 1,
    val metadata: Map<String, String> = emptyMap()
) {
    /**
     * Получить активные гены для текущего контекста
     */
    fun getActiveGenes(context: GeneContext): List<Gene> {
        return genes
            .filter { it.checkConditions(context) }
            .sortedByDescending { it.priority }
    }
    
    /**
     * Получить первый активный ген указанного типа
     */
    fun getFirstActiveGene(type: GeneType, context: GeneContext): Gene? {
        return getActiveGenes(context).firstOrNull { it.type == type }
    }
    
    /**
     * Мутировать геном с заданной вероятностью
     */
    fun mutate(mutationRate: Float = SimulationConstants.MUTATION_RATE_DEFAULT): Genome {
        val mutatedGenes = genes.mapNotNull { gene ->
            when {
                // Удаление гена
                Math.random() < mutationRate * 0.3 -> null
                
                // Модификация параметров
                Math.random() < mutationRate * 0.5 -> {
                    val modifiedParams = gene.parameters.toMutableMap()
                    if (modifiedParams.isNotEmpty()) {
                        val key = modifiedParams.keys.random()
                        modifiedParams[key] = (modifiedParams[key]!! * (0.8f + Math.random().toFloat() * 0.4f))
                            .coerceIn(0f, 100f)
                    }
                    gene.copy(parameters = modifiedParams)
                }
                
                // Изменение приоритета
                Math.random() < mutationRate * 0.2 -> {
                    gene.copy(priority = (gene.priority + (-1..1).random()).coerceIn(0, 10))
                }
                
                else -> gene
            }
        }
        
        // Добавление нового случайного гена с небольшой вероятностью
        val finalGenes = if (Math.random() < mutationRate * 0.3 && mutatedGenes.size < SimulationConstants.MAX_GENOME_LENGTH) {
            mutatedGenes + generateRandomGene()
        } else {
            mutatedGenes
        }
        
        return copy(genes = finalGenes, version = version + 1)
    }
    
    /**
     * Скрещивание двух геномов (половое размножение)
     */
    fun crossover(other: Genome): Genome {
        val crossoverPoint = (genes.size / 2).coerceIn(1, maxOf(1, genes.size - 1))
        val myPart = genes.take(crossoverPoint)
        val otherPart = other.genes.drop(crossoverPoint)
        
        return copy(
            genes = myPart + otherPart,
            speciesName = "$speciesName-${other.speciesName}",
            version = maxOf(version, other.version) + 1
        )
    }
    
    /**
     * Валидация генома
     */
    fun validate(): ValidationResult {
        val errors = mutableListOf<String>()
        
        if (genes.isEmpty()) {
            errors.add("Геном не содержит генов")
        }
        
        if (genes.size > SimulationConstants.MAX_GENOME_LENGTH) {
            errors.add("Превышена максимальная длина генома: ${genes.size} > ${SimulationConstants.MAX_GENOME_LENGTH}")
        }
        
        // Проверка дубликатов ID
        val duplicateIds = genes.groupingBy { it.id }.eachCount().filter { it.value > 1 }.keys
        if (duplicateIds.isNotEmpty()) {
            errors.add("Обнаружены дубликаты ID генов: ${duplicateIds.joinToString()}")
        }
        
        // Проверка параметров генов
        genes.forEach { gene ->
            gene.parameters.forEach { (key, value) ->
                if (value.isNaN() || value.isInfinite()) {
                    errors.add("Некорректный параметр '${key}' в гене '${gene.id}'")
                }
            }
        }
        
        return ValidationResult(errors.isEmpty(), errors)
    }
    
    /**
     * Генерация случайного гена для мутации
     */
    private fun generateRandomGene(): Gene {
        val randomType = GeneType.values().random()
        return Gene(
            id = "gene_${System.currentTimeMillis()}_${Math.random()}",
            type = randomType,
            parameters = generateDefaultParameters(randomType),
            conditions = listOf(Condition.AlwaysActive),
            priority = (0..5).random()
        )
    }
    
    /**
     * Параметры по умолчанию для разных типов генов
     */
    private fun generateDefaultParameters(type: GeneType): Map<String, Float> {
        return when (type) {
            GeneType.MOVE_RANDOM -> mapOf("speed" to 1.0f, "changeDirectionChance" to 0.1f)
            GeneType.MOVE_TOWARD_NUTRIENT -> mapOf("speed" to 1.2f, "sensingRadius" to 50.0f)
            GeneType.METABOLISM_FAST -> mapOf("efficiency" to 0.6f, "rate" to 1.5f)
            GeneType.METABOLISM_EFFICIENT -> mapOf("efficiency" to 0.9f, "rate" to 0.7f)
            GeneType.PHOTOSYNTHESIS -> mapOf("efficiency" to 0.5f, "lightRequirement" to 0.3f)
            GeneType.DIVIDE_ASEXUAL -> mapOf("minEnergy" to 0.8f, "sizeRatio" to 0.5f)
            GeneType.PRODUCE_TOXIN -> mapOf("toxicity" to 0.5f, "productionCost" to 0.2f)
            else -> emptyMap()
        }
    }
    
    companion object {
        /**
         * Создание базового генома для новых организмов
         */
        fun createDefault(name: String = "New Species"): Genome {
            return Genome(
                genes = listOf(
                    Gene(
                        id = "move_basic",
                        type = GeneType.MOVE_RANDOM,
                        parameters = mapOf("speed" to 1.0f, "changeDirectionChance" to 0.1f),
                        conditions = listOf(Condition.AlwaysActive),
                        priority = 5
                    ),
                    Gene(
                        id = "metabolism_basic",
                        type = GeneType.METABOLISM_EFFICIENT,
                        parameters = mapOf("efficiency" to 0.8f, "rate" to 0.8f),
                        conditions = listOf(Condition.AlwaysActive),
                        priority = 5
                    ),
                    Gene(
                        id = "divide_basic",
                        type = GeneType.DIVIDE_ASEXUAL,
                        parameters = mapOf("minEnergy" to 0.75f, "sizeRatio" to 0.5f),
                        conditions = listOf(Condition.EnergyThreshold(0.75f)),
                        priority = 3
                    )
                ),
                speciesName = name,
                version = 1
            )
        }
    }
}

/**
 * Результат валидации генома
 */
data class ValidationResult(
    val isValid: Boolean,
    val errors: List<String>
)
