package org.hyperledger.ariesframework.basicmessages.messages

import kotlinx.serialization.Serializable
import org.hyperledger.ariesframework.agent.AgentMessage

@Serializable
class BasicMessage(
    val content: String,
    val sentTime: String? = System.currentTimeMillis().toString(),
) : AgentMessage( generateId(), BasicMessage.type) {

    companion object {
        const val type = "https://didcomm.org/basicmessage/1.0/message"
    }

    //  Add other methods here if needed
}



