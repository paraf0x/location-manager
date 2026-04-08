package ua.favn.baseManager.lodestone;

import com.destroystokyo.paper.profile.PlayerProfile;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Skull;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.UUID;

/**
 * Scans the block structure above a lodestone to find player heads.
 * Uses BFS with 6-directional adjacency. Any non-air block is part of the network.
 * PLAYER_HEAD and PLAYER_WALL_HEAD blocks contribute their owner as a member.
 */
public final class StructureScanner {

    private static final BlockFace[] SCAN_FACES = {
        BlockFace.UP, BlockFace.DOWN,
        BlockFace.NORTH, BlockFace.SOUTH,
        BlockFace.EAST, BlockFace.WEST
    };

    private StructureScanner() {
    }

    /**
     * Scans the structure above the lodestone for player heads.
     *
     * @param lodestone The lodestone block (excluded from traversal to prevent scanning downward)
     * @param maxBlocks Maximum number of blocks to visit
     * @return Map of UUID to player name for all found player heads with valid owners
     */
    public static Map<UUID, String> scanForPlayerHeads(Block lodestone, int maxBlocks) {
        Map<UUID, String> foundMembers = new LinkedHashMap<>();
        Block startBlock = lodestone.getRelative(BlockFace.UP);

        if (startBlock.getType().isAir()) {
            return foundMembers;
        }

        Set<Long> visited = new HashSet<>();
        Queue<Block> queue = new ArrayDeque<>();

        // Exclude lodestone from traversal to prevent scanning downward
        visited.add(blockKey(lodestone));
        visited.add(blockKey(startBlock));
        queue.add(startBlock);

        int blocksScanned = 0;

        while (!queue.isEmpty() && blocksScanned < maxBlocks) {
            Block current = queue.poll();
            blocksScanned++;

            if (isPlayerHead(current.getType())) {
                collectHeadOwner(current, foundMembers);
            }

            for (BlockFace face : SCAN_FACES) {
                Block neighbor = current.getRelative(face);
                long key = blockKey(neighbor);

                if (!visited.contains(key) && !neighbor.getType().isAir()) {
                    visited.add(key);
                    queue.add(neighbor);
                }
            }
        }

        return foundMembers;
    }

    private static boolean isPlayerHead(Material material) {
        return material == Material.PLAYER_HEAD || material == Material.PLAYER_WALL_HEAD;
    }

    private static void collectHeadOwner(Block headBlock, Map<UUID, String> members) {
        if (!(headBlock.getState() instanceof Skull skull)) {
            return;
        }

        // Use getOwningPlayer() for reliable UUID detection
        OfflinePlayer owner = skull.getOwningPlayer();
        if (owner == null || owner.getUniqueId() == null) {
            return;
        }

        UUID uuid = owner.getUniqueId();

        // Prefer name from player profile (stored in block data, works for players who never joined)
        // Fall back to OfflinePlayer.getName(), then truncated UUID
        String name = null;
        PlayerProfile profile = skull.getPlayerProfile();
        if (profile != null && profile.getName() != null) {
            name = profile.getName();
        }
        if (name == null) {
            name = owner.getName();
        }
        if (name == null) {
            name = uuid.toString().substring(0, 8);
        }

        members.put(uuid, name);
    }

    /**
     * Pack block coordinates into a long for efficient set operations.
     */
    private static long blockKey(Block block) {
        return ((long) block.getX() & 0x3FFFFFFL)
             | (((long) block.getY() & 0xFFFL) << 26)
             | (((long) block.getZ() & 0x3FFFFFFL) << 38);
    }
}
