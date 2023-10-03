package org.hyperledger.ariesframework.basicmessages.handlers
import org.hyperledger.ariesframework.InboundMessageContext
import org.hyperledger.ariesframework.OutboundMessage
import org.hyperledger.ariesframework.agent.Agent
import org.hyperledger.ariesframework.agent.MessageHandler
import org.hyperledger.ariesframework.basicmessages.messages.BasicMessage

class BasicMessageHandler(val agent: Agent) : MessageHandler {
    override val messageType = BasicMessage.type

    override suspend fun handle(messageContext: InboundMessageContext): OutboundMessage? {
        val connection = messageContext.assertReadyConnection()
        agent.basicMessageService.save(messageContext)
        return null
    }
}






