# 2026-10-13 真机 QA 两轮收口 + main 线合并交接（交给主对话）

> 本文档面向"主对话"会话（负责 main 线的那一路）。设备 QA 分支的功能细节见
> `docs/handoff/2026-10-13-stage-4-device-qa-fixes-report.md`，本文不重复，
> 只交接 **合并回主线** 这件事：远端 main 的异常拓扑、已做的本地处置、以及建议的收口路径。

## 1. 本次目标

用户原话："确认没问题就合回主线"。随后用户改口："你给我一个交接文档，然后给一个提示词，我去主对话跟他说"——
即 **本会话不再直接推 main**，合并由主对话执行。

## 2. QA 分支现状（功能侧，已收口）

- 分支 `stage-4-device-qa-fixes`，基于 F4-03 分支头 `a16069b`。
- 提交：`5327324`（第一轮：发音提速/缓存/预热 + 笔记删除修复 + 笔记弹窗化）、
  `3d9a4ca`（第二轮：笔记改为"例句下方展示栏 + 弹窗管理"）。两者均已推送到
  `origin/stage-4-device-qa-fixes`。
- 门禁：`:app:assembleDebug` → `:app:testDebugUnitTest` **1118 用例全绿**（基线从 1097 增至 1118）→
  `:app:compileDebugAndroidTestKotlin` 编译过。detekt 1705→1710（+5 报告项，均为既有 UI 文件风格债，门禁不卡）。
- 真机复测：发音问题用户已确认通过（"单词读没问题了"）；笔记展示栏新版面 **尚未收到用户复测反馈**（见第 5 节）。

## 3. 关键发现：远端 main 已被重置，与交付历史无共同祖先

`git fetch` 后实测（2026-10-13）：

- `origin/main` = 孤立单提交 `1567401` "Initial commit"（仅一个 README.md，内容
  "# -\n个人自用的学习软件"，2026-09-17）。与我们 238 提交的交付历史线 **无共同祖先**
  （unrelated histories），说明远端 main 曾被 force-reset。
- 旧 main 线.tip = `132f507` "docs: report check-in navigation tests"——一条从
  `f49a8a8`（"docs: add stage zero plan and delivery rules"）分叉的 **12 提交并行线**，
  内容是打卡/学习统计（check-in center 等）。它在本地对象库中仍完整可读
  （`git log 132f507` 可用），但远端已无分支指向它；远端另有 `origin/workbuddy/main-2c1382cf`。
- 注意：我们这条 238 提交主线 **本身已包含打卡相关文件**（checkin 源码在树中），
  合并前主对话需比对两条打卡线是否是同一功能的不同实现，避免重复/覆盖。

## 4. 本会话已做的本地处置（未推送）

- 在 `stage-4-device-qa-fixes` 上创建了合并提交
  `6135812` "Merge remote main (initial README) into stage-4 device QA line…"，
  命令为 `git merge origin/main --allow-unrelated-histories`，效果仅是把那份 README.md
  并入我们的树。**已随 QA 分支推送到 `origin/stage-4-device-qa-fixes`；未动 origin/main**。
- 这样做的收益：该分支现已包含 `1567401` 作为祖先，主对话把 main 指过来时可以是
  **快进合并**，不需要 force。如果主对话不认可这个合并，可把分支 reset 回 `3d9a4ca`
  后用 `--force-with-lease` 重写该 QA 分支（只影响分支，不影响 main）。

## 5. 未完成 / 遗留问题

- 笔记展示栏新版面（第二轮改动）待用户真机走查确认。
- F4-03 验收记录第 6 节的 SAF 备份导出往返真机走查仍开放
  （`docs/handoff/2026-10-13-f4-03-closeout-report.md`）。
- 合并 main 后下一任务按计划是 **F4-04 配对认证、备份密码与恢复**，再往后 F3-03 拍照 OCR。

## 6. 主对话接手须知（建议收口路径）

1. `git fetch origin`，复核第 3 节事实：`git log --oneline origin/main`（应只有 1567401）、
   `git cat-file -t 132f507`（本地应存在）、`git merge-base 132f507 <我们主线>`（应为 f49a8a8）。
2. 决定两件事（需要用户拍板的地方）：
   - a. 孤儿打卡线 `132f507` 的 12 提交是否还有独占价值？若其功能已被主线覆盖，可只保留引用（打 tag 存档）；
   - b. README.md 以哪份为准（1567401 那份内容很薄，可考虑替换为正式项目 README）。
3. 合并操作（非破坏式，全程无 force）：远端 `stage-4-device-qa-fixes` 已含合并提交
   `6135812`（其第二父就是 `1567401`），所以把 main 指过来天然是快进：
   - `git checkout -b main origin/main`（或 `git fetch origin main:main` 式创建本地 main）→
     `git merge --ff-only stage-4-device-qa-fixes` → `git push origin main`。
   - 若主对话更想重做合并：`git reset --hard 3d9a4ca` 回到不含 README 的 QA 头，自行
     `git merge origin/main --allow-unrelated-histories`；推导 main 时用普通 merge 亦可。
   - 推送 main **不要 force**：若被拒说明远端 main 又变了，先重新 fetch 核查拓扑。
4. 合并后重跑门禁：`assembleDebug` → `testDebugUnitTest`（应 1118 全绿）→ `compileDebugAndroidTestKotlin`。
5. 环境坑（本会话已验证）：
   - 网络：git 需走代理 `-c http.proxy=http://127.0.0.1:3067 -c https.proxy=http://127.0.0.1:3067`；
   - 提交身份：`-c user.name=steveliuyan -c user.email=2021250388@qq.com`；
   - Gradle：`C:/Users/steveliuyan/.gradle/wrapper/dists/gradle-8.13-bin/5xuhj0ry160q40clulazy9h7d/gradle-8.13/bin/gradle.bat`；
   - Kokoro 边界测试依赖已构建 APK，**必须先 assembleDebug 再跑单测**；
   - 真机：adb `C:/adb/platform-tools/adb.exe`，设备 bf353dda（Redmi/MIUI），
     启动用 `monkey -p com.example.englishlearning -c android.intent.category.LAUNCHER 1`（MIUI 禁止直接 am start 非导出 Activity）。

## 7. 用户确认过的决策

- 三个真机缺陷"都修，单独走一轮"；发音复测已通过。
- 笔记交互最终版："例句下方展示栏 + 弹窗管理（添加/删除收进弹窗，去掉入口 pill）"——第二轮已实现，推翻第一轮的"入口按钮+弹窗"设计。
- 合并方式：本会话不直接推 main，改为主对话经交接文档处理（用户原话见第 1 节）。
