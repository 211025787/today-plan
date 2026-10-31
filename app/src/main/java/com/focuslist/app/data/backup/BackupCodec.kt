package com.focuslist.app.data.backup

import com.google.gson.GsonBuilder
import com.google.gson.JsonSyntaxException

/**
 * 备份文件的 JSON 编解码器（纯 Kotlin，不依赖 Android 框架，便于单元测试）。
 *
 * 备份文件是单一 JSON 对象，字段与 [BackupPayload] 一一对应，
 * 采用可读的缩进格式，方便用户直接用文本编辑器查看。
 */
object BackupCodec {

    /** 缩进两格的 Gson 实例 */
    private val gson = GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create()

    /**
     * 将备份内容序列化为 JSON 文本。
     *
     * @param payload 备份内容
     * @return 缩进格式的 JSON 字符串
     */
    fun encode(payload: BackupPayload): String = gson.toJson(payload)

    /**
     * 解析备份 JSON 文本。
     *
     * @param json 备份文件内容
     * @return 解析出的备份内容
     * @throws IllegalArgumentException JSON 非法、结构版本不支持或内容为空
     */
    fun decode(json: String): BackupPayload {
        val payload = try {
            gson.fromJson(json, BackupPayload::class.java)
        } catch (e: JsonSyntaxException) {
            throw IllegalArgumentException("不是有效的备份文件", e)
        } ?: throw IllegalArgumentException("备份内容为空")
        if (payload.schemaVersion <= 0) {
            throw IllegalArgumentException("无法识别的备份格式")
        }
        if (payload.schemaVersion > BACKUP_SCHEMA_VERSION) {
            throw IllegalArgumentException("备份来自更高版本（v${payload.schemaVersion}），请升级应用后再导入")
        }
        return payload
    }
}
