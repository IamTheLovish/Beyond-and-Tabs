package com.solegendary.reignofnether.maps.gen;

import com.solegendary.reignofnether.maps.MapLayout;
import com.solegendary.reignofnether.startpos.CapturePointServerEvents.Kind;

import java.util.ArrayList;
import java.util.List;

/**
 * A crude 15-minute economic simulation of a generated map (lab only): the sanity check of the fairness score, since
 * bots cannot play offline. It shares the score's inputs (walk distances, exposure) but none of its pricing:
 * <ul>
 * <li>two build crews per seat (the commander, bp 3; three workers, bp 3) walk (2.5 b/s, 1.3 x the straight line) to
 *     their seat's patches nearest first and build T1 extractors (70 m / 90 e, 42 s at bp 1); a patch an enemy holds is
 *     skipped; from 8:00 safe extractors are refitted to T2 (180 m / 300 e, 45 s without income) one per 30 s;</li>
 * <li>energy is made, not priced: a base crew builds wind generators (50 m / 30 e, +14 e/s) whenever energy income falls
 *     below 1.37 x metal income (the unit cost ratio); spending stalls on whichever resource runs out;</li>
 * <li>everything left over is army (metal spent on units). Every 20 s each objective goes to the team whose army
 *     projects most force onto it (army / (1 + walk / 80), log-normal noise 0.3) if it leads by 25% and has had time to
 *     walk there and capture; its income or units go to that team's nearest seat;</li>
 * <li>raids: every 20 s an extractor is lost with probability 0.03 x smoothstep(0.35, 0.8, exposure) x the enemy
 *     team's army over its own (capped at 2), and must be rebuilt.</li>
 * </ul>
 * Result per seat: army value at 15:00 and metal income at 15:00, averaged over the runs; "strength" = army + 120 x
 * income (the next two minutes). Crude on purpose: it checks the score's ordering and the size of its gaps, not BAR.
 */
final class EconSim {
    static final double WALK = 2.5, HORIZON = 900, STEP = 1;

    record Result(double[] army, double[] income, double[] strength, double spread) { }

    /** Per-minute curves (lab): income[seat][minute] and army[seat][minute], averaged over the runs. */
    record Curves(double[][] income, double[][] army, double[][] objectives) { }

    static Curves curves(GenMap m, WalkGrid g, int[][] dist, int runs, long seed) {
        int n = m.n, mins = (int) (HORIZON / 60) + 1;
        double[][] inc = new double[n][mins], arm = new double[n][mins], obj = new double[n][mins];
        for (int r = 0; r < runs; r++) {
            Sim s = new Sim(m, g, dist, new GenRng(GenRng.mix(seed + r * 7919L)));
            s.curve = new double[3][n][mins];
            s.play();
            for (int k = 0; k < n; k++)
                for (int t = 0; t < mins; t++) {
                    inc[k][t] += s.curve[0][k][t] / runs;
                    arm[k][t] += s.curve[1][k][t] / runs;
                    obj[k][t] += s.curve[2][k][t] / runs;
                }
        }
        return new Curves(inc, arm, obj);
    }

    static Result run(GenMap m, WalkGrid g, int[][] dist, int runs, long seed) {
        int n = m.n;
        double[] army = new double[n], inc = new double[n];
        for (int r = 0; r < runs; r++) {
            Sim s = new Sim(m, g, dist, new GenRng(GenRng.mix(seed + r * 7919L)));
            s.play();
            for (int k = 0; k < n; k++) {
                army[k] += s.army[k] / runs;
                inc[k] += s.metalIncome(k) / runs;
            }
        }
        double[] str = new double[n];
        double mean = 0, lo = Double.MAX_VALUE, hi = -Double.MAX_VALUE;
        for (int k = 0; k < n; k++) {
            str[k] = army[k] + 120 * inc[k];
            mean += str[k] / n;
        }
        for (double v : str) {
            lo = Math.min(lo, v);
            hi = Math.max(hi, v);
        }
        return new Result(army, inc, str, mean > 0 ? (hi - lo) / mean : 1);
    }

    static final class Patch {
        final int x, z;
        final double value;
        final double[] d;
        int owner = -1;       // seat with an extractor on it
        boolean t2, refitting;
        double refitDone;

        Patch(int x, int z, double value, double[] d) {
            this.x = x;
            this.z = z;
            this.value = value;
            this.d = d;
        }
    }

