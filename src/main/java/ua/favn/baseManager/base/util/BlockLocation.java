package ua.favn.baseManager.base.util;

import java.util.Objects;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;

public record BlockLocation(World world, int x, int y, int z) {
   public Location getLocation() {
      return new Location(this.world, (double)this.x, (double)this.y, (double)this.z);
   }

   public Chunk getChunk() {
      return this.world.getChunkAt(this.getChunkX(), this.getChunkZ());
   }

   public boolean isInChunk(Chunk chunk) {
      return chunk.getZ() == this.getChunkZ() && chunk.getX() == this.getChunkX();
   }

   public int getChunkZ() {
      return this.z / 16;
   }

   public int getChunkX() {
      return this.x / 16;
   }

   public static BlockLocation fromLocation(Location location) {
      return new BlockLocation(location.getWorld(), location.getBlockX(), location.getBlockY(), location.getBlockZ());
   }

   @Override
   public boolean equals(Object object) {
      if (this == object) {
         return true;
      } else if (object != null && this.getClass() == object.getClass()) {
         BlockLocation that = (BlockLocation)object;
         return this.x == that.x && this.y == that.y && this.z == that.z && Objects.equals(this.world.getName(), that.world.getName());
      } else {
         return false;
      }
   }

   @Override
   public int hashCode() {
      return Objects.hash(this.world.getName(), this.x, this.y, this.z);
   }
}
