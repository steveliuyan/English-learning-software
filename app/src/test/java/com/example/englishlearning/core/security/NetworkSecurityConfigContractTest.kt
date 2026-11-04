package com.example.englishlearning.core.security

import java.io.File
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test

/**
 * 网络信任基线的源码级契约（决策 2026-11-05 D-3 / Spec 00 新增 AC0-06）。
 *
 * **先说清楚这枚配置不是新防线**：`targetSdk 36` 下「禁明文 + 只信系统根」本来就是平台默认，
 * 本批落地它没有改变 release 包的任何一条出站行为。它的价值在三处，全部是**防回归**：
 *  1. 把 AGENTS.md 那条「仅支持 HTTPS 公网 Endpoint」从口头要求变成机器可判的文件；
 *  2. 给 debug 包一个**受控**的抓包口（`debug-overrides` 只额外信任用户根，且只对 debuggable 包生效），
 *     替代「为了让 Charles 能抓包就顺手把 release 也放开」这条常见的滑坡；
 *  3. 挡住未来任何一次「顺手加一行 cleartextTrafficPermitted="true" 或加个 domain-config」——
 *     那类改动今天会在这里变红，而不是等到某次真机抓包才发现明文流量能走通了。
 *
 * 判据用纯函数 [networkSecurityViolations] 表达，并且**同时喂给真实文件与一份故意违规的合成配置**：
 * 本仓源码契约反复踩过「正则写错→永远绿」这一坑，must-flag 那一条就是「判据真的抓得住」的证据。
 */
class NetworkSecurityConfigContractTest {
    private val debuggableTrue = "android:debuggable=\"true\""
    private val cleartextTrafficAttr = "android:usesCleartextTraffic=\"true\""

