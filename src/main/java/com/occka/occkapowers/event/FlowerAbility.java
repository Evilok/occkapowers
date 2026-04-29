package com.occka.occkapowers.event;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongTag;
import net.minecraft.nbt.Tag;
import net.minecraft.core.registries.BuiltInRegistries;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;

public final class FlowerAbility {
    private FlowerAbility() {
    }

    private static final Random RNG = new Random();

    // Все возможные блоки цветов для следа
    private static final BlockState[] FLOWER_BLOCKS = {
            Blocks.DANDELION.defaultBlockState(),
            Blocks.POPPY.defaultBlockState(),
            Blocks.BLUE_ORCHID.defaultBlockState(),
            Blocks.ALLIUM.defaultBlockState(),
            Blocks.AZURE_BLUET.defaultBlockState(),
            Blocks.RED_TULIP.defaultBlockState(),
            Blocks.ORANGE_TULIP.defaultBlockState(),
            Blocks.WHITE_TULIP.defaultBlockState(),
            Blocks.PINK_TULIP.defaultBlockState(),
            Blocks.OXEYE_DAISY.defaultBlockState(),
            Blocks.CORNFLOWER.defaultBlockState(),
            Blocks.LILY_OF_THE_VALLEY.defaultBlockState(),
    };

    // Двойные (высокие) цветы
    private static final BlockState[] TALL_FLOWER_BLOCKS = {
            Blocks.SUNFLOWER.defaultBlockState(),
            Blocks.LILAC.defaultBlockState(),
            Blocks.ROSE_BUSH.defaultBlockState(),
            Blocks.PEONY.defaultBlockState(),
    };

    // Листва для ульты
    private static final BlockState[] LEAF_BLOCKS = {
            Blocks.OAK_LEAVES.defaultBlockState(),
            Blocks.BIRCH_LEAVES.defaultBlockState(),
            Blocks.JUNGLE_LEAVES.defaultBlockState(),
            Blocks.ACACIA_LEAVES.defaultBlockState(),
            Blocks.SPRUCE_LEAVES.defaultBlockState(),
    };

    // ===== SHIFT: переключаемая природная стойка (тропа из цветов) =====

    public static void activateShift(ServerPlayer player, ServerLevel level) {
        boolean active = player.getPersistentData().getBoolean("occka_flower_form_active");
        if (!active) {
            enableFlowerForm(player, level);
        } else {
            disableFlowerForm(player, level);
        }
    }

    private static void enableFlowerForm(ServerPlayer player, ServerLevel level) {
        player.getPersistentData().putBoolean("occka_flower_form_active", true);

        level.sendParticles(ParticleTypes.HAPPY_VILLAGER,
                player.getX(), player.getY() + 1, player.getZ(),
                20, 1.0, 1.0, 1.0, 0.1);
        level.sendParticles(ParticleTypes.COMPOSTER,
                player.getX(), player.getY() + 0.5, player.getZ(),
                15, 0.5, 0.5, 0.5, 0.05);

        player.sendSystemMessage(AbilityCommon.msg(
                "Nature Stance: ON", ChatFormatting.GREEN, ChatFormatting.BOLD));
    }

    private static void disableFlowerForm(ServerPlayer player, ServerLevel level) {
        player.getPersistentData().putBoolean("occka_flower_form_active", false);

        // Убираем активные эффекты формы
        player.removeEffect(MobEffects.NIGHT_VISION);
        player.removeEffect(MobEffects.MOVEMENT_SPEED);

        level.sendParticles(ParticleTypes.POOF,
                player.getX(), player.getY() + 1, player.getZ(),
                10, 0.5, 0.5, 0.5, 0.05);

        player.sendSystemMessage(AbilityCommon.msg("Nature Stance: OFF", ChatFormatting.GRAY));
    }

