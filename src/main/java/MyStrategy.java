import common.BuildPosition;
import common.BuildState;
import common.BuildTask;
import common.FindingPositionResult;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
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
import model.DebugData.Primitives;
import model.Entity;
import model.EntityAction;
import model.EntityType;
import model.MoveAction;
import model.PlayerView;
import model.PrimitiveType;
import model.RepairAction;
import model.Vec2Float;
import model.Vec2Int;

public class MyStrategy {

    private static final int BUILDERS_MAX = 8;

    private Entity[][] entities = null;
    private boolean[][] buildPlace = new boolean[40][40];

//    Set<Entity> resourceWorkers = new HashSet<>();
//    Set<Entity> buildWorkers = new HashSet<>();
    Set<Entity> allBuilders = new HashSet<>();
    Set<Entity> allRangers = new HashSet<>();
    Set<Entity> allMelee = new HashSet<>();
    Set<BuildTask> buildTasks = new HashSet<>();

    Integer resourceCount = 0;
    Integer resourcesTotal = 0;
    float avgResources = 0;

    public Action getAction(PlayerView playerView, DebugInterface debugInterface) {
        var actionMap = new HashMap<Integer, EntityAction>();

        resourceCount = Stream.of(playerView.getPlayers()).filter(p -> p.getId() == playerView.getMyId()).findFirst().get().getResource();
        resourcesTotal += resourceCount;
        avgResources = resourcesTotal.floatValue() / (playerView.getCurrentTick() + 1);

        entities = new Entity[playerView.getMapSize()][playerView.getMapSize()];
        updateMatrix(playerView);
        fillBuildPlace();

        var populationMax = 0;
        var populationUse = 0;

        Set<Entity> busyBuilders = buildTasks.stream().flatMap(t -> t.getOnlyBuilders().stream()).collect(Collectors.toSet());

        Entity builderBase = null;
        Entity meleeBase = null;
        for (Entity entity : playerView.getEntities()) {
            if (entity.getPlayerId() == null || entity.getPlayerId() != playerView.getMyId()) {
                continue;
            }

            populationMax += playerView.getEntityProperties().get(entity.getEntityType()).getPopulationProvide();
            populationUse += playerView.getEntityProperties().get(entity.getEntityType()).getPopulationUse();

            switch (entity.getEntityType()) {
                case BUILDER_UNIT:
                    allBuilders.add(entity);
                    if (!busyBuilders.contains(entity)) {
                        actionMap.put(entity.getId(), new EntityAction(null, null, new AttackAction(null, new AutoAttack(1000, new EntityType[]{EntityType.RESOURCE})), null));
                    }
//                    if (!buildWorkers.contains(entity)) {
//                        resourceWorkers.add(entity);
//                    }
//                    updatePositions(entity);
                    break;
                case BUILDER_BASE:
//                    if (populationUse <= populationMax) {
//                        actionMap.put(entity.getId(), new EntityAction(null, new BuildAction(EntityType.BUILDER_UNIT, new Vec2Int(entity.getPosition().getX() + 5, entity.getPosition().getY())), null, null));
//                    } else {
//                        actionMap.put(entity.getId(), new EntityAction(null, null, null,null));
//                    }
                    builderBase = entity;
                    break;
                case RANGED_BASE:
                    if (buildTasks.size() * 50 < resourceCount) {
                        actionMap.put(entity.getId(), new EntityAction(null, new BuildAction(EntityType.RANGED_UNIT, new Vec2Int(entity.getPosition().getX() + 5, entity.getPosition().getY())), null, null));
                    } else {
                        actionMap.put(entity.getId(), new EntityAction(null, null, null,null));
                    }
                    break;
                case MELEE_BASE:
                    meleeBase = entity;
                    break;
                case MELEE_UNIT:
                    allMelee.add(entity);
                    actionMap.put(entity.getId(), new EntityAction(new MoveAction(new Vec2Int(79, 79), true, true), null, new AttackAction(null, new AutoAttack(playerView.getMaxPathfindNodes(), new EntityType[]{EntityType.MELEE_UNIT, EntityType.RANGED_UNIT, EntityType.BUILDER_UNIT, EntityType.MELEE_BASE, EntityType.RANGED_BASE, EntityType.BUILDER_BASE, EntityType.HOUSE, EntityType.WALL, EntityType.TURRET})), null));
                    break;
                case RANGED_UNIT:
                    allRangers.add(entity);
                    actionMap.put(entity.getId(), new EntityAction(new MoveAction(new Vec2Int(79, 79), true, true), null, new AttackAction(null, new AutoAttack(playerView.getMaxPathfindNodes(), new EntityType[]{EntityType.MELEE_UNIT, EntityType.RANGED_UNIT, EntityType.BUILDER_UNIT, EntityType.MELEE_BASE, EntityType.RANGED_BASE, EntityType.BUILDER_BASE, EntityType.HOUSE, EntityType.WALL, EntityType.TURRET})), null));
                    break;
                case TURRET:
                    actionMap.put(entity.getId(), new EntityAction(null, null, new AttackAction(null, new AutoAttack(playerView.getMaxPathfindNodes(), new EntityType[]{EntityType.MELEE_UNIT, EntityType.RANGED_UNIT, EntityType.BUILDER_UNIT, EntityType.MELEE_BASE, EntityType.RANGED_BASE, EntityType.BUILDER_BASE, EntityType.HOUSE, EntityType.WALL, EntityType.TURRET})), null));
                    break;

            }
        }

        var resourceThreshold = Math.round((float) playerView.getCurrentTick() / 10) + 10;
        if (builderBase != null) {
            if (avgResources < Math.min(resourceThreshold, 50) && allBuilders.size() < 30) {
                actionMap.put(builderBase.getId(), new EntityAction(null, new BuildAction(EntityType.BUILDER_UNIT, new Vec2Int(builderBase.getPosition().getX() + 5, builderBase.getPosition().getY())), null, null));
            } else {
                actionMap.put(builderBase.getId(), new EntityAction(null, null, null,null));
            }
        }

        if (meleeBase != null) {
            if (buildTasks.size() * 50 < resourceCount && allMelee.size() < allRangers.size() / 3) {
                actionMap.put(meleeBase.getId(), new EntityAction(null, new BuildAction(EntityType.MELEE_UNIT, new Vec2Int(meleeBase.getPosition().getX() + 5, meleeBase.getPosition().getY())), null, null));
            } else {
                actionMap.put(meleeBase.getId(), new EntityAction(null, null, null,null));
            }
        }

//        buildWorkers.removeIf(w -> !allBuilders.contains(w));

        if (busyBuilders.size() < allBuilders.size() / 3 && resourceCount > 25 && (populationMax - populationUse) < 10) {
            var place = findBestPlaceForHouse();
            if (place != null) {
                List<BuildPosition> builders = new ArrayList<>();
                Set<Vec2Int> occupiedPositions = new HashSet<>();
                place.getBuilders().forEach(b -> {
                    if (!busyBuilders.contains(b)) {
                        var builderPos = findClosestBuildPosition(place.getBuildCorner(), 3, playerView.getMapSize(), occupiedPositions);
                        if (builderPos != null) {
                            builders.add(new BuildPosition(b, builderPos));
                            occupiedPositions.add(builderPos);
                        }
                    }
                });
                if (builders.size() > 0) {
                    buildTasks.add(new BuildTask(place.getBuildCorner(), builders, EntityType.HOUSE));
                }
            }


//            findPlaceForHouse().forEach(c -> {
//                var exist = buildTasks.stream().filter(t -> t.getBuildCorner().equals(c)).findAny();
//                if (exist.isEmpty()) {
//                    buildTasks.add(new BuildTask(c, EntityType.HOUSE));
//                }
//            });
        }

//        if (!buildTasks.isEmpty() && buildWorkers.size() <= BUILDERS_MAX) {
//            while (buildWorkers.size() < Math.min(resourceWorkers.size() / 4,  BUILDERS_MAX)) {
//                var worker = resourceWorkers.iterator().next();
//                resourceWorkers.remove(worker);
//                buildWorkers.add(worker);
//            }
//        }

//        Set<Entity> busyBuilders = buildTasks.stream().flatMap(t -> t.getOnlyBuilders().stream()).collect(Collectors.toSet());
//        Set<Entity> freeBuilders = new HashSet<>(buildWorkers);
//        freeBuilders.removeIf(busyBuilders::contains);
//        buildTasks.stream().filter(t -> t.getState() != BuildState.DONE).sorted(Comparator.reverseOrder()).forEach(t -> {
//            while (t.getBuilders().size() < 2 && !freeBuilders.isEmpty()) {
//                var builder = freeBuilders.iterator().next();
//                var builderPos = findClosestBuildPosition(t.getBuildCorner(), 3, playerView.getMapSize(), t.getOnlyPositions());
//                if (builderPos != null) {
//                    t.setBuilder(builder, builderPos);
//                    freeBuilders.remove(builder);
//                }
//            }
//        });

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

//        if (buildTasks.isEmpty()) {
//            resourceWorkers.addAll(buildWorkers);
//            buildWorkers.clear();
//        }

        //updateBuildersPosition(playerView.getMapSize());
        cleanBuildTask();
        allBuilders.clear();
        allRangers.clear();
        allMelee.clear();
//        activateResourceWorkers(actionMap);
//        resourceWorkers.clear();

        return new Action(actionMap);
    }

