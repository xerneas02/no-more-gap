package fr.xerneas02.nomoregap.mixin;

import fr.xerneas02.nomoregap.lava.LavaLogging;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.StateHolder;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.Property;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Map;

@Mixin(StateDefinition.Builder.class)
abstract class WaterloggedStateDefinitionMixin<O, S extends StateHolder<O, S>> {
    @Shadow private Map<String, Property<?>> properties;

    @Shadow public abstract StateDefinition.Builder<O, S> add(Property<?>... properties);

    @Inject(method = "add", at = @At("HEAD"))
    private void noMoreGap$addLavaLoggedProperty(Property<?>[] added,
                                                  CallbackInfoReturnable<StateDefinition.Builder<O, S>> callback) {
        for (Property<?> property : added) {
            if (property == BlockStateProperties.WATERLOGGED && !properties.containsKey(LavaLogging.LAVA_LOGGED.getName())) {
                add(LavaLogging.LAVA_LOGGED);
                return;
            }
        }
    }
}
