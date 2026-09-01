package net.minex.enchant.mixin;

import net.minecraft.world.inventory.ResultContainer;
import net.minecraft.world.Container;
import net.minecraft.world.inventory.ItemCombinerMenu;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ItemCombinerMenu.class)
public interface ForgingAccessor {
	@Accessor("inputSlots")
    Container getInputSlots();

	@Accessor("resultSlots")
    ResultContainer getResultSlots();
}
