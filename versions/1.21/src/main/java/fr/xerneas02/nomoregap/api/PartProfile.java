package fr.xerneas02.nomoregap.api;

import fr.xerneas02.nomoregap.api.attachment.AttachmentPort;

import java.util.List;

/** Experimental API; may change before 1.0. */
public record PartProfile(PartGeometryProvider geometry, PartBehaviorAdapter behavior, List<AttachmentPort> attachments) {
    public PartProfile { attachments = List.copyOf(attachments); }
}