    static final class Obj {
        final int code;       // Kind ordinal, 10 creep, 11 hydra, 12 relic, 20 vent
        final int x, z;
        final double[] d;
        int team = -1;
        double since;
        boolean done;         // hydra slain / vent plant built
        int plantSeat = -1;

        Obj(int code, int x, int z, double[] d) {
            this.code = code;
            this.x = x;
            this.z = z;
            this.d = d;
        }
    }

    static final class Crew {
        final double bp;
        double x, z;
        Patch target;
        double busyUntil;
        boolean walking;

        Crew(double bp, double x, double z) {
            this.bp = bp;
            this.x = x;
            this.z = z;
        }
    }

    static final class Sim {
        final GenMap m;
        final int n;
        final GenRng rng;
        final List<Patch> patches = new ArrayList<>();
        final List<Obj> objs = new ArrayList<>();
        final double[] metal, energy, army, wind, mult, mines, energyObj, units;
        final List<List<Crew>> crews = new ArrayList<>();
        final double[] windBusy, nextRefit;
        final int[] team;
        double[][][] curve;

        Sim(GenMap m, WalkGrid g, int[][] dist, GenRng rng) {
            this.m = m;
            this.n = m.n;
            this.rng = rng;
            team = m.teams;
            for (MapLayout.Mex x : m.mexes)
                patches.add(new Patch(x.dx(), x.dz(), x.value(), d(g, dist, x.dx(), x.dz())));
            for (MapLayout.Site s : m.sites)
                objs.add(new Obj(s.kind().ordinal(), s.dx(), s.dz(), d(g, dist, s.dx(), s.dz())));
            for (GenMap.Camp c : m.camps)
                objs.add(new Obj(10 + c.kind().ordinal(), c.dx(), c.dz(), d(g, dist, c.dx(), c.dz())));
            for (MapLayout.Spot v : m.vents)
                objs.add(new Obj(20, v.dx(), v.dz(), d(g, dist, v.dx(), v.dz())));
            metal = new double[n];
            energy = new double[n];
            army = new double[n];
            wind = new double[n];
            mult = new double[n];
            mines = new double[n];
            energyObj = new double[n];
            units = new double[n];
            windBusy = new double[n];
            nextRefit = new double[n];
            for (int s = 0; s < n; s++) {
                metal[s] = 1000;
                energy[s] = 1000;
                mult[s] = 1;
                MapLayout.Slot sl = m.slots.get(s);
                List<Crew> cs = new ArrayList<>();
                cs.add(new Crew(3, sl.dx(), sl.dz()));
                cs.add(new Crew(3, sl.dx(), sl.dz()));
                crews.add(cs);
                nextRefit[s] = 480;
            }
        }

        static double[] d(WalkGrid g, int[][] dist, int x, int z) {
            double[] r = new double[dist.length];
            for (int s = 0; s < dist.length; s++)
                r[s] = g.blocks(dist[s], x, z);
            return r;
        }

        int nearestOfTeam(double[] d, int t) {
            int best = -1;
            for (int s = 0; s < n; s++)
                if (team[s] == t && (best < 0 || d[s] < d[best]))
                    best = s;
            return best;
        }

        double exposure(double[] d, int s) {
            return Fairness.exposure(m, d, s);
        }

        double metalIncome(int s) {
            double v = 2 + mines[s];
            for (Patch p : patches)
                if (p.owner == s && !p.refitting)
                    v += (p.t2 ? 7 : 2) * p.value;
            return v;
        }

        double energyIncome(int s) {
            return 20 + 14 * wind[s] + energyObj[s];
        }

        /** The next patch for a seat's crew: its own team's nearest seat is this one, not deep in enemy ground. */
        Patch nextFor(int s, Crew c) {
            Patch best = null;
            double bd = Double.MAX_VALUE;
            for (Patch p : patches) {
                if (p.owner >= 0 || Double.isInfinite(p.d[s]) || nearestOfTeam(p.d, team[s]) != s || exposure(p.d, s) > 0.62)
                    continue;
                boolean taken = false;
                for (List<Crew> cs : crews)
                    for (Crew o : cs)
                        if (o.target == p)
                            taken = true;
                if (taken)
                    continue;
                double travel = Geo.len(p.x - c.x, p.z - c.z) * 1.3;
                double score = p.d[s] * 0.6 + travel * 0.4;
                if (score < bd) {
                    bd = score;
                    best = p;
                }
            }
            return best;
        }

