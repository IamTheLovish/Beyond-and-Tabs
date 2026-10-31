package com.solegendary.reignofnether.gametest;

import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.barfx.BarFx;
import com.solegendary.reignofnether.barfx.BarFxClientboundPacket;
import com.solegendary.reignofnether.barfx.FeelFx;
import com.solegendary.reignofnether.barfx.InputBuffer;
import com.solegendary.reignofnether.barfx.ParticleBudget;
import com.solegendary.reignofnether.barfx.Telegraphs;
import io.netty.buffer.Unpooled;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * The game feel kit (hero-and-tier4 design, slice H3): trauma shake and its slider, hit-stop lengths, the telegraph's
 * 400 ms lead, the 150 ms input buffer, the particle cap and the sound layer pitch. Named after the acceptance
 * criteria; the maths is pure, so most checks need no world beyond the arena.
 */
@GameTestHolder(ReignOfNether.MOD_ID)
@PrefixGameTestTemplate(false)
public class FeelKitGameTests {

    static final String ARENA = SkirmishGameTests.ARENA;

    /** AC-1: with the slider at 0 the camera is not touched, whatever the trauma. */
    @GameTest(template = ARENA)
    public static void feel_ac1_shake_slider_zero_gives_zero_offset(GameTestHelper helper) {
        for (float trauma = 0f; trauma <= 1.0001f; trauma += 0.1f) {
            float s = FeelFx.shake(trauma, 0f);
            if (s != 0f) {
                helper.fail("slider 0 with trauma " + trauma + " still shakes by " + s);
                return;
            }
            // the offset applied is s * max * noise: zero for any noise
            for (int axis = 0; axis < 3; axis++)
                if (s * FeelFx.MAX_YAW_DEGREES * FeelFx.noise(1.234f, axis) != 0f) {
                    helper.fail("non-zero camera offset at slider 0");
                    return;
                }
        }
        if (FeelFx.shake(1f, 1f) != 1f) {
            helper.fail("full trauma at full slider should be 1, got " + FeelFx.shake(1f, 1f));
            return;
        }
        // never in the RTS view, whatever the trauma
        if (FeelFx.canShake(true, true) || !FeelFx.canShake(false, true) || FeelFx.canShake(false, false)) {
            helper.fail("canShake must be false in the RTS view and true only in a first/third person view");
            return;
        }
        helper.succeed();
    }

    /** AC-1b: trauma decays 1.5/s, never below 0, and the offset is trauma squared. */
    @GameTest(template = ARENA)
    public static void feel_ac1_trauma_decay_math(GameTestHelper helper) {
        if (Math.abs(FeelFx.decayed(1f, 0.5f) - 0.25f) > 1e-5f) {
            helper.fail("1.0 trauma after 0.5 s should be 0.25, got " + FeelFx.decayed(1f, 0.5f));
            return;
        }
        if (FeelFx.decayed(0.2f, 1f) != 0f) {
            helper.fail("trauma must clamp at 0, got " + FeelFx.decayed(0.2f, 1f));
            return;
        }
        if (Math.abs(FeelFx.shake(0.5f, 1f) - 0.25f) > 1e-5f) {
            helper.fail("shake is trauma^2: 0.5 -> 0.25, got " + FeelFx.shake(0.5f, 1f));
            return;
        }
        if (Math.abs(FeelFx.shake(0.5f, 0.5f) - 0.125f) > 1e-5f) {
            helper.fail("slider scales the shake: 0.5^2 * 0.5 = 0.125, got " + FeelFx.shake(0.5f, 0.5f));
            return;
        }
        // distance falloff: full at 0, nothing at the range
        if (FeelFx.traumaAt(1f, 0f) != 1f || FeelFx.traumaAt(1f, FeelFx.SHAKE_RANGE) != 0f) {
            helper.fail("distance falloff should run from 1 at the impact to 0 at SHAKE_RANGE");
            return;
        }
        // hit-stop lengths: 40 / 70 / 100 ms, all inside 40..120
        int s = FeelFx.hitStopMs(FeelFx.SMALL), b = FeelFx.hitStopMs(FeelFx.BIG), u = FeelFx.hitStopMs(FeelFx.ULTIMATE);
        if (s != 40 || b != 70 || u != 100) {
            helper.fail("hit-stop should be 40/70/100 ms, got " + s + "/" + b + "/" + u);
            return;
        }
        // the freeze holds the shake clock for its length, then trauma decays again
        FeelFx.reset();
        FeelFx.addTrauma(1f);
        FeelFx.startHitStop(1, 2, FeelFx.BIG, 1000);
        FeelFx.step(0.05f, 1050);   // inside the 70 ms freeze: held
        if (FeelFx.trauma() != 1f || !FeelFx.isFrozen(1, 1050) || !FeelFx.isFrozen(2, 1050) || FeelFx.isFrozen(3, 1050)) {
            FeelFx.reset();
            helper.fail("during hit-stop trauma is held and only the host and victim are frozen");
            return;
        }
        FeelFx.step(0.1f, 1100);    // after it: decays
        boolean decayed = Math.abs(FeelFx.trauma() - 0.85f) < 1e-4f && !FeelFx.isFrozen(1, 1100);
        FeelFx.reset();
        if (!decayed) {
            helper.fail("after the hit-stop trauma should decay by 1.5/s");
            return;
        }
        // the sound layers vary pitch by +-5%
        if (FeelFx.pitchVariance(0f) < 0.9499f || FeelFx.pitchVariance(0.9999f) > 1.0501f) {
            helper.fail("pitch variance should stay within +-5%");
            return;
        }
        helper.succeed();
    }