    @Test
    fun theManifestMountsTheNetworkSecurityConfig() {
        val manifest = moduleFile("src/main/AndroidManifest.xml").readText()

        assertTrue(
            Regex("""android:networkSecurityConfig\s*=\s*"@xml/network_security_config"""").containsMatchIn(manifest),
            "AndroidManifest 的 <application> 没挂 @xml/network_security_config：配置写了但不生效，" +
                "比没写更糟——它会让人以为基线已经被钉住了",
        )
        assertTrue(
            !manifest.contains(cleartextTrafficAttr),
            "Manifest 里出现了 usesCleartextTraffic=true：它会整体推翻 network_security_config 的明文禁令",
        )
        // debug-overrides 的那道受控开口只对 debuggable 包生效；若有人把 debuggable 写进主干，
        // 「用户根只在调试包被信任」这句承诺就当场失效——这条判据守的正是那句承诺。
        assertTrue(
            !manifest.contains(debuggableTrue),
            "Manifest 里出现了 android:debuggable=\"true\"：用户根 CA 的调试豁免会顺带进入 release 包",
        )
        val buildScript = moduleFile("build.gradle.kts").readText()
        val releaseBlock = Regex("""getByName\("release"\)\s*\{([\s\S]*?)\n {8}\}""").find(buildScript)?.groupValues?.get(1)
        assertTrue(releaseBlock != null, "app/build.gradle.kts 里找不到 release buildType 块：本判据将空转，必须先修锚点")
        assertTrue(
            "isDebuggable" !in releaseBlock!!,
            "release buildType 动了 isDebuggable：debug-overrides 的用户根信任会跟到发布包",
        )
    }

    @Test
    fun theShippedConfigSatisfiesEveryRule() {
        val config = moduleFile(CONFIG_PATH).readText()

        assertTrue(config.isNotBlank(), "$CONFIG_PATH 是空文件：契约无声通过")
        assertTrue(
            networkSecurityViolations(config).isEmpty(),
            "网络信任基线不合规：${networkSecurityViolations(config).joinToString(" / ")}",
        )
    }

    @Test
    fun theJudgementsFlagAPlantedViolationInsteadOfQuietlyPassing() {
        val planted = """
            <?xml version="1.0" encoding="utf-8"?>
            <network-security-config>
                <base-config cleartextTrafficPermitted="true">
                    <trust-anchors>
                        <certificates src="user" />
                        <certificates src="system" />
                    </trust-anchors>
                </base-config>
                <domain-config cleartextTrafficPermitted="true">
                    <domain includeSubdomains="true">api.example.com</domain>
                </domain-config>
            </network-security-config>
        """.trimIndent()

        val codes = networkSecurityViolations(planted).map { it.substringBefore(":") }.toSet()

        listOf(
            R_BASE_CLEARTEXT,
            R_BASE_ANCHORS,
            R_CLEARTEXT,
            R_NO_DEBUG_OVERRIDES,
            R_USER_OUTSIDE_DEBUG,
            R_DOMAIN_CONFIG,
        ).forEach {
            assertTrue(codes.contains(it), "判据 $it 对着明显的违规样本没反应，本契约已成空转：$codes")
        }

        // 另一种隐形失效：只写了根节点、没写策略（平台默认仍在，但本仓声明的基线消失了）。
        assertTrue(
            networkSecurityViolations("<network-security-config/>").any { it.startsWith(R_MISSING_BASE_CONFIG) },
            "缺 <base-config> 必须判红",
        )
        assertTrue(
            networkSecurityViolations("<oops/>").any { it.startsWith(R_MISSING_ROOT) },
            "连根节点都没有时必须判红，而不是返回空清单",
        )
    }

    @Test
    fun commentsAreNotJudgedAsConfiguration() {
        // 本文件正文里合法出现过 cleartextTrafficPermitted="true" 与 src="user" 这两个字面量——
        // 它们都在解释「为什么不许这么写」的注释里。判据若连注释都算，第一份诚实的文档就会把自己判红，
        // 而现实里文档一旦被判红，人的反应是把文档删掉，不是把配置改对。
        val documented = """
            <?xml version="1.0" encoding="utf-8"?>
            <network-security-config>
                <!-- 不要写成 cleartextTrafficPermitted="true"，也不要把 src="user" 放进 base-config -->
                <base-config cleartextTrafficPermitted="false">
                    <trust-anchors>
                        <!-- 系统根：src="user" 只允许出现在 debug-overrides -->
                        <certificates src="system" />
                    </trust-anchors>
                </base-config>
                <debug-overrides>
                    <trust-anchors>
                        <certificates src="user" />
                        <certificates src="system" />
                    </trust-anchors>
                </debug-overrides>
            </network-security-config>
        """.trimIndent()

        assertTrue(
            networkSecurityViolations(documented).isEmpty(),
            "带解释性注释的合规配置被判红：${networkSecurityViolations(documented).joinToString(" / ")}",
        )
        // 反向核验：剥注释不是把判据整体调哑——同一段文本去掉注释里的禁令字样后，真违规照样要红。
        val realLeak = documented.replace(
            "<base-config cleartextTrafficPermitted=\"false\">",
            "<base-config cleartextTrafficPermitted=\"true\">",
        )
        assertTrue(
            networkSecurityViolations(realLeak).any { it.startsWith(R_CLEARTEXT) },
            "剥注释后真违规没被判红，说明判据读错了对象",
        )
    }

    private fun duggable(): String = debuggableTrue

    private fun moduleFile(relativePath: String): File =
        listOf(File(relativePath), File("app/$relativePath")).firstOrNull { it.isFile }
            ?: error("找不到 $relativePath（JVM 工作目录 ${File(".").absolutePath}）")
}

private const val CONFIG_PATH = "src/main/res/xml/network_security_config.xml"

/** XML 注释整段（惰性到最近的闭合序列）：判据读的是生效的配置，不是解释它的文档。 */
private val XML_COMMENTS = Regex("""<!--[\s\S]*?-->""")

/** 判据编号：合成夹具与真实文件共用同一组码，改判据时必须先改这里。 */
private const val R_MISSING_ROOT = "R0-缺根节点"
private const val R_MISSING_BASE_CONFIG = "R1-缺base-config"
private const val R_BASE_CLEARTEXT = "R2-未显式禁明文"
private const val R_BASE_NO_SYSTEM_ANCHOR = "R3-未显式列系统根"
private const val R_BASE_ANCHORS = "R4-base-config里出现用户根"
private const val R_CLEARTEXT = "R5-任何位置允许明文"
private const val R_NO_DEBUG_OVERRIDES = "R6-缺debug-overrides"
private const val R_USER_OUTSIDE_DEBUG = "R7-用户根越出debug-overrides"
private const val R_DOMAIN_CONFIG = "R8-出现per-domain配置"

private const val CLEARTXT_FALSE = "cleartextTrafficPermitted=\"false\""
private const val USER_ANCHOR = "src=\"user\""
private const val CLEARTXT_TRUE = "cleartextTrafficPermitted=\"true\""

/**
 * 返回 `config` 违反的判据清单（空 = 合规）。每条以判据码开头，调用方按码断言，
 * 这样「判据失效」与「配置违规」在失败消息里是两种可读的形状。
 *
 * **先剥 XML 注释再判**：本文件的注释里合法出现过被禁字面量（它解释的正是「为什么不许这么写」），
 * 不剥注释的判据会把第一份诚实的文档判红；`commentsAreNotJudgedAsConfiguration` 双向钉住这一点。
 */
private fun networkSecurityViolations(rawConfig: String): List<String> {
    val config = XML_COMMENTS.replace(rawConfig, " ")
    val out = mutableListOf<String>()
    if (!config.contains("<network-security-config")) {
        return listOf("$R_MISSING_ROOT:没有 <network-security-config> 根节点")
    }

    val baseBlock = Regex("""<base-config\b[^>]*>[\s\S]*?</base-config>""").find(config)?.value
    val baseTag = Regex("""<base-config\b[^>]*>""").find(config)?.value.orEmpty()
    if (baseBlock == null) {
        out += "$R_MISSING_BASE_CONFIG:必须有显式的 <base-config> 段，平台默认不算本仓声明的基线"
    } else {
        if (!baseTag.contains(CLEARTXT_FALSE)) {
            out += "$R_BASE_CLEARTEXT:<base-config> 必须逐字写 cleartextTrafficPermitted=false"
        }
        if (!Regex("""<certificates\s+src\s*=\s*"system"\s*/>""").containsMatchIn(baseBlock)) {
            out += "$R_BASE_NO_SYSTEM_ANCHOR:信任锚要显式列出系统根，隐式默认挡不住「顺手加一行 user」"
        }
        if (baseBlock.contains(USER_ANCHOR)) {
            out += "$R_BASE_ANCHORS:用户根 CA 不得出现在 release 也生效的 base-config 里"
        }
    }

    if (config.contains(CLEARTXT_TRUE)) {
        out += "$R_CLEARTEXT:任何一处 cleartextTrafficPermitted=true 都推翻了 AGENTS.md 的仅 HTTPS 要求"
    }

    val debugBlock = Regex("""<debug-overrides\b[^>]*>[\s\S]*?</debug-overrides>""").find(config)?.value
    if (debugBlock == null) {
        out += "$R_NO_DEBUG_OVERRIDES:缺 debug-overrides——受控的调试抓包口没了，下一步就是有人放开 release"
    } else if (!debugBlock.contains(USER_ANCHOR)) {
        out += "$R_NO_DEBUG_OVERRIDES:debug-overrides 里没有用户根，抓包链路仍然走不通"
    }

    val userOccurrences = Regex(Regex.escape(USER_ANCHOR)).findAll(config).count()
    val userInDebug = Regex(Regex.escape(USER_ANCHOR)).findAll(debugBlock.orEmpty()).count()
    if (userOccurrences != userInDebug) {
        out += "$R_USER_OUTSIDE_DEBUG:用户根出现在 debug-overrides 之外（总 $userOccurrences 处 / 调试段内 $userInDebug 处）"
    }

    if (config.contains("<domain-config") || config.contains("<domain-client-config")) {
        out += "$R_DOMAIN_CONFIG:per-domain 例外需要单独的决策，本批只允许全站基线 + 调试豁免"
    }

    return out
}
