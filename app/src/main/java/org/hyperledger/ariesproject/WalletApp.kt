package org.hyperledger.ariesproject

import android.app.Application
import android.content.res.Configuration
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import org.hyperledger.ariesframework.agent.Agent
import org.hyperledger.ariesframework.agent.AgentConfig
import org.hyperledger.ariesframework.agent.MediatorPickupStrategy
import org.hyperledger.ariesframework.credentials.models.AutoAcceptCredential
import org.hyperledger.ariesframework.proofs.models.AutoAcceptProof
import org.hyperledger.ariesframework.wallet.Wallet
import java.io.File

const val PREFERENCE_NAME = "aries-framework-kotlin-sample"
const val genesisPath = "bcovrin-genesis.txn"

class WalletApp : Application() {
    var agent: Agent? = null
    var walletOpened: Boolean = false

    private fun copyResourceFile(resource: String) {
        val inputStream = applicationContext.assets.open(resource)
        val file = File(applicationContext.filesDir.absolutePath, resource)
        if (!file.exists()) {
            file.outputStream().use { inputStream.copyTo(it) }
        }
    }

    private suspend fun openWallet() {
        val pref = applicationContext.getSharedPreferences(PREFERENCE_NAME, 0)
        var key = pref.getString("walletKey", null)

        if (key == null) {
            key = Wallet.generateKey()
            pref.edit().putString("walletKey", key).apply()
            copyResourceFile(genesisPath)
        }

        //val invitationUrl = "https://public.mediator.indiciotech.io?c_i=eyJAdHlwZSI6ICJkaWQ6c292OkJ6Q2JzTlloTXJqSGlxWkRUVUFTSGc7c3BlYy9jb25uZWN0aW9ucy8xLjAvaW52aXRhdGlvbiIsICJAaWQiOiAiMDVlYzM5NDItYTEyOS00YWE3LWEzZDQtYTJmNDgwYzNjZThhIiwgInNlcnZpY2VFbmRwb2ludCI6ICJodHRwczovL3B1YmxpYy5tZWRpYXRvci5pbmRpY2lvdGVjaC5pbyIsICJyZWNpcGllbnRLZXlzIjogWyJDc2dIQVpxSktuWlRmc3h0MmRIR3JjN3U2M3ljeFlEZ25RdEZMeFhpeDIzYiJdLCAibGFiZWwiOiAiSW5kaWNpbyBQdWJsaWMgTWVkaWF0b3IifQ=="
        val invitationUrl = "https://bd984eba1d7c.ngrok.app?c_i=eyJAdHlwZSI6ICJodHRwczovL2RpZGNvbW0ub3JnL2Nvbm5lY3Rpb25zLzEuMC9pbnZpdGF0aW9uIiwgIkBpZCI6ICI2YTllMzkxYi0wZDBmLTQ0OWUtYjE5NC1kZThmNjMwMzY0MTkiLCAicmVjaXBpZW50S2V5cyI6IFsiMVRONkNWUERWdWl1ank5c1ZnRGY4d2tvSHNWcGtjajFZOGs0S2lRYUtqbSJdLCAic2VydmljZUVuZHBvaW50IjogImh0dHBzOi8vYmQ5ODRlYmExZDdjLm5ncm9rLmFwcCIsICJsYWJlbCI6ICJNZWRpYXRvciJ9"
        // val invitationUrl = URL("http://10.0.2.2:3001/invitation").readText() // This uses local AFJ mediator and needs MediatorPickupStrategy.PickUpV1
        val config = AgentConfig(
            walletKey = key,
            genesisPath = File(applicationContext.filesDir.absolutePath, genesisPath).absolutePath,
            mediatorConnectionsInvite = invitationUrl,
            mediatorPickupStrategy = MediatorPickupStrategy.Implicit,
            label = "SampleApp",
            autoAcceptCredential = AutoAcceptCredential.Never,
            autoAcceptProof = AutoAcceptProof.Never,
        )
        agent = Agent(applicationContext, config)
        agent!!.initialize()

        walletOpened = true
        Log.d("demo", "Agent initialized")
    }

    override fun onCreate() {
        super.onCreate()

        GlobalScope.launch(Dispatchers.IO) {
            openWallet()
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
    }

    override fun onLowMemory() {
        super.onLowMemory()
    }
}
