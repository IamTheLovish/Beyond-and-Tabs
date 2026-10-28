"""Phase 1 analysis: exp1 CSVs + curves -> markdown tables and PNG charts (lab only, needs pandas + matplotlib).
Usage: python3 tools/mapgen_sim/analyse.py EXP_DIR OUT_DIR [CURVES_CSV]
  EXP_DIR: the CSVs of `run.sh lab GenExperiments EXP_DIR n shape contrast seeds threads simRuns`, every cell
  CURVES_CSV: `run.sh lab GenCurves CURVES_CSV seeds` (optional)
"""
import glob, os, sys
import pandas as pd
import numpy as np
import matplotlib
matplotlib.use('Agg')
import matplotlib.pyplot as plt

EXP = sys.argv[1]
OUT = sys.argv[2]
os.makedirs(OUT, exist_ok=True)

# reference palette, light mode (dataviz skill): slots 1-3 validate all-pairs
S1, S2, S3 = '#2a78d6', '#eb6834', '#1baf7a'
INK, INK2, GRID, SURF = '#0b0b0b', '#52514e', '#e4e3df', '#fcfcfb'
plt.rcParams.update({'font.family': 'DejaVu Sans', 'font.size': 10, 'axes.edgecolor': GRID, 'axes.labelcolor': INK2,
                     'xtick.color': INK2, 'ytick.color': INK2, 'axes.facecolor': SURF, 'figure.facecolor': SURF,
                     'axes.grid': True, 'grid.color': GRID, 'grid.linewidth': 0.8, 'axes.axisbelow': True})

rows = pd.concat([pd.read_csv(f) for f in glob.glob(os.path.join(EXP, 'n*_*.csv')) if not f.endswith('_seats.csv')])
seats = pd.concat([pd.read_csv(f) for f in glob.glob(os.path.join(EXP, 'n*_seats.csv'))])
STRATS = ['NONE', 'NUDGE', 'REROLL', 'SWAP', 'SWAP_NUDGE', 'REROLL_NUDGE']
CONTR = ['MIRROR', 'PROSPECTOR_WARDEN', 'OPEN_BASTION', 'RIVER', 'MIXED']
md = []

def pct(x):
    return f'{100 * x:.0f}%'

def summary(df):
    g = df.groupby('strategy')
    t = pd.DataFrame({
        'median spread': g.spread.median().map(lambda v: f'{100 * v:.1f}%'),
        'p90 spread': g.spread.quantile(0.9).map(lambda v: f'{100 * v:.1f}%'),
        'within 5%': g.spread.apply(lambda s: (s <= 0.05).mean()).map(pct),
        'team spread med': g.teamSpread.median().map(lambda v: f'{100 * v:.1f}%'),
        'robust median': g.robMedian.median().map(lambda v: f'{100 * v:.1f}%'),
        'robust within 5%': g.robWithin5.mean().map(pct),
        'sim spread med': g.simSpread.median().map(lambda v: '' if np.isnan(v) else f'{100 * v:.1f}%'),
        'sim team med': g.simTeam.median().map(lambda v: '' if np.isnan(v) else f'{100 * v:.1f}%'),
        'gen ms med': g.ms.median().map(lambda v: f'{v:.0f}'),
        'valid': g.valid.mean().map(pct),
    })
    return t.reindex([s for s in STRATS if s in t.index])

for n in sorted(rows.n.unique()):
    label = '1v1' if n == 2 else f'{n // 2}v{n // 2}'
    df = rows[(rows.n == n) & (rows.contrast != 'MIRROR')]
    md.append(f'\n### {label}: all asymmetric contrasts, round + square ({df.seed.nunique()} seeds x strategies)\n')
    md.append(summary(df).to_markdown())
    mir = rows[(rows.n == n) & (rows.contrast == 'MIRROR') & (rows.strategy == 'NONE')]
    if len(mir):
        md.append(f'\nMirror control ({label}, {len(mir)} maps): seat spread median {100 * mir.spread.median():.1f}%, '
                  f'team {100 * mir.teamSpread.median():.1f}%, sim spread {100 * mir.simSpread.median():.1f}%, '
                  f'sim team {100 * mir.simTeam.median():.1f}%.')
    # per contrast, the shipped strategy
    t = rows[(rows.n == n) & (rows.strategy.isin(['NONE', 'SWAP_NUDGE']))].groupby(['contrast', 'shape', 'strategy']).agg(
        spread=('spread', 'median'), within5=('spread', lambda s: (s <= 0.05).mean()), robust=('robWithin5', 'mean'),
        sim=('simSpread', 'median'), simTeam=('simTeam', 'median'), patches=('patchesTotal', 'median'),
        sites=('sites', 'median'), holeP90=('holeP90', 'median'), holeMax=('holeMax', 'median'))
    md.append(f'\n#### {label} by contrast and shape (NONE vs SWAP_NUDGE)\n')
    md.append(t.round(3).to_markdown())

