import common.ResourcePosition;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;
import model.*;
import model.DebugCommand.Add;
import model.DebugData.Primitives;

public class MyStrategy {

    private Entity[][] entities = null;
    private Map<Integer, Entity> id2Entity = new HashMap<>();
    private Map<Integer, ResourcePosition> builderTasks = new HashMap<>();

    public Action getAction(PlayerView playerView, DebugInterface debugInterface) {
        var populationMax = 0;
        var populationUse = 0;

        entities = new Entity[playerView.getMapSize()][playerView.getMapSize()];
        id2Entity.clear();
        updateMatrix(playerView.getEntities());
        var actionMap = new HashMap<Integer, EntityAction>();
        List<Entity> myEntities = new ArrayList<>();
        Entity builderBase = null;

        for (Entity entity : playerView.getEntities()) {
            if (entity.getPlayerId() != null && entity.getPlayerId() != playerView.getMyId()) {
                continue;
            }

            populationMax += playerView.getEntityProperties().get(entity.getEntityType()).getPopulationProvide();
            populationUse += playerView.getEntityProperties().get(entity.getEntityType()).getPopulationUse();

            id2Entity.put(entity.getId(), entity);
            myEntities.add(entity);

            if (entity.getEntityType() == EntityType.BUILDER_BASE) {
                builderBase = entity;
            }
        }

        //--builders----------------------------------------------------
        myEntities.forEach(e -> {
            if (e.getEntityType() == EntityType.BUILDER_UNIT) {
//                if (builderTasks.containsKey(e.getId())) {
//                    builderTasks.get(e.getId()).setCurrentPos(e.getPosition());
//                } else {
//                    addBuilderTaskForResource(e, playerView.getMapSize());
//                }
                actionMap.put(e.getId(), new EntityAction(null, null, new AttackAction(null, new AutoAttack(20, new EntityType[]{EntityType.RESOURCE})), null));
            }
        });

//        List<Integer> toRemove = new ArrayList<>();
//        for (Map.Entry<Integer, ResourcePosition> entry : builderTasks.entrySet()) {
//            if (entry.getValue().getAttackPos().equals(entry.getValue().getCurrentPos())) {
//                var resPos = entry.getValue().getResource();
//                var res = entities[resPos.getX()][resPos.getY()];
//                if (res != null && res.getEntityType() == EntityType.RESOURCE) {
//                    actionMap.put(entry.getKey(), new EntityAction(null, null, new AttackAction(res.getId(), null), null));
//                } else if (res == null) {
//                    toRemove.add(entry.getKey());
//                }
//            }
//        }
//
//        toRemove.forEach(r -> {
//            builderTasks.remove(r);
//            addBuilderTaskForResource(id2Entity.get(r), playerView.getMapSize());
//        });
//
//        for (Map.Entry<Integer, ResourcePosition> entry : builderTasks.entrySet()) {
//            if (!entry.getValue().getAttackPos().equals(entry.getValue().getCurrentPos())) {
//                actionMap.put(entry.getKey(), new EntityAction(new MoveAction(entry.getValue().getAttackPos(), false, false), null, null, null));
//            }
//        }
        //--builders base----------------------------------------------------
        if (populationUse <= populationMax) {
            actionMap.put(builderBase.getId(), new EntityAction(null, new BuildAction(EntityType.BUILDER_UNIT, new Vec2Int(builderBase.getPosition().getX() + 5, builderBase.getPosition().getY() + 1)), null, null));
        }

        return new Action(actionMap);
    }

