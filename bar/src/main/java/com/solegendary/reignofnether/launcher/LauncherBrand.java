package com.solegendary.reignofnether.launcher;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

/**
 * Brings lovish's launcher look - his cat as the icon, a "lovish" shortcut - to testers who installed the launcher
 * before it had it (lovish, Oct 23 00:01: "implement for people that already have the launcher so they dont have to
 * reinstall it"). New installs get it from installer/ (BeyondAndTabs-Setup.exe).
 *
 * Runs once per instance, on Windows, and only inside a Prism instance made by our installer: a portable Prism folder
 * whose instance.cfg runs the packwiz pack updater. Any other launcher is left alone. Plain Java (no Minecraft
 * classes), so it is unit-tested.
 *
 * Prism keeps instance.cfg in memory and writes it back when the game closes, so the icon key is set by a small
 * detached PowerShell helper that first waits for Prism (this game's parent process) to exit; the cat then shows the
 * next time the launcher opens. The helper also swaps the "Beyond and Tabs" desktop / Start Menu shortcut for "lovish".
 */
public final class LauncherBrand {
    public static final String ICON_KEY = "lovish_cat";       // same key as installer/main.go (catIconKey)
    static final String SHORTCUT = "lovish";                   // installer/main.go shortcutName
    static final String OLD_SHORTCUT = "Beyond and Tabs";      // installer/main.go appName
    static final String MARKER = ".lovish-brand-v1";
    static final String PNG_RES = "/assets/reignofnether/brand/lovish_cat.png";
    static final String ICO_RES = "/assets/reignofnether/brand/lovish.ico";

    private LauncherBrand() {}

    public record Layout(Path prismDir, Path instanceDir, Path instanceCfg, String instanceName) {}

    /** The installer's layout around the game folder: {@code <prism>/instances/<inst>/.minecraft}, else empty. */
    public static Optional<Layout> detect(Path gameDir) {
        try {
            Path inst = gameDir.toAbsolutePath().normalize().getParent();
            if (inst == null || inst.getParent() == null || inst.getParent().getParent() == null) return Optional.empty();
            Path cfg = inst.resolve("instance.cfg");
            Path prism = inst.getParent().getParent();
            if (!Files.isRegularFile(cfg) || !Files.isRegularFile(prism.resolve("portable.txt"))) return Optional.empty();
            String text = Files.readString(cfg, StandardCharsets.UTF_8);
            if (!text.contains("packwiz-installer-bootstrap")) return Optional.empty();
            String name = inst.getFileName().toString();
            for (String line : text.split("\\r?\\n")) {
                if (line.startsWith("name=")) name = line.substring(5).trim();
            }
            return Optional.of(new Layout(prism, inst, cfg, name));
        } catch (IOException | RuntimeException e) {
            return Optional.empty();
        }
    }

    public static boolean needsBrand(Layout l) {
        return !Files.exists(l.instanceDir().resolve(MARKER));
    }

    public static void markBranded(Layout l) throws IOException {
        Files.writeString(l.instanceDir().resolve(MARKER), "lovish's cat + shortcut applied by the mod\n");
    }

    /** iconKey set to the cat (added when missing); a second call changes nothing. Same rule as the installer. */
    public static String withCatIcon(String cfg) {
        String[] lines = cfg.replace("\r\n", "\n").split("\n", -1);
        for (int i = 0; i < lines.length; i++) {
            if (lines[i].startsWith("iconKey=")) {
                lines[i] = "iconKey=" + ICON_KEY;
                return String.join("\n", lines);
            }
        }
        for (int i = 0; i < lines.length; i++) {
            if (lines[i].trim().equals("[General]")) {
                lines[i] = lines[i] + "\niconKey=" + ICON_KEY;
                return String.join("\n", lines);
            }
        }
        return cfg.stripTrailing() + "\niconKey=" + ICON_KEY + "\n";
    }

    public static void writeIcons(Layout l, byte[] png, byte[] ico) throws IOException {
        Path icons = l.prismDir().resolve("icons");
        Files.createDirectories(icons);
        Files.write(icons.resolve(ICON_KEY + ".png"), png);
        Files.write(l.prismDir().resolve(SHORTCUT + ".ico"), ico);
    }

    private static String q(Path p) {
        return "'" + p.toString().replace("'", "''") + "'";
    }

    private static String q(String s) {
        return "'" + s.replace("'", "''") + "'";
    }

