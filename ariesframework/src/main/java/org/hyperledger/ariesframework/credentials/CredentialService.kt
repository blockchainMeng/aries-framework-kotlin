package org.hyperledger.ariesframework.credentials

import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.future.await
import kotlinx.coroutines.launch
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.hyperledger.ariesframework.AckStatus
import org.hyperledger.ariesframework.InboundMessageContext
import org.hyperledger.ariesframework.agent.Agent
import org.hyperledger.ariesframework.agent.AgentEvents
import org.hyperledger.ariesframework.agent.MessageSerializer
import org.hyperledger.ariesframework.agent.decorators.Attachment
import org.hyperledger.ariesframework.agent.decorators.ThreadDecorator
import org.hyperledger.ariesframework.credentials.messages.CredentialAckMessage
import org.hyperledger.ariesframework.credentials.messages.IssueCredentialMessage
import org.hyperledger.ariesframework.credentials.messages.OfferCredentialMessage
import org.hyperledger.ariesframework.credentials.messages.ProposeCredentialMessage
import org.hyperledger.ariesframework.credentials.messages.RequestCredentialMessage
import org.hyperledger.ariesframework.credentials.models.AcceptCredentialOptions
import org.hyperledger.ariesframework.credentials.models.AcceptOfferOptions
import org.hyperledger.ariesframework.credentials.models.AcceptRequestOptions
import org.hyperledger.ariesframework.credentials.models.CreateOfferOptions
import org.hyperledger.ariesframework.credentials.models.CreateProposalOptions
import org.hyperledger.ariesframework.credentials.models.CredentialPreview
import org.hyperledger.ariesframework.credentials.models.CredentialState
import org.hyperledger.ariesframework.credentials.models.CredentialValues
import org.hyperledger.ariesframework.credentials.models.IndyCredential
import org.hyperledger.ariesframework.credentials.repository.CredentialExchangeRecord
import org.hyperledger.ariesframework.credentials.repository.CredentialRecordBinding
import org.hyperledger.ariesframework.storage.BaseRecord
import org.hyperledger.ariesframework.storage.DidCommMessageRole
import org.hyperledger.indy.sdk.anoncreds.Anoncreds
import org.slf4j.LoggerFactory

class CredentialService(val agent: Agent) {
    private val logger = LoggerFactory.getLogger(CredentialService::class.java)
    private val ledgerService = agent.ledgerService
    private val credentialRepository = agent.credentialRepository

    /**
     * Create a ``ProposeCredentialMessage`` not bound to an existing credential record.
     *
     * @param options options for the proposal.
     * @return proposal message and associated credential record.
     */
    suspend fun createProposal(options: CreateProposalOptions): Pair<ProposeCredentialMessage, CredentialExchangeRecord> {
        val credentialRecord = CredentialExchangeRecord(
            connectionId = options.connection.id,
            threadId = BaseRecord.generateId(),
            state = CredentialState.ProposalSent,
            autoAcceptCredential = options.autoAcceptCredential,
            protocolVersion = "v1",
        )

        val message = ProposeCredentialMessage(
            options.comment,
            options.credentialPreview,
            options.schemaIssuerDid,
            options.schemaId,
            options.schemaName,
            options.schemaVersion,
            options.credentialDefinitionId,
            options.issuerDid,
        )
        message.id = credentialRecord.threadId

        agent.didCommMessageRepository.saveAgentMessage(DidCommMessageRole.Sender, message, credentialRecord.id)

        credentialRepository.save(credentialRecord)
        agent.eventBus.publish(AgentEvents.CredentialEvent(credentialRecord.copy()))

        return Pair(message, credentialRecord)
    }

