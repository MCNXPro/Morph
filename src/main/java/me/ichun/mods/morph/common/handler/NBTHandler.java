package me.ichun.mods.morph.common.handler;

import com.google.common.collect.Ordering;
import com.google.gson.GsonBuilder;
import me.ichun.mods.morph.common.morph.MorphVariant;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityList;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.monster.EntityMob;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.registry.EntityEntry;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

public class NBTHandler
{
    public static HashMap<Class<? extends EntityLivingBase>, TagModifier> modModifiers = new HashMap<>();
    public static HashMap<Class<? extends EntityLivingBase>, TagModifier> nbtModifiers = new HashMap<>();

    public static void modifyNBT(Class<? extends EntityLivingBase> clz, NBTTagCompound tag)
    {
        ArrayList<TagModifier> modifiers = getModifiers(clz);
        for(TagModifier modifier : modifiers)
        {
            modifier.modifyTag(tag);
        }
    }

    public static ArrayList<TagModifier> getModifiers(Class<? extends Entity> entClz)
    {
        ArrayList<TagModifier> modifiers = new ArrayList<>();
        while(entClz != Entity.class)
        {
            if(modModifiers.containsKey(entClz))
            {
                modifiers.add(0, modModifiers.get(entClz));
            }
            if(nbtModifiers.containsKey(entClz))
            {
                modifiers.add(0, nbtModifiers.get(entClz));
            }
            entClz = (Class<? extends Entity>)entClz.getSuperclass();
        }
        return modifiers;
    }

    public static class TagModifier
    {
        public String parentKey;
        public HashMap<String, Object> modifiers = new HashMap<>();

        public void modifyTag(NBTTagCompound tag)
        {
            for(Map.Entry<String, Object> e : modifiers.entrySet())
            {
                Object obj = e.getValue();
                if(obj == null)
                {
                    tag.removeTag(e.getKey());
                }
                else if(obj instanceof TagModifier)
                {
                    NBTTagCompound tagToModify = tag.getCompoundTag(((TagModifier)obj).parentKey);
                    if(!tagToModify.hasNoTags())
                    {
                        ((TagModifier)obj).modifyTag(tagToModify);
                    }
                }
                else if(obj instanceof Boolean)
                {
                    tag.setBoolean(e.getKey(), (Boolean)obj);
                }
                else if(obj instanceof String)
                {
                    if(obj.equals("nullAsString"))
                    {
                        tag.setString(e.getKey(), "null");
                    }
                    else
                    {
                        tag.setString(e.getKey(), (String)obj);
                    }
                }
                else if(obj instanceof Float)
                {
                    tag.setFloat(e.getKey(), (Float)obj);
                }
                else if(obj instanceof Double)
                {
                    tag.setDouble(e.getKey(), (Double)obj);
                }
                else if(obj instanceof Integer)
                {
                    tag.setInteger(e.getKey(), (Integer)obj);
                }
                else if(obj instanceof Byte)
                {
                    tag.setByte(e.getKey(), (Byte)obj);
                }
                else if(obj instanceof Short)
                {
                    tag.setShort(e.getKey(), (Short)obj);
                }
                else if(obj instanceof Long)
                {
                    tag.setLong(e.getKey(), (Long)obj);
                }
            }
        }
    }

    public static void handleModifier(NBTHandler.TagModifier tagModifier, String key, String value)
    {
        if(value.contains(";") && !value.contains(":"))
        {
            key = value.substring(0, value.indexOf(";"));
            value = value.substring(value.indexOf(";") + 1, value.length());
        }
        Object obj = value;
        if(value.contains(":"))
        {
            NBTHandler.TagModifier nestedTagModifier = new NBTHandler.TagModifier();
            obj = nestedTagModifier;
            nestedTagModifier.parentKey = value.substring(0, value.indexOf(":"));
            handleModifier(nestedTagModifier, nestedTagModifier.parentKey, value.substring(value.indexOf(":") + 1, value.length()));
        }
        else if(value.equalsIgnoreCase("null"))
        {
            obj = null;
        }
        else if(value.equalsIgnoreCase("false") || value.equalsIgnoreCase("true"))
        {
            obj = value.equalsIgnoreCase("true");
        }
        else if(value.endsWith("F"))
        {
            try
            {
                obj = Float.parseFloat(value.substring(0, value.length() - 1));
            }
            catch(NumberFormatException ignored){}
        }
        else if(value.endsWith("D"))
        {
            try
            {
                obj = Double.parseDouble(value.substring(0, value.length() - 1));
            }
            catch(NumberFormatException ignored){}
        }
        else if(value.endsWith("B"))
        {
            try
            {
                obj = Byte.parseByte(value.substring(0, value.length() - 1));
            }
            catch(NumberFormatException ignored){}
        }
        else if(value.endsWith("S"))
        {
            try
            {
                obj = Short.parseShort(value.substring(0, value.length() - 1));
            }
            catch(NumberFormatException ignored){}
        }
        else if(value.endsWith("L"))
        {
            try
            {
                obj = Long.parseLong(value.substring(0, value.length() - 1));
            }
            catch(NumberFormatException ignored){}
        }
        else
        {
            try
            {
                obj = Integer.parseInt(value);
            }
            catch(NumberFormatException ignored){}
        }
        tagModifier.modifiers.put(key, obj);
    }

    public static void createMinecraftEntityTags(World world)
    {
        TreeMap<String, TreeMap<String, String>> list = new TreeMap<>(Ordering.natural());
        for(EntityEntry entry : net.minecraftforge.registries.GameData.getEntityRegistry().getValues())
        {
            Class clz = entry.getEntityClass();
            if(EntityLivingBase.class.isAssignableFrom(clz) && clz != EntityLivingBase.class && clz != EntityLiving.class && clz != EntityMob.class)
            {
                try
                {
                    EntityLivingBase living = (EntityLivingBase)clz.getConstructor(World.class).newInstance(world);
                    NBTTagCompound tag = new NBTTagCompound();
                    if(living.writeToNBTOptional(new NBTTagCompound()))
                    {
                        living.writeEntityToNBT(tag);
                        TreeMap<String, String> tags = new TreeMap<>(Ordering.natural());
                        MorphVariant.clean(living, tag);
                        tag.removeTag("HealF");
                        tag.removeTag("Health");
                        tag.removeTag("CanPickUpLoot");
                        tag.removeTag("PersistenceRequired");
                        tag.removeTag("NoAI");
                        tag.removeTag("Age");

                        for(Object obj1 : tag.tagMap.entrySet())
                        {
                            tags.put((String)((Map.Entry)obj1).getKey(), "null");
                        }
                        if(!tags.isEmpty())
                        {
                            list.put(clz.getName(), tags);
                        }
                    }
                }
                catch(InstantiationException | IllegalAccessException | InvocationTargetException | NoSuchMethodException e)
                {
                    e.printStackTrace();
                }
            }
        }
        System.out.println((new GsonBuilder().setPrettyPrinting().create()).toJson(list));
    }
}