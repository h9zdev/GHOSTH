package hx.wardriver.hayos.util;

import hx.wardriver.hayos.model.RouteDescriptor;

public interface RouteConfigurable {
    void configureMapForRoute(RouteDescriptor polyRoute);
    void clearCurrentRoute();
}
