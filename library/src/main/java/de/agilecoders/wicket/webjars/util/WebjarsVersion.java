package de.agilecoders.wicket.webjars.util;

import de.agilecoders.wicket.webjars.WicketWebjars;
import de.agilecoders.wicket.webjars.settings.IWebjarsSettings;
import org.apache.wicket.util.lang.Args;
import org.apache.wicket.util.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static de.agilecoders.wicket.webjars.util.Helper.prependWebjarsPathIfMissing;

/**
 * Collects recent versions of webjars resources.
 *
 * @author miha
 */
public final class WebjarsVersion {
    private static final Logger LOG = LoggerFactory.getLogger(WicketWebjars.class);
    private static final ConcurrentMap<String, FutureTask<String>> VERSIONS_CACHE = new ConcurrentHashMap<>();

    private static final class Holder {
        //
        // Issue https://github.com/l0rdn1kk0n/wicket-webjars/issues/51
        // Do not use static attributes related to wicket application
        // as they are not initializable at deserialization time.
        //
        
        private static final IWebjarsSettings settings() {
            return WicketWebjars.settings();
        }

        private static final String recentVersionPattern() {
            return Helper.PATH_PREFIX + "[^/]*/" + settings().recentVersionPlaceHolder() + "/.*";
        }

        private static final String replacePattern() {
            return "/" + settings().recentVersionPlaceHolder() + "/";
        }

        private static final Duration timeout() {
            return settings().readFromCacheTimeout();
        }
    }

    /**
     * replaces the version string "current" with the recent available version
     *
     * @param path the full resource path
     * @return The version of webjars resource
     */
    public static String useRecent(String path) {
        Args.notEmpty(path, "path");

        path = prependWebjarsPathIfMissing(path);

        if (path.matches(Holder.recentVersionPattern())) {
            return path.replaceFirst(Holder.replacePattern(), "/" + recentVersion(path) + "/");
        }

        return path;
    }

    /**
     * returns recent version of given dependency (from internal versions cache)
     *
     * @param partialPath the path of dependency
     * @return recent version
     */
    public static String recentVersion(final String partialPath) {
        if (!VERSIONS_CACHE.containsKey(partialPath)) {
            final FutureTask<String> futureTask = RecentVersionCallable.createFutureTask(partialPath);
            final FutureTask<String> prevFutureTask = VERSIONS_CACHE.putIfAbsent(partialPath, futureTask);

            if (prevFutureTask == null) {
                futureTask.run();
            }
        }

        try {
            return VERSIONS_CACHE.get(partialPath).get(Holder.timeout().getMilliseconds(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException | ExecutionException | TimeoutException e) {
            LOG.error("can't collect recent version of {}; {}", partialPath, e.getMessage());
        }

        throw new WebJarAssetLocator.ResourceException(partialPath, "there is no webjars dependency for: " +
                                                                    partialPath);
    }

    public static void reset() {
        VERSIONS_CACHE.clear();
    }

    private WebjarsVersion() {
        throw new UnsupportedOperationException();
    }
}
