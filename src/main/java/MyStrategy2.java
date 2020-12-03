import common.BuildState;
import common.BuildTask;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
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

    private static final int BUILDERS_MAX = 8;

    private Entity[][] entities = null;

    Set<Entity> resourceWorkers = new HashSet<>();
    Set<Entity> buildWorkers = new HashSet<>();
    Set<Entity> allBuilders = new HashSet<>();

    Set<BuildTask> buildTasks = new HashSet<>();

    Integer resourceCount = 0;

    public Action getAction(PlayerView playerView, DebugInterface debugInterface) {
        var actionMap = new HashMap<Integer, EntityAction>();

        resourceCount = Stream.of(playerView.getPlayers()).filter(p -> p.getId() == playerView.getMyId()).findFirst().get().getResource();

        entities = new Entity[playerView.getMapSize()][playerView.getMapSize()];
        updateMatrix(playerView);

        var populationMax = 0;
        var populationUse = 0;

        for (Entity entity : playerView.getEntities()) {
            if (entity.getPlayerId() == null || entity.getPlayerId() != playerView.getMyId()) {
                continue;
            }

            populationMax += playerView.getEntityProperties().get(entity.getEntityType()).getPopulationProvide();
            populationUse += playerView.getEntityProperties().get(entity.getEntityType()).getPopulationUse();

            switch (entity.getEntityType()) {
                case BUILDER_UNIT:
                    allBuilders.add(entity);
                    if (!buildWorkers.contains(entity)) {
                        resourceWorkers.add(entity);
                    }
                    updatePositions(entity);
                    break;
                case BUILDER_BASE:
                    if (populationUse <= populationMax && resourceWorkers.size() < 35) {
                        actionMap.put(entity.getId(), new EntityAction(null, new BuildAction(EntityType.BUILDER_UNIT, new Vec2Int(entity.getPosition().getX() + 5, entity.getPosition().getY())), null, null));
                    } else {
                        actionMap.put(entity.getId(), new EntityAction(null, null, null,null));
                    }
                    break;
                case RANGED_BASE:
                    if (populationUse <= populationMax) {
                        actionMap.put(entity.getId(), new EntityAction(null, new BuildAction(EntityType.RANGED_UNIT, new Vec2Int(entity.getPosition().getX() + 5, entity.getPosition().getY())), null, null));
                    } else {
                        actionMap.put(entity.getId(), new EntityAction(null, null, null,null));
                    }
                    break;
                case MELEE_UNIT:
                    actionMap.put(entity.getId(), new EntityAction(new MoveAction(getRandomCorner(), true, true), null, new AttackAction(null, new AutoAttack(playerView.getMaxPathfindNodes(), new EntityType[]{EntityType.MELEE_UNIT, EntityType.RANGED_UNIT, EntityType.BUILDER_UNIT, EntityType.MELEE_BASE, EntityType.RANGED_BASE, EntityType.BUILDER_BASE, EntityType.HOUSE, EntityType.WALL, EntityType.TURRET})), null));
                    break;
                case RANGED_UNIT:
                    actionMap.put(entity.getId(), new EntityAction(new MoveAction(getRandomCorner(), true, true), null, new AttackAction(null, new AutoAttack(playerView.getMaxPathfindNodes(), new EntityType[]{EntityType.MELEE_UNIT, EntityType.RANGED_UNIT, EntityType.BUILDER_UNIT, EntityType.MELEE_BASE, EntityType.RANGED_BASE, EntityType.BUILDER_BASE, EntityType.HOUSE, EntityType.WALL, EntityType.TURRET})), null));
                    break;

            }
        }

        buildWorkers.removeIf(w -> !allBuilders.contains(w));

        if (populationMax - populationUse < 6) {
            findPlaceForHouse().forEach(c -> {
                var exist = buildTasks.stream().filter(t -> t.getBuildCorner().equals(c)).findAny();
                if (exist.isEmpty()) {
                    buildTasks.add(new BuildTask(c, EntityType.HOUSE));
                }
            });
        }

        if (!buildTasks.isEmpty() && buildWorkers.size() <= BUILDERS_MAX) {
            while (buildWorkers.size() < Math.min(resourceWorkers.size() / 4,  BUILDERS_MAX)) {
                var worker = resourceWorkers.iterator().next();
                resourceWorkers.remove(worker);
                buildWorkers.add(worker);
            }
        }

        Set<Entity> busyBuilders = buildTasks.stream().flatMap(t -> t.getOnlyBuilders().stream()).collect(Collectors.toSet());
        Set<Entity> freeBuilders = new HashSet<>(buildWorkers);
        freeBuilders.removeIf(busyBuilders::contains);
        buildTasks.stream().filter(t -> t.getState() != BuildState.DONE).sorted(Comparator.reverseOrder()).forEach(t -> {
            while (t.getBuilders().size() < 2 && !freeBuilders.isEmpty()) {
                var builder = freeBuilders.iterator().next();
                var builderPos = findClosestBuildPosition(t.getBuildCorner(), 3, playerView.getMapSize(), t.getOnlyPositions());
                if (builderPos != null) {
                    t.setBuilder(builder, builderPos);
                    freeBuilders.remove(builder);
                }
            }
        });

        buildTasks.forEach(t -> {
            t.updateStatus(entities, playerView);
            switch (t.getState()) {
                case MOVING:
                    t.getBuilders().forEach(b -> {
                        actionMap.put(b.getEntity().getId(), new EntityAction(new MoveAction(b.getBuildPosition(), false, false), new BuildAction(EntityType.HOUSE, t.getBuildCorner()), null, null));
                    });
                    break;
                case REPAIRING:
                    t.getBuilders().forEach(b -> {
                        actionMap.put(b.getEntity().getId(), new EntityAction(new MoveAction(b.getBuildPosition(), false, false), new BuildAction(EntityType.HOUSE, t.getBuildCorner()), null, new RepairAction(t.getBuildId())));
                    });
                    break;
            }
        });

        if (buildTasks.isEmpty()) {
            resourceWorkers.addAll(buildWorkers);
            buildWorkers.clear();
        }

        //updateBuildersPosition(playerView.getMapSize());
        cleanBuildTask();
        activateResourceWorkers(actionMap);

        return new Action(actionMap);
    }

    private void cleanBuildTask() {
        buildTasks.removeIf(t -> t.getState() == BuildState.DONE);
    }

    private void updatePositions(Entity entity) {
        resourceWorkers.forEach(w -> {
            if (w.equals(entity)) {
                w.setPosition(entity.getPosition());
            }
        });
        buildWorkers.forEach(w -> {
            if (w.equals(entity)) {
                w.setPosition(entity.getPosition());
            }
        });
    }

    private void activateResourceWorkers(HashMap<Integer, EntityAction> actionHashMap) {
        resourceWorkers.forEach(w ->
                actionHashMap.put(w.getId(), new EntityAction(null, null, new AttackAction(null, new AutoAttack(80, new EntityType[]{EntityType.RESOURCE})), null)));
    }

    private Vec2Int getRandomCorner() {
        var r = ThreadLocalRandom.current().nextInt(0,3);
        if (r == 0) {
            return new Vec2Int(79, 79);
        } else if (r == 1) {
            return new Vec2Int(79, 0);
        } else {
            return new Vec2Int(0, 79);
        }
    }

