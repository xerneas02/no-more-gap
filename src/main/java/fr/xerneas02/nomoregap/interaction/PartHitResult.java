package fr.xerneas02.nomoregap.interaction;

import net.minecraft.world.phys.Vec3;

public record PartHitResult(int partId, Vec3 location, double distanceSquared) {}