        void play() {
            for (double t = 0; t < HORIZON; t += STEP) {
                if (curve != null && ((int) t) % 60 == 0)
                    for (int s = 0; s < n; s++) {
                        curve[0][s][(int) t / 60] = metalIncome(s);
                        curve[1][s][(int) t / 60] = army[s];
                        curve[2][s][(int) t / 60] = mines[s] + energyObj[s] / 35.0;
                    }
                for (int s = 0; s < n; s++)
                    economy(s, t);
                if (((int) t) % 20 == 0) {
                    objectives(t);
                    raids();
                }
            }
        }

        void economy(int s, double t) {
            double mi = metalIncome(s), ei = energyIncome(s);
            metal[s] += mi;
            energy[s] += ei;
            // spending demands this second
            double needM = 0, needE = 0;
            for (Crew c : crews.get(s)) {
                if (c.target == null && t >= c.busyUntil) {
                    c.target = nextFor(s, c);
                    if (c.target != null) {
                        double travel = Geo.len(c.target.x - c.x, c.target.z - c.z) * 1.3 / WALK;
                        c.busyUntil = t + travel;
                        c.walking = true;
                    }
                }
                if (c.target != null && t >= c.busyUntil) {
                    if (c.walking) {
                        c.walking = false;
                        c.x = c.target.x;
                        c.z = c.target.z;
                        if (c.target.owner >= 0) {   // somebody got there first
                            c.target = null;
                            continue;
                        }
                        c.busyUntil = t + 42 / c.bp;
                    } else {
                        c.target.owner = s;
                        c.target = null;
                    }
                }
                if (c.target != null && !c.walking) {
                    needM += 70 / (42 / c.bp);
                    needE += 90 / (42 / c.bp);
                }
            }
            // T2 refits from 8:00, one every 30 s, safe extractors first (the Forge discounts them)
            double forge = teamHolds(team[s], Kind.FORGE.ordinal()) ? 0.75 : 1;
            for (Patch p : patches)
                if (p.refitting && t >= p.refitDone) {
                    p.refitting = false;
                    p.t2 = true;
                }
            if (t >= nextRefit[s] && metal[s] > 180 * forge && energy[s] > 300 * forge) {
                Patch pick = null;
                for (Patch p : patches)
                    if (p.owner == s && !p.t2 && !p.refitting && exposure(p.d, s) < 0.42 && (pick == null || p.d[s] < pick.d[s]))
                        pick = p;
                if (pick != null) {
                    pick.refitting = true;
                    pick.refitDone = t + 45;
                    metal[s] -= 180 * forge;
                    energy[s] -= 300 * forge;
                    nextRefit[s] = t + 30;
                }
            }
            // a base crew builds wind generators (23 s each at bp 2) whenever energy lags metal
            boolean building = ei < 1.37 * mi + 5;
            if (building) {
                needM += 50 / 23.0;
                needE += 30 / 23.0;
            }
            double stall = Math.min(1, Math.min(needM > 0 ? metal[s] / needM : 1, needE > 0 ? energy[s] / needE : 1));
            metal[s] -= needM * stall;
            energy[s] -= needE * stall;
            if (building) {
                windBusy[s] += stall;
                if (windBusy[s] >= 23) {
                    wind[s]++;
                    windBusy[s] = 0;
                }
            }
            // army takes what is left (units cost about 1.37 energy a metal); storage 1500 caps the banks
            double spend = Math.max(0, Math.min(metal[s] - 50, (energy[s] - 50) / 1.37));
            if (t > 60 && spend > 0) {
                metal[s] -= spend;
                energy[s] -= spend * 1.37;
                army[s] += spend * mult[s];
            }
            metal[s] = Math.min(metal[s], 1500);
            energy[s] = Math.min(energy[s], 1500);
        }

        boolean teamHolds(int t, int code) {
            for (Obj o : objs)
                if (o.code == code && o.team == t)
                    return true;
            return false;
        }

        double teamArmy(int t) {
            double a = 0;
            for (int s = 0; s < n; s++)
                if (team[s] == t)
                    a += army[s];
            return a;
        }

