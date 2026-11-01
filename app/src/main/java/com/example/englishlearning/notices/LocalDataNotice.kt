package com.example.englishlearning.notices

/**
 * U-6（决策 `docs/decisions/2026-11-03-duplication-consolidation.md` §8）：
 * 「学习数据只在本机 / 卸载前导出」这条警告的**唯一措辞源**。
 *
 * 改前四处各写一套（许可页那一条、备份 hub 页尾、学习记录页尾、留档导出声明末尾），
 * 而这句话是用户决定「现在要不要导出一份」的依据：同一件事四种句子，
 * 其中 hub 那句只说了「卸载 App」，漏掉 AGENTS.md「本地优先」点名要求说全的「清除数据」「设备损坏」两件。
 * 现在四枚事实各写一遍，三屏的整句**由事实拼出来**——屏里不再手写中文警告，
 * 判据在 `LocalDataNoticeSingleSourceTest`（旧句在 `ui/` 含注释归零 + 剥注释后 `ui/` 再无任何字面量写这条警告）。
 *
 * 许可页那五条 `thirdPartyPrivacyStatements()` 是阶段 5 Spec 发布要点的逐条对应（按关键词钉），
 * 本文件不复述它们，只在最长的那条声明末尾指回那一页——短句子说事实，完整政策只有一个落点。
 */
object LocalDataNotice {
    // 四枚事实：每枚只在本仓写这一遍；改动只改这里，三屏的句子跟着走。
    const val StoredLocally = "只保存在本机"
    const val NoAccountNoCloud = "无账户、也不会自动上云"
    const val LostOnUninstall = "卸载 App、清除数据或设备损坏后可能无法恢复"
    const val ExportIsTheOnlyExit = "导出备份是唯一的留档途径"

    /** 备份 hub 页尾那条常驻警告（危险区之上、条件回执之外，原型 `sMeData` 的那一条）。 */
    const val BackupHubWarning = "所有数据" + StoredLocally + "，" + NoAccountNoCloud + "；" + LostOnUninstall + "，" + ExportIsTheOnlyExit + "。"

    /** 学习记录页尾那条：与 hub 只差主语（记录是数据的子集，事实一句都不能少）。 */
    const val LearningRecordsWarning = "学习记录" + StoredLocally + "，" + NoAccountNoCloud + "；" + LostOnUninstall + "，" + ExportIsTheOnlyExit + "。"

    /** 留档导出声明的尾巴：那条声明最长，所以补上「导出之后还得自己看着办」这层后果。 */
    const val ExportTail = "学习数据" + StoredLocally + "，" + NoAccountNoCloud + "；" + LostOnUninstall + "，导出后的备份文件请自行妥善保存。"

    /** 完整政策的出口：只在此处指一次，别处不再复述第二遍。 */
    const val PrivacyStatementsPointer = "完整隐私说明见「我的 → 关于 → 开源许可与隐私说明」。"
}
