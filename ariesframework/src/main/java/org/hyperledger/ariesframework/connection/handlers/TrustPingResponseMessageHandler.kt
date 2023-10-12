package org.hyperledger.ariesframework.connection.handlers

import org.hyperledger.ariesframework.InboundMessageContext
import org.hyperledger.ariesframework.OutboundMessage
import org.hyperledger.ariesframework.agent.Agent
import org.hyperledger.ariesframework.agent.MessageHandler
import org.hyperledger.ariesframework.connection.ConnectionService
import org.hyperledger.ariesframework.connection.messages.TrustPingResponseMessage
import org.hyperledger.ariesframework.connection.models.ConnectionState

class TrustPingResponseMessageHandler (val agent: Agent) : MessageHandler{
    override val messageType = TrustPingResponseMessage.type

     override suspend fun handle(messageContext: InboundMessageContext) : OutboundMessage? {
         agent.connectionService.processPingResponse(messageContext)
         return null

    }





}