    /**
     * Вызывается каждый тик из AbilityEventHandler пока у игрока класс FLOWER.
     * Рисует след из цветов и поддерживает эффекты формы.
     */
    public static void tickFlowerForm(ServerPlayer player, ServerLevel level) {
        if (!player.getPersistentData().getBoolean("occka_flower_form_active"))
            return;

        // Поддерживаем эффекты пока форма активна
        player.addEffect(AbilityCommon.fx(MobEffects.NIGHT_VISION, 300, 0));
        player.addEffect(AbilityCommon.fx(MobEffects.MOVEMENT_SPEED, 25, 1)); // Speed II

        // Спавним след из цветов каждые 8 тиков (не слишком часто)
        if (player.tickCount % 8 != 0)
            return;

        BlockPos footPos = player.blockPosition();
        // Ищем блок под ногами (на котором стоит игрок)
        BlockPos ground = footPos.below();

        // Только если игрок на твёрдом блоке — ставим цветы
        if (!level.getBlockState(ground).isSolidRender(level, ground))
            return;
        if (!level.getBlockState(footPos).isAir())
            return; // не заменяем блоки

        // Небольшой шанс поставить высокий цветок, в основном маленькие
        boolean tall = RNG.nextInt(5) == 0; // 20% шанс высокого

        if (tall) {
            BlockState tallFlower = TALL_FLOWER_BLOCKS[RNG.nextInt(TALL_FLOWER_BLOCKS.length)];
            BlockPos above = footPos.above();
            // Высокий цветок требует 2 свободных блока
            if (level.getBlockState(footPos).isAir() && level.getBlockState(above).isAir()) {
                level.setBlock(footPos, tallFlower, 3);
                // Верхняя часть двойного блока
                try {
                    var topState = tallFlower.setValue(
                            net.minecraft.world.level.block.DoublePlantBlock.HALF,
                            net.minecraft.world.level.block.state.properties.DoubleBlockHalf.UPPER);
                    level.setBlock(above, topState, 3);
                } catch (Exception ignored) {
                    // Если setValue не доступен — просто ставим нижнюю часть
                }
                scheduleFlowerRemoval(player, footPos, level, true);
            }
        } else {
            BlockState flower = FLOWER_BLOCKS[RNG.nextInt(FLOWER_BLOCKS.length)];
            level.setBlock(footPos, flower, 3);
            scheduleFlowerRemoval(player, footPos, level, false);
        }

        // Частицы
        level.sendParticles(ParticleTypes.HAPPY_VILLAGER,
                footPos.getX() + 0.5, footPos.getY() + 0.5, footPos.getZ() + 0.5,
                2, 0.2, 0.2, 0.2, 0.02);
    }

    /**
     * Сохраняем позиции цветов для удаления через 5 секунд (100 тиков).
     */
    private static void scheduleFlowerRemoval(ServerPlayer player, BlockPos pos, ServerLevel level, boolean tall) {
        CompoundTag data = player.getPersistentData();
        ListTag list = data.getList("occka_flower_trail", Tag.TAG_COMPOUND);

        CompoundTag entry = new CompoundTag();
        entry.putLong("pos", pos.asLong());
        entry.putLong("removeAt", level.getGameTime() + 100); // 5 сек
        entry.putBoolean("tall", tall);
        list.add(entry);

        // Ограничиваем список 30 записями
        while (list.size() > 30)
            list.remove(0);

        data.put("occka_flower_trail", list);
    }

    /**
     * Тикает удаление цветов. Вызывается из AbilityEventHandler каждый тик.
     */
    public static void tickFlowerTrailCleanup(ServerPlayer player, ServerLevel level) {
        CompoundTag data = player.getPersistentData();
        ListTag list = data.getList("occka_flower_trail", Tag.TAG_COMPOUND);
        if (list.isEmpty())
            return;

        long now = level.getGameTime();
        List<CompoundTag> keep = new ArrayList<>();

        for (Tag t : list) {
            if (!(t instanceof CompoundTag entry))
                continue;
            long removeAt = entry.getLong("removeAt");
            BlockPos pos = BlockPos.of(entry.getLong("pos"));
            boolean tall = entry.getBoolean("tall");

            if (removeAt <= now) {
                // Удаляем цветок если он ещё стоит (игрок мог сломать сам)
                BlockState current = level.getBlockState(pos);
                if (!current.isAir() && !current.isSolidRender(level, pos)) {
                    level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
                }
                if (tall) {
                    BlockPos above = pos.above();
                    BlockState aboveState = level.getBlockState(above);
                    if (!aboveState.isAir() && !aboveState.isSolidRender(level, above)) {
                        level.setBlock(above, Blocks.AIR.defaultBlockState(), 3);
                    }
                }
                level.sendParticles(ParticleTypes.POOF,
                        pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                        2, 0.1, 0.1, 0.1, 0.02);
            } else {
                keep.add(entry);
            }
        }

        ListTag rebuilt = new ListTag();
        keep.forEach(rebuilt::add);
        data.put("occka_flower_trail", rebuilt);
    }

