package com.microbesim.data

import android.content.Context
import androidx.room.*
import com.microbesim.genome.Genome
import kotlinx.coroutines.flow.Flow

/**
 * Entity для сохранённых организмов
 */
@Entity(tableName = "saved_organisms")
data class SavedOrganism(
    @PrimaryKey val id: String,
    val name: String,
    val genomeJson: String,
    val createdAt: Long = System.currentTimeMillis(),
    val stats: String = "{}", // JSON с статистикой
    val tags: String = "" // Теги для поиска
)

/**
 * Entity для прогресса кампании
 */
@Entity(tableName = "campaign_progress")
data class CampaignProgress(
    @PrimaryKey val levelId: Int,
    val isCompleted: Boolean = false,
    val bestScore: Int = 0,
    val completedAt: Long? = null,
    val attemptsCount: Int = 0
)

/**
 * DAO для работы с организмами
 */
@Dao
interface OrganismDao {
    @Query("SELECT * FROM saved_organisms ORDER BY createdAt DESC")
    fun getAllOrganisms(): Flow<List<SavedOrganism>>
    
    @Query("SELECT * FROM saved_organisms WHERE id = :id")
    suspend fun getOrganismById(id: String): SavedOrganism?
    
    @Query("SELECT * FROM saved_organisms WHERE tags LIKE :tagQuery")
    fun searchOrganisms(tagQuery: String): Flow<List<SavedOrganism>>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrganism(organism: SavedOrganism)
    
    @Delete
    suspend fun deleteOrganism(organism: SavedOrganism)
    
    @Query("DELETE FROM saved_organisms WHERE id = :id")
    suspend fun deleteOrganismById(id: String)
}

/**
 * DAO для прогресса кампании
 */
@Dao
interface CampaignDao {
    @Query("SELECT * FROM campaign_progress ORDER BY levelId")
    fun getAllProgress(): Flow<List<CampaignProgress>>
    
    @Query("SELECT * FROM campaign_progress WHERE levelId = :levelId")
    suspend fun getLevelProgress(levelId: Int): CampaignProgress?
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun updateProgress(progress: CampaignProgress)
    
    @Query("UPDATE campaign_progress SET isCompleted = 1, bestScore = :score, completedAt = :completedAt WHERE levelId = :levelId")
    suspend fun completeLevel(levelId: Int, score: Int, completedAt: Long)
}

/**
 * База данных приложения
 */
@Database(
    entities = [SavedOrganism::class, CampaignProgress::class],
    version = 1,
    exportSchema = false
)
abstract class MicrobeSimDatabase : RoomDatabase() {
    abstract fun organismDao(): OrganismDao
    abstract fun campaignDao(): CampaignDao
    
    companion object {
        @Volatile private var INSTANCE: MicrobeSimDatabase? = null
        
        fun getInstance(context: Context): MicrobeSimDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    MicrobeSimDatabase::class.java,
                    "microbesim_database"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}

/**
 * Репозиторий для работы с данными
 */
class DataRepository(private val database: MicrobeSimDatabase) {
    private val organismDao = database.organismDao()
    private val campaignDao = database.campaignDao()
    
    // Организмы
    val allOrganisms: Flow<List<SavedOrganism>> = organismDao.getAllOrganisms()
    
    suspend fun getOrganism(id: String): SavedOrganism? = organismDao.getOrganismById(id)
    suspend fun saveOrganism(organism: SavedOrganism) = organismDao.insertOrganism(organism)
    suspend fun deleteOrganism(organism: SavedOrganism) = organismDao.deleteOrganism(organism)
    
    // Прогресс кампании
    val campaignProgress: Flow<List<CampaignProgress>> = campaignDao.getAllProgress()
    
    suspend fun getLevelProgress(levelId: Int): CampaignProgress? = 
        campaignDao.getLevelProgress(levelId)
    
    suspend fun completeLevel(levelId: Int, score: Int) = 
        campaignDao.completeLevel(levelId, score, System.currentTimeMillis())
    
    suspend fun updateProgress(progress: CampaignProgress) = 
        campaignDao.updateProgress(progress)
}
