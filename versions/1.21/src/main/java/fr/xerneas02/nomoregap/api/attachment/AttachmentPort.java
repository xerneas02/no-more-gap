package fr.xerneas02.nomoregap.api.attachment;

import net.minecraft.core.Direction;

/** Experimental API; may change before 1.0. */
public sealed interface AttachmentPort permits FloorAttachmentPort, WallAttachmentPort, CeilingAttachmentPort {
    Direction normal();
}
