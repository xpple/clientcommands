package net.earthcomputer.clientcommands.mixin;

import net.earthcomputer.clientcommands.MultiVersionCompat;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.ProtectionEnchantment;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.Item;
import net.minecraft.registry.tag.TagKey;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ProtectionEnchantment.class)
public abstract class MixinProtectionEnchantment extends Enchantment {

    protected MixinProtectionEnchantment(Rarity rarity, TagKey<Item> applicableItems, EquipmentSlot[] slotTypes) {
        super(rarity, applicableItems, slotTypes);
    }

    @Inject(method = "canAccept", at = @At("HEAD"), cancellable = true)
    public void isCompatible1140(Enchantment other, CallbackInfoReturnable<Boolean> ci) {
        int protocolVersion = MultiVersionCompat.INSTANCE.getProtocolVersion();
        if (protocolVersion < MultiVersionCompat.V1_14 || protocolVersion > MultiVersionCompat.V1_14_2) {
            return;
        }

        ci.setReturnValue(other != this);
    }

}
