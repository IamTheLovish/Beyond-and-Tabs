#!/usr/bin/env python3
"""
Fluid model of the opening and the road to Tier 2 under the flow economy (EconomyMath.granted / stall: when demand
outruns the pool every consumer gets the same share), with every number read from the Java source - so the PR tables
and tools/test_energy_scaling.py follow the code, not a copy of it (lovish, Oct 29: "energy scaling doesn't really work
the same way as it does in BAR"; "you can make 5 metal extractors and other buildings without having to make an energy
building").

The start: the commander (WorkerUnit.COMMANDER_BUILD_POWER) and four workers in two crews, the capitol's income and
storage, the capitol training workers back to back. Each crew walks WALK_S to every new site. After its opening list a
player follows the bots' rule (bot/BotEnergyRule): energy before the next extractor when the planned energy income is
short, extractors up to 7, a second lab at 2:30, the Tier 2 research decided at 4:30 (tools/t2_gate_sim.py's normal
player), then the T2 lab. The first lab trains the faction's T1 line unit back to back.

Usage: python3 tools/energy_sim.py      (the opening table: greedy / wind / solar openers)
"""
import os
import re
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import unit_stats  # noqa: E402

SRC = unit_stats.SRC
WALK_S = 6.0          # a crew's walk to its next site
DT = 0.1              # model step, seconds
HORIZON = 900         # seconds modelled
MAX_WORKERS = 10      # the capitol stops training past this (four start beside the commander)
DECIDE_T2 = 270       # the normal player decides on Tier 2 at 4:30 (tools/t2_gate_sim.py)
SECOND_LAB = 150      # and raises a second lab at 2:30
EXTRACTORS = 7        # patches near home


def _src(rel):
    p = os.path.join(SRC, rel)
    return open(p).read() if os.path.exists(p) else ''


def _num(text, name, default=None):
    """A `NAME = 12.5f` constant (several on one line are fine)."""
    m = re.search(r'\b' + name + r'\s*=\s*(-?[\d.]+)f?\b', text)
    return float(m.group(1)) if m else default


def numbers():
    """Every number the model needs, from the Java source. Missing constants (an older tree) fall back to what that
    tree does: no lab tiers, one converter ratio, the config's energy prices."""
    costs = unit_stats.load_costs()
    eco = _src('resources/EconomyServerEvents.java')
    gate = _src('resources/TierGate.java')
    worker = _src('unit/interfaces/WorkerUnit.java')
    building = _src('building/Building.java')
    wind = _src('resources/Wind.java')
    solar = _src('building/buildings/shared/SolarGenerator.java')
    table = _src('resources/EnergyCosts.java')
    ticks_per_block = _num(building, 'BUILD_TICKS_PER_BLOCK')

    def blocks(rel):
        return float(re.search(r'this\.buildTimeBlocks\s*=\s*(\d+)', _src(rel)).group(1))

    # the Solar Collector's price and build time are its Kind's arguments (energy may name an EnergyCosts constant)
    m = re.search(r'BASIC\([^,]+,[^,]+,\s*ResourceCost\.Building\(\s*0\s*,\s*([\w.]+)\s*,\s*(\d+)\s*,\s*0\)\s*,\s*([\d.]+)f\s*,'
                  r'\s*[\d.]+d\s*,\s*[\d.]+f\s*,\s*(\d+)', solar)
    solar_e = m.group(1)
    solar_e = float(solar_e) if solar_e.replace('.', '').isdigit() else _num(table, solar_e.split('.')[-1])
    mean_wind = (_num(wind, 'MIN') + _num(wind, 'MAX')) / 2
    t1_conv = _num(gate, 'CONVERTER_ENERGY_PER_METAL')
    n = dict(
        ticks_per_block=ticks_per_block,
        base_m=_num(eco, 'BASE_METAL_INCOME'), base_e=_num(eco, 'BASE_ENERGY_INCOME'),
        storage_m=_num(eco, 'DEFAULT_METAL_STORAGE') + _num(eco, 'CAPITOL_METAL_STORAGE'),
        storage_e=_num(eco, 'DEFAULT_ENERGY_STORAGE') + _num(eco, 'CAPITOL_ENERGY_STORAGE'),
        start_m=1000.0, start_e=1000.0,
        commander_bp=_num(worker, 'COMMANDER_BUILD_POWER'), worker_bp=_num(worker, 'DEFAULT_BUILD_POWER'),
        wind_e_s=mean_wind, solar_e_s=float(m.group(3)), mex_m_s=2.0,
        mex_upkeep=_num(gate, 'T1_EXTRACTOR_ENERGY_UPKEEP', 0.0),
        worker=(costs['VILLAGER']['metal'], costs['VILLAGER']['energy'], costs['VILLAGER']['seconds']),
        unit=(costs['VINDICATOR']['metal'], costs['VINDICATOR']['energy'], costs['VINDICATOR']['seconds']),
        items={
            'mex': (costs['METAL_EXTRACTOR']['metal'], costs['METAL_EXTRACTOR']['energy'],
                    blocks('building/buildings/shared/MetalExtractor.java') * ticks_per_block / 20),
            'wind': (costs['WIND_GENERATOR']['metal'], costs['WIND_GENERATOR']['energy'],
                     blocks('building/buildings/shared/WindGenerator.java') * ticks_per_block / 20),
            'solar': (float(m.group(2)), solar_e, float(m.group(4)) * ticks_per_block / 20),
            'lab': (costs['BARRACKS']['metal'], costs['BARRACKS']['energy'],
                    blocks('building/buildings/villagers/Barracks.java') * ticks_per_block / 20),
            't2lab': (_num(gate, 'T2_LAB_METAL'), _num(gate, 'T2_LAB_ENERGY'),
                      _num(gate, 'T2_LAB_BUILD_BLOCKS') * ticks_per_block / 20),
        },
        research=(_num(gate, 'T2_RESEARCH_METAL'), _num(gate, 'T2_RESEARCH_ENERGY'), _num(gate, 'T2_RESEARCH_SECONDS')),
        lab_power={1: _num(gate, 'T1_LAB_BUILD_POWER', 1.0), 2: _num(gate, 'T2_LAB_BUILD_POWER', 1.0),
                   3: _num(gate, 'T3_LAB_BUILD_POWER', 1.0)},
        converter={1: t1_conv, 2: _num(gate, 'T2_CONVERTER_ENERGY_PER_METAL', t1_conv),
                   3: _num(gate, 'T3_CONVERTER_ENERGY_PER_METAL', t1_conv)},
        rule_per_mex=_num(_src('bot/BotEnergyRule.java'), 'ENERGY_PER_EXTRACTOR', 6.0),
        rule_per_lab=_num(_src('bot/BotEnergyRule.java'), 'ENERGY_PER_LAB', 24.0),
    )
    return n


