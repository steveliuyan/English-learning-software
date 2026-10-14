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

    @Query("DELETE FROM today_plan_tasks WHERE planId = :planId")
    suspend fun deleteTasks(planId: String)

    @Query("DELETE FROM today_plans WHERE profileId = :profileId AND localDate = :localDate")
    suspend fun deletePlan(profileId: String, localDate: String)

    // ---- F4-01 决策 §9：计划快照（含任务行）按档案计入「删除全部学习数据」 ----

    @Query("SELECT COUNT(*) FROM today_plans WHERE profileId = :profileId")
    suspend fun countForProfile(profileId: String): Int

    /** 任务行没有 profileId 列，必须经 planId 子查询级联删；先删任务再删计划本体。 */
    @Query(
        "DELETE FROM today_plan_tasks WHERE planId IN (SELECT planId FROM today_plans WHERE profileId = :profileId)",
    )
    suspend fun deleteTasksForProfile(profileId: String)

    @Query("DELETE FROM today_plans WHERE profileId = :profileId")
    suspend fun deleteForProfile(profileId: String)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertPlan(plan: TodayPlanEntity)

    @Insert
    suspend fun insertTasks(tasks: List<TodayPlanTaskEntity>)

    @Transaction
    suspend fun insertIfAbsent(plan: TodayPlanEntity, tasks: List<TodayPlanTaskEntity>) {
        insertPlan(plan)
        insertTasks(tasks)
    }

    @Transaction
    suspend fun replaceForDate(
        oldPlan: TodayPlanEntity?,
        plan: TodayPlanEntity,
        tasks: List<TodayPlanTaskEntity>,
    ) {
        oldPlan?.let {
            deleteTasks(it.planId)
            deletePlan(it.profileId, it.localDate)
        }
        insertPlan(plan)
        insertTasks(tasks)
    }
}