    /**
     * Сброс формы при смерти/смене класса.
     */
    public static void clearFlowerForm(Player player) {
        if (!player.getPersistentData().getBoolean("occka_flower_form_active"))
            return;
        player.getPersistentData().putBoolean("occka_flower_form_active", false);
        player.removeEffect(MobEffects.NIGHT_VISION);
        player.removeEffect(MobEffects.MOVEMENT_SPEED);
    }

    // ===== ABILITY: призыв 2 волков на 15 сек (кд 40 сек) =====

    public static void activateAbility(ServerPlayer player, ServerLevel level) {
        for (int i = 0; i < 2; i++) {
            Wolf wolf = new Wolf(EntityType.WOLF, level);

            double angle = (i / 2.0) * Math.PI * 2;
            wolf.moveTo(
                    player.getX() + 2.0 * Math.cos(angle),
                    player.getY(),
                    player.getZ() + 2.0 * Math.sin(angle),
                    RNG.nextFloat() * 360, 0);

            // Приручаем волка игроку
            wolf.setTame(true);
            wolf.setOwnerUUID(player.getUUID());
            wolf.setOrderedToSit(false);
            wolf.setPersistenceRequired();

            // Метим для авто-удаления
            wolf.getPersistentData().putBoolean("occka_flower_wolf", true);
            wolf.getPersistentData().putInt("occka_wolf_lifetime", 300); // 15 сек

            level.addFreshEntity(wolf);

            level.sendParticles(ParticleTypes.HAPPY_VILLAGER,
                    wolf.getX(), wolf.getY() + 0.5, wolf.getZ(),
                    8, 0.3, 0.3, 0.3, 0.05);
        }

        player.sendSystemMessage(AbilityCommon.msg(
                "2 wolves summoned! (15s)", ChatFormatting.GREEN, ChatFormatting.BOLD));
    }

    /**
     * Тикает удаление волков. Вызывается из AbilityEventHandler каждые 20 тиков.
     */
    public static void tickWolves(ServerPlayer player, ServerLevel level) {
        level.getEntitiesOfClass(Wolf.class,
                player.getBoundingBox().inflate(60),
                e -> e.getPersistentData().getBoolean("occka_flower_wolf")
                        && e.isOwnedBy(player))
                .forEach(wolf -> {
                    int life = wolf.getPersistentData().getInt("occka_wolf_lifetime") - 20;
                    if (life <= 0) {
                        level.sendParticles(ParticleTypes.POOF,
                                wolf.getX(), wolf.getY() + 0.5, wolf.getZ(),
                                5, 0.3, 0.3, 0.3, 0.05);
                        wolf.discard();
                    } else {
                        wolf.getPersistentData().putInt("occka_wolf_lifetime", life);
                    }
                });
    }

    // ===== ULT: превращение в 2 блока листвы на 10 сек =====

    public static void activateUlt(ServerPlayer player, ServerLevel level) {
        // Выбираем случайную листву
        BlockState leaf = LEAF_BLOCKS[RNG.nextInt(LEAF_BLOCKS.length)];

        Vec3 center = player.position();

        // Ставим 2 блока листвы (нижний и верхний — на месте игрока)
        BlockPos lower = BlockPos.containing(center.x, center.y, center.z);
        BlockPos upper = lower.above();

        // Убираем игрока на время (невидимость + сопротивление)
        player.addEffect(AbilityCommon.fx(MobEffects.INVISIBILITY, 220, 0));
        player.addEffect(AbilityCommon.fx(MobEffects.DAMAGE_RESISTANCE, 220, 4)); // Resistance V

        // Ставим блоки листвы
        // Сохраняем что было на этих позициях чтобы потом восстановить
        CompoundTag nbt = player.getPersistentData();
        nbt.putLong("occka_flower_ult_lower", lower.asLong());
        nbt.putLong("occka_flower_ult_upper", upper.asLong());
        nbt.putString("occka_flower_ult_leaf", net.minecraft.core.registries.BuiltInRegistries.BLOCK
                .getKey(leaf.getBlock()).toString());
        nbt.putInt("occka_flower_ult_ticks", 200); // 10 сек
        nbt.putDouble("occka_flower_ult_cx", center.x);
        nbt.putDouble("occka_flower_ult_cy", center.y);
        nbt.putDouble("occka_flower_ult_cz", center.z);

        level.setBlock(lower, leaf, 3);
        level.setBlock(upper, leaf, 3);

        // Частицы появления
        for (int i = 0; i < 30; i++) {
            double a = RNG.nextDouble() * Math.PI * 2;
            double r = RNG.nextDouble() * 1.5;
            level.sendParticles(ParticleTypes.HAPPY_VILLAGER,
                    center.x + r * Math.cos(a),
                    center.y + RNG.nextDouble() * 2,
                    center.z + r * Math.sin(a),
                    1, 0, 0, 0, 0.05);
        }
        level.sendParticles(ParticleTypes.COMPOSTER,
                center.x, center.y + 1, center.z, 20, 1, 1, 1, 0.1);

        player.sendSystemMessage(AbilityCommon.msg(
                "LEAF FORM! 10s...", ChatFormatting.DARK_GREEN, ChatFormatting.BOLD));
    }

