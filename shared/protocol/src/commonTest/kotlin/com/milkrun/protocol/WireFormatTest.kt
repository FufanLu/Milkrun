package com.milkrun.protocol

import com.milkrun.model.ClientId
import com.milkrun.model.HandoffMethod
import com.milkrun.model.Hlc
import com.milkrun.model.Mutation
import com.milkrun.model.ParcelId
import com.milkrun.model.StopId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class WireFormatTest {
    private val client = ClientId("c7f3a9")
    private val stop = StopId("S-014")
    private val arrive = Mutation.Arrive(stop, atMillis = 1762419600123)
    private val deliver =
        Mutation.Deliver(
            stopId = stop,
            parcelIds = listOf(ParcelId("TBA312459870001")),
            handoff = HandoffMethod.FRONT_DOOR,
            atMillis = 1762419641877,
        )
    private val request =
        PushRequest(
            clientId = client,
            mutations =
            listOf(
                MutationEnvelope(seq = 1, hlc = Hlc(1762419600123, 0, client.value), mutation = arrive),
                MutationEnvelope(seq = 2, hlc = Hlc(1762419641877, 0, client.value), mutation = deliver),
            ),
        )

    /** The exact bytes are part of the contract with already-installed apps. */
    @Test
    fun pushRequestWireFormatIsStable() {
        val expected =
            """{"clientId":"c7f3a9","mutations":[""" +
                """{"seq":1,"hlc":"0001762419600123-0000-c7f3a9",""" +
                """"mutation":{"type":"arrive","stopId":"S-014","atMillis":1762419600123}},""" +
                """{"seq":2,"hlc":"0001762419641877-0000-c7f3a9","mutation":{"type":"deliver","stopId":"S-014",""" +
                """"parcelIds":["TBA312459870001"],"handoff":"front_door","atMillis":1762419641877}}]}"""
        assertEquals(expected, SyncApi.json.encodeToString(PushRequest.serializer(), request))
        assertEquals(request, SyncApi.json.decodeFromString(PushRequest.serializer(), expected))
    }

    @Test
    fun unknownFieldsFromANewerServerAreIgnored() {
        val body = """{"lastProcessedSeq":2,"results":[],"serverHlc":"0000000000000005-0000-srv","newField":42}"""
        assertEquals(2, SyncApi.json.decodeFromString(PushResponse.serializer(), body).lastProcessedSeq)
    }

    @Test
    fun nonContiguousBatchIsRefusedBeforeItLeavesThePhone() {
        val a = request.mutations[0]
        assertFailsWith<IllegalArgumentException> { PushRequest(ClientId("x"), listOf(a, a.copy(seq = 3))) }
    }
}
