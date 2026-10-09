package io.aegisflow.core;

import io.aegisflow.config.center.api.ConfigCenter;
import io.aegisflow.register.center.api.RegisterCenter;
import org.junit.Test;

import java.util.ServiceLoader;

import static org.junit.Assert.assertTrue;

/** Build-level regression: real provider metadata must be on the classpath. */
public class SpiDiscoveryTest {
    @Test
    public void configProviderIsPackaged() {
        assertTrue("ConfigCenter provider is missing", ServiceLoader.load(ConfigCenter.class)
                .stream().anyMatch(provider -> provider.type().getName().contains("Nacos")));
    }

    @Test
    public void registerProviderIsPackaged() {
        assertTrue("RegisterCenter provider is missing", ServiceLoader.load(RegisterCenter.class)
                .stream().anyMatch(provider -> provider.type().getName().contains("Nacos")));
    }
}
