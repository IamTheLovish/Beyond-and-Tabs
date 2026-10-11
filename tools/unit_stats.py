#!/usr/bin/env python3
"""
Static unit-stat extractor for the bar/ fork (used by tools/balance_audit.py --report).

Reads each unit class's `final static public float attackDamage / attacksPerSecond / maxHealth / armorValue /
movementSpeed / attackRange` straight from the Java source - no game launch needed (CI logs aren't readable,
so this runs locally and in CI). Simple constant expressions are evaluated ("FANGS_DAMAGE * 2",
"1f / (SetFangsLine.CD_MAX_SECONDS + FANGS_CHANNEL_SECONDS)"), resolving names from the same class first,
then from any class of that name in the source tree. The cost comes from the matching *Prod class's
`cost = ResourceCosts.X`, converted to Metal/Energy exactly like ResourceCost.bakeValues.

The roster below is what the six live factions can actually train (from building/buildings/* production
lists); keep it in sync when a unit is added or moved to another lab.
"""
import math, os, re

SRC = os.path.join(os.path.dirname(__file__), '..', 'bar/src/main/java/com/solegendary/reignofnether')

# (faction, tier bucket, unit class, role) - role is the design job, used to excuse outliers and set flags
# tiers: worker, worker2 (T2 constructor), t1, t2, t3. Scouts sit in t1 with role "scout".
ROSTER = [
    # Sunforged (villagers)
    ('Sunforged', 'worker', 'villagers/VillagerUnit', 'builder'),
    ('Sunforged', 'worker2', 'villagers/RoyalArchitectUnit', 'builder'),
    ('Sunforged', 't1', 'villagers/ScoutDogUnit', 'scout'),
    ('Sunforged', 't1', 'villagers/ScoutCatUnit', 'scout'),
    ('Sunforged', 't1', 'villagers/VindicatorUnit', 'line'),
    ('Sunforged', 't1', 'villagers/PillagerUnit', 'skirmisher'),
    ('Sunforged', 't2', 'villagers/EvokerUnit', 'caster'),
    ('Sunforged', 't2', 'villagers/WindcallerUnit', 'anti-air'),
    ('Sunforged', 't2', 'villagers/WitchUnit', 'support'),
    ('Sunforged', 't2', 'villagers/IronGolemUnit', 'tank'),
    ('Sunforged', 't2', 'villagers/RavagerUnit', 'siege'),
    ('Sunforged', 't3', 'villagers/SunColossusUnit', 'experimental'),
    # Gravebound (monsters)
    ('Gravebound', 'worker', 'monsters/ZombieVillagerUnit', 'builder'),
    ('Gravebound', 'worker2', 'monsters/EmbalmerUnit', 'builder'),
    ('Gravebound', 't1', 'monsters/BatUnit', 'scout'),
    ('Gravebound', 't1', 'monsters/ZombieUnit', 'line'),
    ('Gravebound', 't1', 'monsters/HuskUnit', 'line'),
    ('Gravebound', 't1', 'monsters/DrownedUnit', 'line'),
    ('Gravebound', 't1', 'monsters/SkeletonUnit', 'skirmisher'),
    ('Gravebound', 't1', 'monsters/StrayUnit', 'skirmisher'),
    ('Gravebound', 't1', 'monsters/BoggedUnit', 'debuffer'),
    ('Gravebound', 't1', 'monsters/SpiderUnit', 'raider'),
    ('Gravebound', 't1', 'monsters/PoisonSpiderUnit', 'raider'),
    ('Gravebound', 't1', 'monsters/SlimeUnit', 'merger'),
    ('Gravebound', 't2', 'monsters/CreeperUnit', 'suicide'),
    ('Gravebound', 't2', 'monsters/WraithUnit', 'caster'),
    ('Gravebound', 't2', 'monsters/WardenUnit', 'tank'),
    ('Gravebound', 't3', 'monsters/BoneDragonUnit', 'experimental'),
    # Horde (piglins)
    ('Horde', 'worker', 'piglins/GruntUnit', 'builder'),
    ('Horde', 'worker2', 'piglins/BonewrightUnit', 'builder'),
    ('Horde', 't1', 'piglins/StriderUnit', 'scout'),
    ('Horde', 't1', 'piglins/BruteUnit', 'line'),
    ('Horde', 't1', 'piglins/HeadhunterUnit', 'skirmisher'),
    ('Horde', 't1', 'piglins/MarauderUnit', 'raider'),
    ('Horde', 't1', 'piglins/HoglinUnit', 'mount'),
    ('Horde', 't2', 'piglins/BlazeUnit', 'anti-air'),
    ('Horde', 't2', 'piglins/WitherSkeletonUnit', 'debuffer'),
    ('Horde', 't2', 'piglins/MagmaCubeUnit', 'merger'),
    ('Horde', 't2', 'piglins/GhastUnit', 'artillery'),
    ('Horde', 't3', 'piglins/WarMammothUnit', 'experimental'),
    # Verdant Court
    ('Verdant', 'worker', 'verdant/SeedshaperUnit', 'builder'),
    ('Verdant', 'worker2', 'verdant/ElderDruidUnit', 'builder'),
    ('Verdant', 't1', 'verdant/FoxCourierUnit', 'scout'),
    ('Verdant', 't1', 'verdant/OwlWatcherUnit', 'scout'),
    ('Verdant', 't1', 'verdant/LeafbladeUnit', 'raider'),
    ('Verdant', 't1', 'verdant/ThornbowUnit', 'skirmisher'),
    ('Verdant', 't1', 'verdant/SentinelTreantUnit', 'tank'),
    ('Verdant', 't1', 'verdant/HiveKeeperUnit', 'anti-swarm'),
    ('Verdant', 't1', 'verdant/MoonwellBearerUnit', 'support'),
    ('Verdant', 't2', 'verdant/ElderTreantUnit', 'tank'),
    ('Verdant', 't2', 'verdant/StagLancerUnit', 'cavalry'),
    ('Verdant', 't2', 'verdant/WispChoirUnit', 'anti-air'),
    ('Verdant', 't2', 'verdant/ShadeRangerUnit', 'sniper'),
    ('Verdant', 't2', 'verdant/BloomPriestessUnit', 'support'),
    ('Verdant', 't3', 'verdant/WorldTreeWalkerUnit', 'experimental'),
    # Tidewrought
    ('Tidewrought', 'worker', 'tide/ShipwrightUnit', 'builder'),
    ('Tidewrought', 't1', 'tide/GullSpotterUnit', 'scout'),
    ('Tidewrought', 't1', 'tide/CutlassRaiderUnit', 'raider'),
    ('Tidewrought', 't1', 'tide/HarpoonerUnit', 'skirmisher'),
    ('Tidewrought', 't1', 'tide/ReefGuardUnit', 'line'),
    ('Tidewrought', 't1', 'tide/BombardCrewUnit', 'artillery'),
    ('Tidewrought', 't1', 'tide/FlakSwivelUnit', 'anti-air'),
    ('Tidewrought', 't1', 'tide/MudskipperSloopUnit', 'raider'),
    ('Tidewrought', 't1', 'tide/TidePriestUnit', 'support'),
    ('Tidewrought', 'worker2', 'tide/HarbourmasterUnit', 'builder'),
    # the Drydock's Kraken Caller, Mortar Galley and Sky Corsair are not listed yet: two of the three deal splash
    # (Bombard shells, bomb lines) the per-target DPS column can't see, and adding them drags the T2 median down far
    # enough to flag every other faction's T2 - list them once the table models splash
    ('Tidewrought', 't3', 'tide/LeviathanUnit', 'experimental'),
    # Stormcallers (slices 1-3)
    ('Stormcallers', 'worker', 'storm/GeomancerUnit', 'builder'),
    ('Stormcallers', 't1', 'storm/PaperCraneUnit', 'scout'),
    ('Stormcallers', 't1', 'storm/BladeDancerUnit', 'raider'),
    ('Stormcallers', 't1', 'storm/FireAdeptUnit', 'skirmisher'),
    ('Stormcallers', 't1', 'storm/JadeGuardUnit', 'line'),
    ('Stormcallers', 't1', 'storm/WindMonkUnit', 'anti-air'),
    ('Stormcallers', 't1', 'storm/FrostAdeptUnit', 'skirmisher'),
    ('Stormcallers', 't1', 'storm/RocketBatteryUnit', 'artillery'),
    ('Stormcallers', 't1', 'storm/InkScribeUnit', 'support'),
    # slice 3: the Celestial Observatory's T2 (the Thunder Dragon's chain and the Gravity Monk's pull are spells the
    # per-target DPS column can't see, so both are casters)
    ('Stormcallers', 'worker2', 'storm/LeyMasterUnit', 'builder'),
    ('Stormcallers', 't2', 'storm/ThunderDragonUnit', 'caster'),
    ('Stormcallers', 't2', 'storm/EarthshakerUnit', 'tank'),
    ('Stormcallers', 't2', 'storm/GravityMonkUnit', 'caster'),
    ('Stormcallers', 't2', 'storm/MirrorMageUnit', 'support'),
    # slice 4: the Seer and the Lantern Fortress do not fight (the Fortress is bought for its dome); the Phoenix's
    # rebirth and the Celestial Dragon's Tempest are what the table can't see
    ('Stormcallers', 't2', 'storm/SeerUnit', 'scout'),
    ('Stormcallers', 't2', 'storm/LanternFortressUnit', 'support'),
    ('Stormcallers', 't2', 'storm/PhoenixUnit', 'skirmisher'),
    ('Stormcallers', 't3', 'storm/CelestialDragonUnit', 'experimental'),
]

