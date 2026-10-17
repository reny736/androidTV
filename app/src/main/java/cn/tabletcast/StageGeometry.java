package cn.tabletcast;

final class StageGeometry {
    private StageGeometry() { }
    static int playerWidth(int availableWidth, boolean fullscreen) {
        return Math.max(0, fullscreen ? availableWidth : Math.round(availableWidth * .75f));
    }
}
