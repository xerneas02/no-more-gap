package fr.xerneas02.nomoregap.lava;

import net.minecraft.world.level.block.state.properties.BooleanProperty;

/** State shared by every vanilla block that already supports waterlogging. */
public final class LavaLogging {
    public static final BooleanProperty LAVA_LOGGED = BooleanProperty.create("lava_logged");

    private LavaLogging() {}
}
