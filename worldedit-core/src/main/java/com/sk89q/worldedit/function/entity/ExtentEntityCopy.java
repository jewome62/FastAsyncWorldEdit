/*
 * WorldEdit, a Minecraft world manipulation toolkit
 * Copyright (C) sk89q <http://www.sk89q.com>
 * Copyright (C) WorldEdit team and contributors
 *
 * This program is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as published by the
 * Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License
 * for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */

package com.sk89q.worldedit.function.entity;

import static com.google.common.base.Preconditions.checkNotNull;

import com.sk89q.jnbt.CompoundTag;
import com.sk89q.jnbt.CompoundTagBuilder;
import com.sk89q.worldedit.WorldEditException;
import com.sk89q.worldedit.entity.BaseEntity;
import com.sk89q.worldedit.entity.Entity;
import com.sk89q.worldedit.extent.Extent;
import com.sk89q.worldedit.function.EntityFunction;
import com.sk89q.worldedit.internal.helper.MCDirections;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.math.Vector3;
import com.sk89q.worldedit.math.transform.Transform;
import com.sk89q.worldedit.util.Direction;
import com.sk89q.worldedit.util.Direction.Flag;
import com.sk89q.worldedit.util.Location;

/**
 * Copies entities provided to the function to the provided destination
 * {@code Extent}.
 */
public class ExtentEntityCopy implements EntityFunction {

    private final Extent destination;
    private final Vector3 from;
    private final Vector3 to;
    private final Transform transform;
    private boolean removing;

    /**
     * Create a new instance.
     *
     * @param from the from position
     * @param destination the destination {@code Extent}
     * @param to the destination position
     * @param transform the transformation to apply to both position and orientation
     */
    public ExtentEntityCopy(Vector3 from, Extent destination, Vector3 to, Transform transform) {
        checkNotNull(from);
        checkNotNull(destination);
        checkNotNull(to);
        checkNotNull(transform);
        this.destination = destination;
        this.from = from;
        this.to = to;
        this.transform = transform;
    }

    /**
     * Return whether entities that are copied should be removed.
     *
     * @return true if removing
     */
    public boolean isRemoving() {
        return removing;
    }

    /**
     * Set whether entities that are copied should be removed.
     *
     * @param removing true if removing
     */
    public void setRemoving(boolean removing) {
        this.removing = removing;
    }

    @Override
    public boolean apply(Entity entity) throws WorldEditException {
        BaseEntity state = entity.getState();
        if (state != null) {
            Location newLocation;
            Location location = entity.getLocation();

            Vector3 pivot = from.round().add(0.5, 0.5, 0.5);
            Vector3 newPosition = transform.apply(location.toVector().subtract(pivot));
            Vector3 newDirection;

            newDirection = transform.isIdentity() ?
                    entity.getLocation().getDirection()
                    : transform.apply(location.getDirection()).subtract(transform.apply(Vector3.ZERO)).normalize();
            newLocation = new Location(destination, newPosition.add(to.round().add(0.5, 0.5, 0.5)), newDirection);

            // Some entities store their position data in NBT
            state = transformNbtData(state);

            boolean success = destination.createEntity(newLocation, state) != null;

            // Remove
            if (isRemoving() && success) {
                entity.remove();
            }

            return success;
        } else {
            return false;
        }
    }

    /**
     * Transform NBT data in the given entity state and return a new instance
     * if the NBT data needs to be transformed.
     *
     * @param state the existing state
     * @return a new state or the existing one
     */
    private BaseEntity transformNbtData(BaseEntity state) {
        CompoundTag tag = state.getNbtData();

        if (tag != null) {
            // Handle hanging entities (paintings, item frames, etc.)
            boolean hasTilePosition = tag.containsKey("TileX") && tag.containsKey("TileY") && tag.containsKey("TileZ");
            boolean hasDirection = tag.containsKey("Direction");
            boolean hasLegacyDirection = tag.containsKey("Dir");
            boolean hasFacing = tag.containsKey("Facing");

            if (hasTilePosition) {
                Vector3 tilePosition = Vector3.at(tag.asInt("TileX"), tag.asInt("TileY"), tag.asInt("TileZ"));
                BlockVector3 newTilePosition = transform.apply(tilePosition.subtract(from)).add(to).toBlockPoint();

                CompoundTagBuilder builder = tag.createBuilder()
                        .putInt("TileX", newTilePosition.getBlockX())
                        .putInt("TileY", newTilePosition.getBlockY())
                        .putInt("TileZ", newTilePosition.getBlockZ());

                if (hasDirection || hasLegacyDirection || hasFacing) {
                    Direction direction;
                    if (hasDirection) {
                        direction = MCDirections.fromPre13Hanging(tag.asInt("Direction"));
                    } else if (hasLegacyDirection) {
                        direction = MCDirections.fromPre13Hanging(
                            MCDirections.fromLegacyHanging((byte) tag.asInt("Dir"))
                        );
                    } else {
                        direction = MCDirections.fromHanging(tag.asInt("Facing"));
                    }

                    if (direction != null) {
                        Vector3 vector = transform.apply(direction.toVector()).subtract(transform.apply(Vector3.ZERO)).normalize();
                        Direction newDirection = Direction.findClosest(vector, Flag.CARDINAL);

                        if (newDirection != null) {
                            builder.putByte("Direction", (byte) MCDirections.toPre13Hanging(newDirection));
                            builder.putByte("Facing", (byte) MCDirections.toHanging(newDirection));
                            builder.putByte("Dir", MCDirections.toLegacyHanging(MCDirections.toHanging(newDirection)));
                        }
                    }
                }

                return new BaseEntity(state.getType(), builder.build());
            }
        }

        return state;
    }

}
