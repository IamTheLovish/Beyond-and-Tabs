"""Engine Shed (Brassworks T1 vehicle lab) r11 detail - STUB (tests first): returns r10's build so the r11 camera-truth
tests (tools/test_engine_shed_r11.py) can be shown red on r10 before the r11 generator is written."""
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import engine_shed_massing as M  # noqa: E402
import lab_vehicle_brass as L  # noqa: E402

OUT = "design/vehicle_labs/brass_t1_r11.nbt"
META = dict(tower=M.AF["tower"], cornice=15, eave=6, stack_from=7,
            stacks=[dict(base=s["base"], shaft=s["shaft"], top=s["top"]) for s in M.AF["stacks"]])


def detail():
    return L.detail()
