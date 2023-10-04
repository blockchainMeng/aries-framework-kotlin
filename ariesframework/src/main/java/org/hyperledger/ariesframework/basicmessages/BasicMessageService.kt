package org.hyperledger.ariesframework.basicmessages


import org.hyperledger.ariesframework.agent.Agent
import org.hyperledger.ariesframework.agent.AgentEvents
import org.hyperledger.ariesframework.InboundMessageContext
import org.hyperledger.ariesframework.basicmessages.messages.BasicMessage
import org.hyperledger.ariesframework.basicmessages.repository.BasicMessageRecord
import org.hyperledger.ariesframework.connection.repository.ConnectionRecord

import org.slf4j.LoggerFactory


class BasicMessageService(val agent: Agent) {
    private val basicMessageRepository = agent.basicMessageRepository
    private val logger = LoggerFactory.getLogger(BasicMessageService::class.java)



    suspend fun createMessage(
        message: String,
        connectionRecord: ConnectionRecord,
        parentThreadId: String? = null
    ): Pair<BasicMessage, BasicMessageRecord> {
        val basicMessage = BasicMessage(message, parentThreadId)

        val basicMessageRecord = BasicMessageRecord(
            sentTime = basicMessage.sentTime.toString(),
            content = basicMessage.content,
            connectionId = connectionRecord.id,
            role = BasicMessageRole.Receiver, // Adjusted role to Receiver.
            threadId = basicMessage.threadId,
            parentThreadId = parentThreadId
        )

        basicMessageRepository.save(basicMessageRecord)
        // Adjusted the event publishing to match the TypeScript version more closely.
        agent.eventBus.publish(AgentEvents.BasicMessageEvent(basicMessageRecord))
        return basicMessage to basicMessageRecord
    }



    suspend fun save(messageContext: InboundMessageContext) {
        val message = messageContext.message as BasicMessage
        val connectionRecord = messageContext.connection!!

        val basicMessageRecord = BasicMessageRecord(
            sentTime =message.sentTime.toString(),
            content = message.content ,
            connectionId = connectionRecord.id,
            role=BasicMessageRole.Sender,
            threadId = message.threadId,
            parentThreadId = message.thread?.parentThreadId
        )

        basicMessageRepository.save(basicMessageRecord)
        agent.eventBus.publish(AgentEvents.BasicMessageEvent(basicMessageRecord))
    }



    suspend fun findById(basicMessageRecordId: String): BasicMessageRecord? {
        return basicMessageRepository.findById(basicMessageRecordId)
    }

    suspend fun getById(basicMessageRecordId: String): BasicMessageRecord {
        return basicMessageRepository.getById(basicMessageRecordId)
    }


    suspend fun deleteById(basicMessageRecordId: String) {
        val basicMessageRecord = getById(basicMessageRecordId)
        basicMessageRepository.delete(basicMessageRecord)
    }
}