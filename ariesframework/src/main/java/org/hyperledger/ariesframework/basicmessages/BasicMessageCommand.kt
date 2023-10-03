package org.hyperledger.ariesframework.basicmessages

import org.hyperledger.ariesframework.agent.Agent
import org.hyperledger.ariesframework.agent.AgentEvents
import org.hyperledger.ariesframework.agent.Dispatcher
import org.hyperledger.ariesframework.agent.MessageSerializer
import org.hyperledger.ariesframework.OutboundMessage
import org.hyperledger.ariesframework.agent.MessageSender
import org.hyperledger.ariesframework.connection.messages.ConnectionInvitationMessage
import org.hyperledger.ariesframework.connection.models.ConnectionState
import org.hyperledger.ariesframework.connection.repository.ConnectionRecord
import org.hyperledger.ariesframework.basicmessages.handlers.BasicMessageHandler
import org.hyperledger.ariesframework.basicmessages.messages.BasicMessage
import org.hyperledger.ariesframework.basicmessages.BasicMessageService
import org.hyperledger.ariesframework.basicmessages.repository.BasicMessageRecord
import org.slf4j.LoggerFactory

class BasicMessageCommand (val agent: Agent, private val dispatcher: Dispatcher){
    private val logger = LoggerFactory.getLogger(BasicMessageCommand::class.java)
    init {
        registerHandlers(dispatcher)
        registerMessages()
    }

    private fun registerHandlers(dispatcher: Dispatcher) {
        dispatcher.registerHandler(BasicMessageHandler(agent))

    }

    private fun registerMessages(){
        MessageSerializer.registerMessage(BasicMessage.type, BasicMessage::class)

    }

    /**
     * Sends a message to a specified connection.
     *
     * @param connectionId the ID of the connection to which the message will be sent.
     * @param message the message content to be sent.
     * @param parentThreadId the ID of the parent thread, nullable.
     * @return the created BasicMessageRecord corresponding to the sent message.
     * @throws IllegalArgumentException if connectionId or message is blank.
     * @throws MessageSendingException if an error occurs while sending the message.
     */
    suspend fun sendMessage(
        connectionId: String,
        message: String,
        parentThreadId: String? = null
    ): BasicMessageRecord {
        require(connectionId.isNotBlank()) { "Connection ID cannot be blank" }
        require(message.isNotBlank()) { "Message cannot be blank" }

        try {
            val connection = agent.connectionRepository.getById(connectionId)
                ?: throw IllegalArgumentException("Connection not found with ID: $connectionId")

            val (basicMessage, basicMessageRecord) = agent.basicMessageService.createMessage(message, connection, parentThreadId)

            agent.messageSender.send(OutboundMessage(basicMessage, connection))

            return basicMessageRecord
        } catch (e: Exception) {
            logger.error("Error sending message. ConnectionId: $connectionId, Message: $message", e)
            throw RuntimeException("Error occurred while sending message", e)
        }
    }





}