    /** The PowerShell helper: wait for Prism to exit (pid < 0: don't wait), set the icon key, swap the shortcuts. */
    public static String helperScript(long prismPid, Path prismDir, Path instanceCfg, String instanceName) {
        Path exe = prismDir.resolve("prismlauncher.exe");
        Path ico = prismDir.resolve(SHORTCUT + ".ico");
        StringBuilder s = new StringBuilder();
        if (prismPid >= 0) {
            s.append("Wait-Process -Id ").append(prismPid).append(" -ErrorAction SilentlyContinue\n");
        }
        s.append("$cfg = ").append(q(instanceCfg)).append("\n")
         .append("$t = [IO.File]::ReadAllText($cfg)\n")
         .append("if ($t -match '(?m)^iconKey=') { $t = $t -replace '(?m)^iconKey=.*$', 'iconKey=").append(ICON_KEY)
         .append("' } else { $t = $t -replace '(?m)^\\[General\\]\\r?$', \"[General]`niconKey=").append(ICON_KEY)
         .append("\" }\n")
         .append("[IO.File]::WriteAllText($cfg, $t)\n")
         .append("$exe = ").append(q(exe)).append("\n")
         .append("$ws = New-Object -ComObject WScript.Shell\n")
         .append("foreach ($dir in @([Environment]::GetFolderPath('Desktop'), (Join-Path ([Environment]::GetFolderPath('StartMenu')) 'Programs'))) {\n")
         // an existing "lovish" shortcut is never replaced (lovish's own starts his server)
         .append("  $lnk = Join-Path $dir ").append(q(SHORTCUT + ".lnk")).append("\n")
         .append("  if (-not (Test-Path -LiteralPath $lnk)) {\n")
         .append("    $s = $ws.CreateShortcut($lnk)\n")
         .append("    $s.TargetPath = $exe\n")
         .append("    $s.Arguments = ").append(q("-l \"" + instanceName + "\"")).append("\n")
         .append("    $s.WorkingDirectory = ").append(q(prismDir)).append("\n")
         .append("    $s.IconLocation = ").append(q(ico + ",0")).append("\n")
         .append("    $s.Description = 'Play Beyond and Tabs'\n")
         .append("    $s.Save()\n")
         .append("  }\n")
         // the old "Beyond and Tabs" shortcut goes only when it opens this Prism (it was ours)
         .append("  $oldPath = Join-Path $dir ").append(q(OLD_SHORTCUT + ".lnk")).append("\n")
         .append("  if (Test-Path -LiteralPath $oldPath) {\n")
         .append("    $old = $ws.CreateShortcut($oldPath)\n")
         .append("    if ($old.TargetPath -eq $exe) { Remove-Item -LiteralPath $oldPath -ErrorAction SilentlyContinue }\n")
         .append("  }\n")
         .append("}\n");
        return s.toString();
    }

    /** Called once at client start, off the main thread; never throws and never blocks the game. */
    public static void applyOnce(Path gameDir) {
        try {
            if (!System.getProperty("os.name", "").toLowerCase().contains("windows")) return;
            Optional<Layout> found = detect(gameDir);
            if (found.isEmpty() || !needsBrand(found.get())) return;
            Layout l = found.get();
            byte[] png, ico;
            try (InputStream a = LauncherBrand.class.getResourceAsStream(PNG_RES);
                 InputStream b = LauncherBrand.class.getResourceAsStream(ICO_RES)) {
                if (a == null || b == null) return;
                png = a.readAllBytes();
                ico = b.readAllBytes();
            }
            writeIcons(l, png, ico);
            long prismPid = ProcessHandle.current().parent()
                    .filter(p -> p.info().command().map(c -> c.toLowerCase().endsWith("prismlauncher.exe")).orElse(false))
                    .map(ProcessHandle::pid).orElse(-1L);
            Path script = l.instanceDir().resolve("lovish-brand.ps1");
            Files.writeString(script, helperScript(prismPid, l.prismDir(), l.instanceCfg(), l.instanceName()),
                    StandardCharsets.UTF_8);
            new ProcessBuilder("powershell", "-NoProfile", "-WindowStyle", "Hidden", "-ExecutionPolicy", "Bypass",
                    "-File", script.toString())
                    .directory(l.instanceDir().toFile())
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                    .redirectError(ProcessBuilder.Redirect.DISCARD)
                    .start();
            markBranded(l);
        } catch (Throwable ignored) {
            // cosmetic only: a failure must never affect the game; it retries next start (no marker written)
        }
    }
}
