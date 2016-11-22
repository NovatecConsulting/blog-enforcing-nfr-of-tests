# Enforcing Non-Functional Requirements of Tests

Most code we write - be it production or test - has non-functional requirements:

As an example, unit tests should ...
- not take longer than 10 ms
- not interact with the file system
- not make use of a database
- not waste time waiting on something

With this post I will demonstrate how to check one of these requirements using
JUnit 5. For this I will implement an extension to measure the execution time
of tests and throw an exception in case a test runs longer than 10 ms.

```java
public class UnitTestDurationRule implements BeforeTestExecutionCallback, AfterTestExecutionCallback {

    @Override
    public void beforeTestExecution(TestExtensionContext context) {
        getStore(context).put("startTime", System.nanoTime());
    }

    @Override
    public void afterTestExecution(TestExtensionContext context) {
        long start = getStore(context).get("startTime", long.class);
        long now = System.nanoTime();
        long durationInMillis = TimeUnit.NANOSECONDS.toMillis(now - start);
        if (durationInMillis > 10L) {
            String message = "Test is to slow to be a unit test! Duration should be <= 10ms, was: "
                + durationInMillis + "ms.";
            throw new IllegalStateException(message);
        }
    }

    private ExtensionContext.Store getStore(TestExtensionContext context) {
        return context.getStore(Namespace.create(UnitTestDurationRule.class));
    }

}
```

In the callback method `beforeTestExecution` the current time is stored in nanoseconds.
We use nanoseconds because `System.currentTimeMillis()` is not precise enough when
our threshold is in the low milliseconds.

This value is used as the start timestamp in `afterTestExecution`. Here we calculate
the difference between now and when the tests started to get it's exact duration.
Or at least as close as we can get it anyway. If this duration is higher than 10
milliseconds, we throw an exception stating the rule and the actual duration of
the test.

To use this rule we can write a test like this:

```java
@ExtendWith(UnitTestDurationRule.class)
public class SomeTest {

    @Test
    void fastTest() {
        // will pass because test is executed in ~0 ms
    }

    @Test
    void slowTest() throws InterruptedException {
        // will fail because it is to slow
        Thread.sleep(15);
    }

}
```

But since it is more than likely that we will implement some other rules in the
future, we should come up with a better solution than declaring each rule for each
test class. Luckily JUnit 5 offers composite annotations which allow us to do the
following:

```java
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@ExtendWith(UnitTestDurationRule.class)
public @interface UnitTest {}
```

With this annotation we can classify all of our test classes as unit tests.
For each `@UnitTest` all the the extensions specified in that annotation will
be loaded. Which means we can add rules later, without having to change any of
the tests.

```java
@UnitTest
public class SomeTest {

    @Test
    void fastTest() {
        // will pass because test is executed in ~0 ms
    }

    @Test
    void slowTest() throws InterruptedException {
        // will fail because it is to slow
        Thread.sleep(15);
    }

}
```

Of course this implementation of the rule is rather simple. It is not configurable,
a fixed threshold of 10ms might be prone to false positives and we might want 
to be a little bit more lenient with throwing an exception.

So the final rule could look a little more like this:

```java
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
```

In this version of the rule, we define three thresholds:

- WARNING logged for tests that run longer than 10 ms
- ERROR logged for tests that run longer than 25 ms
- EXCEPTION thrown for tests that run longer than 50 ms

Each of those thresholds can be overridden via system properties.

**Where to go from here?**
This is of course only one of many possible rules we could implement to create
an early warning system for our tests. But why limit ourselves to rules?

With the `@UnitTest` annotation we introduced categorization to our test suite.
Categorization allows us to provide specific extensions to certain types of test.

As an example: Unit tests often make use of mocks. Now we could write a simple 
extension to setup our mock objects for all unit test:

```java
public class MockitoExtension implements TestInstancePostProcessor{
    @Override
    public void postProcessTestInstance(Object testInstance, ExtensionContext context) {
        MockitoAnnotations.initMocks(testInstance);
    }
}
```

We could also add `@Tag("unit-tests")` to our `@UnitTest` annotation in order to
allow for the selective execution of 'all unit tests' via command line or in a
certain build phase.

With JUnit 5 and extension the possibilities are endless!

As always, you can check out the source code for this post on
[GitHub](https://github.com/nt-ca-aqe/blog-enforcing-nfr-of-tests).