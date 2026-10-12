package com.dogpound.railmap.mail;

import com.dogpound.railmap.RailMap;
import net.minecraftforge.common.config.Config;

/** Mail by train (batch 5 #8), config/irextras_mail.cfg. Nothing here can lose a letter. */
@Config(modid = RailMap.MODID, name = "irextras_mail", category = "mail")
public final class MailConfig {
    private MailConfig() {}

    @Config.Comment("Postage for a letter (cents, Pride Realms money; free without it)")
    @Config.RangeInt(min = 0, max = 100000)
    public static int letterFee = 50;

    @Config.Comment("Postage for a parcel: base + per 100 g (cents)")
    @Config.RangeInt(min = 0, max = 100000)
    public static int parcelBase = 200, parcelPer100g = 10;

    @Config.Comment("Late fee for a letter posted in a TPO car's late-posting box (cents, on top of the postage)")
    @Config.RangeInt(min = 0, max = 100000)
    public static int lateFee = 50;

    @Config.Comment("Seconds a TPO (travelling post office) sorter takes per letter en route")
    @Config.RangeInt(min = 1, max = 120)
    public static int tpoSortSeconds = 6;

    @Config.Comment("Hand arrived mail to Pride Realms (RealmCoin) delivery robots for the addressee's mailbox, when that mod is installed")
    public static boolean robotDelivery = true;

    @Config.Comment("Letters a pigeonhole holds before the frame calls for a bag (AUTO BAG)")
    @Config.RangeInt(min = 1, max = 64)
    public static int bagAt = 8;
}
