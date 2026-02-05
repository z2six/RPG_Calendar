package org.z2six.rpgtimeline.client.tooltip;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterClientTooltipComponentFactoriesEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import org.z2six.rpgtimeline.Constants;
import org.z2six.rpgtimeline.tooltip.ChronicleItemTooltip;

@EventBusSubscriber(modid = Constants.MOD_ID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public final class ChronicleTooltipComponentRegistrar {

    private ChronicleTooltipComponentRegistrar() {
        // no-op
    }

    @SubscribeEvent
    public static void onRegisterTooltipComponents(RegisterClientTooltipComponentFactoriesEvent event) {
        event.register(ChronicleItemTooltip.class, ChronicleItemTooltipClient::new);
    }
}
