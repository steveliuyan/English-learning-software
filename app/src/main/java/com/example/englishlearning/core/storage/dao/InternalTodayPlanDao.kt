package com.example.englishlearning.core.storage.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.example.englishlearning.core.storage.entity.TodayPlanEntity
import com.example.englishlearning.core.storage.entity.TodayPlanTaskEntity

@Dao
internal interface InternalTodayPlanDao {
    @Query("SELECT * FROM today_plans WHERE profileId = :profileId AND localDate = :localDate LIMIT 1")
    suspend fun findPlan(profileId: String, localDate: String): TodayPlanEntity?

    @Query("SELECT * FROM today_plans WHERE profileId = :profileId ORDER BY localDate DESC LIMIT 1")
    suspend fun findLatestPlan(profileId: String): TodayPlanEntity?

    @Query("SELECT * FROM today_plan_tasks WHERE planId = :planId ORDER BY ordinal ASC")
    suspend fun findTasks(planId: String): List<TodayPlanTaskEntity>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertPlan(plan: TodayPlanEntity)

    @Insert
    suspend fun insertTasks(tasks: List<TodayPlanTaskEntity>)

    @Transaction
    suspend fun insertIfAbsent(plan: TodayPlanEntity, tasks: List<TodayPlanTaskEntity>) {
        insertPlan(plan)
        insertTasks(tasks)
    }
}
