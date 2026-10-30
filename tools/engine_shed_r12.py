"""Engine Shed r12 - STUB for the red run: returns r11's build, so tools/test_engine_shed_r12.py is run red on r11
before any r12 change is built."""
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import engine_shed_r11 as R  # noqa: E402

OUT = "design/vehicle_labs/brass_t1_r12.nbt"
META = dict(R.META)


def detail():
    return R.detail()
