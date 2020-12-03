package common;

import model.Entity;
import model.Vec2Int;

public class BuildPosition {
    private Entity entity;
    private Vec2Int buildPosition;

    public BuildPosition(Entity entity, Vec2Int buildPosition) {
        this.entity = entity;
        this.buildPosition = buildPosition;
    }

    public Entity getEntity() {
        return entity;
    }

    public void setEntity(Entity entity) {
        this.entity = entity;
    }

    public Vec2Int getBuildPosition() {
        return buildPosition;
    }

    public void setBuildPosition(Vec2Int buildPosition) {
        this.buildPosition = buildPosition;
    }
}
