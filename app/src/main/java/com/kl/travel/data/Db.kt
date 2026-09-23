package com.kl.travel.data

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
abstract class TravelDao {
    @Query("SELECT * FROM events WHERE tripId = :tripId ORDER BY date, startTime")
    abstract fun events(tripId: Long): Flow<List<EventEntity>>

    @Query("SELECT * FROM events WHERE tripId = :tripId ORDER BY date, startTime")
    abstract suspend fun eventsOnce(tripId: Long): List<EventEntity>

    @Query("SELECT * FROM hotels WHERE tripId = :tripId ORDER BY checkInDate")
    abstract fun hotels(tripId: Long): Flow<List<HotelEntity>>

    @Query("SELECT * FROM hotels WHERE tripId = :tripId ORDER BY checkInDate")
    abstract suspend fun hotelsOnce(tripId: Long): List<HotelEntity>

    @Query("DELETE FROM events WHERE tripId = :tripId") abstract suspend fun clearEvents(tripId: Long)
    @Query("DELETE FROM hotels WHERE tripId = :tripId") abstract suspend fun clearHotels(tripId: Long)
    @Query("DELETE FROM expenses WHERE tripId = :tripId") abstract suspend fun clearExpenses(tripId: Long)
    @Insert(onConflict = OnConflictStrategy.REPLACE) abstract suspend fun insertEvents(l: List<EventEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) abstract suspend fun insertHotels(l: List<HotelEntity>)

    /** Replaces one trip's cached sheet data; other trips are untouched. */
    @Transaction
    open suspend fun replaceAll(tripId: Long, events: List<EventEntity>, hotels: List<HotelEntity>) {
        clearEvents(tripId); clearHotels(tripId)
        insertEvents(events); insertHotels(hotels)
    }

    /** Deleting a trip removes its events, lodging and expenses. */
    @Transaction
    open suspend fun deleteTrip(tripId: Long) { clearEvents(tripId); clearHotels(tripId); clearExpenses(tripId) }

    @Query("SELECT MIN(date) AS firstDate, MAX(date) AS lastDate, COUNT(*) AS n FROM events WHERE tripId = :tripId")
    abstract suspend fun span(tripId: Long): TripSpan

    @Query("SELECT * FROM expenses WHERE tripId = :tripId ORDER BY date DESC, id DESC")
    abstract fun expenses(tripId: Long): Flow<List<ExpenseEntity>>
    @Insert abstract suspend fun addExpense(e: ExpenseEntity)
    @Query("DELETE FROM expenses WHERE id = :id") abstract suspend fun deleteExpense(id: Long)

    @Query("SELECT * FROM expenses ORDER BY id") abstract suspend fun allExpenses(): List<ExpenseEntity>
    @Query("DELETE FROM expenses") abstract suspend fun clearAllExpenses()
    @Insert abstract suspend fun insertExpenses(l: List<ExpenseEntity>)

    /** Backup restore: every expense is replaced by the backed-up list. */
    @Transaction
    open suspend fun replaceExpenses(l: List<ExpenseEntity>) { clearAllExpenses(); insertExpenses(l.map { it.copy(id = 0) }) }
}

@Database(entities = [EventEntity::class, HotelEntity::class, ExpenseEntity::class], version = 5, exportSchema = false)
abstract class TravelDb : RoomDatabase() {
    abstract fun dao(): TravelDao

    companion object {
        /** v2 adds the optional Seat and Aircraft columns; expenses and everything else are kept. */
        val MIGRATION_1_2 = object : androidx.room.migration.Migration(1, 2) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE events ADD COLUMN seat TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE events ADD COLUMN aircraft TEXT NOT NULL DEFAULT ''")
            }
        }
        val MIGRATION_2_3 = object : androidx.room.migration.Migration(2, 3) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE events ADD COLUMN cabin TEXT NOT NULL DEFAULT ''")
            }
        }
        /** v4: every event, lodging row and expense belongs to a trip; existing rows become trip 1. */
        val MIGRATION_3_4 = object : androidx.room.migration.Migration(3, 4) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE events ADD COLUMN tripId INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE hotels ADD COLUMN tripId INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE expenses ADD COLUMN tripId INTEGER NOT NULL DEFAULT 1")
            }
        }
        /** v5: receipts. An expense can keep its photo and the original currency and amount. */
        val MIGRATION_4_5 = object : androidx.room.migration.Migration(4, 5) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE expenses ADD COLUMN receiptPath TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE expenses ADD COLUMN currency TEXT NOT NULL DEFAULT 'USD'")
                db.execSQL("ALTER TABLE expenses ADD COLUMN originalAmount REAL NOT NULL DEFAULT 0")
            }
        }
        @Volatile private var inst: TravelDb? = null
        fun get(ctx: Context): TravelDb = inst ?: synchronized(this) {
            inst ?: Room.databaseBuilder(ctx.applicationContext, TravelDb::class.java, "kltravel.db")
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
                .build().also { inst = it }
        }
    }
}
