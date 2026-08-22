package fr.xerneas02.nomoregap.network;

import fr.xerneas02.nomoregap.network.payload.AddPartPayload;
import fr.xerneas02.nomoregap.network.payload.RemovePartPayload;
import fr.xerneas02.nomoregap.network.payload.SyncCompositePayload;
import fr.xerneas02.nomoregap.network.payload.MovingPistonPayload;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

public final class NoMoreGapNetworking {
    private NoMoreGapNetworking() {}

    public static void register(IEventBus bus) { bus.addListener(NoMoreGapNetworking::registerPayloads); }

    private static void registerPayloads(RegisterPayloadHandlersEvent event) {
        var registrar = event.registrar("1");
        registrar.playToServer(AddPartPayload.TYPE, AddPartPayload.CODEC, (payload, context) -> {});
        registrar.playToServer(RemovePartPayload.TYPE, RemovePartPayload.CODEC, (payload, context) -> {});
        registrar.playToClient(SyncCompositePayload.TYPE, SyncCompositePayload.CODEC, (payload, context) -> {});
        registrar.playToClient(MovingPistonPayload.TYPE, MovingPistonPayload.CODEC,
                (payload, context) -> context.enqueueWork(() ->
                        fr.xerneas02.nomoregap.NoMoreGapClient.installMovingPiston(payload)));
    }
}
