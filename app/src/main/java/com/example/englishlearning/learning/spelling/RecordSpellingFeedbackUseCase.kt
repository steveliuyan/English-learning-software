package com.example.englishlearning.learning.spelling

import com.example.englishlearning.learning.EventIdFactory
import com.example.englishlearning.learning.SubmitCardFeedbackUseCase
import com.example.englishlearning.learning.SubmitFeedbackCommand
import com.example.englishlearning.learning.SubmitFeedbackResult
import java.time.Instant

/**
 * 把一次拼写作答写进复习排期。
 *
 * 判卷本身只活在这一次会话里，但「这一题拼得怎样」本来就是对同一张词卡的回忆强度信号，
 * 丢掉它等于每轮都从零开始问。所以这里把三档判分经 [toCardFeedback] 折成 V1 的三档反馈，
 * 再走**已有的** [SubmitCardFeedbackUseCase]（幂等键、版本号、写事件与卡状态全都不另开一套）。
 *
 * ## planId 用合成前缀的理由
 *
 * 事件表上的 `planId` 参与「今日计划完成度」的统计（`InternalLearningEventDao` 要 join
 * `today_plans`）。拼写练习不是当日计划里的任务，用真 planId 会把它冒充成一次计划完成，
 * 于是沿用「重学」那条先例（`VocabularyRelearnViewModel` 的 `relearn:$profileId`），
 * 写 [PLAN_PREFIX] + profileId：词卡排期照常前移，今日计划的完成度不动。
 */
class RecordSpellingFeedbackUseCase(
    private val eventIds: EventIdFactory,
    private val submit: suspend (SubmitFeedbackCommand) -> SubmitFeedbackResult,
) {
    constructor(
        submitCardFeedback: SubmitCardFeedbackUseCase,
        eventIds: EventIdFactory,
    ) : this(eventIds = eventIds, submit = submitCardFeedback::invoke)

    /**
     * 记一条拼写结果。[eventId] 默认现取一枚；重试时把同一次作答的 id 传回来，
     * 就与 [SubmitCardFeedbackUseCase] 的幂等口径一致，不会被算成第二次提交。
     */
    suspend fun record(
        profileId: String,
        prompt: SpellingPrompt,
        verdict: SpellingVerdict,
        eventId: String = eventIds.newId(),
    ): RecordSpellingResult {
        val command = SubmitFeedbackCommand(
            eventId = eventId,
            profileId = profileId,
            planId = planIdFor(profileId),
            cardId = prompt.cardId,
            wordBookId = prompt.wordBookId,
            feedback = verdict.toCardFeedback(),
        )
        return when (val result = submit(command)) {
            is SubmitFeedbackResult.Recorded -> RecordSpellingResult.Recorded(result.nextReviewAt)
            is SubmitFeedbackResult.AlreadyRecorded -> RecordSpellingResult.Recorded(result.nextReviewAt)
            SubmitFeedbackResult.StorageUnavailable -> RecordSpellingResult.StorageUnavailable
        }
    }

    /** 拼写练习的合成 planId：只推词卡排期，不冒充今日计划任务。 */
    fun planIdFor(profileId: String): String = "$PLAN_PREFIX$profileId"

    companion object {
        const val PLAN_PREFIX: String = "spelling:"
    }
}

/** 一条拼写结果的落库结局。 */
sealed interface RecordSpellingResult {
    /**
     * 已记入排期，[nextReviewAt] 是调度器真正写入的下次到期时刻。
     *
     * 同一 eventId 的重复提交与首次提交**归为同一档**：复习者关心的是「这张卡下次什么时候问」，
     * 而重放读到的是首次写入的那个值（AC1-04），把它单独摆成一档只会在界面上多一句无用的话。
     */
    data class Recorded(val nextReviewAt: Instant) : RecordSpellingResult

    /** 本地存储不可用：什么都没写，卡片的排期保持原样。 */
    data object StorageUnavailable : RecordSpellingResult
}
