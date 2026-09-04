package com.chedidandrew.emeraldstandard.minecraft;

import java.util.Objects;
import net.minecraft.world.inventory.ContainerData;

/** Presents logical 32-bit values as signed-short limbs at Minecraft's menu boundary. */
final class ShortPackedContainerData implements ContainerData {
    private final ContainerData logical;
    private final int wireSlotCount;

    ShortPackedContainerData(ContainerData logical) {
        this.logical = Objects.requireNonNull(logical, "logical");
        this.wireSlotCount = ContainerDataPacking.wireSlotCount(logical.getCount());
    }

    @Override
    public int get(int index) {
        checkWireIndex(index);
        int logicalIndex = ContainerDataPacking.logicalIndex(index);
        return ContainerDataPacking.encodeLimb(
                logical.get(logicalIndex), ContainerDataPacking.limbIndex(index));
    }

    @Override
    public void set(int index, int wireValue) {
        checkWireIndex(index);
        int logicalIndex = ContainerDataPacking.logicalIndex(index);
        int oldValue = logical.get(logicalIndex);
        int newValue;
        if (ContainerDataPacking.limbIndex(index) == 0) {
            newValue = (oldValue & 0xFFFF0000) | (wireValue & 0xFFFF);
        } else {
            newValue = (oldValue & 0x0000FFFF) | ((wireValue & 0xFFFF) << Short.SIZE);
        }
        logical.set(logicalIndex, newValue);
    }

    @Override
    public int getCount() {
        return wireSlotCount;
    }

    private void checkWireIndex(int index) {
        if (index < 0 || index >= wireSlotCount) {
            throw new IndexOutOfBoundsException("ContainerData wire index " + index);
        }
    }
}