# modifiers' effect on the raw spread
mods = rows[(rows.strategy == 'NONE') & (rows.contrast != 'MIRROR')].copy()
mods['mods'] = mods['mods'].fillna('')
mrows = []
for mo in ['RICH_VEIN', 'LEY_LINE', 'GEOTHERMAL', 'GOLD_RUSH', 'MERCENARY_ROAD', 'GARRISON_RUINS', 'WATCH_RIDGE',
           'HERO_RELIC', 'HYDRA_LAIR', 'FOG_VALLEYS']:
    for n in sorted(rows.n.unique()):
        has = mods[(mods.n == n) & mods['mods'].str.contains(mo)]
        hasnt = mods[(mods.n == n) & ~mods['mods'].str.contains(mo)]
        sn = rows[(rows.strategy == 'SWAP_NUDGE') & (rows.n == n) & rows['mods'].fillna('').str.contains(mo)]
        mrows.append((mo, n, len(has), has.spread.median(), hasnt.spread.median(), sn.robWithin5.mean()))
mt = pd.DataFrame(mrows, columns=['modifier', 'n', 'maps', 'raw spread with', 'raw spread without', 'robust within 5% (SWAP_NUDGE)'])
md.append('\n### Modifiers: raw spread with / without, and robustness after balancing\n')
md.append(mt.round(3).to_markdown(index=False))

# score vs sim, 1v1 raw maps
s1 = seats[(seats.n == 2) & (seats.strategy == 'NONE') & seats.simStrength.notna()]
pairs = []
for (sh, c, seed), g in s1.groupby(['shape', 'contrast', 'seed']):
    if len(g) != 2 or c == 'MIRROR':
        continue
    a, b = g.iloc[0], g.iloc[1]
    if a.archetype > b.archetype:
        a, b = b, a
    m = (a.total + b.total) / 2
    ms = (a.simStrength + b.simStrength) / 2
    pairs.append((c, sh, (a.total - b.total) / m, (a.simStrength - b.simStrength) / ms, a.archetype, b.archetype))
pr = pd.DataFrame(pairs, columns=['contrast', 'shape', 'dScore', 'dSim', 'A', 'B'])
md.append('\n### Score vs sim (1v1, raw maps): seat difference A-B (A first alphabetically)\n')
corr = pr.groupby('contrast').apply(lambda g: pd.Series({'maps': len(g), 'corr': g.dScore.corr(g.dSim),
    'slope': np.polyfit(g.dScore, g.dSim, 1)[0], 'mean dScore': g.dScore.mean(), 'mean dSim': g.dSim.mean(),
    'A': g.A.iloc[0], 'B': g.B.iloc[0]}))
md.append(corr.to_markdown())
# balanced maps: the sim's side bias
sb = seats[(seats.n == 2) & (seats.strategy == 'SWAP_NUDGE') & seats.simStrength.notna()]
bias = []
for (sh, c, seed), g in sb.groupby(['shape', 'contrast', 'seed']):
    if len(g) != 2 or c == 'MIRROR' or c == 'MIXED':
        continue
    a, b = g.iloc[0], g.iloc[1]
    if a.archetype > b.archetype:
        a, b = b, a
    bias.append((c, a.archetype, b.archetype, (a.simStrength - b.simStrength) / ((a.simStrength + b.simStrength) / 2)))
bt = pd.DataFrame(bias, columns=['contrast', 'A', 'B', 'dSim'])
md.append('\n### After SWAP_NUDGE (1v1): does the sim still favour a side?\n')
md.append(bt.groupby(['contrast', 'A', 'B']).dSim.agg(['count', 'mean', 'std', lambda s: (s > 0).mean()]).rename(
    columns={'<lambda_0>': 'A ahead share'}).round(3).to_markdown())

# 4v4 seat roles: front vs back
s8 = seats[(seats.n == 8)]
if len(s8):
    rr = s8.groupby(['strategy', 'front']).agg(total=('total', 'mean'), metal=('metal', 'mean'), patches=('patches', 'mean'),
        sim=('simStrength', 'mean')).round(2)
    md.append('\n### 4v4 seats: front (enemy neighbour) vs back\n')
    md.append(rr.to_markdown())

open(os.path.join(OUT, 'tables.md'), 'w').write('\n'.join(md) + '\n')

# ------------------------------------------------------------------ charts
def bars(ax, labels, series, names, colors):
    x = np.arange(len(labels))
    k = len(series)
    w = 0.8 / k
    for i, (vals, nm, col) in enumerate(zip(series, names, colors)):
        bb = ax.bar(x + (i - (k - 1) / 2) * w, vals, w * 0.92, label=nm, color=col)
        for rect, v in zip(bb, vals):
            ax.text(rect.get_x() + rect.get_width() / 2, rect.get_height() + 0.015, f'{100 * v:.0f}', ha='center',
                    va='bottom', fontsize=8, color=INK2)
    ax.set_xticks(x)
    ax.set_xticklabels(labels, rotation=0, fontsize=8)
    ax.set_ylim(0, 1.12)
    ax.yaxis.set_major_formatter(matplotlib.ticker.PercentFormatter(1.0))

