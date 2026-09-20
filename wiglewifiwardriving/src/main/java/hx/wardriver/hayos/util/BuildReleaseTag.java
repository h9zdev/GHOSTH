package hx.wardriver.hayos.util;

import hx.wardriver.hayos.BuildConfig;

/**
 * Appends "-FOSS" to exported version strings when {@link BuildConfig#FOSS_MAIN_BUILD} is true
 * (either by detection of `foss-main` branch or from explicit CI property).
 */
public final class BuildReleaseTag {

    private static final String FOSS_SUFFIX = "-FOSS";

    private BuildReleaseTag() {}

    public static String tagVersionForExports(final String versionName) {
        if (versionName == null) {
            return null;
        }
        if (!BuildConfig.FOSS_MAIN_BUILD) {
            return versionName;
        }
        final String base = versionName.endsWith(FOSS_SUFFIX)
                ? versionName.substring(0, versionName.length() - FOSS_SUFFIX.length())
                : versionName;
        return base + FOSS_SUFFIX;
    }
}
