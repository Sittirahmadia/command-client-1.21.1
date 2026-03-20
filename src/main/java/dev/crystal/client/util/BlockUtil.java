package dev.crystal.client.util;

import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.*;

public final class BlockUtil {

    private static final MinecraftClient MC = MinecraftClient.getInstance();

    public static boolean isAir(BlockPos pos) { return MC.world.isAir(pos); }

    public static boolean isObsOrBedrock(BlockPos pos) {
        var b = MC.world.getBlockState(pos).getBlock();
        return b == Blocks.OBSIDIAN || b == Blocks.BEDROCK;
    }

    public static boolean canPlaceCrystal(BlockPos base) {
        if (!isObsOrBedrock(base)) return false;
        BlockPos up = base.up(), up2 = up.up();
        if (!isAir(up) || !isAir(up2)) return false;
        var box = new net.minecraft.util.math.Box(up.getX(), up.getY(), up.getZ(), up.getX()+1, up.getY()+2, up.getZ()+1);
        return MC.world.getOtherEntities(null, box).isEmpty();
    }

    public static boolean isSafeHole(BlockPos pos) {
        if (!isAir(pos) || !isAir(pos.up())) return false;
        for (Direction d : new Direction[]{Direction.NORTH,Direction.SOUTH,Direction.EAST,Direction.WEST}) {
            var b = MC.world.getBlockState(pos.offset(d)).getBlock();
            if (b != Blocks.BEDROCK && b != Blocks.OBSIDIAN) return false;
        }
        var below = MC.world.getBlockState(pos.down()).getBlock();
        return below == Blocks.BEDROCK || below == Blocks.OBSIDIAN;
    }

    public static PlacePair findPlaceSide(BlockPos target) {
        for (Direction dir : Direction.values()) {
            BlockPos nb = target.offset(dir);
            var st = MC.world.getBlockState(nb);
            if (!st.isAir() && st.isSolidBlock(MC.world, nb)) {
                Direction face = dir.getOpposite();
                Vec3d hit = Vec3d.ofCenter(nb).add(face.getOffsetX()*0.5, face.getOffsetY()*0.5, face.getOffsetZ()*0.5);
                return new PlacePair(nb, face, hit);
            }
        }
        return null;
    }

    public static boolean inReach(BlockPos pos, double reach) {
        return MC.player != null && MC.player.getEyePos().distanceTo(Vec3d.ofCenter(pos)) <= reach;
    }

    public record PlacePair(BlockPos pos, Direction face, Vec3d hit) {}
}
