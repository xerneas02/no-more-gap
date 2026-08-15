package fr.xerneas02.nomoregap.network;

import fr.xerneas02.nomoregap.network.payload.AddPartPayload;
import fr.xerneas02.nomoregap.network.payload.RemovePartPayload;
import fr.xerneas02.nomoregap.network.payload.SyncCompositePayload;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;

/** Payload schemas are registered now; authoritative client intents remain disabled until placement exists. */
public final class NoMoreGapNetworking {
    private NoMoreGapNetworking() {}
    public static void initialize() {
        PayloadTypeRegistry.playC2S().register(AddPartPayload.TYPE, AddPartPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(RemovePartPayload.TYPE, RemovePartPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(SyncCompositePayload.TYPE, SyncCompositePayload.CODEC);
    }
}
