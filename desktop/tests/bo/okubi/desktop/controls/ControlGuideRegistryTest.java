package bo.okubi.desktop.controls;

import java.nio.file.Files;
import java.nio.file.Path;

public final class ControlGuideRegistryTest {
    public static void main(String[] args) throws Exception {
        Path dir = Files.createTempDirectory("okubi-guide-test-");
        Path file = dir.resolve("guides.properties");
        try {
            ControlGuideRegistry registry = new ControlGuideRegistry(file);
            assert registry.shouldShow("GBA", ControlGuideRegistry.Device.KEYBOARD);
            assert registry.shouldShow("gba", ControlGuideRegistry.Device.GAMEPAD);
            assert registry.bindingsFor("Game Boy Color").containsKey("Start");
            assert registry.bindingsFor("PS2").containsKey("Cross");
            assert registry.bindingsFor("Dreamcast").containsKey("Confirm");
            registry.markShown("GBA", ControlGuideRegistry.Device.KEYBOARD);
            assert !registry.shouldShow("GB", ControlGuideRegistry.Device.KEYBOARD);
            assert registry.shouldShow("GBA", ControlGuideRegistry.Device.GAMEPAD);
            registry = new ControlGuideRegistry(file);
            assert !registry.shouldShow("Game Boy", ControlGuideRegistry.Device.KEYBOARD);
            registry.reset("GBC", ControlGuideRegistry.Device.KEYBOARD);
            assert registry.shouldShow("GBA", ControlGuideRegistry.Device.KEYBOARD);
            System.out.println("ControlGuideRegistryTest OK");
        } finally {
            Files.deleteIfExists(file);
            Files.deleteIfExists(dir);
        }
    }
}
