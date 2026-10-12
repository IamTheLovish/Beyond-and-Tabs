#!/usr/bin/env python3
"""
Balance audit for the bar/ fork — run before every build that ships (part of the safety protocol, with
tools/check_java.py). Replicates ResourceCost.bakeValues offline: parses the config defaults, converts
RoN food/wood/ore to BAR Metal/Energy, and checks the economy's shape:
  - nothing (unit, building, research) may cost zero unless whitelisted
  - every cost constant defined in ResourceCosts must be baked in bakeValues
  - extractor/wind payback times stay in a sane band
  - a standard opening (extractors+winds) must afford the bot's army waves
Exit 1 on any failure so it can gate a push.
"""
import re, sys, math, os

ROOT = os.path.join(os.path.dirname(__file__), '..', 'bar/src/main/java/com/solegendary/reignofnether')

def rd(p):
    return open(os.path.join(ROOT, p)).read()

# same half-up rounding as Java's Math.round in ResourceCost.bakeValues (Python's round() is half-to-even)
def _round_to(v, step): return int(math.floor(v / step + 0.5)) * step
def to_metal(food, wood, ore): return _round_to(ore + food * 0.6 + wood * 0.3, 5)
def to_energy(food, wood, ore): return _round_to(wood * 1.5 + food * 0.8, 10)

cfg = rd('config/ReignOfNetherCommonConfigs.java')
entries = {}
for m in re.finditer(r'ResourceCostConfigEntry (\w+) = ResourceCostConfigEntry\.(Building|Unit|Research|Ability)\(\s*([-\d]+)\s*,\s*([-\d]+)\s*,\s*([-\d]+)\s*,\s*([-\d]+)\s*(?:,\s*([-\d]+))?', cfg):
    name, kind = m.group(1), m.group(2)
    a = [int(x) for x in m.groups()[2:] if x is not None]
    food, wood, ore = a[0], a[1], a[2]
    entries[name] = dict(kind=kind, food=food, wood=wood, ore=ore,
                         metal=to_metal(food, wood, ore), energy=to_energy(food, wood, ore))

costs_src = rd('resources/ResourceCosts.java')
declared = set(re.findall(r'ResourceCost (\w+) = new ResourceCost\(ID, "(?:\w+)"\);', costs_src))
baked = set(re.findall(r'(\w+)\.bakeValues\(', costs_src))

fails, warns = [], []

# 1) every declared configurable cost must be baked
for name in sorted(declared - baked):
    fails.append(f"NOT BAKED (cost stays zero): {name}")

# 2) free things (post-conversion) - abilities may be free, nothing else
# heroes (HeroProductionItem) are free to first-summon by design; pets/scenario units too
# DEPLOYED_TURRET: the Brassworks Guildmaster's 20 s turret - DeployTurret pays its 40 metal, the unit itself is free
FREE_OK = {'SILVERFISH', 'BEE', 'PIGLIN_MERCHANT', 'WILDFIRE', 'HERO_EXTRA_REVIVE_COST_PER_LEVEL', 'DEPLOYED_TURRET',
           'ENCHANTER', 'NECROMANCER', 'ROYAL_GUARD', 'WRETCHED_WRAITH'}
for name, e in sorted(entries.items()):
    if e['kind'] in ('Building', 'Unit') and e['metal'] == 0 and e['energy'] == 0 and name not in FREE_OK:
        fails.append(f"FREE {e['kind'].upper()}: {name} (food={e['food']} wood={e['wood']} ore={e['ore']} -> 0 metal, 0 energy)")

# 3) economy shape - keep in sync with the Java constants
EXTRACTOR_INCOME, WIND_INCOME = 2.0, 14.0      # per second (MetalExtractor / WindGenerator)
BASE_METAL, BASE_ENERGY = 2.0, 20.0            # EconomyServerEvents with a capitol
ex, wd = entries.get('METAL_EXTRACTOR'), entries.get('WIND_GENERATOR')
if ex:
    payback = ex['metal'] / EXTRACTOR_INCOME
    if not 15 <= payback <= 60:
        fails.append(f"Extractor metal payback {payback:.0f}s outside 15-60s (cost {ex['metal']}m/{ex['energy']}e)")
