#!/usr/bin/env python3
"""Fluid model of the Tier 2 gate (resources/TierGate.java): when does the T2 lab finish for an eco rush and for a
normal player, and - with --refits - what does the economy earn over the match once the extractors are refitted?
Mirrors EconomyServerEvents' flow economy (one global stall = min(1, metal/demand, energy/demand), income banked to a
1500 cap) with nominal wind (14 energy/s a generator). A refitted extractor drains its energy upkeep first and makes
the share of its metal that the energy paid for (EconomyServerEvents.payExtractorUpkeep); its drill is stopped while
the refit runs (BuildingPlacement.getMetalIncome).

It is a model: the real timings come from the nightly bot ladder (T2 minute per bot) and the GameTest
bots_reach_tier_2_after_fielding_t1_units_and_not_before_minute_6. Run: python3 tools/t2_gate_sim.py [--refits]
The gates below must match TierGate.java / MetalExtractor.T2_METAL_INCOME; the PR tables are this output.
"""
import sys

WIND = 14.0
CAP_M = 2.0
CAP_E = 20.0
MEX = 2.0
STORAGE = 1500.0

# the gate as it stood on each decision (TierGate.java's javadoc quotes each one)
BASE = dict(eff=0.6, unit_m=60, unit_e=85, unit_s=25, decide=270, upkeep=20.0, base_spend=60.0)
GATES = {
    # before the tier gate (Oct 20): cheap research, cheap lab, the refit for 7 metal/s with no energy upkeep
    'oct20': dict(BASE, rm=400, re=1200, rs=120, lm=245, le=220, lw=257, moho=7.0, fm=180, fe=300, fs=45, upkeep=0.0),
    # the tier gate (lovish, Oct 22)
    'oct22': dict(BASE, rm=1000, re=8000, rs=120, lm=700, le=6000, lw=504, moho=7.0, fm=180, fe=600, fs=45),
    # the softer refit and the slower research (lovish, Oct 24)
    'oct24': dict(BASE, rm=1000, re=8000, rs=180, lm=700, le=6000, lw=504, moho=4.5, fm=300, fe=600, fs=75),
}


class Job:
    def __init__(self, name, m, e, work, kind='build'):
        self.name = name
        self.m = m
        self.e = e
        self.work = work
        self.done = 0.0
        self.kind = kind


MEXJ = dict(n='mex', m=70, e=90, w=42)
WINDJ = dict(n='wind', m=50, e=30, w=46)
LABJ = dict(n='lab', m=45, e=220, w=123)


def run(pol, P, horizon=900, refits=False, sample=()):
    """One player, dt = 1 s. Stops when the T2 lab stands unless refits is on, then plays to the horizon, refitting
    one extractor at a time while the energy can carry one more drill (the bot's rule:
    TierGate.botEnergyIncomeForRefit) and adding a wind whenever the build queue runs dry."""
    t = 0
    M = 1000.0
    E = 1000.0
    mex = winds = labs = moho = 0
    workers = 5
    wq = 0.0
    jobs = []
    queue = list(pol['order'])
    log = {'income': {}, 'refits': []}
    army = 0.0
    research = None
    refit = None
    t2 = t2lab = False
    while t < horizon:
        state = dict(t=t, mex=mex, winds=winds, labs=labs, t2=t2, army=army, M=M, E=E, t2lab=t2lab)
        while queue and len([j for j in jobs if j.kind == 'build']) < pol['sites']:
            q = queue[0]
            if q.get('after') and not q['after'](state):
                break
            queue.pop(0)
            jobs.append(Job(q['n'], q['m'], q['e'], q['w']))
        if research is None and not t2 and pol['research_when'](state):
            research = Job('T2', P['rm'], P['re'], P['rs'], 'research')
            jobs.append(research)
            log['research_start'] = t
        if refits and t2:
            if refit is None and mex > 0 and CAP_E + winds * WIND >= P['base_spend'] + (moho + 1) * P['upkeep']:
                refit = Job('refit', P['fm'], P['fe'], P['fs'], 'refit')
                jobs.append(refit)
                mex -= 1   # the drill stops while it refits
            if not queue and not any(j.kind == 'build' for j in jobs) and winds < 30:
                queue.append(dict(WINDJ))
        # worker production at the capitol (blocked while it researches)
        if workers + (1 if wq > 0 else 0) < pol['workers'](t) and research is None and wq == 0:
            wq = 22.0
        incM = CAP_M + mex * MEX
        incE = CAP_E + winds * WIND
        BP = P['eff'] * (3 + workers)
        builds = [j for j in jobs if j.kind == 'build']
        dM = dE = 0.0
        share = BP / max(1, len(builds)) if builds else 0
        rates = {}
        for j in builds:
            r = min(share, BP) / j.work   # progress fraction per s
            rates[j] = r
            dM += j.m * r
            dE += j.e * r
        for j in (research, refit):   # time-based jobs: their own clock, not build power
            if j is not None:
                r = 1.0 / j.work
                rates[j] = r
                dM += j.m * r
                dE += j.e * r
        ulabs = min(labs, pol['unit_labs'](t))
        uM = ulabs * P['unit_m'] / P['unit_s']
        uE = ulabs * P['unit_e'] / P['unit_s']
        dM += uM
        dE += uE
        if wq > 0:
            dM += 50 / 22
            dE += 70 / 22
        stallM = min(1, M / dM) if dM > 0 and M < dM else 1
        stallE = min(1, E / dE) if dE > 0 and E < dE else 1
        s = min(stallM, stallE)
        for j, r in rates.items():
            j.done += r * s
        M -= dM * s
        E -= dE * s
        army += uM * s
        if wq > 0:
            wq -= s
            if wq <= 0:
                workers += 1
                wq = 0
        # income: energy first, then the refitted drills' upkeep from that pool, then the metal it paid for
        E = min(STORAGE, E + incE)
        upkeep = moho * P['upkeep']
        paid = 1.0
        if upkeep > 0:
            paid = min(1.0, E / upkeep)
            E -= upkeep * paid
        metal_now = incM + moho * P['moho'] * paid
        M = min(STORAGE, M + metal_now)
        if t in sample:
            log['income'][t] = metal_now
        for j in list(jobs):
            if j.done >= 1.0:
                jobs.remove(j)
                n = j.name
                if n == 'mex':
                    mex += 1
                elif n == 'wind':
                    winds += 1
                elif n == 'lab':
                    labs += 1
                elif n == 'T2':
                    t2 = True
                    log['t2_research_done'] = t
                    research = None
                elif n == 't2lab':
                    t2lab = True
                    log['t2_lab_done'] = t
                elif n == 'refit':
                    moho += 1
                    refit = None
                    log['refits'].append(t)
        if t2 and not t2lab and not any(q['n'] == 't2lab' for q in queue) and not any(j.name == 't2lab' for j in jobs):
            queue.insert(0, dict(n='t2lab', m=P['lm'], e=P['le'], w=P['lw']))
        if t2lab and not refits:
            break
        t += 1
    log['army_metal'] = round(army)
    log['mex'] = mex
    log['moho'] = moho
    log['winds'] = winds
    log['labs'] = labs
    return log