# roles whose raw DPS/HP per metal is not meant to match the tier: they pay for an ability, mobility or vision
# debuffer: most of its damage is a damage-over-time effect the table can't see (Bogged poison arrows, Wither
#   Skeleton's stacking wither + weakness); merger: slimes/magma cubes spawn at size 2 and grow by eating each
#   other (225 HP at size 6), so their value is in the merge; mount: the Hoglin carries a rider (Eject)
ROLE_EXEMPT = {'builder', 'scout', 'support', 'caster', 'suicide', 'experimental', 'anti-swarm', 'debuffer',
               'merger', 'mount'}

# static defences and economy buildings (class path, kind)
BUILDINGS = [
    ('villagers/Watchtower', 'tower'),
    ('monsters/DarkWatchtower', 'tower'),
    ('verdant/StormOak', 'tower'),
    ('shared/MetalExtractor', 'eco'),
    ('shared/WindGenerator', 'eco'),
    ('shared/EnergyConverter', 'eco'),
    ('shared/StorageVault', 'eco'),
]

# stats the builder can't express: slimes spawn at size 2 (SlimeUnit.getMaxHealthForSize(2) = 50 HP,
# attackDamagePerSize * 2), and the Blaze's fireball does no direct hit - it sets the target burning
# (vanilla fire = 1 damage/s, BlazeUnitFireball.FIRE_SECONDS), so its sustained DPS is ~1
OVERRIDES = {
    'SlimeUnit': {'maxHealth': 50.0},
    'MagmaCubeUnit': {'maxHealth': 50.0},
    'BlazeUnit': {'attackDamage': 1.0},
    # attacksPerSecond excludes the crossbow's 25-tick charge: 20 / (20 / 0.632 + 25) = 0.353 real shots a second
    'PillagerUnit': {'attacksPerSecond': 0.353},
    # every third swing adds bigHitDamageMod (3): 7 + 3 / 3 = 8 average
    'MarauderUnit': {'attackDamage': 8.0},
}