if wd:
    payback = wd['energy'] / WIND_INCOME if WIND_INCOME else 1e9
    if not 2 <= payback <= 40:
        warns.append(f"Wind energy payback {payback:.0f}s (cost {wd['metal']}m/{wd['energy']}e)")

# 4) can a normal opening afford the bot's waves?
#    income by ~6 min with 4 extractors and 4 winds; a medium wave = 14 t1 fighters
inc_m = BASE_METAL + 4 * EXTRACTOR_INCOME
inc_e = BASE_ENERGY + 4 * WIND_INCOME
wave = [entries.get('VINDICATOR'), entries.get('ZOMBIE')]
for u in wave:
    if not u:
        continue
    per_min_m, per_min_e = inc_m * 60, inc_e * 60
    affordable_m = per_min_m / max(1, u['metal'])
    affordable_e = per_min_e / max(1, u['energy'])
    rate = min(affordable_m, affordable_e)
    if rate < 3:
        fails.append(f"Army too expensive: ~{rate:.1f} per minute at a 4-ex/4-wind economy "
                     f"(unit {u['metal']}m/{u['energy']}e, income {inc_m:.0f}m/{inc_e:.0f}e per s)")
    elif rate > 30:
        warns.append(f"Army very cheap: ~{rate:.1f} per minute (unit {u['metal']}m/{u['energy']}e)")

# 5) per-unit stat sheet (tools/unit_stats.py reads the unit classes statically) - extraction must keep working,
#    tier outliers are reported, and `--report` regenerates design/balance.md from it
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import unit_stats

units, stat_errors = unit_stats.unit_stats()
buildings, b_errors = unit_stats.building_stats()
for e in stat_errors + b_errors:
    fails.append(f"STAT EXTRACTION: {e}")

OUTLIER = 0.40   # >40% off the tier median on DPS/100m or HP/100m
TIERS = [('t1', 'T1'), ('t2', 'T2'), ('t3', 'T3'), ('worker', 'Workers'), ('worker2', 'T2 workers'),
         ('engineer', 'Combat engineers')]

