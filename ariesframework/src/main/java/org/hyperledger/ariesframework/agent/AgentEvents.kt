package org.hyperledger.ariesframework.agent

import org.hyperledger.ariesframework.basicmessages.repository.BasicMessageRecord
import org.hyperledger.ariesframework.connection.repository.ConnectionRecord
import org.hyperledger.ariesframework.credentials.repository.CredentialExchangeRecord
import org.hyperledger.ariesframework.oob.repository.OutOfBandRecord
import org.hyperledger.ariesframework.proofs.repository.ProofExchangeRecord
import org.hyperledger.ariesframework.routing.repository.MediationRecord

sealed interface AgentEvents {
    class ConnectionEvent(val record: ConnectionRecord) : AgentEvents
    class MediationEvent(val record: MediationRecord) : AgentEvents
    class OutOfBandEvent(val record: OutOfBandRecord) : AgentEvents
    class CredentialEvent(val record: CredentialExchangeRecord) : AgentEvents
    class ProofEvent(val record: ProofExchangeRecord) : AgentEvents
    class BasicMessageEvent( val record: BasicMessageRecord) : AgentEvents
}