STAT_FIELDS = ('attackDamage', 'attacksPerSecond', 'maxHealth', 'armorValue', 'movementSpeed', 'attackRange',
               'rangedResist')

_files = {}
def _read(path):
    if path not in _files:
        with open(path) as f:
            _files[path] = f.read()
    return _files[path]

_class_index = None
def _find_class(name):
    """path of the first .java file named <name>.java under SRC"""
    global _class_index
    if _class_index is None:
        _class_index = {}
        for dp, _, fns in os.walk(SRC):
            for fn in fns:
                if fn.endswith('.java'):
                    _class_index.setdefault(fn[:-5], os.path.join(dp, fn))
    return _class_index.get(name)

_CONST_RE = r'(?:public|private|protected|static|final|\s)+(?:float|double|int|long)\s+{name}\s*=\s*([^;]+);'

def _parent(path):
    """source path of the class this one extends, when it's in our tree (SkeletonUnit for BoggedUnit)"""
    m = re.search(r'class\s+\w+\s+extends\s+(\w+)', _read(path))
    return _find_class(m.group(1)) if m else None

def _resolve(name, path, depth=0):
    """value of constant <name> declared in <path> or, failing that, in a superclass"""
    if depth > 8:
        raise ValueError(f'expression too deep: {name}')
    while path:
        m = re.search(_CONST_RE.format(name=re.escape(name)), _read(path))
        if m:
            return _eval(m.group(1), path, depth + 1)
        path = _parent(path)
    return None

