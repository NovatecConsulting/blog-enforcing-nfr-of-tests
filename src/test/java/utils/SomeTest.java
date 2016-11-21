package utils;

import org.junit.jupiter.api.Test;


@UnitTest
public class SomeTest {

    @Test
    void fastTest() {
        // do nothing - should always pass
    }

    @Test
    void warningThresholdTest() throws InterruptedException {
        Thread.sleep(15);
    }

    @Test
    void errorThresholdTest() throws InterruptedException {
        Thread.sleep(35);
    }

    @Test
    void exceptionThresholdTest() throws InterruptedException {
        Thread.sleep(75);
    }

    @Test
    @IgnoreRules
    void superSlowTest() throws InterruptedException {
        Thread.sleep(150);
    }

}