    private void addBuilderTaskForResource(Entity entity, int worldSizeOriginal) {
        var enX = entity.getPosition().getX();
        var enY = entity.getPosition().getY();
        var worldSize = worldSizeOriginal - 1;
        var step = 1;
        var found = false;

        while (step <= 60 && !found) {
            //square
            var tempFound = new ArrayList<Vec2Int>();
            for (int y = Math.max(enY - step, 0); y <= Math.min(enY + step, worldSize); y++) {
                if (entities[Math.max(enX - step, 0)][y] != null && entities[Math.max(enX - step, 0)][y].getEntityType() == EntityType.RESOURCE) {
                    tempFound.add(new Vec2Int(Math.max(enX - step, 0), y));
                } else if (entities[Math.min(enX + step, worldSize)][y] != null && entities[Math.min(enX + step, worldSize)][y].getEntityType() == EntityType.RESOURCE) {
                    tempFound.add(new Vec2Int(Math.min(enX + step, worldSize), y));
                }
            }
            for (int x = Math.max(enX - step, 0); x <= Math.min(enX + step, worldSize); x++) {
                if (entities[x][Math.max(enY - step, 0)] != null && entities[x][Math.max(enY - step, 0)].getEntityType() == EntityType.RESOURCE) {
                    tempFound.add(new Vec2Int(x, Math.max(enY - step, 0)));
                } else if (entities[x][Math.min(enY + step, worldSize)] != null && entities[x][Math.min(enY + step, worldSize)].getEntityType() == EntityType.RESOURCE) {
                    tempFound.add(new Vec2Int(x, Math.min(enX + step, worldSize)));
                }
            }
            if (tempFound.isEmpty()) {
                step++;
                continue;
            }
            //cross cells
            var r = new Random();
            var color = new Color(r.nextFloat(), r.nextFloat(), r.nextFloat(), 1);

            for (Vec2Int res : tempFound) {
                var attackPos = new Vec2Int(res.getX() - 1, res.getY());
                if (attackPos.getX() >= 0 && entities[attackPos.getX()][attackPos.getY()] == null && notInBuildTasks(attackPos)) {
                    builderTasks.put(entity.getId(), new ResourcePosition(res, attackPos, entity.getPosition(), color));
                    found = true;
                    break;
                }
                attackPos = new Vec2Int(res.getX() + 1, res.getY());
                if (attackPos.getX() <= worldSize && entities[attackPos.getX()][attackPos.getY()] == null && notInBuildTasks(attackPos)) {
                    builderTasks.put(entity.getId(), new ResourcePosition(res, attackPos, entity.getPosition(), color));
                    found = true;
                    break;
                }
                attackPos = new Vec2Int(res.getX(), res.getY() - 1);
                if (attackPos.getY() >= 0 && entities[attackPos.getX()][attackPos.getY()] == null && notInBuildTasks(attackPos)) {
                    builderTasks.put(entity.getId(), new ResourcePosition(res, attackPos, entity.getPosition(), color));
                    found = true;
                    break;
                }
                attackPos = new Vec2Int(res.getX(), res.getY() + 1);
                if (attackPos.getY() <= worldSize && entities[attackPos.getX()][attackPos.getY()] == null && notInBuildTasks(attackPos)) {
                    builderTasks.put(entity.getId(), new ResourcePosition(res, attackPos, entity.getPosition(), color));
                    found = true;
                    break;
                }
            }

            step++;
        }
    }

    private boolean notInBuildTasks(Vec2Int pos) {
        return builderTasks.values().stream().filter(r -> r.getAttackPos().equals(pos)).findFirst().isEmpty();
    }

    private void updateMatrix(Entity[] startEntities) {
        for (Entity entity : startEntities) {
            var pos = entity.getPosition();
            entities[pos.getX()][pos.getY()] = entity;
        }
    }

    public void debugUpdate(PlayerView playerView, DebugInterface debugInterface) {
        debugInterface.send(new DebugCommand.Clear());
        builderTasks.forEach((e,c) -> {
            ColoredVertex[] vertices = new ColoredVertex[3];
            vertices[0] = new ColoredVertex(new Vec2Float(c.getCurrentPos().getX(), c.getCurrentPos().getY() + 1), new Vec2Float(0f, 0f), c.getColor());
            vertices[1] = new ColoredVertex(new Vec2Float(c.getCurrentPos().getX() + 1, c.getCurrentPos().getY() + 1), new Vec2Float(0f, 0f), c.getColor());
            vertices[2] = new ColoredVertex(new Vec2Float(c.getCurrentPos().getX() + 0.5f, c.getCurrentPos().getY()), new Vec2Float(0f, 0f), c.getColor());
            debugInterface.send(new Add(new Primitives(vertices, PrimitiveType.TRIANGLES)));

            vertices = new ColoredVertex[3];
            vertices[0] = new ColoredVertex(new Vec2Float(c.getAttackPos().getX(), c.getAttackPos().getY() + 1), new Vec2Float(0f, 0f), c.getColor());
            vertices[1] = new ColoredVertex(new Vec2Float(c.getAttackPos().getX() + 1, c.getAttackPos().getY() + 1), new Vec2Float(0f, 0f), c.getColor());
            vertices[2] = new ColoredVertex(new Vec2Float(c.getAttackPos().getX() + 0.5f, c.getAttackPos().getY()), new Vec2Float(0f, 0f), c.getColor());
            debugInterface.send(new Add(new Primitives(vertices, PrimitiveType.TRIANGLES)));
        });


//        ColoredVertex[] vertices = new ColoredVertex[3];
//        vertices[0] = new ColoredVertex(new Vec2Float(10f, 10f), new Vec2Float(0.1f, 0.1f), new Color(255, 0, 0, 1));
//        vertices[1] = new ColoredVertex(new Vec2Float(10f, 10f), new Vec2Float(0.1f, 0.9f), new Color(255, 0, 0, 1));
//        vertices[2] = new ColoredVertex(new Vec2Float(10f, 10f), new Vec2Float(0.9f, 0.1f), new Color(255, 0, 0, 1));
//        vertices[3] = new ColoredVertex(new Vec2Float(10f, 10f), new Vec2Float(0.9f, 0.9f), new Color(255, 0, 0, 1));
//        vertices[0] = new ColoredVertex(new Vec2Float(10f, 10f), new Vec2Float(0f, 0f), new Color(255, 0, 0, 1));
//        vertices[1] = new ColoredVertex(new Vec2Float(11f, 10f), new Vec2Float(0f, 0f), new Color(255, 0, 0, 1));
//        vertices[2] = new ColoredVertex(new Vec2Float(10.5f, 9f), new Vec2Float(0f, 0f), new Color(255, 0, 0, 1));
//        debugInterface.send(new Add(new Primitives(vertices, PrimitiveType.TRIANGLES)));
//        debugInterface.getState();
    }
}