    /** AC-2: a telegraph reaches the clients at least 400 ms (8 ticks) before the effect may land. */
    @GameTest(template = ARENA)
    public static void feel_ac2_telegraph_lead_is_at_least_400ms_in_ticks(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        long now = level.getGameTime();
        Vec3 at = Vec3.atCenterOf(helper.absolutePos(new net.minecraft.core.BlockPos(4, 2, 4)));
        // an ability that would land at once, or in 3 ticks, is held back to the floor
        long lands0 = Telegraphs.announce(level, at, 4f, 0);
        long lands3 = Telegraphs.announce(level, at, 4f, 3);
        long lands30 = Telegraphs.announce(level, at, 4f, 30);
        if (lands0 - now < 8 || lands3 - now < 8) {
            helper.fail("a quick effect must land >= 8 ticks (400 ms) after the telegraph: " + (lands0 - now) + ", " + (lands3 - now));
            return;
        }
        if (lands30 - now < 30) {
            helper.fail("a long windup must keep its own delay, got " + (lands30 - now));
            return;
        }
        if (Telegraphs.MIN_LEAD_TICKS * 50 < 400) {
            helper.fail("MIN_LEAD_TICKS is under 400 ms");
            return;
        }
        // the event carries the lead and survives the packet codec (it goes out through the BarFx packet)
        BarFx.Event ev = Telegraphs.event(at, 4f, Telegraphs.lead(2), 4);
        BarFxClientboundPacket out = new BarFxClientboundPacket(java.util.List.of(ev));
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        out.encode(buf);
        BarFx.Event back = new BarFxClientboundPacket(buf).events().get(0);
        if (back.type != BarFx.TELEGRAPH || back.b < Telegraphs.MIN_LEAD_TICKS || back.a != 4f) {
            helper.fail("telegraph event lost its lead or radius in the codec: type " + back.type + " lead " + back.b);
            return;
        }
        helper.succeed();
    }

    /** AC-3: a cast never spawns more than its particle cap. */
    @GameTest(template = ARENA)
    public static void feel_ac3_particle_cap_per_cast_respected(GameTestHelper helper) {
        ParticleBudget b = ParticleBudget.high();
        int spawned = 0;
        for (int i = 0; i < 50; i++)
            spawned += b.take(20);   // asks for 1000
        if (spawned != ParticleBudget.HIGH || b.used() != ParticleBudget.HIGH || b.left() != 0 || b.take(5) != 0) {
            helper.fail("a High cast should stop at " + ParticleBudget.HIGH + " particles, spawned " + spawned);
            return;
        }
        if (new ParticleBudget(30).take(100) != 30 || new ParticleBudget(0).take(1) != 0) {
            helper.fail("a lower preset cap must hold too");
            return;
        }
        helper.succeed();
    }

    /** AC-4: a press during recovery is held for 150 ms and fires when ready, later presses are dropped. */
    @GameTest(template = ARENA)
    public static void feel_ac4_input_buffer_holds_a_press_for_150ms(GameTestHelper helper) {
        InputBuffer buf = new InputBuffer();
        if (!buf.press(2, 1000, false)) {
            helper.fail("a press with nothing in recovery should cast at once");
            return;
        }
        if (buf.press(2, 1000, true) || !buf.hasPending()) {
            helper.fail("a press during recovery should be buffered");
            return;
        }
        if (buf.take(1150) != 2) {   // ready exactly 150 ms later: still inside the window
            helper.fail("a press 150 ms before ready should fire");
            return;
        }
        if (buf.take(1150) != -1) {
            helper.fail("a buffered press fires once");
            return;
        }
        buf.press(1, 2000, true);
        if (buf.take(2151) != -1) {
            helper.fail("a press 151 ms before ready is too old");
            return;
        }
        buf.press(1, 3000, true);
        buf.press(3, 3050, true);   // the newer press replaces the older
        if (buf.take(3100) != 3) {
            helper.fail("the newest buffered press should win");
            return;
        }
        helper.succeed();
    }
}