    /**
     * Тикает ульту — телепортирует игрока в центр листвы каждый тик, убирает блоки
     * по истечении.
     * Вызывается из AbilityEventHandler каждый тик.
     */
    public static void tickUlt(ServerPlayer player, ServerLevel level) {
        CompoundTag nbt = player.getPersistentData();
        int ticks = nbt.getInt("occka_flower_ult_ticks");
        if (ticks <= 0)
            return;

        ticks--;
        nbt.putInt("occka_flower_ult_ticks", ticks);

        double cx = nbt.getDouble("occka_flower_ult_cx");
        double cy = nbt.getDouble("occka_flower_ult_cy");
        double cz = nbt.getDouble("occka_flower_ult_cz");

        // Телепортируем игрока в центр листвы каждые 20 тиков (1 раз в секунду)
        if (ticks % 20 == 0 && ticks > 0) {
            // Небольшое смещение чтоб не глитчило в блоке
            player.teleportTo(cx, cy, cz);

            BlockPos lower = BlockPos.of(nbt.getLong("occka_flower_ult_lower"));
            BlockPos upper = BlockPos.of(nbt.getLong("occka_flower_ult_upper"));
            String leafKey = nbt.getString("occka_flower_ult_leaf");
            try {
                var leafBlock = net.minecraft.core.registries.BuiltInRegistries.BLOCK
                        .get(new net.minecraft.resources.ResourceLocation(leafKey));
                if (leafBlock != null) {
                    BlockState leaf = leafBlock.defaultBlockState();
                    level.setBlock(lower, leaf, 3);
                    level.setBlock(upper, leaf, 3);
                }
            } catch (Exception ignored) {
            }

            //level.sendParticles(ParticleTypes.COMPOSTER,
            //        cx + 0.5, cy + 1, cz + 0.5, 5, 0.3, 0.3, 0.3, 0.05);
        }

        // Конец ульты
        if (ticks == 0) {
            BlockPos lower = BlockPos.of(nbt.getLong("occka_flower_ult_lower"));
            BlockPos upper = BlockPos.of(nbt.getLong("occka_flower_ult_upper"));

            // Убираем блоки листвы
            BlockState lowerState = level.getBlockState(lower);
            BlockState upperState = level.getBlockState(upper);

            // Проверяем что там ещё листва (не заменили)
            if (lowerState.is(net.minecraft.tags.BlockTags.LEAVES)) {
                level.setBlock(lower, Blocks.AIR.defaultBlockState(), 3);
            }
            if (upperState.is(net.minecraft.tags.BlockTags.LEAVES)) {
                level.setBlock(upper, Blocks.AIR.defaultBlockState(), 3);
            }

            // Частицы конца
            for (int i = 0; i < 20; i++) {
                double a = RNG.nextDouble() * Math.PI * 2;
                double r = RNG.nextDouble() * 1.5;
                level.sendParticles(ParticleTypes.POOF,
                        cx + r * Math.cos(a),
                        cy + RNG.nextDouble() * 2,
                        cz + r * Math.sin(a),
                        1, 0, 0, 0, 0.05);
            }

            player.sendSystemMessage(AbilityCommon.msg("Leaf Form ended.", ChatFormatting.GREEN));
        }
    }
}