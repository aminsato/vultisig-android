package com.vultisig.wallet.ui.screens.keysign

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.asFlow
import com.vultisig.wallet.R
import com.vultisig.wallet.data.common.Utils
import com.vultisig.wallet.data.models.Vault
import com.vultisig.wallet.data.models.payload.KeysignPayload
import com.vultisig.wallet.ui.components.MultiColorButton
import com.vultisig.wallet.ui.models.keysign.KeysignFlowState
import com.vultisig.wallet.ui.models.keysign.KeysignFlowViewModel
import com.vultisig.wallet.ui.screens.PeerDiscoveryView
import com.vultisig.wallet.ui.screens.keygen.FastPeerDiscovery
import com.vultisig.wallet.ui.theme.Theme
import com.vultisig.wallet.ui.utils.NetworkPromptOption
import timber.log.Timber

@Composable
internal fun KeysignPeerDiscovery(
    vault: Vault,
    keysignPayload: KeysignPayload,
    viewModel: KeysignFlowViewModel,
) {
    val selectionState = viewModel.selection.asFlow().collectAsState(initial = emptyList()).value
    val participants = viewModel.participants.asFlow().collectAsState(initial = emptyList()).value
    val context = LocalContext.current.applicationContext
    LaunchedEffect(key1 = viewModel.participants) {
        viewModel.participants.asFlow().collect { newList ->
            // add all participants to the selection
            for (participant in newList) {
                viewModel.addParticipant(participant)
            }
        }
    }
    LaunchedEffect(key1 = viewModel.selection) {
        viewModel.selection.asFlow().collect { newList ->
            if (vault.signers.isEmpty()) {
                Timber.e("Vault signers size is 0")
                return@collect
            }
            if (newList.size >= Utils.getThreshold(vault.signers.size)) {
                // automatically kickoff keysign
                viewModel.moveToState(KeysignFlowState.KEYSIGN)
            }
        }
    }
    LaunchedEffect(Unit) {
        // start mediator server
        viewModel.setData(vault, context, keysignPayload)
    }
    DisposableEffect(Unit) {
        onDispose {
            viewModel.stopParticipantDiscovery()
        }
    }

    KeysignPeerDiscovery(
        isLookingForVultiServer = viewModel.isFastSign &&
                Utils.getThreshold(vault.signers.size) == 2,
        selectionState = selectionState,
        participants = participants,
        keysignMessage = viewModel.keysignMessage.value,
        networkPromptOption = viewModel.networkOption.value,
        hasNetworkPrompt = !viewModel.isFastSign,
        onChangeNetwork = { viewModel.changeNetworkPromptOption(it, context) },
        onAddParticipant = { viewModel.addParticipant(it) },
        onRemoveParticipant = { viewModel.removeParticipant(it) },
        onStopParticipantDiscovery = {
            viewModel.stopParticipantDiscovery()
            viewModel.moveToState(KeysignFlowState.KEYSIGN)
        }
    )
}

@Composable
internal fun KeysignPeerDiscovery(
    isLookingForVultiServer: Boolean,
    selectionState: List<String>,
    participants: List<String>,
    keysignMessage: String,
    networkPromptOption: NetworkPromptOption,
    hasNetworkPrompt: Boolean,
    onChangeNetwork: (NetworkPromptOption) -> Unit = {},
    onAddParticipant: (String) -> Unit = {},
    onRemoveParticipant: (String) -> Unit = {},
    onStopParticipantDiscovery: () -> Unit = {},
) {
    Scaffold(
        containerColor = Theme.colors.oxfordBlue800,
        content = {
            if (isLookingForVultiServer) {
                FastPeerDiscovery()
            } else {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(it)
                ) {
                    PeerDiscoveryView(
                        selectionState = selectionState,
                        participants = participants,
                        keygenPayloadState = keysignMessage,
                        networkPromptOption = networkPromptOption,
                        hasNetworkPrompt = hasNetworkPrompt,
                        onChangeNetwork = onChangeNetwork,
                        onAddParticipant = onAddParticipant,
                        onRemoveParticipant = onRemoveParticipant,
                    )
                }
            }
        },
        bottomBar = {
            if (!isLookingForVultiServer) {
                MultiColorButton(
                    text = stringResource(R.string.keysign_peer_discovery_start),
                    backgroundColor = Theme.colors.turquoise600Main,
                    textColor = Theme.colors.oxfordBlue600Main,
                    minHeight = 45.dp,
                    textStyle = Theme.montserrat.subtitle1,
                    disabled = selectionState.size < 2,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(
                            vertical = 16.dp,
                            horizontal = 16.dp,
                        ),
                    onClick = onStopParticipantDiscovery,
                )
            }
        }
    )
}

@Preview
@Composable
private fun KeysignPeerDiscoveryPreview() {
    KeysignPeerDiscovery(
        isLookingForVultiServer = true,
        selectionState = listOf("1", "2"),
        participants = listOf("1", "2", "3"),
        keysignMessage = "keysignMessage",
        networkPromptOption = NetworkPromptOption.LOCAL,
        hasNetworkPrompt = true,
    )
}