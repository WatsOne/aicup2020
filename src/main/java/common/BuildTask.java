package common;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import model.Entity;
import model.EntityType;
import model.PlayerView;
import model.Vec2Int;

public class BuildTask implements Comparable<BuildTask> {
    private Vec2Int buildCorner;
    private BuildState state;
    private EntityType type;
    private int buildId;

    private List<BuildPosition> builders;

    public BuildTask(Vec2Int buildCorner, List<BuildPosition> builders, EntityType type) {
        this.buildCorner = buildCorner;
        this.type = type;
        this.builders = builders;

        state = BuildState.MOVING;
    }

    public BuildState getState() {
        return state;
    }

    public Vec2Int getBuildCorner() {
        return buildCorner;
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

//    public void setBuildPosition(Vec2Int buildPosition) {
//        this.buildPosition = buildPosition;
//    }

//    public void setBuilder(Entity entity, Vec2Int position) {
//        if (state == BuildState.WAIT_FOR_BUILDER) {
//            state = BuildState.MOVING;
//        }
//        builders.add(new BuildPosition(entity, position));
//    }

    public List<BuildPosition> getBuilders() {
        return builders;
    }

    public Set<Entity> getOnlyBuilders() {
        return builders.stream().map(BuildPosition::getEntity).collect(Collectors.toSet());
    }

//    public Set<Vec2Int> getOnlyPositions() {
//        return builders.stream().map(BuildPosition::getBuildPosition).collect(Collectors.toSet());
//    }

    @Override
    public int compareTo(BuildTask o) {
        return builders.size() - o.getBuilders().size();
    }
}
