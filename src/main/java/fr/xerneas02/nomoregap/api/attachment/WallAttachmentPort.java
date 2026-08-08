package fr.xerneas02.nomoregap.api.attachment;

import net.minecraft.core.Direction;

public record WallAttachmentPort(Direction normal) implements AttachmentPort {
    public WallAttachmentPort {
        if (normal.getAxis().isVertical()) throw new IllegalArgumentException("Wall normal must be horizontal");
    }
}