OPENERS = {
    'greedy': ['mex'] * 6 + ['lab'],                              # lovish's symptom: extractors and a lab, no energy
    'wind': ['mex', 'mex', 'wind', 'mex', 'wind', 'lab'],         # BAR's opening: 2 mex, a wind, a mex, a wind, the lab
    'solar': ['mex', 'mex', 'mex', 'solar', 'solar', 'lab'],      # BAR's other one: 3 mex, 2 solars, the lab
}


def run(n, opener, horizon=HORIZON):
    """One player through the opening to the T2 lab. Returns the timeline."""
    items = n['items']
    M, E = n['start_m'], n['start_e']
    have = {'mex': 0, 'wind': 0, 'solar': 0, 'lab': 0, 't2lab': 0}
    crews = [[n['commander_bp'] + n['worker_bp'], None, 0.0, 0.0], [3 * n['worker_bp'], None, 0.0, 0.0]]
    queue = list(OPENERS[opener])
    energy_kind = 'solar' if opener == 'solar' else 'wind'
    workers = 4
    wprog = 0.0
    research = None          # progress 0..1 while it runs
    t2 = False
    out = dict(opener=opener, first_stall=None, lab=None, research_start=None, t2=None, t2lab=None, stalled=0.0)
    run_s = 0.0
    t = 0.0
    while t < horizon and out['t2lab'] is None:
        planned_e = n['base_e'] + have['wind'] * n['wind_e_s'] + have['solar'] * n['solar_e_s']
        for c in crews:
            if c[1] is None and queue:
                c[1] = queue.pop(0)
            if c[1] is None and not queue and out['lab'] is not None:
                # after the opening list: the bots' rule (energy before the next extractor), then expansion
                rising = sum(n['wind_e_s'] if x[1] == 'wind' else n['solar_e_s'] if x[1] == 'solar' else 0 for x in crews)
                need = n['rule_per_mex'] * (have['mex'] + 1) + n['rule_per_lab'] * have['lab']
                if t2 and not have['t2lab'] and not any(x[1] == 't2lab' for x in crews):
                    c[1] = 't2lab'
                elif planned_e + rising < need + (n['research'][1] / n['research'][2] if research is not None else 0):
                    c[1] = energy_kind
                elif have['mex'] + sum(x[1] == 'mex' for x in crews) < EXTRACTORS:
                    c[1] = 'mex'
                elif t >= SECOND_LAB and have['lab'] + sum(x[1] == 'lab' for x in crews) < 2:
                    c[1] = 'lab'
                else:
                    c[1] = energy_kind
            if c[1] is not None and c[2] == 0.0 and c[3] == 0.0:
                c[3] = WALK_S
        if research is None and not t2 and t >= DECIDE_T2 and have['lab'] >= 1:
            research = 0.0
            out['research_start'] = t
        dem = []
        for c in crews:
            if c[1] is not None and c[3] <= 0:
                m, e, bs = items[c[1]]
                df = c[0] / bs * DT
                dem.append((c, m * df, e * df, df))
        if research is None and workers < MAX_WORKERS:   # the capitol trains no workers while it researches
            m, e, s = n['worker']
            dem.append(('w', m * DT / s, e * DT / s, DT / s))
        if research is not None:
            m, e, s = n['research']
            dem.append(('r', m * DT / s, e * DT / s, DT / s))
        for _ in range(have['lab']):
            m, e, s = n['unit']
            p = n['lab_power'][1]
            dem.append(('u', m * DT / s * p, e * DT / s * p, 0))
        upkeep = have['mex'] * n['mex_upkeep'] * DT
        dM = sum(d[1] for d in dem)
        dE = sum(d[2] for d in dem) + upkeep
        gM = 1.0 if dM <= 0 else min(1.0, M / dM)
        gE = 1.0 if dE <= 0 else min(1.0, E / dE)
        g = max(0.0, min(gM, gE))
        if gE < 0.9 and gE <= gM:
            run_s += DT
            out['stalled'] += DT
            if run_s >= 2.0 and out['first_stall'] is None:
                out['first_stall'] = (round(t - 2.0), have['mex'], have['wind'] + have['solar'])
        else:
            run_s = 0.0
        M = max(0.0, M - dM * g)
        E = max(0.0, E - (dE - upkeep) * g - upkeep * gE)
        mex_share = gE   # starved extractors make that share of their metal (EconomyServerEvents.payExtractorUpkeep)
        for c, m, e, df in dem:
            if c == 'w':
                wprog += df * g
                if wprog >= 1:
                    wprog -= 1
                    workers += 1
                    # a new worker joins the newest crew; every third one starts a crew of its own
                    if workers % 3 == 0:
                        crews.append([0.0, None, 0.0, 0.0])
                    crews[-1][0] += n['worker_bp']
                continue
            if c == 'r':
                research += df * g
                if research >= 1:
                    research = None
                    t2 = True
                    out['t2'] = round(t)
                continue
            if c == 'u':
                continue
            c[2] += df * g
            if c[2] >= 1:
                have[c[1]] += 1
                if c[1] == 'lab' and out['lab'] is None:
                    out['lab'] = round(t)
                if c[1] == 't2lab':
                    out['t2lab'] = round(t)
                c[1], c[2], c[3] = None, 0.0, 0.0
        for c in crews:
            if c[1] is not None and c[3] > 0:
                c[3] -= DT
        inc_e = n['base_e'] + have['wind'] * n['wind_e_s'] + have['solar'] * n['solar_e_s']
        inc_m = n['base_m'] + have['mex'] * n['mex_m_s'] * mex_share
        M = min(n['storage_m'], M + inc_m * DT)
        E = min(n['storage_e'], E + inc_e * DT)
        t += DT
    out['stalled'] = round(out['stalled'])
    return out


def per_lab_drain(n, unit_key, tier):
    """Energy/s one lab of that tier draws training that unit back to back (production build power x cost / time)."""
    c = unit_stats.load_costs()[unit_key]
    return c['energy'] / max(1, c['seconds']) * n['lab_power'][tier]


def fm(s):
    return '-' if s is None else '%d:%02d' % (s // 60, s % 60)


def table():
    n = numbers()
    print('| opener | first energy stall | first lab | T2 research | T2 done | T2 lab | stalled s |')
    print('|---|---|---|---|---|---|--:|')
    for o in OPENERS:
        r = run(n, o)
        fs = r['first_stall']
        stall = 'none' if fs is None else '%s (%d mex, %d energy)' % (fm(fs[0]), fs[1], fs[2])
        print('| %s | %s | %s | %s | %s | %s | %d |' % (o, stall, fm(r['lab']), fm(r['research_start']), fm(r['t2']),
                                                      fm(r['t2lab']), r['stalled']))


if __name__ == '__main__':
    table()
