package com.dogpound.railmap.block;

import net.minecraft.inventory.IInventory;

/**
 * Furniture you can put things in (requested feature): numbered storages that open as a chest screen (GUI id GUI_OFFICE_STORE + k), and
 * everything inside drops when the furniture is broken.
 */
public interface IFurniture {
    IInventory storage(int k);
    String storeName(int k);
    int storeSize(int k);
    void dropAll();
}