    private void cleanBuildTask() {
        buildTasks.removeIf(t -> t.getState() == BuildState.DONE);
    }

//    private void updatePositions(Entity entity) {
//        resourceWorkers.forEach(w -> {
//            if (w.equals(entity)) {
//                w.setPosition(entity.getPosition());
//            }
//        });
//        buildWorkers.forEach(w -> {
//            if (w.equals(entity)) {
//                w.setPosition(entity.getPosition());
//            }
//        });
//    }

//    private void activateResourceWorkers(HashMap<Integer, EntityAction> actionHashMap) {
//        resourceWorkers.forEach(w ->
//                actionHashMap.put(w.getId(), new EntityAction(null, null, new AttackAction(null, new AutoAttack(80, new EntityType[]{EntityType.RESOURCE})), null)));
//    }

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

    private void fillBuildPlace() {
        var builds = Set.of(EntityType.MELEE_BASE, EntityType.RANGED_BASE, EntityType.BUILDER_BASE, EntityType.WALL, EntityType.TURRET, EntityType.FEATURE_BUILDING, EntityType.HOUSE);
        for (int x = 0; x < 30; x++) {
            for (int y = 0; y < 30; y++) {
                if (entities[x][y] != null && builds.contains(entities[x][y].getEntityType())) {
                    buildPlace[x][y] = false;
                } else {
                    if (entities[x][y] != null && entities[x][y].getEntityType() == EntityType.RESOURCE) {
                        buildPlace[x][y] = false;
                    } else if (x > 0 && entities[x - 1][y] != null && builds.contains(entities[x - 1][y].getEntityType())) {
                        buildPlace[x][y] = false;
                    } else if (entities[x + 1][y] != null && builds.contains(entities[x + 1][y].getEntityType())) {
                        buildPlace[x][y] = false;
                    } else if (y > 0 && entities[x][y - 1] != null && builds.contains(entities[x][y - 1].getEntityType())) {
                        buildPlace[x][y] = false;
                    } else if (entities[x][y + 1] != null && builds.contains(entities[x][y + 1].getEntityType())) {
                        buildPlace[x][y] = false;
                    } else {
                        buildPlace[x][y] = true;
                    }
                }
            }
        }
    }

