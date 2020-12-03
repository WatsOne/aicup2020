import common.BuildState;
import common.BuildTask;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import model.Action;
import model.AttackAction;
import model.AutoAttack;
import model.BuildAction;
import model.Color;
import model.ColoredVertex;
import model.DebugCommand;
import model.DebugCommand.Add;
import model.DebugData.PlacedText;
import model.Entity;
import model.EntityAction;
import model.EntityType;
import model.MoveAction;
import model.PlayerView;
import model.RepairAction;
import model.Vec2Float;
import model.Vec2Int;

public class MyStrategy2 {

    private Entity[][] entities = null;

    Set<Entity> resourceWorkers = new HashSet<>();
    Set<BuildTask> buildTasks = new HashSet<>();

    Integer resourceCount = 0;

    public Action getAction(PlayerView playerView, DebugInterface debugInterface) {
        var actionMap = new HashMap<Integer, EntityAction>();

        resourceCount = Stream.of(playerView.getPlayers()).filter(p -> p.getId() == playerView.getMyId()).findFirst().get().getResource();

        entities = new Entity[playerView.getMapSize()][playerView.getMapSize()];
        updateMatrix(playerView);

        var populationMax = 0;
        var populationUse = 0;

        var builders = getBuilders();

        for (Entity entity : playerView.getEntities()) {
            if (entity.getPlayerId() == null || entity.getPlayerId() != playerView.getMyId()) {
                continue;
            }

            populationMax += playerView.getEntityProperties().get(entity.getEntityType()).getPopulationProvide();
            populationUse += playerView.getEntityProperties().get(entity.getEntityType()).getPopulationUse();

            updatePositionForBuilders(entity);

            switch (entity.getEntityType()) {
                case BUILDER_UNIT:
                    if (!builders.contains(entity)) {
                        resourceWorkers.add(entity);
                    }
                    break;
                case BUILDER_BASE:
                    if (populationUse <= populationMax) {
                        actionMap.put(entity.getId(), new EntityAction(null, new BuildAction(EntityType.BUILDER_UNIT, new Vec2Int(entity.getPosition().getX() + 5, entity.getPosition().getY() + 1)), null, null));
                    }
            }
        }

        if (resourceCount > 100) {
            findPlaceForHouse().forEach(c -> {
                if (buildTasks.size() < resourceWorkers.size() / 3) {
                    var builderPos = findClosestBuildPosition(c, 3, playerView.getMapSize());
                    if (builderPos != null) {
                        var builder = resourceWorkers.iterator().next();

                        buildTasks.forEach(t -> {
                            if (t.getBuildCorner().equals(c)) {
                                System.out.println("WTF");
                            }
                        });

                        if (checkCornerHouse(c)) {
                            buildTasks.add(new BuildTask(c, builderPos, builder, EntityType.HOUSE));
                            resourceWorkers.remove(builder);
                        }
                    }
                }
            });
        }

        buildTasks.forEach(t -> {
            t.updateStatus(entities, playerView);
            switch (t.getState()) {
                case MOVING:
                    actionMap.put(t.getBuilder().getId(), new EntityAction(new MoveAction(t.getBuildPosition(), false, false), null, null, null));
                    break;
                case READY_FOR_BUILD:
                    actionMap.put(t.getBuilder().getId(), new EntityAction(null, new BuildAction(EntityType.HOUSE, t.getBuildCorner()), null, null));
                    break;
                case REPAIRING:
                    actionMap.put(t.getBuilder().getId(), new EntityAction(null, null, null, new RepairAction(t.getBuildId())));
                    break;
            }
        });

        updateBuildersPosition(playerView.getMapSize());
        cleanBuildTask();
        activateResourceWorkers(actionMap);

        return new Action(actionMap);
    }

    private void cleanBuildTask() {
        buildTasks.removeIf(t -> t.getState() == BuildState.DONE);
    }

    private void updatePositionForBuilders(Entity entity) {
        buildTasks.forEach(t -> t.updateBuilderPosition(entity));
    }

    private Set<Entity> getBuilders() {
        return buildTasks.stream().map(BuildTask::getBuilder).collect(Collectors.toSet());
    }

    private void activateResourceWorkers(HashMap<Integer, EntityAction> actionHashMap) {
        resourceWorkers.forEach(w ->
                actionHashMap.put(w.getId(), new EntityAction(null, null, new AttackAction(null, new AutoAttack(80, new EntityType[]{EntityType.RESOURCE})), null)));
    }

    private void updateBuildersPosition(int mapSize) {
        buildTasks.forEach(t -> {
            var currentPos = t.getBuildPosition();
            if (!cellIsFree(currentPos.getX(), currentPos.getY(), mapSize)) {
                var newPosition = findClosestBuildPosition(t.getBuildCorner(), 3, mapSize);
                if (newPosition != null) {
                    t.setBuildPosition(newPosition);
                }
            }
        });
    }

