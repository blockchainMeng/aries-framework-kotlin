package org.hyperledger.ariesframework.connection.messages

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.hyperledger.ariesframework.agent.AgentMessage

@Serializable
class TrustPingResponseMessage(
    val comment: String? = null,
) : AgentMessage(generateId(), TrustPingResponseMessage.type) {
    companion object {
        const val type = "https://didcomm.org/trust_ping/1.0/ping_response"
    }
}
