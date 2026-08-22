package fr.xerneas02.nomoregap.network;

import fr.xerneas02.nomoregap.network.payload.AddPartPayload;
import fr.xerneas02.nomoregap.network.payload.RemovePartPayload;
import fr.xerneas02.nomoregap.network.payload.SyncCompositePayload;
import fr.xerneas02.nomoregap.network.payload.MovingPistonPayload;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;

/** Payload schemas are registered now; authoritative client intents remain disabled until placement exists. */
public final class NoMoreGapNetworking {
    private NoMoreGapNetworking() {}
    public static void initialize() {
        PayloadTypeRegistry.serverboundPlay().register(AddPartPayload.TYPE, AddPartPayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(RemovePartPayload.TYPE, RemovePartPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(SyncCompositePayload.TYPE, SyncCompositePayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(MovingPistonPayload.TYPE, MovingPistonPayload.CODEC);
    }
}