# well-known engine constants the attribute builders reference
_KNOWN = {'Unit.DEFAULT_SIGHT_RANGE': 0.0}

def _eval(expr, path, depth=0):
    expr = expr.strip()
    expr = re.sub(r'\(\s*(?:float|double|int)\s*\)', '', expr)          # casts
    expr = re.sub(r'(\d+(?:\.\d+)?)[fFdDL]\b', r'\1', expr)              # literal suffixes

    def sub(m):
        token = m.group(0)
        if token in _KNOWN:
            return repr(_KNOWN[token])
        if '.' in token:
            cls, field = token.rsplit('.', 1)
            cls = cls.split('.')[-1]
            p = _find_class(cls)
            v = _resolve(field, p, depth) if p else None
        else:
            v = _resolve(token, path, depth)
        if v is None:
            raise ValueError(f'unresolved {token} in {os.path.basename(path)}')
        return repr(float(v))
    expr = re.sub(r'\b(?:[A-Za-z_]\w*\.)*[A-Za-z_]\w*\b', sub, expr)
    return float(eval(expr, {'__builtins__': {}}, {}))

# createAttributes() keys we report -> our stat name. The custom AttributeRegistrar values are what the RTS
# combat code reads (attack damage/speed/range); vanilla MAX_HEALTH / MOVEMENT_SPEED / ARMOR are live stats.
_ATTR_KEYS = {
    'Attributes.MAX_HEALTH': 'maxHealth',
    'Attributes.MOVEMENT_SPEED': 'movementSpeed',
    'Attributes.ARMOR': 'armorValue',
    'AttributeRegistrar.ATTACK_DAMAGE.get()': 'attackDamage',
    'AttributeRegistrar.ATTACKS_PER_SECOND.get()': 'attacksPerSecond',
    'AttributeRegistrar.ATTACK_RANGE.get()': 'attackRange',
    'AttributeRegistrar.RANGED_DAMAGE_RESIST.get()': 'rangedResist',
}

def _split_add_args(body):
    """yield (key, expr) for each .add(key, expr) in a builder chain, honouring nested parentheses"""
    i = 0
    while True:
        i = body.find('.add(', i)
        if i < 0:
            return
        j, depth, start = i + 5, 1, i + 5
        while depth and j < len(body):
            if body[j] == '(':
                depth += 1
            elif body[j] == ')':
                depth -= 1
            j += 1
        args = body[start:j - 1]
        d, cut = 0, -1
        for k, ch in enumerate(args):
            if ch == '(':
                d += 1
            elif ch == ')':
                d -= 1
            elif ch == ',' and d == 0:
                cut = k
                break
        if cut > 0:
            yield args[:cut].strip(), args[cut + 1:].strip()
        i = j

def attributes(path, depth=0):
    """the unit's attribute values as its createAttributes() builder sets them (later .add wins), following
    delegation (RoyalArchitect -> Villager.createAttributes()) and inheritance when a class has none"""
    if depth > 5 or not path:
        return {}
    src = _read(path)
    m = re.search(r'static\s+AttributeSupplier\.Builder\s+createAttributes\s*\(\s*\)\s*\{(.*?)\n\s*\}', src, re.S)
    if not m:
        return attributes(_parent(path), depth + 1)
    body = m.group(1)
    out = {}
    deleg = re.search(r'return\s+(\w+)\.createAttributes\(\)', body)
    if deleg and deleg.group(1) not in ('Unit', 'Mob', 'Monster', 'super'):
        out.update(attributes(_find_class(deleg.group(1)), depth + 1))
    for key, expr in _split_add_args(body):
        stat = _ATTR_KEYS.get(key)
        if stat:
            out[stat] = _eval(expr, path)
    return out

# Java's Math.round rounds halves up; Python's round() rounds them to even, which mispriced x.5 cases by a step
def _round_to(v, step): return int(math.floor(v / step + 0.5)) * step
def to_metal(food, wood, ore): return _round_to(ore + food * 0.6 + wood * 0.3, 5)
def to_energy(food, wood, ore): return _round_to(wood * 1.5 + food * 0.8, 10)

