package com.solegendary.reignofnether.launcher;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * lovish, Oct 23 00:01: testers who already installed the launcher should get his cat icon and the "lovish" shortcut
 * without reinstalling. The game updates itself every launch, so the mod brings the brand to an installer-made Prism.
 */
class LauncherBrandTest {

    static final String CFG = "[General]\nConfigVersion=1.3\nname=BeyondAndTabs-test\niconKey=default\n"
            + "OverrideCommands=true\nPreLaunchCommand=\"$INST_JAVA\" -jar packwiz-installer-bootstrap.jar https://x/pack.toml\n";

    /** <prism>/instances/<inst>/.minecraft, laid out as the installer makes it */
    static Path installerLayout(Path root, String cfg) throws IOException {
        Path prism = root.resolve("PrismLauncher");
        Path inst = prism.resolve("instances").resolve("BeyondAndTabs-test");
        Path game = inst.resolve(".minecraft");
        Files.createDirectories(game);
        Files.writeString(prism.resolve("portable.txt"), "Portable mode");
        Files.writeString(inst.resolve("instance.cfg"), cfg);
        return game;
    }

    @Test
    void findsAnInstallerMadePrism(@TempDir Path root) throws IOException {
        Path game = installerLayout(root, CFG);
        Optional<LauncherBrand.Layout> l = LauncherBrand.detect(game);
        assertTrue(l.isPresent());
        assertEquals(root.resolve("PrismLauncher"), l.get().prismDir());
        assertEquals(game.getParent().resolve("instance.cfg"), l.get().instanceCfg());
        assertEquals("BeyondAndTabs-test", l.get().instanceName());
    }

    @Test
    void leavesOtherLaunchersAlone(@TempDir Path root) throws IOException {
        // an instance without the pack updater (vanilla launcher, CurseForge, a hand-made Prism instance)
        Path game = installerLayout(root, "[General]\nname=Other\niconKey=default\n");
        assertTrue(LauncherBrand.detect(game).isEmpty());
        // no instance.cfg at all (the vanilla launcher's .minecraft)
        Path plain = root.resolve("vanilla").resolve(".minecraft");
        Files.createDirectories(plain);
        assertTrue(LauncherBrand.detect(plain).isEmpty());
        // an instance.cfg but not inside a portable Prism folder
        Path loose = root.resolve("loose").resolve("inst");
        Files.createDirectories(loose.resolve(".minecraft"));
        Files.writeString(loose.resolve("instance.cfg"), CFG);
        assertTrue(LauncherBrand.detect(loose.resolve(".minecraft")).isEmpty());
    }

    @Test
    void setsTheCatIconKeyOnce() {
        String out = LauncherBrand.withCatIcon(CFG);
        assertTrue(out.contains("\niconKey=" + LauncherBrand.ICON_KEY + "\n"), out);
        assertFalse(out.contains("iconKey=default"), out);
        assertEquals(1, out.split("iconKey=", -1).length - 1, out);
        assertEquals(out, LauncherBrand.withCatIcon(out));
        assertTrue(LauncherBrand.withCatIcon("[General]\nname=x\n").contains("iconKey=" + LauncherBrand.ICON_KEY));
        assertTrue(out.contains("PreLaunchCommand="), "the pack updater line must survive");
    }

    @Test
    void helperWaitsForPrismThenBrands() {
        Path prism = Path.of("C:", "Users", "Ann", "AppData", "Local", "BeyondAndTabs", "PrismLauncher");
        String ps = LauncherBrand.helperScript(4321L, prism, prism.resolve("instances/BeyondAndTabs-test/instance.cfg"),
                "BeyondAndTabs-test");
        // Prism rewrites instance.cfg when the game closes, so the icon key is set only after Prism has exited
        int wait = ps.indexOf("Wait-Process -Id 4321");
        int write = ps.indexOf("iconKey=" + LauncherBrand.ICON_KEY);
        assertTrue(wait >= 0 && write > wait, ps);
        assertTrue(ps.contains("'lovish.lnk'"), ps);
        assertTrue(ps.contains("'Beyond and Tabs.lnk'") && ps.contains("Remove-Item"), "old shortcut not removed:\n" + ps);
        assertTrue(ps.contains("lovish.ico,0"), ps);
        assertTrue(ps.contains("-l \"BeyondAndTabs-test\""), ps);
        assertTrue(ps.contains("prismlauncher.exe"), ps);
    }

    @Test
    void helperWithoutAPrismPidDoesNotWait() {
        String ps = LauncherBrand.helperScript(-1L, Path.of("P"), Path.of("P", "i.cfg"), "x");
        assertFalse(ps.contains("Wait-Process"), ps);
    }

    @Test
    void apostrophesInPathsAreQuoted() {
        Path prism = Path.of("C:", "Users", "O'Brien", "PrismLauncher");
        String ps = LauncherBrand.helperScript(1L, prism, prism.resolve("i.cfg"), "x");
        assertTrue(ps.contains("O''Brien"), ps);
        assertFalse(ps.replace("''", "").contains("O'Brien"), "unescaped quote:\n" + ps);
    }

    @Test
    void runsOncePerInstance(@TempDir Path root) throws IOException {
        Path game = installerLayout(root, CFG);
        LauncherBrand.Layout l = LauncherBrand.detect(game).orElseThrow();
        assertTrue(LauncherBrand.needsBrand(l));
        LauncherBrand.markBranded(l);
        assertFalse(LauncherBrand.needsBrand(l));
    }

    @Test
    void writesTheIconFiles(@TempDir Path root) throws IOException {
        Path game = installerLayout(root, CFG);
        LauncherBrand.Layout l = LauncherBrand.detect(game).orElseThrow();
        byte[] png = {(byte) 0x89, 'P', 'N', 'G'};
        byte[] ico = {0, 0, 1, 0};
        LauncherBrand.writeIcons(l, png, ico);
        assertArrayEquals(png, Files.readAllBytes(l.prismDir().resolve("icons").resolve(LauncherBrand.ICON_KEY + ".png")));
        assertArrayEquals(ico, Files.readAllBytes(l.prismDir().resolve("lovish.ico")));
    }
}