def median(xs):
    xs = sorted(xs)
    n = len(xs)
    if not n:
        return 0.0
    return xs[n // 2] if n % 2 else (xs[n // 2 - 1] + xs[n // 2]) / 2

# outliers a unit's role explains: (role, metric, direction). Tanks and siege beasts are meant to carry more HP per
# metal and less damage; artillery and snipers trade HP (and direct DPS - splash and range) for reach; dedicated
# anti-air pays for its air targeting. These are still listed, marked "role", but not counted as problems.
ROLE_EXPLAINS = {('tank', 'HP/100m', +1), ('tank', 'DPS/100m', -1), ('siege', 'HP/100m', +1),
                 ('artillery', 'HP/100m', -1), ('artillery', 'DPS/100m', -1), ('sniper', 'HP/100m', -1),
                 ('anti-air', 'DPS/100m', -1),
                 # the rock-paper-scissors front (design/balance.md): a line out-lasts raiders on HP, not damage
                 ('line', 'HP/100m', +1)}

medians, outliers = {}, []
for tier, _ in TIERS:
    combat = [u for u in units if u['tier'] == tier and u['role'] not in unit_stats.ROLE_EXEMPT and u['dps'] > 0]
    md, mh = median([u['dps100'] for u in combat]), median([u['hp100'] for u in combat])
    medians[tier] = (md, mh, len(combat))
    for u in combat:
        for key, med, label in (('dps100', md, 'DPS/100m'), ('hp100', mh, 'HP/100m')):
            if med and abs(u[key] - med) / med > OUTLIER:
                direction = 1 if u[key] > med else -1
                outliers.append((tier, u, label, u[key], med, (u['role'], label, direction) in ROLE_EXPLAINS))

# a faction's "T2 heavy" for the T3 rule: its front-line T2 bodies (not artillery, anti-air, casters or mergers)
HEAVY_ROLES = {'tank', 'siege', 'line', 'debuffer', 'cavalry'}
def t2_heavies(faction):
    return [u for u in units if u['tier'] == 't2' and u['faction'] == faction and u['role'] in HEAVY_ROLES]

# T3 rule of thumb (design-factions.md): an experimental costs ~4-6x its faction's priciest T2 fighter
for t3 in [u for u in units if u['tier'] == 't3']:
    t2 = t2_heavies(t3['faction'])
    if t2:
        heavy = max(t2, key=lambda u: u['metal'])
        ratio = t3['metal'] / max(1, heavy['metal'])
        if not 4.0 <= ratio <= 6.5:
            warns.append(f"T3 {t3['unit']} costs {ratio:.1f}x the {heavy['unit']} ({t3['metal']}m vs {heavy['metal']}m), "
                         f"design target 4-6x")
for tier, u, label, v, med, explained in outliers:
    if not explained:
        warns.append(f"outlier {tier} {u['faction']} {u['unit']} ({u['role']}): {label} {v:.2f} vs median {med:.2f}")

# Changes made by balance passes - kept here so a regenerated design/balance.md keeps its reasoning
# this pass (balance-pass, Oct 12): measured with the counter batch of gametest/DuelGameTests - see "Balance pass" under
# Duel results for the before/after rows
BALANCE_CHANGES = [
    '**Lines lose some arrow-proofing**: ranged resist Halberdier (Vindicator), Zombie/Husk/Drowned and Brute 0.2 -> 0.1, '
    'Reef Guard 0.45 -> 0.3. With it every line beat every skirmisher 1.4-3.2x in the open field (Thornbow v Reef Guard '
    '3.1x); now 1.2-2.0x and the Rifle Squad beats the Halberdier.',
    '**Lines are the slowest infantry**: Halberdier, Zombie/Husk/Drowned, Brute speed 0.28 -> 0.25 (the newer factions\' '
    'lines are 0.23-0.25), and skirmishers Pillager, Skeleton, Stray, Headhunter 0.24 -> 0.26 (the newer factions\' are '
    '0.26). Raider > skirmisher > line in speed, so a player can kite a line and a raider can run skirmishers down.',
    '**Zombie/Husk/Drowned** HP 40 -> 45: slower and less arrow-proof after the two changes above, the Zombie lost every '
    'T1 line duel (0-10, up to 1.8x).',
    '**Sentinel Treant** damage 8 -> 10 and cost 190M -> 170M (config ore 170 -> 150): the Court\'s line lost to every '
    'raider (Leafblade 1.6x, Cutlass 1.8x); now beats both 1.4x.',
    '**Thornbow** damage 4 -> 5: the weakest skirmisher per metal (2.15 DPS and 49 HP per 100m).',
    '**Iron Warden** cost 150M -> 125M (config ore 90 -> 65): the worst T1 line by far (2.8 DPS, 48 HP per 100m, plus '
    'heat venting) - 1-9 in the T1 matrix and the core of a Brassworks army that lost every mix fight 1.9-2.3x.',
    '**Steam Runner** cost 95M -> 85M (config ore 27 -> 17): 2-8 in the T1 matrix, beaten by both other raiders.',
    '**Rifle Squad** cost 85M -> 75M (config ore 25 -> 15): Brassworks still lost every mix fight 1.6-2.0x after the '
    'two changes above; the Rifle Squad lost to every line.',
    '**Pillager** cost 90M -> 80M (config food 110 -> 93) and **Headhunter** 70M -> 65M (config food 90 -> 82): they '
    'lost to their own faction\'s line 2.6x and 2.0x.',
    '**Cutlass Raider** cost 80M -> 85M (config ore 0 -> 7): 7-0-3 in the T1 matrix, the Tidewrought mix beat four of '
    'six factions.',
    '**Spider / Poison Spider** cost 75M -> 65M (config ore 20 -> 10) and **Marauder** 165M -> 145M (config ore 75 -> '
    '55) with speed 0.28 -> 0.31: the two Gravebound/Horde raiders lost to skirmishers (Spider v Skeleton 2.0x, '
    'Marauder v Headhunter 1.4x) and the Marauder was no faster than a skirmisher.',
    '**Sun Colossus** HP 1200 -> 1000: at 1200 it beat the equal-metal T2 counter mix 2-0 in one run, at 1100 it drew 1-1 in two of three (T3 rule (c): it loses). Still more HP per metal than the Bone Dragon; Solar Lance carries it.',
    '**Heroes** (not the Arch-Engineer): health and damage +15-35% (Dawn Paladin 160/7 -> 200/9, Gravecaller 120/6 -> '
    '165/8, Warchief 150/8 -> 180/9.5, Thornheart Druid 120/5 -> 165/7, Corsair Captain 140/7 -> 185/9, Storm Witch '
    '110/6 -> 130/7). At level 5 they lost every fight to two Iron Golems; after +~20% they killed about one Golem of '
    'two and split some runs; the second step takes them to the hero test\'s cap (at most 2.5 Tesla Walkers on stats at '
    'level 5; Paladin 2.37, Warchief 2.31). The Arch-Engineer\'s turret already beat two Golems.',
    'Tooling: the duel simulator\'s counter batch (`duel_counters`: rock-paper-scissors rows, best-T1-mix fights, hero '
    'rows), `duel_summary.py` packs its notices under GitHub\'s 4096-character cut (the T1 matrix notice was being '
    'truncated), melee diagnostic pads moved out of the duel matrix, lines\' extra HP per metal is now explained by role.',
]

EARLIER_CHANGES = [
    '**Sentinel Treant** (Verdant T1 tank) cost 260M/90E -> 190M/90E (config ore 240 -> 170). A tank with *less* HP per '
    'metal (54) than the T1 line median (62) and the lowest DPS per metal in the tier: it cost as much as the T2 Iron '
    'Golem it is built on. Now 74 HP/100m - a real tank for T1 money, still the slowest T1 body.',
    '**Elder Treant** (Verdant T2 super-tank) HP 520 -> 440 and cost 405M -> 455M/380E (config ore 170 -> 220). With '
    '50% ranged resist it had 128 HP/100m against a T2 median of ~37 and 2.5x the other T2 tanks (Warden 52, Iron '
    'Golem 45), plus median damage and a boulder AoE - on paper two of them (810M) matched a War Mammoth\'s HP '
    'and out-damaged it for under half its metal. Now 97 HP/100m: still '
    'the toughest T2 body. World Tree Walker stays at 4.0x its cost (T3 rule (a), GameTest-checked).',
    '**Stag Lancer** (Verdant T2 cavalry) attacks/s 0.8 -> 0.6 and cost 205M -> 235M/220E (config ore 90 -> 120). '
    '7.2 DPS for 205 metal was 2.6x the T2 median DPS per metal and almost a War Mammoth\'s DPS; now 5.4 DPS, '
    '2.3/100m - still the hardest-hitting T2 flanker (role: it has to kill artillery it leaps onto).',
    '**Marauder** (Horde T1 raider) cost 200M/250E -> 165M/200E (config food 175 -> 110). Even counting its every-third '
    'big hit (avg 8 dmg) it had 1.6 DPS/100m against a T1 median of ~3, the worst value fighter in T1. Now 1.94 '
    'DPS/100m and 85 HP/100m; Bloodlust is its upside.',
    '**Mudskipper Sloop** (Tidewrought T1 raider) cost 85M/90E -> 95M/100E (config food 110 -> 125). 4.7 DPS/100m was '
    '+50% over the T1 median and above every other raider (Cutlass 4.1, Leafblade 3.75) while also being the fastest '
    'on water; now 4.2.',
    '**Duel-measured pass after additive focus fire** (`focus-fire-rebalance`, details under "Duel results"): '
    '**Reef Guard** damage 5 -> 6 and cost 120M -> 105M (config ore 40 -> 25) - it lost every T1 melee duel 1.8-2.4x, '
    'now 2.86 DPS and 76 HP per 100m; **Brute** cost 70M -> 75M (config food 120 -> 125) - it won four of five and '
    'drew the fifth; **Cutlass Raider** damage 5.5 -> 5 - costed against the Leafblade (5) but beat four of five lines; '
    '**Sun Colossus** HP 1000 -> 1200 (the Leviathan\'s) - the counter mix now lands every hit on it.',
    'Tooling: `balance_audit.py` now rounds metal/energy half-up like Java\'s `Math.round` (Python\'s `round` is '
    'half-to-even and could misprice a cost by one step), and fails CI if the stat extractor stops finding a unit.',
]

# per-unit notes for outliers left alone on purpose
NOTES = {
    'Zombie': 'cheap swarm line (Gravebound identity: numbers + reassembly); fine while the 4-ex/4-wind wave check '
              'passes - watch in playtests',
    'Husk': 'as Zombie', 'Drowned': 'as Zombie',
    'Pillager': 'measured with the crossbow charge time included',
    'Spider': 'glass raider (30 HP, climbs walls): DPS buys no durability; 75M -> 65M this pass, duel-checked',
    'PoisonSpider': 'as Spider',
    'Marauder': 'heavy raider: HP over damage, Bloodlust and the every-third big hit are its upside (duel-checked)',
    'Ravager': 'siege beast, role', 'Warden': 'tank, role',
    'Blaze': 'damage is burning only (~1/s); dedicated anti-air',
    'Ghast': 'fireball splash + 30 range + flying; direct DPS understates it',
    'ShadeRanger': 'cloaked 24-range sniper: 16-damage shots, glass by design (14 HP/100m) - its DPS buys no durability',
    'BombardCrew': 'splash shell to every enemy hit; direct DPS understates it',
    'StagLancer': 'cavalry burst after this pass - still flagged, intentional; watch',
    'ElderTreant': 'super-tank, reduced this pass',
    'SentinelTreant': 'tank; DPS low by role, HP/100m fixed this pass',
}

def fmt(v, nd=1):
    return '-' if not v else (f"{v:.{nd}f}".rstrip('0').rstrip('.') if nd else str(int(round(v))))

def write_report(path):
    L = []
    L.append('# Balance sheet - live factions')
    L.append('')
    L.append('Generated by `python3 tools/balance_audit.py --report` (stats read statically from the unit classes by '
             '`tools/unit_stats.py`: each `createAttributes()` builder, costs from `ReignOfNetherCommonConfigs` converted '
             'like `ResourceCost.bakeValues`). Do not hand-edit the tables; change the Java, rerun, commit.')
    L.append('')
    L.append('Columns: **M/E** metal/energy cost - **HP** max health - **Dmg** attack damage - **APS** attacks per second '
             '- **Rng** attack range (blocks) - **Spd** movement attribute - **RRes** ranged damage resist - **DPS** = Dmg x '
             'APS (direct hits only; splash, DoT and abilities not counted) - **DPS/100m**, **HP/100m** per 100 metal. '
             'Flags: `AA` dedicated anti-air, `hits-air` any ranged attacker (melee cannot hit flyers), `artillery` '
             'range >= 20 or artillery role, `support`, `flying`. Outliers (**bold**) are >40% off the tier median of the '
             'combat units in that tier; builders, scouts, support, casters, debuffers, mergers, mounts, suicide units and '
             'experimentals are excluded from the median because they pay for something the table cannot see.')
    L.append('')
    for tier, title in TIERS:
        rows = [u for u in units if u['tier'] == tier]
        if not rows:
            continue
        md, mh, n = medians[tier]
        L.append(f'## {title}')
        L.append('')
        if n:
            L.append(f'Tier median over {n} combat units: DPS/100m **{md:.2f}**, HP/100m **{mh:.0f}**.')
            L.append('')
        L.append('| Faction | Unit | Role | M | E | HP | Dmg | APS | Rng | Spd | RRes | DPS | DPS/100m | HP/100m | Flags |')
        L.append('|---|---|---|--:|--:|--:|--:|--:|--:|--:|--:|--:|--:|--:|---|')
        flagged = {(id(u), label) for t, u, label, v, m, ex in outliers if t == tier}
        for u in rows:
            d100 = f"{u['dps100']:.2f}"
            h100 = f"{u['hp100']:.0f}"
            if (id(u), 'DPS/100m') in flagged:
                d100 = f'**{d100}**'
            if (id(u), 'HP/100m') in flagged:
                h100 = f'**{h100}**'
            L.append(f"| {u['faction']} | {u['unit']} | {u['role']} | {u['metal']} | {u['energy']} | {fmt(u['hp'], 0)} | "
                     f"{fmt(u['dmg'])} | {fmt(u['aps'], 2)} | {fmt(u['range'])} | {fmt(u['speed'], 2)} | "
                     f"{fmt(u['rres'], 2)} | {fmt(u['dps'], 2)} | {d100} | {h100} | {', '.join(u['flags'])} |")
        L.append('')
    L.append('## Towers')
    L.append('')
    L.append('Watchtowers have no weapon of their own: garrisoned units fire from them with +range. The Storm Oak is the '
             'Verdant T2 static lightning defence.')
    L.append('')
    L.append('| Building | M | E | HP | Range | HP/100m |')
    L.append('|---|--:|--:|--:|--:|--:|')
    for b in buildings:
        if b['kind'] == 'tower':
            L.append(f"| {b['building']} | {b['metal']} | {b['energy']} | {fmt(b['hp'], 0)} | {b['range'] or '-'} | "
                     f"{b['hp'] * 100 / max(1, b['metal']):.0f} |")
    L.append('')
    L.append('## Economy buildings')
    L.append('')
    L.append('| Building | M | E | HP | Metal/s | Energy/s | Payback |')
    L.append('|---|--:|--:|--:|--:|--:|---|')
    for b in buildings:
        if b['kind'] == 'eco':
            pb = '-'
            if b['metal_in']:
                pb = f"{b['metal'] / b['metal_in']:.0f} s metal"
            elif b['energy_in']:
                pb = f"{b['energy'] / b['energy_in']:.0f} s energy"
            L.append(f"| {b['building']} | {b['metal']} | {b['energy']} | {fmt(b['hp'], 0)} | {fmt(b['metal_in'])} | "
                     f"{fmt(b['energy_in'])} | {pb} |")
    L.append('')
    L.append('## T3 cost check')
    L.append('')
    L.append('Design rule: a T3 costs ~4-6x its faction\'s T2 heavy and loses to an equal-cost focused T2 army.')
    L.append('')
    L.append('| Faction | T3 | M | T2 heavy | M | Ratio | HP vs heavy | DPS vs heavy |')
    L.append('|---|---|--:|---|--:|--:|--:|--:|')
    for t3 in [u for u in units if u['tier'] == 't3']:
        t2 = t2_heavies(t3['faction'])
        if not t2:
            continue
        h = max(t2, key=lambda u: u['metal'])
        L.append(f"| {t3['faction']} | {t3['unit']} | {t3['metal']} | {h['unit']} | {h['metal']} | "
                 f"{t3['metal'] / h['metal']:.1f}x | {t3['hp'] / h['hp']:.1f}x | {t3['dps'] / max(0.01, h['dps']):.1f}x |")
    L.append('')
    L.append(f"All {len([u for u in units if u['tier'] == 't3'])} experimentals cost the same 1840M/1740E: the ratio "
             'column swings with how heavy a faction\'s biggest T2 front-liner is (Wither Skeleton 245M to Elder Treant 455M; '
             'the Tidewrought have no T2 front-liner), median ~6x. Rule (c) - an experimental loses to an equal-cost T2 counter '
             'army - is measured, not computed: see "T3 rules" under Duel results.')
    L.append('')
    L.append('## Outliers')
    L.append('')
    if outliers:
        for tier, u, label, v, med, explained in outliers:
            note = NOTES.get(u['unit'], '')
            tag = ' - *role*' if explained else ''
            L.append(f"- {tier.upper()} {u['faction']} **{u['unit']}** ({u['role']}): {label} {v:.2f} vs median {med:.2f} "
                     f"({(v - med) / med * 100:+.0f}%){tag}{' - ' + note if note else ''}")
    else:
        L.append('- none')
    L.append('')
    L.append('## Changes in this pass')
    L.append('')
    for c in BALANCE_CHANGES:
        L.append(f'- {c}')
    L.append('')
    L.append('## Earlier passes')
    L.append('')
    for c in EARLIER_CHANGES:
        L.append(f'- {c}')
    L.append('')
    # the "Duel results" section comes from real fights (gametest/DuelGameTests via CI notices, tools/duel_summary.py),
    # not from this script: keep it, verbatim, at the end of the regenerated sheet
    duel = ''
    if os.path.exists(path):
        old = open(path).read()
        at = old.find('\n## Duel results')
        if at >= 0:
            duel = old[at + 1:].rstrip('\n') + '\n'
    with open(path, 'w') as f:
        f.write('\n'.join(L))
        if duel:
            f.write('\n' + duel)

if '--report' in sys.argv:
    out = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', 'design', 'balance.md')
    write_report(out)
    print(f"wrote {os.path.normpath(out)}")

print(f"{len(entries)} cost entries, {len(declared)} configurable constants, {len(baked)} baked")
for w in warns:
    print("warn:", w)
for f in fails:
    print("FAIL:", f)
if not fails:
    print("balance audit: OK")
sys.exit(1 if fails else 0)
