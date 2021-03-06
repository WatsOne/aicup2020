import common.BuildPosition;
import common.BuildState;
import common.BuildTask;
import common.FindingPositionResult;
import java.util.ArrayList;
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
import model.DebugCommand;
import model.Entity;
import model.EntityAction;
import model.EntityType;
import model.MoveAction;
import model.Player;
import model.PlayerView;
import model.RepairAction;
import model.Vec2Int;

public class MyStrategy {

    private static final int BUILDERS_MAX = 8;

    private Entity[][] entities = null;
    private boolean[][] buildPlace = new boolean[40][40];
    private boolean[][] dangerField = new boolean[80][80];

//    Set<Entity> resourceWorkers = new HashSet<>();
//    Set<Entity> buildWorkers = new HashSet<>();
    Set<Entity> allBuilders = new HashSet<>();
    Set<Entity> allRangers = new HashSet<>();
    Set<Entity> allMelee = new HashSet<>();
    Set<BuildTask> buildTasks = new HashSet<>();

    Integer resourceCount = 0;
    Integer resourcesTotal = 0;
    float avgResources = 0;

    private boolean firstInit = true;
    Map<Integer, Vec2Int> playerToCorner = new HashMap<>();

    public Action getAction(PlayerView playerView, DebugInterface debugInterface) {
        clearDangerField();
        var actionMap = new HashMap<Integer, EntityAction>();

        resourceCount = Stream.of(playerView.getPlayers()).filter(p -> p.getId() == playerView.getMyId()).findFirst().get().getResource();
        resourcesTotal += resourceCount;
        avgResources = resourcesTotal.floatValue() / (playerView.getCurrentTick() + 1);

        entities = new Entity[playerView.getMapSize()][playerView.getMapSize()];
        updateMatrix(playerView);
        fillBuildPlace();

        var countOnMyQuarter = 0;

        var populationMax = 0;
        var populationUse = 0;

        Vec2Int myCorner = new Vec2Int(0,0);

        Set<Entity> busyBuilders = buildTasks.stream().flatMap(t -> t.getOnlyBuilders().stream()).collect(Collectors.toSet());

        Map<Integer, Integer> enemyDistances = new HashMap<>();
        Map<Integer, Integer> enemyCount = new HashMap<>();
        Map<Integer, Integer> enemyHouses = new HashMap<>();

        Entity builderBase = null;
        Entity meleeBase = null;
        Entity rangerBase = null;

        for (Entity entity : playerView.getEntities()) {
            if (entity.getPlayerId() == null) {
                continue;
            }
            if (entity.getPlayerId() != playerView.getMyId()) {
                if (firstInit) {
                    if (playerToCorner.get(entity.getPlayerId()) == null) {
                        playerToCorner.put(entity.getPlayerId(), defineCorner(entity.getPosition()));
                    }
                }

                if (entity.getEntityType() == EntityType.RANGED_UNIT || entity.getEntityType() == EntityType.MELEE_UNIT) {
                    updateDangerField(entity);
                    if (entity.getPosition().getX() < 40 && entity.getPosition().getY() < 40) {
                        countOnMyQuarter++;
                    }
                }

                if (entity.getEntityType() == EntityType.RANGED_UNIT || entity.getEntityType() == EntityType.MELEE_UNIT || entity.getEntityType() == EntityType.BUILDER_UNIT) {
                    enemyDistances.merge(entity.getPlayerId(), distance(entity.getPosition(), myCorner), Integer::sum);
                    enemyCount.merge(entity.getPlayerId(), 1, Integer::sum);
                }

                if (entity.getEntityType() == EntityType.MELEE_BASE || entity.getEntityType() == EntityType.RANGED_BASE || entity.getEntityType() == EntityType.BUILDER_BASE || entity.getEntityType() == EntityType.HOUSE || entity.getEntityType() == EntityType.TURRET || entity.getEntityType() == EntityType.WALL) {
                    enemyHouses.merge(entity.getPlayerId(), 1, Integer::sum);
                }
            }
        }

        Map<Integer, Float> avgToEnemy = new HashMap<>();
        for (Player player : playerView.getPlayers()) {
            if (playerView.getMyId() != player.getId()) {
                if (enemyCount.get(player.getId()) != null) {
                    avgToEnemy.put(player.getId(), enemyDistances.get(player.getId()).floatValue() / enemyCount.get(player.getId()));
                }
            }
        }

        var nearestEnemy = avgToEnemy.entrySet().stream().sorted(Map.Entry.comparingByValue()).limit(1).findFirst();
        Vec2Int attackCorner;
        if (nearestEnemy.isPresent()) {
            attackCorner = playerToCorner.get(nearestEnemy.get().getKey());
        } else {
            attackCorner = new Vec2Int(79, 79);
        }

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
                        if (inDanger(entity)) {
                            var freeCell = findNearestFreeCell(entity);
                            if (freeCell != null) {
                                actionMap.put(entity.getId(), new EntityAction(new MoveAction(freeCell, false, false), null, null, null));
                            }
                        } else {
                            actionMap.put(entity.getId(), new EntityAction(null, null, new AttackAction(null, new AutoAttack(1000, new EntityType[]{EntityType.RESOURCE})), null));
                        }
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
                    rangerBase = entity;
                    break;
                case MELEE_BASE:
                    meleeBase = entity;
                    break;
                case MELEE_UNIT:
                    allMelee.add(entity);
                    if (playerView.getCurrentTick() < 200) {
                        actionMap.put(entity.getId(), new EntityAction(new MoveAction(new Vec2Int(15, 15), true, true), null, new AttackAction(null, new AutoAttack(20, new EntityType[]{EntityType.MELEE_UNIT, EntityType.RANGED_UNIT, EntityType.BUILDER_UNIT, EntityType.MELEE_BASE, EntityType.RANGED_BASE, EntityType.BUILDER_BASE, EntityType.HOUSE, EntityType.TURRET})), null));
                    } else {
                        actionMap.put(entity.getId(), new EntityAction(new MoveAction(attackCorner, true, true), null, new AttackAction(null, new AutoAttack(20, new EntityType[]{EntityType.MELEE_UNIT, EntityType.RANGED_UNIT, EntityType.BUILDER_UNIT, EntityType.MELEE_BASE, EntityType.RANGED_BASE, EntityType.BUILDER_BASE, EntityType.HOUSE, EntityType.TURRET})), null));
                    }
                    break;
                case RANGED_UNIT:
                    allRangers.add(entity);
                    if (playerView.getCurrentTick() < 200) {
                        actionMap.put(entity.getId(), new EntityAction(new MoveAction(new Vec2Int(15, 15), true, true), null, new AttackAction(null, new AutoAttack(20, new EntityType[]{EntityType.MELEE_UNIT, EntityType.RANGED_UNIT, EntityType.BUILDER_UNIT, EntityType.MELEE_BASE, EntityType.RANGED_BASE, EntityType.BUILDER_BASE, EntityType.HOUSE, EntityType.TURRET})), null));
                    } else {
                        actionMap.put(entity.getId(), new EntityAction(new MoveAction(attackCorner, true, true), null, new AttackAction(null, new AutoAttack(20, new EntityType[]{EntityType.MELEE_UNIT, EntityType.RANGED_UNIT, EntityType.BUILDER_UNIT, EntityType.MELEE_BASE, EntityType.RANGED_BASE, EntityType.BUILDER_BASE, EntityType.HOUSE, EntityType.TURRET})), null));
                    }
                    break;
                case TURRET:
                    actionMap.put(entity.getId(), new EntityAction(null, null, new AttackAction(null, new AutoAttack(playerView.getMaxPathfindNodes(), new EntityType[]{EntityType.MELEE_UNIT, EntityType.RANGED_UNIT, EntityType.BUILDER_UNIT, EntityType.MELEE_BASE, EntityType.RANGED_BASE, EntityType.BUILDER_BASE, EntityType.HOUSE, EntityType.WALL, EntityType.TURRET})), null));
                    break;

            }
        }

        var needMoreWarriors = countOnMyQuarter > allRangers.size() + allMelee.size();
        var freeSlots = populationMax - populationUse;
        var slotsEnough = freeSlots >= countOnMyQuarter - (allRangers.size() + allMelee.size());

        var resourceThreshold = Math.round((float) playerView.getCurrentTick() / 10) + 12;
        if (builderBase != null) {
            var limit = playerView.getCurrentTick() < 100 ? 15 : playerView.getCurrentTick() < 400 ? 27 : 40;
            if (!needMoreWarriors && /*avgResources < Math.min(resourceThreshold, limit) && */ allBuilders.size() < limit) {
                actionMap.put(builderBase.getId(), new EntityAction(null, new BuildAction(EntityType.BUILDER_UNIT,findClosestSpawnPosition(builderBase.getPosition())), null, null));
            } else {
                actionMap.put(builderBase.getId(), new EntityAction(null, null, null,null));
            }
        }

        if (rangerBase != null) {
            if (needMoreWarriors || buildTasks.size() * 50 < resourceCount) {
                actionMap.put(rangerBase.getId(), new EntityAction(null, new BuildAction(EntityType.RANGED_UNIT, findClosestSpawnPosition(rangerBase.getPosition())), null, null));
            } else {
                actionMap.put(rangerBase.getId(), new EntityAction(null, null, null,null));
            }

            if (rangerBase.getHealth() < playerView.getEntityProperties().get(EntityType.RANGED_BASE).getMaxHealth()) {
                repair(rangerBase.getPosition(), busyBuilders, playerView, EntityType.RANGED_BASE);
            }
        } else {
            if (resourceCount > 500) {
                buildBase(EntityType.RANGED_BASE, playerView);
            }
        }

        if (meleeBase != null) {
            if (rangerBase == null || needMoreWarriors || buildTasks.size() * 50 < resourceCount && allMelee.size() < allRangers.size() / 3) {
                actionMap.put(meleeBase.getId(), new EntityAction(null, new BuildAction(EntityType.MELEE_UNIT, findClosestSpawnPosition(meleeBase.getPosition())), null, null));
            } else {
                actionMap.put(meleeBase.getId(), new EntityAction(null, null, null,null));
            }

            if (meleeBase.getHealth() < playerView.getEntityProperties().get(EntityType.MELEE_BASE).getMaxHealth()) {
                repair(meleeBase.getPosition(), busyBuilders, playerView, EntityType.MELEE_BASE);
            }
        } else {
            if (resourceCount > 500) {
                buildBase(EntityType.MELEE_BASE, playerView);
            }
        }

