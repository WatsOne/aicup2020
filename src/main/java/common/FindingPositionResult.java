package common;

import java.util.List;
import model.Entity;
import model.Vec2Int;

public class FindingPositionResult {
    private Vec2Int buildCorner;
    private Integer score;
    private List<Entity> builders;

    public FindingPositionResult(Vec2Int buildCorner, Integer score, List<Entity> builders) {
        this.buildCorner = buildCorner;
        this.score = score;
        this.builders = builders;
    }

    public Vec2Int getBuildCorner() {
        return buildCorner;
    }

    public Integer getScore() {
        return score;
    }

    public List<Entity> getBuilders() {
        return builders;
    }
}