    private int distance(Vec2Int a, Vec2Int b) {
        return Math.abs(a.getX() - b.getX()) + Math.abs(a.getY() - b.getY());
    }

    private FindingPositionResult findBestPlaceForHouse() {
        List<FindingPositionResult> results = new ArrayList<>();
        var planPlaces = findPlaceForHouse();
        if (planPlaces.isEmpty()) {
            planPlaces = getAllCornersForHouse();
        }
        planPlaces.forEach(c -> {
            Map<Entity, Integer> distanceMap = new HashMap<>();
            allBuilders.forEach(b -> distanceMap.put(b, distance(new Vec2Int(b.getPosition().getX() + 1, b.getPosition().getY() + 1), c)));
            List<Entity> closest = distanceMap.entrySet().stream().sorted(Map.Entry.comparingByValue(Comparator.reverseOrder())).limit(3).map(Entry::getKey).collect(Collectors.toList());
            for (int k = 0; k < closest.size(); k++) {
                var ticks = 0;
                int[] modifyDistance = new int[k+1];
                for (int k1 = 0; k1 <= k; k1++) {
                    modifyDistance[k1] = distanceMap.get(closest.get(k1));
                }
                var houseHP = 1;
                while (houseHP <= 50) {
                    for (int k2 = 0; k2 <= k; k2++) {
                        if (modifyDistance[k2] > 0) {
                            modifyDistance[k2]--;
                        } else {
                            houseHP++;
                        }
                    }
                    ticks++;
                }
                results.add(new FindingPositionResult(c, ticks, closest.subList(0, k + 1)));
            }
        });

        return results.stream().min(Comparator.comparingInt(FindingPositionResult::getScore)).orElse(null);
    }