def load_costs():
    cfg = _read(os.path.join(SRC, 'config/ReignOfNetherCommonConfigs.java'))
    out = {}
    for m in re.finditer(r'ResourceCostConfigEntry (\w+) = ResourceCostConfigEntry\.\w+\(\s*([-\d]+)\s*,\s*([-\d]+)\s*,\s*([-\d]+)\s*,\s*([-\d]+)', cfg):
        food, wood, ore, secs = (int(x) for x in m.groups()[1:])
        out[m.group(1)] = dict(metal=to_metal(food, wood, ore), energy=to_energy(food, wood, ore), seconds=secs)
    return out

def _cost_key(path):
    m = re.search(r'ResourceCost\s+cost\s*=\s*ResourceCosts\.(\w+)', _read(path))
    return m.group(1) if m else None

def unit_stats():
    costs = load_costs()
    rows, errors = [], []
    for faction, tier, cls, role in ROSTER:
        path = os.path.join(SRC, 'unit/units', cls + '.java')
        name = os.path.basename(cls)
        if not os.path.exists(path):
            errors.append(f'missing unit class {cls}')
            continue
        try:
            attrs = attributes(path)
        except ValueError as e:
            errors.append(f'{name}: {e}')
            attrs = {}
        attrs.update(OVERRIDES.get(name, {}))
        st = {f: float(attrs.get(f, 0.0)) for f in STAT_FIELDS}
        if not st['maxHealth']:
            errors.append(f'{name}: no maxHealth found')
        prod = path.replace('Unit.java', 'Prod.java')
        key = _cost_key(prod) if os.path.exists(prod) else None
        cost = costs.get(key) if key else None
        if not cost:
            errors.append(f'{name}: no cost (Prod key {key})')
            cost = dict(metal=0, energy=0, seconds=0)
        src = _read(path)
        flying = 'FlyingMoveToTargetGoal' in src
        ranged = st['attackRange'] >= 5
        dps = st['attackDamage'] * st['attacksPerSecond']
        m = cost['metal'] or 1
        flags = []
        # melee units can't hit flyers (AttackerUnit / AbstractMeleeAttackUnitGoal), ranged attackers can
        if role == 'anti-air' or (ranged and dps > 0):
            flags.append('AA' if role == 'anti-air' else 'hits-air')
        if role == 'artillery' or st['attackRange'] >= 20:
            flags.append('artillery')
        if role in ('support', 'caster'):
            flags.append('support')
        if flying:
            flags.append('flying')
        rows.append(dict(faction=faction, tier=tier, unit=name.replace('Unit', ''), role=role, key=key,
                         metal=cost['metal'], energy=cost['energy'], build_s=cost['seconds'],
                         hp=st['maxHealth'], armor=st['armorValue'], rres=st['rangedResist'], dmg=st['attackDamage'],
                         aps=st['attacksPerSecond'], range=st['attackRange'], speed=st['movementSpeed'],
                         dps=dps, dps100=dps * 100 / m, hp100=st['maxHealth'] * 100 / m, flags=flags))
    return rows, errors

def building_stats():
    costs = load_costs()
    rows, errors = [], []
    for cls, kind in BUILDINGS:
        path = os.path.join(SRC, 'building/buildings', cls + '.java')
        name = os.path.basename(cls)
        src = _read(path)
        hp = re.search(r'this\.maxHealth\s*=\s*([\d.]+)', src)
        rng = re.search(r'int get(?:Attack)?Range\([^)]*\)\s*\{\s*return\s+(\d+)', src)
        mi = re.search(r'this\.metalIncome\s*=\s*([\d.]+)', src)
        ei = re.search(r'this\.energyIncome\s*=\s*([\d.]+)', src)
        key = _cost_key(path)
        cost = costs.get(key, dict(metal=0, energy=0, seconds=0))
        if not hp:
            errors.append(f'{name}: no maxHealth')
        rows.append(dict(building=name, kind=kind, metal=cost['metal'], energy=cost['energy'],
                         build_s=cost['seconds'], hp=float(hp.group(1)) if hp else 0,
                         range=int(rng.group(1)) if rng else None,
                         metal_in=float(mi.group(1)) if mi else 0, energy_in=float(ei.group(1)) if ei else 0))
    return rows, errors

if __name__ == '__main__':
    import json
    u, e1 = unit_stats()
    b, e2 = building_stats()
    print(json.dumps(dict(units=u, buildings=b, errors=e1 + e2), indent=1))
