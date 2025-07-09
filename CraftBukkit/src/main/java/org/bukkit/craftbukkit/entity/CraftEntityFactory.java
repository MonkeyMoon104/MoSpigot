package org.bukkit.craftbukkit.entity;

import com.google.common.base.Preconditions;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.core.IRegistryCustom;
import net.minecraft.nbt.MojangsonParser;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.ValueInput;
import org.bukkit.entity.EntityFactory;
import org.bukkit.entity.EntitySnapshot;

public class CraftEntityFactory implements EntityFactory {

    private final IRegistryCustom registry;

    public CraftEntityFactory(IRegistryCustom registry) {
        this.registry = registry;
    }

    @Override
    public EntitySnapshot createEntitySnapshot(String input) {
        Preconditions.checkArgument(input != null, "Input string cannot be null");

        NBTTagCompound tag;
        try {
            tag = MojangsonParser.parseCompoundFully(input);
        } catch (CommandSyntaxException e) {
            throw new IllegalArgumentException("Could not parse Entity: " + input, e);
        }

        ValueInput value = TagValueInput.create(ProblemReporter.DISCARDING, registry, tag);
        EntityTypes<?> type = EntityTypes.by(value).orElse(null);
        if (type == null) {
            throw new IllegalArgumentException("Could not parse Entity: " + input);
        }

        return CraftEntitySnapshot.create(tag, CraftEntityType.minecraftToBukkit(type));
    }
}