    /**
     * Create a ``OfferCredentialMessage`` not bound to an existing credential record.
     *
     * @param options options for the offer.
     * @return offer message and associated credential record.
     */
    suspend fun createOffer(options: CreateOfferOptions): Pair<OfferCredentialMessage, CredentialExchangeRecord> {
        if (options.connection == null) {
            logger.info("Creating credential offer without connection. This should be used for out-of-band request message with handshake.")
        }
        val credentialRecord = CredentialExchangeRecord(
            connectionId = options.connection?.id ?: "connectionless-offer",
            threadId = BaseRecord.generateId(),
            state = CredentialState.OfferSent,
            autoAcceptCredential = options.autoAcceptCredential,
            protocolVersion = "v1",
        )

        val offer = Anoncreds.issuerCreateCredentialOffer(agent.wallet.indyWallet, options.credentialDefinitionId).await()
        val attachment = Attachment.fromData(offer.toByteArray(), OfferCredentialMessage.INDY_CREDENTIAL_OFFER_ATTACHMENT_ID)
        val credentialPreview = CredentialPreview(options.attributes)

        val message = OfferCredentialMessage(
            options.comment,
            credentialPreview,
            listOf(attachment),
        )
        message.id = credentialRecord.threadId

        agent.didCommMessageRepository.saveAgentMessage(DidCommMessageRole.Sender, message, credentialRecord.id)

        credentialRecord.credentialAttributes = options.attributes
        credentialRepository.save(credentialRecord)
        agent.eventBus.publish(AgentEvents.CredentialEvent(credentialRecord.copy()))

        return Pair(message, credentialRecord)
    }

    /**
     * Process a received ``OfferCredentialMessage``. This will not accept the credential offer
     * or send a credential request. It will only create a new credential record with
     * the information from the credential offer message. Use ``createRequest(options:)``
     * after calling this method to create a credential request.
     *
     * @param messageContext message context containing the offer message.
     * @return credential record associated with the credential offer message.
     */
    suspend fun processOffer(messageContext: InboundMessageContext): CredentialExchangeRecord {
        val offerMessage = MessageSerializer.decodeFromString(messageContext.plaintextMessage) as OfferCredentialMessage

        require(offerMessage.getOfferAttachmentById(OfferCredentialMessage.INDY_CREDENTIAL_OFFER_ATTACHMENT_ID) != null) {
            "Indy attachment with id ${OfferCredentialMessage.INDY_CREDENTIAL_OFFER_ATTACHMENT_ID} not found in offer message"
        }

        var credentialRecord = credentialRepository.findByThreadAndConnectionId(offerMessage.threadId, messageContext.connection?.id)
        if (credentialRecord != null) {
            agent.didCommMessageRepository.saveAgentMessage(DidCommMessageRole.Receiver, offerMessage, credentialRecord.id)
            updateState(credentialRecord, CredentialState.OfferReceived)
        } else {
            val connection = messageContext.assertReadyConnection()
            credentialRecord = CredentialExchangeRecord(
                connectionId = connection.id,
                threadId = offerMessage.id,
                state = CredentialState.OfferReceived,
                protocolVersion = "v1",
            )

            agent.didCommMessageRepository.saveAgentMessage(DidCommMessageRole.Receiver, offerMessage, credentialRecord.id)
            credentialRepository.save(credentialRecord)
            agent.eventBus.publish(AgentEvents.CredentialEvent(credentialRecord.copy()))
        }

        return credentialRecord
    }

    /**
     * Create a ``RequestCredentialMessage`` as response to a received credential offer.
     *
     * @param options options for the request.
     * @return request message.
     */
    suspend fun createRequest(options: AcceptOfferOptions): RequestCredentialMessage {
        val credentialRecord = credentialRepository.getById(options.credentialRecordId)
        credentialRecord.assertProtocolVersion("v1")
        credentialRecord.assertState(CredentialState.OfferReceived)

        val offerMessageJson = agent.didCommMessageRepository.getAgentMessage(credentialRecord.id, OfferCredentialMessage.type)
        val offerMessage = MessageSerializer.decodeFromString(offerMessageJson) as OfferCredentialMessage
        val offerAttachment = offerMessage.getOfferAttachmentById(OfferCredentialMessage.INDY_CREDENTIAL_OFFER_ATTACHMENT_ID)
        checkNotNull(offerAttachment) {
            "Indy attachment with id ${OfferCredentialMessage.INDY_CREDENTIAL_OFFER_ATTACHMENT_ID} not found in offer message"
        }

        val holderDid = options.holderDid ?: getHolderDid(credentialRecord)

        val credentialOfferJson = offerMessage.getCredentialOffer()
        val credentialOffer = Json.decodeFromString<JsonObject>(credentialOfferJson)
        val credentialDefinition = ledgerService.getCredentialDefinition(
            credentialOffer["cred_def_id"]?.jsonPrimitive?.content ?: "unknown_id",
        )

        val credentialRequest = Anoncreds.proverCreateCredentialReq(
            agent.wallet.indyWallet,
            holderDid,
            credentialOfferJson,
            credentialDefinition,
            agent.wallet.masterSecretId,
        ).await()

        credentialRecord.indyRequestMetadata = credentialRequest.credentialRequestMetadataJson
        credentialRecord.credentialDefinitionId = credentialOffer["cred_def_id"]?.jsonPrimitive?.content

        val attachment = Attachment.fromData(
            credentialRequest.credentialRequestJson.toByteArray(),
            RequestCredentialMessage.INDY_CREDENTIAL_REQUEST_ATTACHMENT_ID,
        )
        val requestMessage = RequestCredentialMessage(
            options.comment,
            listOf(attachment),
        )
        requestMessage.thread = ThreadDecorator(credentialRecord.threadId)

        credentialRecord.credentialAttributes = offerMessage.credentialPreview.attributes
        credentialRecord.autoAcceptCredential = options.autoAcceptCredential ?: credentialRecord.autoAcceptCredential

        agent.didCommMessageRepository.saveAgentMessage(DidCommMessageRole.Sender, requestMessage, credentialRecord.id)
        updateState(credentialRecord, CredentialState.RequestSent)

        return requestMessage
    }