    private Vec2Int findClosestBuildPosition(Vec2Int buildPosition, int size, int mapSize) {
        var y = buildPosition.getY() - 1;
        for (int x = buildPosition.getX(); x < buildPosition.getX() + size; x++) {
            if (cellIsFree(x, y, mapSize)) {
                return new Vec2Int(x, y);
            }
        }
        y = buildPosition.getY() + size;
        for (int x = buildPosition.getX(); x < buildPosition.getX() + size; x++) {
            if (cellIsFree(x, y, mapSize)) {
                return new Vec2Int(x, y);
            }
        }
        var x = buildPosition.getX() - 1;
        for (y = buildPosition.getY(); y < buildPosition.getY() + size; y++) {
            if (cellIsFree(x, y, mapSize)) {
                return new Vec2Int(x, y);
            }
        }
        x = buildPosition.getX() + size;
        for (y = buildPosition.getY(); y < buildPosition.getY() + size; y++) {
            if (cellIsFree(x, y, mapSize)) {
                return new Vec2Int(x, y);
            }
        }

        return null;
    }

    private boolean cellIsFree(int x, int y, int mapSize) {
        if (x >= 0 && x < mapSize && y >= 0 && y < mapSize) {
            var entity = entities[x][y];
            return entity == null || entity.getEntityType() == EntityType.BUILDER_UNIT || entity.getEntityType() == EntityType.MELEE_UNIT
                    || entity.getEntityType() == EntityType.RANGED_UNIT;
        }

        return false;
    }

    private boolean checkCornerHouse(Vec2Int pos) {
        var houseIsInCorner = entities[0][0];
        if (pos.equals(new Vec2Int(3,0)) || pos.equals(new Vec2Int(0, 3))) {
            return houseIsInCorner != null && houseIsInCorner.getEntityType() == EntityType.HOUSE;
        }

        return true;
    }

    private List<Vec2Int> findPlaceForHouse() {
        List<Vec2Int> corners = new ArrayList<>();
        for (int x = 0; x <= 21; x+=3) {
            var corner = new Vec2Int(x, 0);
            if (placeIsFree(corner, 3)) {
                corners.add(corner);
            }
        }
        for (int y = 3; y <= 21; y+=3) {
            var corner = new Vec2Int(0, y);
            if (placeIsFree(corner, 3)) {
                corners.add(corner);
            }
        }
        for (int y = 4; y <= 21; y+=4) {
            var corner = new Vec2Int(11, y);
            if (placeIsFree(corner, 3)) {
                corners.add(corner);
            }
        }

        return corners;
    }

    private boolean placeIsFree(Vec2Int startPoint, int size) {
        for (int x = startPoint.getX(); x < startPoint.getX() + size; x++) {
            for (int y = startPoint.getY(); y < startPoint.getY() + size; y++) {
                var entity = entities[x][y];
                if (entity != null && entity.getEntityType() != EntityType.BUILDER_UNIT && entity.getEntityType() != EntityType.MELEE_UNIT && entity.getEntityType() != EntityType.RANGED_UNIT) {
                    return false;
                }
            }
        }

        return true;
    }

    private void updateMatrix(PlayerView playerView) {
        buildTasks.stream().filter(t -> t.getState() == BuildState.MOVING || t.getState() == BuildState.READY_FOR_BUILD).forEach(t -> {
            var buildCorner = t.getBuildCorner();
            var size = playerView.getEntityProperties().get(t.getType()).getSize();
            var fakeEntity = new Entity();
            fakeEntity.setEntityType(EntityType.FEATURE_BUILDING);
            for (int x = buildCorner.getX(); x < buildCorner.getX() + size; x++) {
                for (int y = buildCorner.getY(); y < buildCorner.getY() + size; y++) {
                    entities[x][y] = fakeEntity;
                }
            }
        });

        for (Entity entity : playerView.getEntities()) {
            var size = playerView.getEntityProperties().get(entity.getEntityType()).getSize();
            for (int x = entity.getPosition().getX(); x < entity.getPosition().getX() + size; x++) {
                for (int y = entity.getPosition().getY(); y < entity.getPosition().getY() + size; y++) {
                    entities[x][y] = entity;
                }
            }
        }
    }

    public void debugUpdate(PlayerView playerView, DebugInterface debugInterface) {
        debugInterface.send(new DebugCommand.Clear());
        var text = new PlacedText(new ColoredVertex(null, new Vec2Float(200f, 30f), new Color(1f, 0f, 0f, 1f)), resourceCount.toString(), 0.5f, 70);
        var text2 = new PlacedText(new ColoredVertex(null, new Vec2Float(200f, 90f), new Color(1f, 0f, 0f, 1f)), String.valueOf(buildTasks.size()), 0.5f, 70);
        debugInterface.send(new Add(text));
        debugInterface.send(new Add(text2));
    }
}
