package dev.crystal.client.util;

import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

public final class DamageUtil {

    private static final MinecraftClient MC = MinecraftClient.getInstance();

    public static float crystalDamage(LivingEntity entity, Vec3d crystalPos) {
        return explosionDamage(entity, crystalPos, 12f);
    }

    public static float explosionDamage(LivingEntity entity, Vec3d center, float power) {
        if (MC.world == null) return 0f;
        double maxDist = power * 2.0;
        Vec3d entityMid = entity.getPos().add(0, entity.getHeight() / 2.0, 0);
        double dist = entityMid.distanceTo(center);
        if (dist > maxDist) return 0f;

        float exposure = getExposure(center, entity);
        double impact  = (1.0 - dist / maxDist) * exposure;
        float raw      = (float)((impact * impact + impact) / 2.0 * 7.0 * (maxDist * 2.0) + 1.0);

        // Armor
        double armor = entity.getAttributeValue(EntityAttributes.GENERIC_ARMOR);
        double tough = entity.getAttributeValue(EntityAttributes.GENERIC_ARMOR_TOUGHNESS);
        raw = (float)(raw * (1.0 - Math.min(20.0, Math.max(0, armor - raw / (2.0 + tough / 4.0))) / 25.0));

        // Resistance
        if (entity.hasStatusEffect(StatusEffects.RESISTANCE)) {
            int amp = entity.getStatusEffect(StatusEffects.RESISTANCE).getAmplifier() + 1;
            raw = Math.max(0, raw * (1f - amp * 0.2f));
        }
        return Math.max(0, raw);
    }

    private static float getExposure(Vec3d center, LivingEntity entity) {
        if (MC.world == null || MC.player == null) return 0f;
        Box box = entity.getBoundingBox();
        double sx = 1.0 / ((box.maxX - box.minX) * 2.0 + 1.0);
        double sy = 1.0 / ((box.maxY - box.minY) * 2.0 + 1.0);
        double sz = 1.0 / ((box.maxZ - box.minZ) * 2.0 + 1.0);
        if (sx < 0 || sy < 0 || sz < 0) return 0f;
        int hit = 0, total = 0;
        for (double x = 0; x <= 1.0; x += sx)
            for (double y = 0; y <= 1.0; y += sy)
                for (double z = 0; z <= 1.0; z += sz) {
                    Vec3d pt = new Vec3d(
                        box.minX + (box.maxX - box.minX) * x,
                        box.minY + (box.maxY - box.minY) * y,
                        box.minZ + (box.maxZ - box.minZ) * z);
                    if (raycastClear(center, pt)) hit++;
                    total++;
                }
        return total == 0 ? 0f : (float) hit / total;
    }

    private static boolean raycastClear(Vec3d from, Vec3d to) {
        if (MC.world == null || MC.player == null) return false;
        return MC.world.raycast(new RaycastContext(from, to,
                RaycastContext.ShapeType.COLLIDER,
                RaycastContext.FluidHandling.NONE, MC.player))
                .getType() == net.minecraft.util.hit.HitResult.Type.MISS;
    }
}
