package com.exprule;

import org.bukkit.GameMode;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

final class PaperCatalystAccess {
    private final Method getHandle;
    private final Method level;
    private final Method position;
    private final Method wasExperienceConsumed;
    private final Method shouldDropExperience;
    private final Method getLastDamageSource;
    private final Method getDamageEntity;
    private final Method processMobExperience;
    private final Method contextOf;
    private final Method containing;
    private final Method getX;
    private final Method getY;
    private final Method getZ;
    private final Method relative;
    private final Method getChunk;
    private final Method getRegistry;
    private final Method visitInRange;
    private final Method recipient;
    private final Method getSpreader;
    private final Method addCursors;
    private final Method awardAdvancement;
    private final Constructor<?> listenerInfo;
    private final Class<?> visitorType;
    private final Class<?> catalystType;
    private final Object entityDie;
    private final Object up;

    PaperCatalystAccess() throws ReflectiveOperationException {
        Class<?> entityType = Class.forName("net.minecraft.world.entity.Entity");
        Class<?> livingType = Class.forName("net.minecraft.world.entity.LivingEntity");
        Class<?> levelType = Class.forName("net.minecraft.world.level.Level");
        Class<?> serverLevelType = Class.forName("net.minecraft.server.level.ServerLevel");
        Class<?> vecType = Class.forName("net.minecraft.world.phys.Vec3");
        Class<?> posType = Class.forName("net.minecraft.core.BlockPos");
        Class<?> directionType = Class.forName("net.minecraft.core.Direction");
        Class<?> holderType = Class.forName("net.minecraft.core.Holder");
        Class<?> contextType = Class.forName("net.minecraft.world.level.gameevent.GameEvent$Context");
        Class<?> listenerType = Class.forName("net.minecraft.world.level.gameevent.GameEventListener");
        Class<?> infoType = Class.forName("net.minecraft.world.level.gameevent.GameEvent$ListenerInfo");
        Class<?> registryType = Class.forName("net.minecraft.world.level.gameevent.GameEventListenerRegistry");
        visitorType = Class.forName("net.minecraft.world.level.gameevent.GameEventListenerRegistry$ListenerVisitor");
        catalystType = Class.forName("net.minecraft.world.level.block.entity.SculkCatalystBlockEntity$CatalystListener");
        getHandle = Class.forName("org.bukkit.craftbukkit.entity.CraftEntity").getMethod("getHandle");
        level = entityType.getMethod("level");
        position = entityType.getMethod("position");
        wasExperienceConsumed = livingType.getMethod("wasExperienceConsumed");
        shouldDropExperience = livingType.getMethod("shouldDropExperience");
        getLastDamageSource = livingType.getMethod("getLastDamageSource");
        getDamageEntity = getLastDamageSource.getReturnType().getMethod("getEntity");
        processMobExperience = Class.forName("net.minecraft.world.item.enchantment.EnchantmentHelper")
                .getMethod("processMobExperience", serverLevelType, entityType, entityType, int.class);
        contextOf = contextType.getMethod("of", entityType);
        containing = posType.getMethod("containing", Class.forName("net.minecraft.core.Position"));
        getX = posType.getMethod("getX");
        getY = posType.getMethod("getY");
        getZ = posType.getMethod("getZ");
        relative = vecType.getMethod("relative", directionType, double.class);
        getChunk = serverLevelType.getMethod("getChunkIfLoadedImmediately", int.class, int.class);
        getRegistry = getChunk.getReturnType().getMethod("getListenerRegistry", int.class);
        visitInRange = registryType.getMethod("visitInRangeListeners", holderType, vecType, contextType, visitorType);
        listenerInfo = infoType.getConstructor(holderType, vecType, contextType, listenerType, vecType);
        recipient = infoType.getMethod("recipient");
        getSpreader = catalystType.getMethod("getSculkSpreader");
        addCursors = getSpreader.getReturnType().getMethod("addCursors", posType, int.class);
        awardAdvancement = catalystType.getDeclaredMethod("tryAwardItSpreadsAdvancement", levelType, livingType);
        awardAdvancement.setAccessible(true);
        entityDie = Class.forName("net.minecraft.world.level.gameevent.GameEvent").getField("ENTITY_DIE").get(null);
        up = directionType.getField("UP").get(null);
    }

    // Player.getBaseExperienceReward, with only KEEP_INVENTORY omitted.
    static int baseExperience(Player player) {
        return player.getGameMode() == GameMode.SPECTATOR ? 0 : Math.min(player.getLevel() * 7, 100);
    }

    int deathExperience(Player player, Entity attacker) throws ReflectiveOperationException {
        Object entity = getHandle.invoke(player);
        if ((boolean) wasExperienceConsumed.invoke(entity)) {
            return 0;
        }
        // Players are always experience droppers in LivingEntity.getExpReward.
        return (int) processMobExperience.invoke(null, level.invoke(entity),
                attacker == null ? null : getHandle.invoke(attacker), entity, baseExperience(player));
    }

    @SuppressWarnings("unchecked")
    void addCharge(Player player, int radius) throws ReflectiveOperationException {
        Object entity = getHandle.invoke(player);
        if ((boolean) wasExperienceConsumed.invoke(entity)) {
            return;
        }
        Object world = level.invoke(entity);
        Object source = position.invoke(entity);
        Object context = contextOf.invoke(null, entity);
        Object blockPos = containing.invoke(null, source);
        int x = (int) getX.invoke(blockPos);
        int y = (int) getY.invoke(blockPos);
        int z = (int) getZ.invoke(blockPos);
        List<Comparable<Object>> listeners = new ArrayList<>();
        Object visitor = Proxy.newProxyInstance(visitorType.getClassLoader(), new Class<?>[]{visitorType},
                (proxy, method, args) -> {
                    if (catalystType.isInstance(args[0])) {
                        listeners.add((Comparable<Object>) listenerInfo.newInstance(
                                entityDie, source, context, args[0], args[1]));
                    }
                    return null;
                });

        // Match GameEventDispatcher's section traversal and stable distance ordering.
        for (int sectionX = (x - radius) >> 4; sectionX <= (x + radius) >> 4; sectionX++) {
            for (int sectionZ = (z - radius) >> 4; sectionZ <= (z + radius) >> 4; sectionZ++) {
                Object chunk = getChunk.invoke(world, sectionX, sectionZ);
                if (chunk != null) {
                    for (int sectionY = (y - radius) >> 4; sectionY <= (y + radius) >> 4; sectionY++) {
                        visitInRange.invoke(getRegistry.invoke(chunk, sectionY), entityDie, source, context, visitor);
                    }
                }
            }
        }
        if (listeners.isEmpty()) {
            return;
        }
        Collections.sort(listeners);
        Object listener = recipient.invoke(listeners.getFirst());
        Object damageSource = getLastDamageSource.invoke(entity);
        Object attacker = damageSource == null ? null : getDamageEntity.invoke(damageSource);
        int charge = (int) processMobExperience.invoke(null, world, attacker, entity, baseExperience(player));
        if ((boolean) shouldDropExperience.invoke(entity) && charge > 0) {
            Object start = containing.invoke(null, relative.invoke(source, up, 0.5));
            addCursors.invoke(getSpreader.invoke(listener), start, charge);
            awardAdvancement.invoke(listener, world, entity);
        }
    }
}