    /**
     * Process a received ``RequestCredentialMessage``. This will not accept the credential request
     * or send a credential. It will only update the existing credential record with
     * the information from the credential request message. Use ``createCredential(options:)``
     * after calling this method to create a credential.
     *
     * @param messageContext message context containing the request message.
     * @return credential record associated with the credential request message.
     */
    suspend fun processRequest(messageContext: InboundMessageContext): CredentialExchangeRecord {
        val requestMessage = MessageSerializer.decodeFromString(messageContext.plaintextMessage) as RequestCredentialMessage

        require(requestMessage.getRequestAttachmentById(RequestCredentialMessage.INDY_CREDENTIAL_REQUEST_ATTACHMENT_ID) != null) {
            "Indy attachment with id ${RequestCredentialMessage.INDY_CREDENTIAL_REQUEST_ATTACHMENT_ID} not found in request message"
        }

        var credentialRecord = credentialRepository.getByThreadAndConnectionId(
            requestMessage.threadId,
            messageContext.connection?.id,
        )

        // The credential offer may have been a connectionless-offer.
        val connection = messageContext.assertReadyConnection()
        credentialRecord.connectionId = connection.id

        agent.didCommMessageRepository.saveAgentMessage(DidCommMessageRole.Receiver, requestMessage, credentialRecord.id)
        updateState(credentialRecord, CredentialState.RequestReceived)

        return credentialRecord
    }

    /**
     * Create a ``IssueCredentialMessage`` as response to a received credential request.
     *
     * @param options options for the credential issueance.
     * @return credential message.
     */
    suspend fun createCredential(options: AcceptRequestOptions): IssueCredentialMessage {
        var credentialRecord = credentialRepository.getById(options.credentialRecordId)
        credentialRecord.assertProtocolVersion("v1")
        credentialRecord.assertState(CredentialState.RequestReceived)

        val offerMessageJson = agent.didCommMessageRepository.getAgentMessage(credentialRecord.id, OfferCredentialMessage.type)
        val offerMessage = MessageSerializer.decodeFromString(offerMessageJson) as OfferCredentialMessage
        val requestMessageJson = agent.didCommMessageRepository.getAgentMessage(credentialRecord.id, RequestCredentialMessage.type)
        val requestMessage = MessageSerializer.decodeFromString(requestMessageJson) as RequestCredentialMessage

        val offerAttachment = offerMessage.getOfferAttachmentById(OfferCredentialMessage.INDY_CREDENTIAL_OFFER_ATTACHMENT_ID)
        val requestAttachment = requestMessage.getRequestAttachmentById(RequestCredentialMessage.INDY_CREDENTIAL_REQUEST_ATTACHMENT_ID)
        check(offerAttachment != null && requestAttachment != null) {
            "Missing data payload in offer or request attachment in credential Record ${credentialRecord.id}"
        }

        val offer = offerAttachment.getDataAsString()
        val request = requestAttachment.getDataAsString()

        val credential = Anoncreds.issuerCreateCredential(
            agent.wallet.indyWallet,
            offer,
            request,
            CredentialValues.convertAttributesToValues(credentialRecord.credentialAttributes!!),
            null,
            0,
        ).await()

        val attachment = Attachment.fromData(
            credential.credentialJson.toByteArray(),
            IssueCredentialMessage.INDY_CREDENTIAL_ATTACHMENT_ID,
        )
        val issueMessage = IssueCredentialMessage(
            options.comment,
            listOf(attachment),
        )
        issueMessage.thread = ThreadDecorator(credentialRecord.threadId)

        agent.didCommMessageRepository.saveAgentMessage(DidCommMessageRole.Sender, issueMessage, credentialRecord.id)
        credentialRecord.autoAcceptCredential = options.autoAcceptCredential ?: credentialRecord.autoAcceptCredential
        updateState(credentialRecord, CredentialState.CredentialIssued)

        return issueMessage
    }