def fm(x):
    return f"{x // 60:.0f}:{x % 60:02.0f}" if isinstance(x, (int, float)) else x


def order_rush(nw):
    o = [WINDJ, MEXJ, MEXJ, WINDJ, MEXJ, MEXJ, LABJ, WINDJ, MEXJ, MEXJ, MEXJ]
    return [dict(x) for x in o] + [dict(WINDJ) for _ in range(max(0, nw - 3))]


def policies(rush_winds, normal_winds, P):
    rush = dict(order=order_rush(rush_winds), sites=2, workers=lambda t: 8,
                research_when=lambda s: s['labs'] >= 1 and s['winds'] >= rush_winds and s['mex'] >= 6,
                unit_labs=lambda t: 0)
    # normal: lab first, 7 mex, 6 winds, 2nd/3rd labs, T2 decided at minute 4:30
    o = [LABJ, WINDJ, MEXJ, MEXJ, WINDJ, MEXJ, MEXJ, dict(LABJ), WINDJ, MEXJ, MEXJ, MEXJ, WINDJ, dict(LABJ)]
    o += [dict(WINDJ) for _ in range(max(0, normal_winds - 4))]
    normal = dict(order=[dict(x) for x in o], sites=2, workers=lambda t: min(12, 5 + t // 30),
                  research_when=lambda s: s['t'] >= P['decide'] and s['winds'] >= normal_winds,
                  unit_labs=lambda t: 99)
    return {'rush': rush, 'normal': normal}


def arrival_table():
    print('| gate   | player | energy   | research start -> done | T2 lab | T1 army spent |')
    print('|---|---|---|---|---|---|')
    rows = [('oct20', 5, 6)] + [(g, rw, nw) for g in ('oct22', 'oct24') for rw, nw in [(4, 5), (7, 7), (10, 10), (14, 14)]]
    for gate, rw, nw in rows:
        P = GATES[gate]
        for who, pol in policies(rw, nw, P).items():
            v = run(pol, P)
            w = rw if who == 'rush' else nw
            print('| %-6s | %-6s | %2d winds | %s -> %s | %s | %d |' % (
                gate, who, w, fm(v.get('research_start')), fm(v.get('t2_research_done')), fm(v.get('t2_lab_done')),
                v['army_metal']))


def refit_table(horizon=20 * 60):
    """The normal player (7 winds at the decision) plays on after the lab: refits one extractor at a time as the energy
    allows and keeps adding winds. Metal income (after the energy share) at each minute mark, and when refits land."""
    marks = [m * 60 for m in (6, 8, 10, 12, 14, 16, 18, 20) if m * 60 < horizon]
    print('| gate   | T2 lab | refits done (first .. last) | ' + ' | '.join('metal/s @%d' % (m // 60) for m in marks) + ' |')
    print('|---|---|---|' + '---|' * len(marks))
    for gate in ('oct22', 'oct24'):
        P = GATES[gate]
        v = run(policies(7, 7, P)['normal'], P, horizon=horizon, refits=True, sample=set(marks))
        r = v['refits']
        done = '%d (%s .. %s)' % (len(r), fm(r[0]), fm(r[-1])) if r else '0'
        print('| %-6s | %s | %s | ' % (gate, fm(v.get('t2_lab_done')), done)
              + ' | '.join('%.1f' % v['income'].get(m, 0) for m in marks) + ' |')
    print('(tier 1 only, for scale: the capitol + 7 extractors = %.1f metal/s)' % (CAP_M + 7 * MEX))


if __name__ == '__main__':
    arrival_table()
    if '--refits' in sys.argv:
        print()
        refit_table()
