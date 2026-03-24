package com.microbesim.core

/**
 * Основные константы симуляции
 */
object SimulationConstants {
    // Физические константы
    const val DIFFUSION_RATE = 0.1f
    const val NUTRIENT_DECAY_RATE = 0.001f
    const val TEMPERATURE_DEFAULT = 25.0f
    const val PH_DEFAULT = 7.0f
    
    // Клеточные константы
    const val CELL_MIN_SIZE = 5.0f
    const val CELL_MAX_SIZE = 50.0f
    const val CELL_DIVISION_THRESHOLD = 0.8f
    const val METABOLIC_BASE_RATE = 0.01f
    
    // Геном
    const val MAX_GENOME_LENGTH = 100
    const val MUTATION_RATE_DEFAULT = 0.01f
    
    // Симуляция
    const val TICK_RATE_MS = 16L // ~60 FPS
    const val MAX_CELLS = 1000
    const val WORLD_WIDTH = 1000f
    const val WORLD_HEIGHT = 1000f
}
