package common;

import java.util.List;
import model.Entity;
import model.EntityType;
import model.PlayerView;
import model.Vec2Int;

public class BuildTask {
    private Entity builder;
    private Vec2Int buildCorner;
    private Vec2Int buildPosition;
    private BuildState state;
    private EntityType type;
    private int buildId;
    private List<Entity> builders;

    public BuildTask(Vec2Int buildCorner, Vec2Int buildPosition, Entity builder, EntityType type) {
        this.buildCorner = buildCorner;
        this.builder = builder;
        this.buildPosition = buildPosition;
        this.type = type;

        state = BuildState.MOVING;
    }

    public void updateBuilderPosition(Entity entity) {
        if (entity.equals(builder)) {
            builder.setPosition(entity.getPosition());
            if (readyForBuild()) {
                state = BuildState.READY_FOR_BUILD;
            }
        }
    }

    public Entity getBuilder() {
        return builder;
    }

    public BuildState getState() {
        return state;
    }

    public Vec2Int getBuildPosition() {
        return buildPosition;
    }

    public Vec2Int getBuildCorner() {
        return buildCorner;
    }

    private boolean readyForBuild() {
        return buildPosition.equals(builder.getPosition());
    }

    public void updateStatus(Entity[][] map, PlayerView view) {
        var entity = map[buildCorner.getX()][buildCorner.getY()];
        if (entity != null && entity.getEntityType() == type) {
            buildId = entity.getId();
            if (entity.getHealth() < view.getEntityProperties().get(type).getMaxHealth()) {
                state = BuildState.REPAIRING;
            } else {
                state = BuildState.DONE;
            }
        }
    }

    public EntityType getType() {
        return type;
    }

    public int getBuildId() {
        return buildId;
    }

    public void setBuildPosition(Vec2Int buildPosition) {
        this.buildPosition = buildPosition;
    }
}
