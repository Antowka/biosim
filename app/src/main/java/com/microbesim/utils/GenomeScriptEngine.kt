package com.microbesim.utils

import com.microbesim.genome.Genome
import com.microbesim.genome.Gene
import com.microbesim.genome.GeneType
import com.microbesim.genome.Condition
import kotlinx.serialization.json.*
import java.io.File

/**
 * Скриптовый движок для описания поведения микроорганизмов
 * Позволяет создавать и модифицировать геномы через JSON/DSL
 */
class GenomeScriptEngine {
    private val json = Json {
        prettyPrint = true
        isLenient = true
        ignoreUnknownKeys = true
    }
    
    /**
     * Парсинг генома из JSON строки
     */
    fun parseGenome(jsonString: String): Genome {
        return try {
            json.decodeFromString(Genome.serializer(), jsonString)
        } catch (e: Exception) {
            throw ScriptParseException("Ошибка парсинга генома: ${e.message}")
        }
    }
    
    /**
     * Сериализация генома в JSON
     */
    fun genomeToJson(genome: Genome): String {
        return json.encodeToString(Genome.serializer(), genome)
    }
    
    /**
     * Создание генома из DSL скрипта
     * Пример:
     * ```
     * genome "Predator" {
     *     gene MOVE_TOWARD_NUTRIENT priority 5 {
     *         param "speed" 1.5f
     *         param "sensingRadius" 75f
     *         condition ENERGY_MIN 0.3f
     *     }
     *     gene PRODUCE_TOXIN priority 3 {
     *         param "toxicity" 0.7f
     *         condition POPULATION_MIN 5
     *     }
     * }
     * ```
     */
    fun createGenomeFromDsl(script: String): Genome {
        val builder = GenomeBuilder()
        val lines = script.lines().map { it.trim() }.filter { it.isNotBlank() && !it.startsWith("//") }
        
        var currentGene: GeneBuilder? = null
        var genomeName = "Custom Species"
        
        for (line in lines) {
            when {
                line.startsWith("genome") -> {
                    genomeName = extractStringValue(line)
                }
                line.startsWith("gene") -> {
                    currentGene = GeneBuilder()
                    val parts = line.split("\\s+".toRegex())
                    if (parts.size >= 2) {
                        currentGene.type = GeneType.valueOf(parts[1])
                    }
                }
                line.startsWith("priority") && currentGene != null -> {
                    val parts = line.split("\\s+".toRegex())
                    if (parts.size >= 2) {
                        currentGene.priority = parts[1].toInt()
                    }
                }
                line.startsWith("param") && currentGene != null -> {
                    val parts = line.split("\\s+".toRegex())
                    if (parts.size >= 3) {
                        val key = parts[1].trim('"')
                        val value = parts[2].replace("f", "").toFloat()
                        currentGene.parameters[key] = value
                    }
                }
                line.startsWith("condition") && currentGene != null -> {
                    val condition = parseCondition(line)
                    if (condition != null) {
                        currentGene.conditions.add(condition)
                    }
                }
                line == "}" && currentGene != null -> {
                    builder.addGene(currentGene.build())
                    currentGene = null
                }
            }
        }
        
        return builder.build(genomeName)
    }
    
    /**
     * Парсинг условия из строки
     */
    private fun parseCondition(line: String): Condition? {
        val parts = line.split("\\s+".toRegex())
        if (parts.size < 2) return null
        
        return when (parts[1]) {
            "ALWAYS" -> Condition.AlwaysActive
            "ENERGY_MIN" -> {
                if (parts.size >= 3) {
                    Condition.EnergyThreshold(minEnergy = parts[2].toFloat())
                } else null
            }
            "ENERGY_RANGE" -> {
                if (parts.size >= 4) {
                    Condition.EnergyThreshold(
                        minEnergy = parts[2].toFloat(),
                        maxEnergy = parts[3].toFloat()
                    )
                } else null
            }
            "SIZE_MIN" -> {
                if (parts.size >= 3) {
                    Condition.SizeThreshold(minSize = parts[2].toFloat())
                } else null
            }
            "NUTRIENT_NEAR" -> {
                if (parts.size >= 4) {
                    Condition.NutrientNearby(
                        minConcentration = parts[2].toFloat(),
                        radius = parts[3].toFloat()
                    )
                } else null
            }
            "PREDATOR_NEAR" -> {
                if (parts.size >= 3) {
                    Condition.PredatorNearby(maxDistance = parts[2].toFloat())
                } else null
            }
            "POPULATION_MIN" -> {
                if (parts.size >= 3) {
                    Condition.PopulationDensity(minDensity = parts[2].toInt())
                } else null
            }
            "TIME_RANGE" -> {
                if (parts.size >= 4) {
                    Condition.TimeOfDay(
                        hourStart = parts[2].toInt(),
                        hourEnd = parts[3].toInt()
                    )
                } else null
            }
            "TEMP_RANGE" -> {
                if (parts.size >= 4) {
                    Condition.TemperatureRange(
                        minTemp = parts[2].toFloat(),
                        maxTemp = parts[3].toFloat()
                    )
                } else null
            }
            else -> null
        }
    }
    
    /**
     * Извлечение строкового значения из кавычек
     */
    private fun extractStringValue(line: String): String {
        val match = "\"([^\"]*)\"".toRegex().find(line)
        return match?.groupValues?.get(1) ?: ""
    }
    
    /**
     * Сохранение генома в файл
     */
    fun saveGenomeToFile(genome: Genome, file: File) {
        file.writeText(genomeToJson(genome))
    }
    
    /**
     * Загрузка генома из файла
     */
    fun loadGenomeFromFile(file: File): Genome {
        return parseGenome(file.readText())
    }
    
