package cn.tabletcast;

final class DeviceNames {
    private DeviceNames() { }
    static String forV2(String stored) {
        return stored == null || stored.trim().isEmpty() || stored.equals("小米平板 7") ? "AndroidTV" : stored;
    }
}
