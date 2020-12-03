package common;

import model.Color;
import model.Vec2Int;

public class ResourcePosition {
    private Vec2Int resource;
    private Vec2Int attackPos;
    private Vec2Int currentPos;
    private Color color;

    public ResourcePosition(Vec2Int resource, Vec2Int attackPos, Vec2Int currentPos, Color color) {
        this.resource = resource;
        this.attackPos = attackPos;
        this.currentPos = currentPos;
        this.color = color;
    }

    public Color getColor() {
        return color;
    }

    public Vec2Int getResource() {
        return resource;
    }

    public Vec2Int getAttackPos() {
        return attackPos;
    }

    public Vec2Int getCurrentPos() {
        return currentPos;
    }

    public void setCurrentPos(Vec2Int currentPos) {
        this.currentPos = currentPos;
    }
}