    private List<Vec2Int> getAllCornersForHouse() {
        var result = new ArrayList<Vec2Int>();
        for (int x = 0; x < 36; x++) {
            for (int y = 0; y < 36; y++) {
                if (buildPlace[x][y]) {
                    var placeIsFree = true;
                    for (int x1 = x; x1 < x + 3; x1++) {
                        for (int y1 = y; y1 < y + 3; y1++) {
                            placeIsFree = placeIsFree && buildPlace[x1][y1];
                        }
                    }
                    if (placeIsFree) {
                        result.add(new Vec2Int(x, y));
                    }
                }
            }
        }

        return result;
    }

    public void debugUpdate(PlayerView playerView, DebugInterface debugInterface) {
        debugInterface.send(new DebugCommand.Clear());
        var text = new PlacedText(new ColoredVertex(null, new Vec2Float(200f, 30f), new Color(1f, 0f, 0f, 1f)), String.valueOf(avgResources), 0.5f, 70);
        var text2 = new PlacedText(new ColoredVertex(null, new Vec2Float(200f, 90f), new Color(1f, 0f, 0f, 1f)), String.valueOf(Math.round((float) playerView.getCurrentTick() / 10)), 0.5f, 70);
        debugInterface.send(new Add(text));
        debugInterface.send(new Add(text2));
//
//        for (int x = 0; x < 30; x++) {
//            for (int y = 0; y < 30; y++) {
//                ColoredVertex[] vertices = new ColoredVertex[3];
//                var color = buildPlace[x][y] ? new Color(0, 1, 0, 0.5f) : new Color(1, 0, 0, 0.5f);
//                vertices[0] = new ColoredVertex(new Vec2Float(x, y + 1), new Vec2Float(0f, 0f), color);
//                vertices[1] = new ColoredVertex(new Vec2Float(x + 1, y + 1), new Vec2Float(0f, 0f), color);
//                vertices[2] = new ColoredVertex(new Vec2Float(x + 0.5f, y), new Vec2Float(0f, 0f), color);
//                debugInterface.send(new Add(new Primitives(vertices, PrimitiveType.TRIANGLES)));
//            }
//        }

//        for (Vec2Int v : test) {
//            ColoredVertex[] vertices = new ColoredVertex[3];
//            var color = new Color(0, 0, 1, 0.7f);
//            vertices[0] = new ColoredVertex(new Vec2Float(v.getX(), v.getY() + 1), new Vec2Float(0f, 0f), color);
//            vertices[1] = new ColoredVertex(new Vec2Float(v.getX() + 1, v.getY() + 1), new Vec2Float(0f, 0f), color);
//            vertices[2] = new ColoredVertex(new Vec2Float(v.getX() + 0.5f, v.getY()), new Vec2Float(0f, 0f), color);
//            debugInterface.send(new Add(new Primitives(vertices, PrimitiveType.TRIANGLES)));
//        }
    }
}