//    private void updateBuildersPosition(int mapSize) {
//        buildTasks.forEach(t -> {
//            var currentPos = t.getBuildPosition();
//            if (!cellIsFree(currentPos.getX(), currentPos.getY(), mapSize)) {
//                var newPosition = findClosestBuildPosition(t.getBuildCorner(), 3, mapSize);
//                if (newPosition != null) {
//                    t.setBuildPosition(newPosition);
//                }
//            }
//        });
//    }

    private Vec2Int findClosestBuildPosition(Vec2Int buildPosition, int size, int mapSize, Set<Vec2Int> occupiedPositions) {
        var y = buildPosition.getY() - 1;
        for (int x = buildPosition.getX(); x < buildPosition.getX() + size; x++) {
            var res = new Vec2Int(x, y);
            if (cellIsFree(x, y, mapSize) && !occupiedPositions.contains(res)) {
                return res;
            }
        }
        y = buildPosition.getY() + size;
        for (int x = buildPosition.getX(); x < buildPosition.getX() + size; x++) {
            var res = new Vec2Int(x, y);
            if (cellIsFree(x, y, mapSize) && !occupiedPositions.contains(res)) {
                return res;
            }
        }
        var x = buildPosition.getX() - 1;
        for (y = buildPosition.getY(); y < buildPosition.getY() + size; y++) {
            var res = new Vec2Int(x, y);
            if (cellIsFree(x, y, mapSize) && !occupiedPositions.contains(res)) {
                return res;
            }
        }
        x = buildPosition.getX() + size;
        for (y = buildPosition.getY(); y < buildPosition.getY() + size; y++) {
            var res = new Vec2Int(x, y);
            if (cellIsFree(x, y, mapSize) && !occupiedPositions.contains(res)) {
                return res;
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

    private List<Vec2Int> findPlaceForHouse() {
        List<Vec2Int> corners = new ArrayList<>();
        var corner = new Vec2Int(0, 0);
        if (placeIsFree(corner, 3)) {
            corners.add(corner);
        }
        for (int x = 4; x <= 22; x+=3) {
            corner = new Vec2Int(x, 0);
            if (placeIsFree(corner, 3)) {
                corners.add(corner);
            }
        }
        for (int y = 4; y <= 22; y+=3) {
            corner = new Vec2Int(0, y);
            if (placeIsFree(corner, 3)) {
                corners.add(corner);
            }
        }
        for (int y = 5; y <= 22; y+=4) {
            corner = new Vec2Int(11, y);
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
        buildTasks.stream().filter(t -> t.getState() == BuildState.MOVING).forEach(t -> {
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