        void objectives(double t) {
            int teams = 0;
            for (int x : team)
                teams = Math.max(teams, x + 1);
            for (Obj o : objs) {
                if (o.done && o.code == 11)
                    continue;
                double[] ctl = new double[teams];
                for (int s = 0; s < n; s++) {
                    if (Double.isInfinite(o.d[s]) || t < 100 + o.d[s] / WALK + 20)
                        continue;
                    double vis = (teamHolds(team[s], Kind.WATCHTOWER.ordinal()) ? 1.08 : 1)
                        * (teamHolds(team[s], Kind.BELL.ordinal()) ? 1.05 : 1);
                    ctl[team[s]] += (army[s] + 150) / (1 + o.d[s] / 80) * vis
                        * StrictMath.exp(0.3 * gauss());
                }
                int lead = -1;
                double a = 0, b = 0;
                for (int k = 0; k < teams; k++) {
                    if (ctl[k] > a) {
                        b = a;
                        a = ctl[k];
                        lead = k;
                    } else if (ctl[k] > b) {
                        b = ctl[k];
                    }
                }
                if (lead < 0 || a < 1.25 * b)
                    continue;
                if (o.code == 11) {       // the hydra: needs a real army; the slayers' army works 15% better
                    if (t >= 300 && teamArmy(lead) >= 1200) {
                        o.done = true;
                        for (int s = 0; s < n; s++)
                            if (team[s] == lead)
                                mult[s] *= 1.15;
                    }
                    continue;
                }
                if (o.team != lead) {
                    // the vent's plant belongs to the old holder: it falls with the site
                    if (o.code == 20 && o.plantSeat >= 0) {
                        energyObj[o.plantSeat] -= 120;
                        o.plantSeat = -1;
                    }
                    if (o.team >= 0)
                        payOut(o, o.team, false);
                    o.team = lead;
                    o.since = t;
                    payOut(o, lead, true);
                }
                tick(o, t);
            }
        }

        /** Starts (on) or stops (off) a held objective's continuous effect for the team's nearest seat. */
        void payOut(Obj o, int t, boolean on) {
            int s = nearestOfTeam(o.d, t);
            double sign = on ? 1 : -1;
            if (o.code == Kind.MINE.ordinal())
                mines[s] += 3 * sign;
            else if (o.code == Kind.SHRINE.ordinal())
                energyObj[s] += 30 * sign;
            else if (o.code == Kind.VENT.ordinal())
                energyObj[s] += 45 * sign;
            else if (o.code == Kind.OASIS.ordinal())
                mult[s] *= on ? 1.03 : 1 / 1.03;
            else if (o.code == 12)
                mult[s] *= on ? 1.08 : 1 / 1.08;
        }

        /** Every 20 s a held objective's periodic reward. */
        void tick(Obj o, double t) {
            int s = nearestOfTeam(o.d, o.team);
            if (o.code == Kind.CAMP.ordinal())
                army[s] += 70 * 20 / 60.0 * 0.6;        // a wolf a minute, most of them lost in play
            else if (o.code == Kind.GARRISON.ordinal())
                army[s] += 95 * 20 / 90.0 * 0.6;
            else if (o.code == 10 && ((int) (t - o.since)) % 180 == 0)
                metal[s] += 150;                          // a creep camp's bounty, respawning every 3 minutes
            else if (o.code == 20 && o.plantSeat < 0 && t >= 420 && metal[s] > 280 && energy[s] > 1000) {
                metal[s] -= 280;
                energy[s] -= 1000;
                energyObj[s] += 120;
                o.plantSeat = s;
            }
        }

        void raids() {
            for (Patch p : patches) {
                if (p.owner < 0)
                    continue;
                int s = p.owner;
                double x = exposure(p.d, s);
                double enemy = 0;
                for (int o = 0; o < n; o++)
                    if (team[o] != team[s])
                        enemy += army[o];
                double ratio = Math.min(2, (enemy + 100) / (teamArmy(team[s]) + 100));
                double pr = 0.03 * Fairness.smooth(0.35, 0.8, x) * ratio;
                if (rng.nextDouble() < pr) {
                    p.owner = -1;
                    p.t2 = false;
                    p.refitting = false;
                }
            }
        }

        double gauss() {
            double u = Math.max(1e-12, rng.nextDouble()), v = rng.nextDouble();
            return Math.sqrt(-2 * Math.log(u)) * StrictMath.cos(2 * Math.PI * v);
        }
    }
}
