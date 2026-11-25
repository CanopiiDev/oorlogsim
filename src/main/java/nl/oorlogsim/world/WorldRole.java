package nl.oorlogsim.world;

public enum WorldRole {
    LOBBY, MAPS, KITPVP, OTHER;

    public boolean isPluginWorld() {
        return this != OTHER;
    }
}
