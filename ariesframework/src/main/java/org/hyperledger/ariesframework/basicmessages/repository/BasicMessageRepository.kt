package org.hyperledger.ariesframework.basicmessages.repository

import org.hyperledger.ariesframework.agent.Agent
import org.hyperledger.ariesframework.storage.Repository

class BasicMessageRepository(agent: Agent) : Repository<BasicMessageRecord>(BasicMessageRecord::class, agent) {

    suspend fun findByThreadAndConnectionId(threadId: String, connectionId: String?): BasicMessageRecord? {
        return if (connectionId != null) {
            findSingleByQuery("{\"threadId\": \"$threadId\", \"connectionId\": \"$connectionId\"}")
        } else {
            findSingleByQuery("{\"threadId\": \"$threadId\"}")
        }
    }

    suspend fun getByThreadAndConnectionId(threadId: String, connectionId: String?): BasicMessageRecord {
        return if (connectionId != null) {
            getSingleByQuery("{\"threadId\": \"$threadId\", \"connectionId\": \"$connectionId\"}")
        } else {
            getSingleByQuery("{\"threadId\": \"$threadId\"}")
        }
    }
}