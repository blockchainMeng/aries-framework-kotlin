package org.hyperledger.ariesframework.basicmessages.repository

import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.Serializable
import org.hyperledger.ariesframework.Tags
import org.hyperledger.ariesframework.basicmessages.BasicMessageRole
import org.hyperledger.ariesframework.storage.BaseRecord



@Serializable
data class BasicMessageRecord(
    @EncodeDefault
    override var id: String = generateId(),
    override var _tags: Tags? = null,
    @EncodeDefault
    override val createdAt: Instant = Clock.System.now(),
    override var updatedAt: Instant? = null,

    var connectionId: String,
    var role: BasicMessageRole,
    var threadId: String? = null,
    var parentThreadId: String? = null,
    var content: String,
    var sentTime: String
) : BaseRecord() {
    override fun getTags(): Tags {
        val tags = (_tags ?: mutableMapOf()).toMutableMap()

        tags["connectionId"] = connectionId
        tags["role"] = role.name
        threadId?.let { tags["threadId"] = it }
        parentThreadId?.let { tags["parentThreadId"] = it }

        return tags
    }
}
