package com.crashlab.analyzer.data

import android.content.Context
import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import com.crashlab.analyzer.domain.Round
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "rounds")
data class RoundEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    @ColumnInfo(name = "multiplier") val multiplier: Double,
    @ColumnInfo(name = "timestamp") val timestamp: Long,
    @ColumnInfo(name = "platform") val platform: String
) {
    fun toDomain() = Round(id, multiplier, timestamp, platform)
}

fun Round.toEntity() = RoundEntity(id, multiplier, timestamp, platform)

@Dao
interface RoundDao {
    @Query("SELECT * FROM rounds ORDER BY timestamp DESC, id DESC")
    fun observeAll(): Flow<List<RoundEntity>>

    @Query("SELECT * FROM rounds ORDER BY timestamp DESC, id DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<RoundEntity>>

    @Query("SELECT * FROM rounds ORDER BY timestamp ASC, id ASC")
    suspend fun allChronological(): List<RoundEntity>

    @Insert
    suspend fun insert(round: RoundEntity): Long

    @Insert
    suspend fun insertAll(rounds: List<RoundEntity>): List<Long>

    @Query("DELETE FROM rounds WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM rounds")
    suspend fun clear()

    @Query("SELECT COUNT(*) FROM rounds")
    suspend fun count(): Int
}

@Database(entities = [RoundEntity::class], version = 1, exportSchema = false)
abstract class CrashDatabase : RoomDatabase() {
    abstract fun roundDao(): RoundDao

    companion object {
        @Volatile private var INSTANCE: CrashDatabase? = null

        fun get(context: Context): CrashDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    CrashDatabase::class.java,
                    "crashlab.db"
                ).fallbackToDestructiveMigration().build().also { INSTANCE = it }
            }
    }
}
