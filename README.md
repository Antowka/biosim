# MicrobeSim - Симулятор жизни микроорганизмов

Платформа: Android 14+  
Жанр: Симулятор жизни / Эволюционный симулятор

## Описание

MicrobeSim - это продвинутый симулятор жизни для простейших микроорганизмов, вдохновленный Cell Lab, но с улучшенной архитектурой и расширенными возможностями.

## Ключевые особенности

### 🧬 Система генома
- **Гибкое программирование поведения** через гены с условиями активации
- **25+ типов генов**: движение, метаболизм, размножение, защита, социальное поведение
- **Приоритеты генов** для разрешения конфликтов поведения
- **Мутации и скрещивание** для эволюции

### ⚡ Производительность
- **Spatial Hashing** для оптимизации поиска соседей (O(1) вместо O(n²))
- **Поддержка до 1000 клеток** без падения FPS
- **Адаптивная отрисовка** с тремя уровнями качества
- **Object pooling** для уменьшения GC

### 🎮 Геймплей
- **Режим песочницы** - свободная эволюция
- **Режим кампании** - задания на выживание
- **Редактор генома** - визуальное и скриптовое создание организмов
- **Отслеживание родословной** - генеалогическое древо видов

### 🛠 Технические улучшения (vs Cell Lab)
- Исправлены проблемы производительности при >500 клетках
- Современный UI с Jetpack Compose (планируется)
- Сохранение прогресса в Room Database
- Поддержка Android 14+ с правильной работой permissions
- Профилирование памяти и батареи

## Архитектура проекта

```
com.microbesim/
├── core/                    # Константы и базовые интерфейсы
│   └── SimulationConstants.kt
├── genome/                  # Система генов
│   ├── Gene.kt             # Типы генов и условия
│   └── Genome.kt           # Геном клетки
├── models/                  # Модели данных
│   └── Cell.kt             # Модель клетки
├── engine/                  # Движок симуляции
│   └── SimulationEngine.kt # Основной цикл и spatial hash
├── ui/                      # UI компоненты
│   └── SimulationView.kt   # Custom View для отрисовки
└── utils/                   # Утилиты
    └── GenomeScriptEngine.kt # DSL для создания геномов
```

## Быстрый старт

### Создание организма через DSL

```kotlin
val predatorGenome = genome("Fast Predator") {
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
```

### Создание организма через JSON

```json
{
  "speciesName": "Photosynthesizer",
  "genes": [
    {
      "id": "photo_gene",
      "type": "PHOTOSYNTHESIS",
      "parameters": {"efficiency": 0.5, "lightRequirement": 0.3},
      "conditions": [{"type": "TIME_OF_DAY", "hourStart": 6, "hourEnd": 18}],
      "priority": 6
    }
  ]
}
```

### Запуск симуляции

```kotlin
val engine = SimulationEngine()
val cell = Cell(
    id = System.nanoTime(),
    positionX = 500f,
    positionY = 500f,
    genome = Genome.createDefault("Starter")
)
engine.addCell(cell)

// В игровом цикле
fun update(deltaTime: Float) {
    engine.update(deltaTime)
    simulationView.invalidate()
}
```

## Типы генов

### Движение
- `MOVE_RANDOM` - случайное блуждание
- `MOVE_TOWARD_NUTRIENT` - хемотаксис к питанию
- `MOVE_AWAY_PREDATOR` - избегание хищников
- `MOVE_TOWARD_LIGHT` - фототаксис
- `MOVE_VERTICAL` - вертикальная миграция

### Метаболизм
- `METABOLISM_FAST` - быстрый расход энергии
- `METABOLISM_EFFICIENT` - экономный метаболизм
- `PHOTOSYNTHESIS` - синтез энергии из света
- `CHEMOSYNTHESIS` - синтез из химических веществ

### Размножение
- `DIVIDE_ASEXUAL` - бинарное деление
- `DIVIDE_SEXUAL` - конъюгация (обмен ДНК)
- `BUDDING` - почкование

### Защита
- `PRODUCE_TOXIN` - производство яда
- `FORM_SPORE` - образование спор
- `DEVELOP_ARMOR` - укрепление мембраны

### Социальное
- `SIGNAL_OTHERS` - химическая сигнализация
- `FORM_COLONY` - образование колоний
- `COOPERATIVE_HUNT` - групповая охота

## Условия активации генов

- `EnergyThreshold` - уровень энергии
- `SizeThreshold` - размер клетки
- `NutrientNearby` - концентрация питания
- `PredatorNearby` - близость хищника
- `PopulationDensity` - плотность популяции
- `TimeOfDay` - время суток
- `TemperatureRange` - температура

## Сборка и запуск

```bash
# Сборка debug версии
./gradlew assembleDebug

# Установка на устройство
./gradlew installDebug

# Запуск тестов
./gradlew test
```

## Требования

- Android 14+ (API 34)
- Min SDK: 24 (Android 7.0)
- Kotlin 1.9+
- Jetpack Compose (для UI)

## Лицензия

MIT License

## Roadmap

- [ ] Jetpack Compose UI
- [ ] Система достижений
- [ ] Мультиплеер (асинхронный)
- [ ] Экспорт/импорт организмов
- [ ] Статистика эволюции
- [ ] Туториал
- [ ] Sound effects