    /**
     * Библиотека предустановленных шаблонов генов
     */
    object GeneTemplates {
        fun basicMover(): Gene = Gene(
            id = "move_basic_${System.currentTimeMillis()}",
            type = GeneType.MOVE_RANDOM,
            parameters = mapOf("speed" to 1.0f, "changeDirectionChance" to 0.1f),
            conditions = listOf(Condition.AlwaysActive),
            priority = 5
        )
        
        fun nutrientSeeker(): Gene = Gene(
            id = "seek_nutrient_${System.currentTimeMillis()}",
            type = GeneType.MOVE_TOWARD_NUTRIENT,
            parameters = mapOf("speed" to 1.2f, "sensingRadius" to 50f),
            conditions = listOf(Condition.EnergyThreshold(minEnergy = 0.2f)),
            priority = 7
        )
        
        fun efficientMetabolism(): Gene = Gene(
            id = "metab_efficient_${System.currentTimeMillis()}",
            type = GeneType.METABOLISM_EFFICIENT,
            parameters = mapOf("efficiency" to 0.85f, "rate" to 0.75f),
            conditions = listOf(Condition.AlwaysActive),
            priority = 5
        )
        
        fun fastMetabolism(): Gene = Gene(
            id = "metab_fast_${System.currentTimeMillis()}",
            type = GeneType.METABOLISM_FAST,
            parameters = mapOf("efficiency" to 0.6f, "rate" to 1.5f),
            conditions = listOf(Condition.EnergyThreshold(minEnergy = 0.5f)),
            priority = 4
        )
        
        fun asexualReproducer(): Gene = Gene(
            id = "divide_asexual_${System.currentTimeMillis()}",
            type = GeneType.DIVIDE_ASEXUAL,
            parameters = mapOf("minEnergy" to 0.75f, "sizeRatio" to 0.5f),
            conditions = listOf(Condition.EnergyThreshold(minEnergy = 0.75f, maxEnergy = 1.0f)),
            priority = 3
        )
        
        fun toxinProducer(): Gene = Gene(
            id = "toxin_${System.currentTimeMillis()}",
            type = GeneType.PRODUCE_TOXIN,
            parameters = mapOf("toxicity" to 0.5f, "productionCost" to 0.2f),
            conditions = listOf(Condition.PopulationDensity(minDensity = 5)),
            priority = 2
        )
        
        fun photosynthesizer(): Gene = Gene(
            id = "photo_${System.currentTimeMillis()}",
            type = GeneType.PHOTOSYNTHESIS,
            parameters = mapOf("efficiency" to 0.5f, "lightRequirement" to 0.3f),
            conditions = listOf(Condition.TimeOfDay(hourStart = 6, hourEnd = 18)),
            priority = 6
        )
        
        fun colonyFormer(): Gene = Gene(
            id = "colony_${System.currentTimeMillis()}",
            type = GeneType.FORM_COLONY,
            parameters = mapOf("radius" to 150f, "strength" to 0.2f),
            conditions = listOf(Condition.PopulationDensity(minDensity = 3, maxDensity = 20)),
            priority = 4
        )
        
        fun predatorAvoider(): Gene = Gene(
            id = "avoid_predator_${System.currentTimeMillis()}",
            type = GeneType.MOVE_AWAY_PREDATOR,
            parameters = mapOf("maxDistance" to 100f, "speed" to 1.5f),
            conditions = listOf(Condition.AlwaysActive),
            priority = 8
        )
    }
}

/**
 * Билдер для создания генома
 */
class GenomeBuilder {
    private val genes = mutableListOf<Gene>()
    
    fun addGene(gene: Gene) {
        genes.add(gene)
    }
    
    fun build(speciesName: String = "Custom Species"): Genome {
        return Genome(
            genes = genes,
            speciesName = speciesName,
            version = 1
        )
    }
}

/**
 * Билдер для создания гена
 */
class GeneBuilder {
    var type: GeneType = GeneType.MOVE_RANDOM
    var priority: Int = 5
    val parameters = mutableMapOf<String, Float>()
    val conditions = mutableListOf<Condition>()
    
    fun build(): Gene {
        return Gene(
            id = "${type.name.lowercase()}_${System.currentTimeMillis()}",
            type = type,
            parameters = parameters,
            conditions = conditions,
            priority = priority
        )
    }
}

/**
 * Исключение парсинга скрипта
 */
class ScriptParseException(message: String) : Exception(message)

/**
 * Расширения для удобного создания геномов в коде
 */
fun genome(name: String = "Custom", block: GenomeBuilder.() -> Unit): Genome {
    val builder = GenomeBuilder()
    builder.block()
    return builder.build(name)
}

fun GenomeBuilder.gene(type: GeneType, block: GeneBuilder.() -> Unit) {
    val geneBuilder = GeneBuilder()
    geneBuilder.type = type
    geneBuilder.block()
    addGene(geneBuilder.build())
}

fun GeneBuilder.param(key: String, value: Float) {
    parameters[key] = value
}

fun GeneBuilder.condition(condition: Condition) {
    conditions.add(condition)
}

// Пример использования DSL:
/*
val myGenome = genome("Fast Predator") {
    gene(GeneType.MOVE_TOWARD_NUTRIENT) {
        priority = 7
        param("speed", 1.5f)
        param("sensingRadius", 75f)
        condition(Condition.EnergyThreshold(minEnergy = 0.3f))
    }
    
    gene(GeneType.METABOLISM_FAST) {
        priority = 5
        param("efficiency", 0.65f)
        param("rate", 1.4f)
    }
    
    gene(GeneType.DIVIDE_ASEXUAL) {
        priority = 3
        param("minEnergy", 0.7f)
        param("sizeRatio", 0.5f)
    }
}
*/
