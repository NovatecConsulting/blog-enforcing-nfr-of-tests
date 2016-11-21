package utils;

import static org.slf4j.helpers.MessageFormatter.format;

import java.lang.reflect.Method;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.extension.AfterTestExecutionCallback;
import org.junit.jupiter.api.extension.BeforeTestExecutionCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ExtensionContext.Namespace;
import org.junit.jupiter.api.extension.TestExtensionContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class UnitTestDurationRule implements BeforeTestExecutionCallback, AfterTestExecutionCallback {

    private static final Logger LOGGER = LoggerFactory.getLogger(UnitTestDurationRule.class);

    private static final long WARNING_THRESHOLD = getThreshold("unit.threshold.warning", 10L);
    private static final long ERROR_THRESHOLD = getThreshold("unit.threshold.error", 25L);
    private static final long EXCEPTION_THRESHOLD = getThreshold("unit.threshold.exception", 50L);
    private static final String MESSAGE =
        "Test is to slow to be a unit test! Duration should be <= " + WARNING_THRESHOLD + "ms, was: {}ms.";

    private static long getThreshold(String property, long defaultValue) {
        return Long.valueOf(System.getProperty("property", String.valueOf(defaultValue)));
    }

    @Override
    public void beforeTestExecution(TestExtensionContext context) throws Exception {
        getStore(context).put("startTime", System.nanoTime());
    }

    @Override
    public void afterTestExecution(TestExtensionContext context) throws Exception {

        Method method = context.getTestMethod().get();
        if(method.isAnnotationPresent(IgnoreRules.class)){
            return;
        }

        long start = getStore(context).get("startTime", long.class);
        long now = System.nanoTime();
        long durationInMillis = TimeUnit.NANOSECONDS.toMillis(now - start);

        if (durationInMillis > EXCEPTION_THRESHOLD) {
            throw new IllegalStateException(format(MESSAGE, durationInMillis).getMessage());
        } else if (durationInMillis > ERROR_THRESHOLD) {
            LOGGER.error(MESSAGE, durationInMillis);
        } else if (durationInMillis > WARNING_THRESHOLD) {
            LOGGER.warn(MESSAGE, durationInMillis);
        }

    }

    private ExtensionContext.Store getStore(TestExtensionContext context) {
        return context.getStore(Namespace.create(UnitTestDurationRule.class));
    }

}
