package com.microbesim

import android.app.Application

/**
 * Основное приложение MicrobeSim
 * Инициализация глобальных компонентов
 */
class MicrobeSimApplication : Application() {
    
    override fun onCreate() {
        super.onCreate()
        instance = this
        
        // Инициализация компонентов приложения
        initAppComponents()
    }
    
    private fun initAppComponents() {
        // Здесь будет инициализация:
        // - Базы данных Room
        // - Настроек SharedPreferences
        // - Других синглтонов
    }
    
    companion object {
        lateinit var instance: MicrobeSimApplication
            private set
    }
}