fig, axes = plt.subplots(1, 2, figsize=(12, 4.2), sharey=True)
for ax, n in zip(axes, [2, 8]):
    df = rows[(rows.n == n) & (rows.contrast != 'MIRROR')]
    if not len(df):
        continue
    st = [s for s in STRATS if s in df.strategy.unique()]
    central = [(df[df.strategy == s].spread <= 0.05).mean() for s in st]
    robust = [df[df.strategy == s].robWithin5.mean() for s in st]
    bars(ax, [s.replace('_', '\n') for s in st], [central, robust], ['central weights', 'under weight uncertainty (40 draws)'],
         [S1, S2])
    ax.set_title(('1v1' if n == 2 else '4v4') + ': maps with every seat within 5%', color=INK, fontsize=11, loc='left')
h, lb = axes[0].get_legend_handles_labels()
fig.legend(h, lb, frameon=False, fontsize=9, loc='lower center', ncol=2)
fig.tight_layout(rect=(0, 0.07, 1, 1))
fig.savefig(os.path.join(OUT, 'chart_strategies.png'), dpi=130)

fig, axes = plt.subplots(1, 2, figsize=(12, 4.0), sharey=True)
for ax, n in zip(axes, [2, 8]):
    df = rows[(rows.n == n) & (rows.strategy == 'SWAP_NUDGE')]
    if not len(df):
        continue
    cs = [c for c in CONTR if c in df.contrast.unique()]
    central = [(df[df.contrast == c].spread <= 0.05).mean() for c in cs]
    robust = [df[df.contrast == c].robWithin5.mean() for c in cs]
    bars(ax, [c.replace('_', '\n') for c in cs], [central, robust], ['central weights', 'under weight uncertainty'], [S1, S2])
    ax.set_title(('1v1' if n == 2 else '4v4') + ' after SWAP_NUDGE, by contrast', color=INK, fontsize=11, loc='left')
h, lb = axes[0].get_legend_handles_labels()
fig.legend(h, lb, frameon=False, fontsize=9, loc='lower center', ncol=2)
fig.tight_layout(rect=(0, 0.07, 1, 1))
fig.savefig(os.path.join(OUT, 'chart_contrasts.png'), dpi=130)

if len(pr):
    fig, ax = plt.subplots(figsize=(6.4, 5))
    for c, col in zip(['PROSPECTOR_WARDEN', 'OPEN_BASTION', 'RIVER'], [S1, S2, S3]):
        g = pr[pr.contrast == c]
        ax.scatter(g.dScore * 100, g.dSim * 100, s=14, color=col, alpha=0.75, edgecolors=SURF, linewidths=0.6,
                   label=f'{c} (r={g.dScore.corr(g.dSim):.2f})')
    ax.axhline(0, color=INK2, lw=0.8)
    ax.axvline(0, color=INK2, lw=0.8)
    ax.set_xlabel('score difference between the seats, % of mean')
    ax.set_ylabel('sim strength difference at 15:00, % of mean')
    ax.set_title('1v1 raw maps: the score predicts the sim, loosely', color=INK, fontsize=11, loc='left')
    ax.legend(frameon=False, fontsize=8)
    fig.tight_layout()
    fig.savefig(os.path.join(OUT, 'chart_score_vs_sim.png'), dpi=130)

cv = sys.argv[3] if len(sys.argv) > 3 else ''
if os.path.exists(cv):
    d = pd.read_csv(cv)
    fig, ax = plt.subplots(figsize=(7, 4))
    pairsC = [('PROSPECTOR_WARDEN', 'WARDEN', 'PROSPECTOR', S1), ('OPEN_BASTION', 'BASTION', 'OPEN', S2),
              ('RIVER', 'MOATED', 'BANKS', S3)]
    for c, a, b, col in pairsC:
        g = d[d.contrast == c]
        pa = g[g.archetype == a].groupby('minute').army.mean()
        pb = g[g.archetype == b].groupby('minute').army.mean()
        gap = ((pa - pb) / ((pa + pb) / 2) * 100).iloc[2:]
        ax.plot(gap.index, gap.values, color=col, lw=2, label=f'{a.title()} ahead of {b.title()}')
        ax.annotate(f'{gap.values[-1]:+.1f}%', (gap.index[-1], gap.values[-1]), xytext=(4, 0), textcoords='offset points',
                    color=INK2, fontsize=8, va='center')
    ax.axhline(0, color=INK2, lw=0.8)
    ax.set_xlim(1.5, 15)
    ax.set_xlabel('minute')
    ax.set_ylabel('army value gap, % of mean')
    ax.set_title('Balanced 1v1 maps in the sim: who is ahead, minute by minute (60 seeds each)', color=INK, fontsize=10, loc='left')
    ax.legend(frameon=False, fontsize=8)
    fig.tight_layout()
    fig.savefig(os.path.join(OUT, 'chart_sim_curves.png'), dpi=130)
print(open(os.path.join(OUT, 'tables.md')).read()[:6000])