    /**
     * Process a received ``IssueCredentialMessage``. This will store the credential, but not accept it yet.
     * Use ``createAck(options:)`` after calling this method to accept the credential and create an ack message.
     *
     * @param messageContext message context containing the credential message.
     * @return credential record associated with the credential message.
     */
    suspend fun processCredential(messageContext: InboundMessageContext): CredentialExchangeRecord {
        val issueMessage = MessageSerializer.decodeFromString(messageContext.plaintextMessage) as IssueCredentialMessage

        val issueAttachment = issueMessage.getCredentialAttachmentById(IssueCredentialMessage.INDY_CREDENTIAL_ATTACHMENT_ID)
        check(issueAttachment != null) {
            "Indy attachment with id ${IssueCredentialMessage.INDY_CREDENTIAL_ATTACHMENT_ID} not found in issue message"
        }

        var credentialRecord = credentialRepository.getByThreadAndConnectionId(issueMessage.threadId, messageContext.connection?.id)
        val credential = issueAttachment.getDataAsString()
        logger.debug("Storing credential: $credential")
        val credentialInfo = Json { ignoreUnknownKeys = true }.decodeFromString<IndyCredential>(credential)
        val credentialDefinition = ledgerService.getCredentialDefinition(credentialInfo.credentialDefinitionId)
        val revocationRegistry = credentialInfo.revocationRegistryId?.let { ledgerService.getRevocationRegistryDefinition(it) }
        if (revocationRegistry != null) {
            GlobalScope.launch {
                agent.revocationService.downloadTails(revocationRegistry)
            }
        }

        val credentialId = Anoncreds.proverStoreCredential(
            agent.wallet.indyWallet,
            null,
            credentialRecord.indyRequestMetadata,
            credential,
            credentialDefinition,
            revocationRegistry,
        ).await()
        credentialRecord.credentials.add(CredentialRecordBinding("indy", credentialId!!))

        agent.didCommMessageRepository.saveAgentMessage(DidCommMessageRole.Receiver, issueMessage, credentialRecord.id)
        updateState(credentialRecord, CredentialState.CredentialReceived)

        return credentialRecord
    }

    /**
     * Create an ``CredentialAckMessage`` as response to a received credential.
     *
     * @param options options for the acknowledgement message.
     * @return credential acknowledgement message.
     */
    suspend fun createAck(options: AcceptCredentialOptions): CredentialAckMessage {
        var credentialRecord = credentialRepository.getById(options.credentialRecordId)
        credentialRecord.assertProtocolVersion("v1")
        credentialRecord.assertState(CredentialState.CredentialReceived)

        updateState(credentialRecord, CredentialState.Done)

        return CredentialAckMessage(credentialRecord.threadId, AckStatus.OK)
    }

    /**
     * Process a received ``CredentialAckMessage``.
     *
     * @param messageContext message context containing the credential acknowledgement message.
     * @return credential record associated with the credential acknowledgement message.
     */
    suspend fun processAck(messageContext: InboundMessageContext): CredentialExchangeRecord {
        val ackMessage = MessageSerializer.decodeFromString(messageContext.plaintextMessage) as CredentialAckMessage

        var credentialRecord = credentialRepository.getByThreadAndConnectionId(ackMessage.threadId, messageContext.connection?.id)
        updateState(credentialRecord, CredentialState.Done)

        return credentialRecord
    }

    private suspend fun getHolderDid(credentialRecord: CredentialExchangeRecord): String {
        val connection = agent.connectionRepository.getById(credentialRecord.connectionId)
        return connection.did
    }

    suspend fun updateState(credentialRecord: CredentialExchangeRecord, newState: CredentialState) {
        credentialRecord.state = newState
        credentialRepository.update(credentialRecord)
        agent.eventBus.publish(AgentEvents.CredentialEvent(credentialRecord.copy()))
    }
}
