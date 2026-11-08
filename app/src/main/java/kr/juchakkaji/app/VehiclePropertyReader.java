package kr.juchakkaji.app;

/** Platform-free boundary: a bad property must not discard unrelated hardware values. */
final class VehiclePropertyReader {
    enum Property { MAKE, MODEL, YEAR, DIMENSIONS, FUEL }
    record Response(boolean available, Object value) {}
    interface Source {
        Response read(Property property);
    }

    static VehicleProfile read(Source source) {
        return VehicleProfile.fromHardware(
                get(source, Property.MAKE, String.class),
                get(source, Property.MODEL, String.class),
                get(source, Property.YEAR, Integer.class),
                get(source, Property.DIMENSIONS, Integer[].class),
                get(source, Property.FUEL, Integer[].class));
    }

    private static <T> T get(Source source, Property property, Class<T> type) {
        try {
            Response response = source.read(property);
            return response != null && response.available() && type.isInstance(response.value())
                    ? type.cast(response.value()) : null;
        } catch (RuntimeException unavailable) {
            return null;
        }
    }

    private VehiclePropertyReader() {}
}
