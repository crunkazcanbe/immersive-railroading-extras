package com.dogpound.railmap.track;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;

/**
 * Survey peg (her list #18 "generate a construction blueprint"): the timber setting-out stake the Surveyor's
 * instrument drives along a planned alignment, its board written with the chainage, the cut or fill to formation level
 * at that spot, the gradient and the curve. Build the railway to the pegs.
 */
public class TileSurveyPeg extends PlainTile {
    private String route = "";

    public void write(String route, String ch, String level, String info) {
        this.route = route;
        ctl.texts.put("ch", ch);
        ctl.texts.put("lev", level);
        ctl.texts.put("info", info);
        markDirty();
        controlsChanged();
    }

    @Override protected void serverTick() {}
    @Override public String controlPress(String id, EntityPlayer who) { return "§e" + route + " §7" + ctl.text("ch") + " · " + ctl.text("lev") + " · " + ctl.text("info"); }
    @Override protected void save(NBTTagCompound t) { t.setString("route", route); }
    @Override protected void load(NBTTagCompound t) { route = t.getString("route"); }
}
