package fr.xerneas02.nomoregap.api.attachment;

import net.minecraft.core.Direction;

public record FloorAttachmentPort() implements AttachmentPort {
    @Override public Direction normal() { return Direction.UP; }
}