//        buildWorkers.removeIf(w -> !allBuilders.contains(w));


        if (needMoreWarriors && !slotsEnough || !needMoreWarriors && busyBuilders.size() < allBuilders.size() / 3 && resourceCount > 25 && (populationMax - populationUse) < 10) {
            var place = findBestPlaceForBuild(getPlacesForHouse(playerView.getCurrentTick()), playerView.getEntityProperties().get(EntityType.HOUSE).getMaxHealth(), 3);
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


//        if (!needMoreWarriors && busyBuilders.size() < allBuilders.size() / 3 && resourceCount > 25 && playerView.getCurrentTick() > 200) {
//            var place = findBestPlaceForBuild(getAllCornersForTurret(), playerView.getEntityProperties().get(EntityType.TURRET).getMaxHealth(), 2);
//            if (place != null) {
//                List<BuildPosition> builders = new ArrayList<>();
//                Set<Vec2Int> occupiedPositions = new HashSet<>();
//                place.getBuilders().forEach(b -> {
//                    if (!busyBuilders.contains(b)) {
//                        var builderPos = findClosestBuildPosition(place.getBuildCorner(), 2, playerView.getMapSize(), occupiedPositions);
//                        if (builderPos != null) {
//                            builders.add(new BuildPosition(b, builderPos));
//                            occupiedPositions.add(builderPos);
//                        }
//                    }
//                });
//                if (builders.size() > 0) {
//                    buildTasks.add(new BuildTask(place.getBuildCorner(), builders, EntityType.TURRET));
//                }
//            }
//        }

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
                        actionMap.put(b.getEntity().getId(), new EntityAction(new MoveAction(b.getBuildPosition(), false, false), new BuildAction(t.getType(), t.getBuildCorner()), null, null));
                    });
                    break;
                case REPAIRING:
                    t.getBuilders().forEach(b -> {
                        actionMap.put(b.getEntity().getId(), new EntityAction(new MoveAction(b.getBuildPosition(), false, false), new BuildAction(t.getType(), t.getBuildCorner()), null, new RepairAction(t.getBuildId())));
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
        firstInit = false;
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

    private void buildBase(EntityType base, PlayerView playerView) {
        var exist = buildTasks.stream().anyMatch(t -> t.getType() == base);
        if (exist) {
            return;
        }

        var busyBuilders = buildTasks.stream().flatMap(t -> t.getOnlyBuilders().stream()).collect(Collectors.toSet());

        var size = playerView.getEntityProperties().get(base).getSize();
        var place = findBestPlaceForBuild(getAllCornersForBuild(size), playerView.getEntityProperties().get(base).getMaxHealth(), 4);
        if (place != null) {
            List<BuildPosition> builders = new ArrayList<>();
            Set<Vec2Int> occupiedPositions = new HashSet<>();
            place.getBuilders().forEach(b -> {
                if (!busyBuilders.contains(b)) {
                    var builderPos = findClosestBuildPosition(place.getBuildCorner(), size, playerView.getMapSize(), occupiedPositions);
                    if (builderPos != null) {
                        builders.add(new BuildPosition(b, builderPos));
                        occupiedPositions.add(builderPos);
                    }
                }
            });
            if (builders.size() > 0) {
                buildTasks.add(new BuildTask(place.getBuildCorner(), builders, base));
            }
        }
    }

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

    private Vec2Int findClosestSpawnPosition(Vec2Int buildPosition) {
        var x = buildPosition.getX() + 5;
        for (int y = buildPosition.getY() + 4; y >= buildPosition.getY(); y--) {
            var res = new Vec2Int(x, y);
            if (entities[x][y] == null) {
                return res;
            }
        }

        var y = buildPosition.getX() + 5;
        for (x = buildPosition.getX() + 4; x >= buildPosition.getX(); x--) {
            var res = new Vec2Int(x, y);
            if (entities[x][y] == null) {
                return res;
            }
        }

        return new Vec2Int(0,0);
    }

    private boolean cellIsFree(int x, int y, int mapSize) {
        if (x >= 0 && x < mapSize && y >= 0 && y < mapSize) {
            var entity = entities[x][y];
            return entity == null || entity.getEntityType() == EntityType.BUILDER_UNIT || entity.getEntityType() == EntityType.MELEE_UNIT
                    || entity.getEntityType() == EntityType.RANGED_UNIT;
        }

        return false;
    }

    private void repair(Vec2Int position, Set<Entity> busyBuilders, PlayerView playerView, EntityType type) {
        var exist = buildTasks.stream().filter(b -> b.getType() == type).findFirst();
        if (exist.isPresent()) {
            return;
        }

        List<BuildPosition> builders = new ArrayList<>();
        Set<Vec2Int> occupiedPositions = new HashSet<>();

        Map<Entity, Integer> distanceMap = new HashMap<>();
        allBuilders.forEach(b -> distanceMap.put(b, distance(b.getPosition(), new Vec2Int(position.getX() + 2, position.getY() + 2))));
        List<Entity> closest = distanceMap.entrySet().stream().sorted(Map.Entry.comparingByValue()).limit(3).map(Entry::getKey).collect(Collectors.toList());
        closest.forEach(b -> {
            if (!busyBuilders.contains(b)) {
                var builderPos = findClosestBuildPosition(position, 5, playerView.getMapSize(), occupiedPositions);
                if (builderPos != null) {
                    builders.add(new BuildPosition(b, builderPos));
                    occupiedPositions.add(builderPos);
                }
            }
        });
        if (builders.size() > 0) {
            buildTasks.add(new BuildTask(position, builders, type));
        }
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

    private List<Vec2Int> getPlacesForHouse(int tick) {
        List<Vec2Int> planPlaces;
        if (tick > 400) {
            planPlaces = getAllCornersForBuild(3);
        } else {
            planPlaces = findPlaceForHouse();
        }
        if (planPlaces.isEmpty()) {
            planPlaces = getAllCornersForBuild(3);
        }

        return planPlaces;
    }

    private FindingPositionResult findBestPlaceForBuild(List<Vec2Int> planPlaces, int maxHP, int buildersLimit) {
        List<FindingPositionResult> results = new ArrayList<>();

        planPlaces.forEach(c -> {
            Map<Entity, Integer> distanceMap = new HashMap<>();
            allBuilders.forEach(b -> distanceMap.put(b, distance(b.getPosition(), new Vec2Int(c.getX() + 1, c.getY() + 1))));
            List<Entity> closest = distanceMap.entrySet().stream().sorted(Map.Entry.comparingByValue()).limit(buildersLimit).map(Entry::getKey).collect(Collectors.toList());
            for (int k = 0; k < closest.size(); k++) {
                var ticks = 0;
                int[] modifyDistance = new int[k+1];
                for (int k1 = 0; k1 <= k; k1++) {
                    modifyDistance[k1] = distanceMap.get(closest.get(k1));
                }
                var houseHP = 1;
                while (houseHP <= maxHP) {
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

    private List<Vec2Int> getAllCornersForBuild(int size) {
        var result = new ArrayList<Vec2Int>();
        for (int x = 0; x < 36; x++) {
            for (int y = 0; y < 36; y++) {
                if (buildPlace[x][y]) {
                    var placeIsFree = true;
                    for (int x1 = x; x1 < x + size; x1++) {
                        for (int y1 = y; y1 < y + size; y1++) {
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

    private List<Vec2Int> getAllCornersForTurret() {
        var result = new ArrayList<Vec2Int>();
        for (int x = 18; x < 30; x++) {
            for (int y = 18; y < 30; y++) {
                if (buildPlace[x][y]) {
                    var placeIsFree = true;
                    for (int x1 = x; x1 < x + 2; x1++) {
                        for (int y1 = y; y1 < y + 2; y1++) {
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

    private Vec2Int defineCorner(Vec2Int pos) {
        if (pos.getX() > 40 && pos.getY() > 40) {
            return new Vec2Int(79, 79);
        } else if (pos.getX() > 40 && pos.getY() < 40) {
            return new Vec2Int(79, 0);
        } else {
            return new Vec2Int(0, 79);
        }
    }

    private void clearDangerField() {
        for (int x = 0; x < 80; x++) {
            for (int y = 0; y < 80; y++) {
                dangerField[x][y] = false;
            }
        }
    }

    private void updateDangerField(Entity entity) {
        var range = entity.getEntityType() == EntityType.MELEE_UNIT ? 3 : 7;
        var entityX = entity.getPosition().getX();
        var entityY = entity.getPosition().getY();
        for (int x = Math.max(entityX - range, 0); x <= Math.min(entityX + range, 79); x++) {
            var diff = range - Math.abs(entityX - x);
            for (int y = Math.max(entityY - diff, 0); y <= Math.min(entityY + diff, 79); y++) {
                dangerField[x][y] = true;
            }
        }
    }

    private Vec2Int findNearestFreeCell(Entity entity) {
        var x = entity.getPosition().getX();
        var y = entity.getPosition().getY();

        var xUp = Math.min(x + 1, 79);
        var xDown = Math.max(x - 1, 0);
        var yUp = Math.min(y + 1, 79);
        var yDown = Math.max(y - 1, 0);

        if (cellIsFree(xUp, y,80) && !dangerField[xUp][y]) {
            return new Vec2Int(xUp, y);
        }
        if (cellIsFree(xDown, y,80) && !dangerField[xDown][y]) {
            return new Vec2Int(xDown, y);
        }
        if (cellIsFree(x, yUp,80) && !dangerField[x][yUp]) {
            return new Vec2Int(x, yUp);
        }
        if (cellIsFree(x, yDown,80) && !dangerField[x][yDown]) {
            return new Vec2Int(x,yDown);
        }

        return null;
    }

    private boolean inDanger(Entity entity) {
        return dangerField[entity.getPosition().getX()][entity.getPosition().getY()];
    }

    public void debugUpdate(PlayerView playerView, DebugInterface debugInterface) {
        debugInterface.send(new DebugCommand.Clear());
//        var text = new PlacedText(new ColoredVertex(null, new Vec2Float(200f, 30f), new Color(1f, 0f, 0f, 1f)), String.valueOf(allBuilders.size()), 0.5f, 70);
//        var text2 = new PlacedText(new ColoredVertex(null, new Vec2Float(200f, 90f), new Color(1f, 0f, 0f, 1f)), String.valueOf(playerView.getCurrentTick()), 0.5f, 70);
//        debugInterface.send(new Add(text));
//        debugInterface.send(new Add(text2));
//
//        for (int x = 0; x < 80; x++) {
//            for (int y = 0; y < 80; y++) {
//                ColoredVertex[] vertices = new ColoredVertex[3];
//                var color = dangerField[x][y] ? new Color(0, 1, 0, 0.5f) : new Color(1, 0, 0, 0.5f);
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
