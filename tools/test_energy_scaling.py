#!/usr/bin/env python3
"""
Energy scales like BAR (lovish, Oct 29). Written before the change, from the rules, against the Java source (through
tools/energy_sim.py and tools/unit_stats.py): on main d2fb33f0 / b04b9d20 every test here failed.

  - the opening: a greedy opener (6 extractors and a lab, no energy) runs out of energy by its 3rd extractor, while a
    BAR opener (2-3 winds or 2 solars round the first extractors) has its first lab near 1:30;
  - energy per metal grows with the tier: T1 ~8, T2 ~15, T3 ~18 (lovish: "in between" Claude's 5/10/15 and BAR's 10/20/20);
  - higher-tier labs build faster and so drain more energy a second (BAR: 150 / 600 / 1800 build power);
  - the converter gets better with the tier (BAR: 70 -> 58 energy a metal), the T1 one stays the last resort;
  - a T1 extractor drains 3 energy/s, BAR's value: expansion needs energy.

Run: python3 -m unittest discover -s tools -p 'test_energy_scaling.py'   (CI runs every tools/test_*.py)
"""
import os
import statistics
import sys
import unittest

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import energy_sim  # noqa: E402
import unit_stats  # noqa: E402

N = energy_sim.numbers()


class Opening(unittest.TestCase):
    def test_a_greedy_opener_runs_out_of_energy_by_its_third_extractor(self):
        r = energy_sim.run(N, 'greedy')
        self.assertIsNotNone(r['first_stall'], 'six extractors and a lab with no energy building never stalled: %s' % r)
        self.assertLessEqual(r['first_stall'][1], 3, 'the greedy opener stalled only after %d extractors' % r['first_stall'][1])

    def test_a_wind_opener_has_its_first_lab_near_a_minute_and_a_half(self):
        r = energy_sim.run(N, 'wind')
        self.assertIsNotNone(r['lab'])
        self.assertTrue(75 <= r['lab'] <= 105, 'wind opener: first lab at %ss, want ~90' % r['lab'])

    def test_a_solar_opener_beats_the_greedy_one(self):
        # solars cost metal and build time instead of energy (BAR): slower to raise than winds, but no energy stall
        greedy, solar = energy_sim.run(N, 'greedy'), energy_sim.run(N, 'solar')
        self.assertLess(solar['lab'], greedy['lab'], 'solar %s vs greedy %s' % (solar, greedy))
        self.assertLess(solar['stalled'], greedy['stalled'], 'solar %s vs greedy %s' % (solar, greedy))

    def test_the_opening_drain_is_about_three_times_the_start_income(self):
        # BAR: the commander alone on mexes drains ~2.8x its 30 E/s - the bank lasts to about the 3rd-4th mex
        m, e, bs = N['items']['mex']
        crew = N['commander_bp'] + 4 * N['worker_bp']
        drain = crew * e / bs + N['worker'][1] / N['worker'][2]
        self.assertGreaterEqual(drain / N['base_e'], 2.5, 'the opening crew drains %.0f E/s against %.0f' % (drain, N['base_e']))


class Tiers(unittest.TestCase):
    def setUp(self):
        self.units, errors = unit_stats.unit_stats()
        self.assertEqual(errors, [])

    def median(self, *tiers):
        return statistics.median(u['energy'] / u['metal'] for u in self.units if u['tier'] in tiers and u['metal'] > 0)

    def test_energy_per_metal_grows_with_the_tier(self):
        t1, t2, t3 = self.median('t1'), self.median('t2', 'worker2', 'engineer'), self.median('t3')
        for got, want, name in ((t1, 8, 'T1'), (t2, 15, 'T2'), (t3, 18, 'T3')):
            self.assertAlmostEqual(got, want, delta=want * 0.05, msg='%s units: median energy per metal %.2f, want ~%d' % (name, got, want))

    def test_higher_tier_labs_build_faster(self):
        p = N['lab_power']
        self.assertLess(p[1], p[2], 'a T2 lab builds no faster than a T1 lab: %s' % p)
        self.assertLess(p[2], p[3], 'a T3 lab builds no faster than a T2 lab: %s' % p)

    def test_higher_tier_labs_drain_more_energy_a_second(self):
        t1 = energy_sim.per_lab_drain(N, 'VINDICATOR', 1)
        t2 = energy_sim.per_lab_drain(N, 'RAVAGER', 2)
        t3 = energy_sim.per_lab_drain(N, 'SUN_COLOSSUS', 3)
        self.assertGreaterEqual(t2, 4 * t1, 'T2 lab %.0f E/s vs T1 %.0f' % (t2, t1))
        self.assertGreaterEqual(t3, 2.5 * t2, 'T3 lab %.0f E/s vs T2 %.0f' % (t3, t2))

    def test_the_converter_gets_better_with_the_tier(self):
        c = N['converter']
        self.assertGreater(c[1], c[2], 'energy per metal %s' % c)
        self.assertGreater(c[2], c[3], 'energy per metal %s' % c)

    def test_a_t1_extractor_drains_bars_three_energy_a_second(self):
        self.assertEqual(N['mex_upkeep'], 3.0)


if __name__ == '__main__':
    unittest.main()
